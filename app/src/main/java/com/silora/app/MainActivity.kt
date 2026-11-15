package com.silora.app

import android.app.Activity
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var imageReply: ((JSONObject)->Unit)?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled((applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
        createWebView()
    }
    private fun createWebView() {
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this)).build()
        web = WebView(this)
        web.webChromeClient = object:android.webkit.WebChromeClient() {
            override fun onJsConfirm(view:WebView,url:String,message:String,result:android.webkit.JsResult):Boolean {
                android.app.AlertDialog.Builder(this@MainActivity).setTitle("静律").setMessage(message)
                    .setPositiveButton("确定") { _,_->result.confirm() }.setNegativeButton("取消") { _,_->result.cancel() }
                    .setOnCancelListener { result.cancel() }.show()
                return true
            }
        }
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(web,"Silora",setOf("https://appassets.androidplatform.net")) { _, message, origin, mainFrame, reply ->
                if(!mainFrame || origin.toString()!="https://appassets.androidplatform.net")return@addWebMessageListener
                val raw=message.data ?: return@addWebMessageListener
                if(raw.length>1_000_000)return@addWebMessageListener
                var requestId=""
                try {
                    StrictJson.validate(raw)
                    val request=JSONObject(raw);requestId=request.getString("requestId")
                    val action=request.getString("action")
                    if(action=="ai_cancel") {
                        DeepSeek.get(this).cancel()
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action.startsWith("ai_")) {
                        check(DeepSeek.busy.compareAndSet(false,true)) { "已有 AI 请求正在处理，请等待或取消" }
                        DeepSeek.get(this).begin()
                        if(action=="ai_image") {
                            imageReply={ result -> reply.postMessage(result.put("requestId",requestId).toString()) }
                            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addCategory(Intent.CATEGORY_OPENABLE),5001) }
                            catch(e:Exception) { DeepSeek.busy.set(false);imageReply=null;throw IllegalStateException("系统文件选择器不可用") }
                        } else DeepSeek.executor.execute {
                            val response=JSONObject().put("requestId",requestId)
                            try { response.put("ok",true).put("data",DeepSeek.get(this).command(request)) }
                            catch(e:Exception) { response.put("ok",false).put("error",e.cause?.message ?: e.message ?: "AI 操作失败") }
                            finally { DeepSeek.busy.set(false) }
                            runOnUiThread { if(!isDestroyed)reply.postMessage(response.toString()) }
                        }
                    } else if(action=="permission") {
                        if (request.getString("permission") == "notifications" && android.os.Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                            (!getPreferences(MODE_PRIVATE).getBoolean("notificationAsked", false) || shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS))) {
                            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationAsked", true).apply()
                            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 5002)
                            reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                            return@addWebMessageListener
                        }
                        val intent=when(request.getString("permission")) {
                            "exact" -> if(android.os.Build.VERSION.SDK_INT>=31)Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:$packageName")) else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName"))
                            "policy" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            "notifications" -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            else -> throw IllegalArgumentException("未知权限")
                        }
                        startActivity(intent)
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else Core.executor.execute {
                        val response=JSONObject().put("requestId",requestId)
                        try { response.put("ok",true).put("data",Core.get(this).command(request)) }
                        catch(e: Exception) { response.put("ok",false).put("error",e.message ?: "原生操作失败") }
                        runOnUiThread { if(!isDestroyed)reply.postMessage(response.toString()) }
                    }
                } catch(e: Exception) { reply.postMessage(JSONObject().put("requestId",requestId).put("ok",false).put("error",e.message ?: "操作失败").toString()) }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                view.destroy()
                createWebView()
                return true
            }
        }
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }
    @Deprecated("Activity back callback for initial target SDK")
    override fun onBackPressed() { web.evaluateJavascript("window.goBack()") { handled -> if (handled != "true") finish() } }
    override fun onResume() {
        super.onResume()
        Core.executor.execute {
            try {
                val core=Core.get(this);core.reconcile("应用恢复")
                val data=core.snapshot().toString()
                runOnUiThread { if(!isDestroyed)web.evaluateJavascript("window.nativeUpdate && window.nativeUpdate($data)",null) }
            } catch(e: Exception) { android.util.Log.e("Silora","应用恢复同步失败",e) }
        }
    }
    @Deprecated("Framework picker callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=5001)return
        val callback=imageReply;imageReply=null
        val uris = buildList {
            data?.clipData?.let { clip -> for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri) }
            if (isEmpty()) data?.data?.let { add(it) }
        }.distinct()
        if(resultCode!=RESULT_OK || uris.isEmpty()) {
            DeepSeek.busy.set(false);callback?.invoke(JSONObject().put("ok",true).put("data",JSONObject().put("cancelled",true)));return
        }
        DeepSeek.executor.execute {
            val result=JSONObject()
            try { result.put("ok",true).put("data",DeepSeek.get(this).selectImages(uris)) }
            catch(e:Exception) { result.put("ok",false).put("error",e.message ?: "无法读取图片") }
            finally { DeepSeek.busy.set(false) }
            runOnUiThread { if(!isDestroyed)callback?.invoke(result) }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 5002) web.evaluateJavascript("window.refresh && window.refresh()", null)
    }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}

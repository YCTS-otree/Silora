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
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var web: WebView
    private var imageReply: ((JSONObject)->Unit)?=null
    private var logPickerOpen = false
    private val permissionGuide by lazy { PermissionGuide(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        logPickerOpen = savedInstanceState?.getBoolean("logPickerOpen") ?: false
        AppLog.record("activity_create")
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
                        AppLog.operation(action, true)
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action.startsWith("ai_")) {
                        check(DeepSeek.busy.compareAndSet(false,true)) { "已有 AI 请求正在处理，请等待或取消" }
                        DeepSeek.get(this).begin()
                        if(action=="ai_image") {
                            imageReply={ result -> AppLog.operation(action, result.optBoolean("ok")); reply.postMessage(result.put("requestId",requestId).toString()) }
                            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true).addCategory(Intent.CATEGORY_OPENABLE),5001) }
                            catch(e:Exception) { DeepSeek.busy.set(false);imageReply=null;throw IllegalStateException("系统文件选择器不可用") }
                        } else DeepSeek.executor.execute {
                            val response=JSONObject().put("requestId",requestId)
                            try { response.put("ok",true).put("data",DeepSeek.get(this).command(request) { phase ->
                                if(action == "ai_chat") runOnUiThread {
                                    val safePhase = phase.takeIf { it in setOf("preparing", "sending", "waiting", "receiving", "validating") } ?: "unknown"
                                    AppLog.record("ai_progress phase=$safePhase")
                                    if(!isDestroyed) reply.postMessage(JSONObject().put("requestId",requestId).put("progress",phase).toString())
                                }
                            }); AppLog.operation(action, true) }
                            catch(e:Exception) { AppLog.operation(action, false); AppLog.exception("ai_command", e);response.put("ok",false).put("error",e.cause?.message ?: e.message ?: "AI 操作失败") }
                            finally { DeepSeek.busy.set(false) }
                            runOnUiThread { if(!isDestroyed)reply.postMessage(response.toString()) }
                        }
                    } else if(action=="update_check") {
                        val id = requestId
                        UpdateChecker.executor.execute {
                            val data = UpdateChecker.check(request.optBoolean("manual", false))
                            runOnUiThread { if(!isDestroyed) reply.postMessage(JSONObject().put("requestId",id).put("ok",true).put("data",data).toString()) }
                        }
                    } else if(action=="update_open") {
                        val url = request.getString("url")
                        require(UpdateChecker.safePage(url)) { "无效的更新下载地址" }
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action=="guard_set") {
                        val enabled = request.get("enabled"); require(enabled is Boolean)
                        GuardService.setEnabled(this, enabled)
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",GuardService.snapshot(this)).toString())
                    } else if(action=="background_settings") {
                        val intent = if (request.optString("kind") == "battery") Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                        startActivity(intent)
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action=="log_status") {
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",AppLog.snapshot()).toString())
                    } else if(action=="log_export") {
                        check(!logPickerOpen && !AppLog.isExporting()) { "日志导出正在进行，请先完成或取消" }
                        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/zip").putExtra(Intent.EXTRA_TITLE, AppLog.exportName()), 5003)
                        logPickerOpen = true
                        AppLog.record("log_export choosing_destination")
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action=="open_api_help") {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://api-docs.deepseek.com/zh-cn/")).addCategory(Intent.CATEGORY_BROWSABLE))
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else if(action=="permission") {
                        permissionGuide.open(request.getString("permission"))
                        reply.postMessage(JSONObject().put("requestId",requestId).put("ok",true).put("data",JSONObject()).toString())
                    } else Core.executor.execute {
                        val response=JSONObject().put("requestId",requestId)
                        try { response.put("ok",true).put("data",Core.get(this).command(request));AppLog.operation(action, true) }
                        catch(e: Exception) { AppLog.operation(action, false);AppLog.exception("core_command", e);response.put("ok",false).put("error",e.message ?: "原生操作失败") }
                        runOnUiThread { if(!isDestroyed)reply.postMessage(response.toString()) }
                    }
                } catch(e: Exception) { AppLog.exception("bridge", e);reply.postMessage(JSONObject().put("requestId",requestId).put("ok",false).put("error",e.message ?: "操作失败").toString()) }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                return loader.shouldInterceptRequest(request.url)
                    ?: WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(byteArrayOf()))
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                AppLog.record("webview_process_gone crashed=${detail.didCrash()}")
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
    override fun onStart() { super.onStart(); permissionGuide.onStart() }
    override fun onPause() { permissionGuide.onPause(); super.onPause() }
    override fun onResume() {
        super.onResume()
        GuardService.ensureStarted(this)
        permissionGuide.onResume()
        Core.executor.execute {
            try {
                val core=Core.get(this);core.reconcile("应用恢复")
                val data=core.snapshot().toString()
                runOnUiThread { if(!isDestroyed)web.evaluateJavascript("window.nativeUpdate && window.nativeUpdate($data)",null) }
            } catch(e: Exception) { AppLog.exception("activity_resume",e) }
        }
    }
    @Deprecated("Framework picker callback")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode == 5003) {
            logPickerOpen = false
            val uri = data?.data
            if (resultCode != RESULT_OK || uri == null) {
                AppLog.record("log_export cancelled")
                logExportResult("cancelled"); return
            }
            if (uri.scheme != "content") { logExportResult("failed"); return }
            try {
                logExportResult("writing")
                AppLog.export(this, uri) { success -> runOnUiThread {
                    if (!isDestroyed) logExportResult(if (success) "done" else "failed")
                } }
            } catch (e: Exception) { AppLog.exception("log_export_start", e);logExportResult("failed") }
            return
        }
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
            catch(e:Exception) { AppLog.exception("image_selection",e);result.put("ok",false).put("error",e.message ?: "无法读取图片") }
            finally { DeepSeek.busy.set(false) }
            runOnUiThread { if(!isDestroyed)callback?.invoke(result) }
        }
    }
    private fun logExportResult(status: String) {
        web.evaluateJavascript("window.logExportResult && window.logExportResult(${JSONObject.quote(status)})", null)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("logPickerOpen", logPickerOpen)
        super.onSaveInstanceState(outState)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 5002) {
            permissionGuide.onPermissionResult()
            web.evaluateJavascript("window.refresh && window.refresh()", null)
        }
    }
    override fun onDestroy() { permissionGuide.destroy(); web.destroy(); super.onDestroy() }
}

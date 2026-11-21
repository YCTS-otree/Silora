package com.silora.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import android.webkit.WebResourceResponse
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

internal class ChatGallery(private val context:Context) {
    private fun open(id:String):InputStream? {
        require(id.matches(Regex("[a-zA-Z0-9-]{1,80}")))
        val file=File(context.filesDir,"conversation_images/$id.png")
        return if(file.isFile) file.inputStream() else DeepSeek.get(context).pendingImage(id)?.inputStream()
    }
    fun resource(path:String):WebResourceResponse {
        val missing={ WebResourceResponse("text/plain","UTF-8",404,"Not Found",mapOf("Cache-Control" to "no-store"),null) }
        val match=Regex("(full|thumb)/([a-zA-Z0-9-]{1,80})").matchEntire(path) ?: return missing()
        val input=open(match.groupValues[2]) ?: return missing()
        val thumb=match.groupValues[1]=="thumb"
        val stream=if(!thumb) input else input.use {
            val bitmap=BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=8 }) ?: return missing()
            try { ByteArrayOutputStream().apply { bitmap.compress(Bitmap.CompressFormat.PNG,100,this) }.toByteArray().inputStream() }
            finally {bitmap.recycle()}
        }
        return WebResourceResponse("image/png",null,200,"OK",mapOf("Cache-Control" to "no-store"),stream)
    }
    fun save(id:String) {
        val resolver=context.contentResolver
        (open(id) ?: error("本机没有这张图片")).use { src ->
            val values=ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME,"Silora-${System.currentTimeMillis()}.png")
                put(MediaStore.MediaColumns.MIME_TYPE,"image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH,"Pictures/Silora")
                put(MediaStore.MediaColumns.IS_PENDING,1)
            }
            val uri=resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("无法创建相册文件")
            try {
                checkNotNull(resolver.openOutputStream(uri)).use { src.copyTo(it) }
                values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0)
                check(resolver.update(uri,values,null,null)==1)
            } catch(e:Exception){resolver.delete(uri,null,null);throw e}
        }
    }
}

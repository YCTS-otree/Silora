package com.silora.app

import android.app.Instrumentation
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import java.io.ByteArrayOutputStream

class GalleryChecks:Instrumentation(){
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        try{
            val ctx=targetContext
            check(ctx.packageName.endsWith(".devicecheck"))
            val bytes=ByteArrayOutputStream().apply{
                val bitmap=Bitmap.createBitmap(96,128,Bitmap.Config.ARGB_8888)
                bitmap.compress(Bitmap.CompressFormat.PNG,100,this);bitmap.recycle()
            }.toByteArray()
            val archive=ConversationImages(ctx);archive.save("gallery-check",bytes)
            val gallery=ChatGallery(ctx)
            check(gallery.resource("full/gallery-check").data.use{it.readBytes()}.contentEquals(bytes))
            val thumb=gallery.resource("thumb/gallery-check").data.use{BitmapFactory.decodeStream(it)}
            check(thumb.width==12 && thumb.height==16);thumb.recycle()
            check(gallery.resource("full/../silora.db").statusCode==404)
            gallery.save("gallery-check")
            val base=MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val resolver=ctx.contentResolver
            val uri=resolver.query(base,arrayOf("_id"),"owner_package_name=? AND relative_path=?",arrayOf(ctx.packageName,"Pictures/Silora/"),"_id DESC")!!.use{
                check(it.moveToFirst());ContentUris.withAppendedId(base,it.getLong(0))
            }
            try{check(resolver.openInputStream(uri)!!.use{it.readBytes()}.contentEquals(bytes))}
            finally{resolver.delete(uri,null,null)}
            archive.clear();check(gallery.resource("full/gallery-check").statusCode==404)
            finish(0,Bundle().apply{putString("stream","PASS aspect ratio, full image, original PNG export, missing file and path bounds")})
        }catch(e:Throwable){finish(1,Bundle().apply{putString("stream","FAIL "+e.stackTraceToString())})}
    }
}

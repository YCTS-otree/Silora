package com.silora.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Transcript paging and the model's working window have independent cursors. */
internal class ChatHistory(context:Context):SQLiteOpenHelper(context,"chat_history.db",null,1) {
    private val prefs=context.getSharedPreferences("conversation",Context.MODE_PRIVATE)
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE turns(id INTEGER PRIMARY KEY AUTOINCREMENT, u TEXT NOT NULL, a TEXT NOT NULL)")
        db.execSQL("CREATE TABLE memory(k INTEGER PRIMARY KEY, cutoff INTEGER NOT NULL, digest TEXT NOT NULL, usage INTEGER NOT NULL)")
        db.execSQL("INSERT INTO memory VALUES(1,0,'',0)")
        val legacy=JSONArray(prefs.getString("history","[]"))
        for(i in 0 until legacy.length()-1 step 2) insert(db,legacy.getJSONObject(i),legacy.getJSONObject(i+1))
    }
    override fun onUpgrade(db:SQLiteDatabase,old:Int,new:Int)=error("未知聊天记录版本")
    private fun insert(db:SQLiteDatabase,u:JSONObject,a:JSONObject) {
        db.execSQL("INSERT INTO turns(u,a) VALUES(?,?)",arrayOf(u.toString(),a.toString()))
    }
    fun clear(){val db=writableDatabase;db.beginTransaction();try{
        db.delete("turns",null,null);db.execSQL("UPDATE memory SET cutoff=0,digest='',usage=0")
        db.setTransactionSuccessful()
    }finally{db.endTransaction()}}
    private fun brief(a:JSONObject):String {
        val d=JSONObject(a.getString("content"))
        return d.optString("question").ifBlank { d.optString("summary").ifBlank { "历史候选方案，可在计划表核对已保存的安排。" } }
    }
    fun page(before:Long):JSONObject {
        val list=mutableListOf<JSONObject>()
        readableDatabase.rawQuery("SELECT id,u,a FROM turns WHERE id<? ORDER BY id DESC LIMIT 3",arrayOf(before.toString())).use { c ->
            while(c.moveToNext())list.add(JSONObject().put("id",c.getLong(0)).put("user",JSONObject(c.getString(1)).getString("content"))
                .put("imageIds",JSONObject(c.getString(1)).optJSONArray("image_ids") ?: JSONArray()).put("assistant",brief(JSONObject(c.getString(2)))))
        }
        return JSONObject().put("turns",JSONArray(list.take(2).reversed())).put("more",list.size>2)
    }
    data class Window(val messages:JSONArray,val digest:String)
    fun window(extra:Int):Window {
        compact(extra)
        val db=readableDatabase
        val digest=db.rawQuery("SELECT digest FROM memory",null).use{it.moveToFirst();it.getString(0)}
        val msgs=JSONArray()
        db.rawQuery("SELECT u,a FROM turns WHERE id>(SELECT cutoff FROM memory) ORDER BY id",null).use { c->
            while(c.moveToNext()){msgs.put(JSONObject(c.getString(0)));msgs.put(JSONObject(c.getString(1)))}
        }
        return Window(msgs,digest)
    }
    fun append(u:JSONObject,a:JSONObject,usage:Int):Long {
        val db=writableDatabase;db.beginTransaction()
        try {
            insert(db,u,a)
            db.execSQL("UPDATE memory SET usage=?",arrayOf(usage))
            compact(0)
            val id=db.rawQuery("SELECT max(id) FROM turns",null).use{it.moveToFirst();it.getLong(0)}
            db.setTransactionSuccessful();return id
        }finally{db.endTransaction()}
    }
    private fun compact(extra:Int){
        val db=writableDatabase
        db.rawQuery("SELECT cutoff,digest,usage FROM memory",null).use { m->
            m.moveToFirst();val cutoff=m.getLong(0)
            val size=db.rawQuery("SELECT COALESCE(SUM(length(u)+length(a)),0) FROM turns WHERE id>?",arrayOf(cutoff.toString())).use{it.moveToFirst();it.getLong(0)}
            // API usage when available; text-size estimate before the next call (never count base64).
            val estimate=size/2+m.getString(1).length/2+extra
            if(maxOf(m.getLong(2)+extra,estimate)<500_000)return
            val keep=db.rawQuery("SELECT id FROM turns WHERE id>? ORDER BY id DESC LIMIT 1 OFFSET 1",arrayOf(cutoff.toString())).use{if(it.moveToFirst())it.getLong(0) else return}
            var last=cutoff;var digest=m.getString(1)
            db.rawQuery("SELECT id,u,a FROM turns WHERE id>? AND id<? ORDER BY id",arrayOf(cutoff.toString(),keep.toString())).use { c->
                while(c.moveToNext()){
                    last=c.getLong(0); val u=JSONObject(c.getString(1)).getString("content")
                    val a=JSONObject(c.getString(2));val image=JSONObject(a.getString("content")).optString("image_context")
                    digest=(digest+"\n用户："+u.take(400)+"\n候选："+brief(a).take(500)+" "+image.take(2000)).takeLast(12000)
                }
            }
            if(last>cutoff)db.execSQL("UPDATE memory SET cutoff=?,digest=?,usage=0",arrayOf(last,digest))
        }
    }
    fun recentImages():JSONArray {
        val a=JSONArray()
        readableDatabase.rawQuery("SELECT u FROM turns WHERE id IN (SELECT id FROM turns WHERE u LIKE '%\"image_ids\":[\"%' ORDER BY id DESC LIMIT 6) ORDER BY id",null).use{c->while(c.moveToNext())a.put(JSONObject(c.getString(0)))}
        return a
    }
}

package com.silora.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class PlanStore(context: Context, databaseName: String = "silora.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE plans(id TEXT PRIMARY KEY, start INTEGER NOT NULL, end INTEGER NOT NULL, mode INTEGER NOT NULL, label TEXT NOT NULL, enabled INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL)")
        db.execSQL("CREATE TABLE events(seq INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, plan TEXT, revision INTEGER NOT NULL, result TEXT NOT NULL, detail TEXT NOT NULL, actual INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX events_plan ON events(plan,seq)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("不支持的数据库升级") }
    fun get(key: String, fallback: String = ""): String = readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use { if (it.moveToFirst()) it.getString(0) else fallback }
    fun put(key: String, value: String) { writableDatabase.insertWithOnConflict("meta", null, ContentValues().apply { put("k",key);put("v",value) }, SQLiteDatabase.CONFLICT_REPLACE) }
    fun revision() = get("revision", "0").toLong()
    fun plans(): List<Plan> = readableDatabase.rawQuery("SELECT id,start,end,mode,label,enabled FROM plans ORDER BY start", null).use { c ->
        buildList { while(c.moveToNext()) add(Plan(c.getString(0),c.getLong(1),c.getLong(2),c.getInt(3),c.getString(4),c.getInt(5)==1)) }
    }
    fun replace(plans: List<Plan>, expectedRevision: Long, metadata: Map<String, String> = emptyMap()) {
        PlanRules.validate(plans)
        val db=writableDatabase
        db.beginTransaction()
        try {
            require(revision()==expectedRevision) { "计划已变化，请刷新后重试" }
            db.delete("plans",null,null)
            plans.forEach { p -> db.insertOrThrow("plans",null,ContentValues().apply {
                put("id",p.id);put("start",p.start);put("end",p.end);put("mode",p.mode);put("label",p.label);put("enabled",if(p.enabled)1 else 0)
            }) }
            put("revision",(expectedRevision+1).toString())
            metadata.forEach { (key, value) -> put(key, value) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun saveTable(table: JSONObject?, expectedVersion: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            require(get("periodVersion", "0").toLong() == expectedVersion) { "课时表已变化，请重新打开后编辑" }
            val normalized = table?.let { AcademicJson.tableJson(AcademicJson.table(it)).toString() }.orEmpty()
            put("periodTable", normalized)
            put("periodVersion", (expectedVersion + 1).toString())
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun state(): RunState {
        val o=JSONObject(get("runtime","{}"))
        return RunState(o.optBoolean("session"),o.optInt("before",2),if(o.has("expected"))o.getInt("expected") else null,o.optBoolean("suppressed"),o.optString("activeId").ifBlank { null })
    }
    fun state(s: RunState) { put("runtime",JSONObject().put("session",s.session).put("before",s.before).put("expected",s.expected).put("suppressed",s.suppressed).put("activeId",s.activeId).toString()) }
    fun log(plan: String?, result: String, detail: String, actual: Int) {
        writableDatabase.insertOrThrow("events",null,ContentValues().apply { put("at",System.currentTimeMillis());put("plan",plan);put("revision",revision());put("result",result);put("detail",detail);put("actual",actual) })
        writableDatabase.execSQL("DELETE FROM events WHERE seq <= (SELECT MAX(seq)-500 FROM events)")
    }
    fun events(): JSONArray = readableDatabase.rawQuery("SELECT at,plan,revision,result,detail,actual FROM events ORDER BY seq DESC LIMIT 100",null).use { c ->
        JSONArray().apply { while(c.moveToNext())put(JSONObject().put("at",c.getLong(0)).put("plan",c.getString(1)).put("revision",c.getLong(2)).put("result",c.getString(3)).put("detail",c.getString(4)).put("actual",c.getInt(5))) }
    }
}

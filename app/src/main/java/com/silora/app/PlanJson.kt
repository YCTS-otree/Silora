package com.silora.app

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

object PlanJson {
    private fun string(o:JSONObject,key:String):String { val v=o.get(key);require(v is String){"$key 必须是字符串"};return v }
    fun mode(text: String) = when(text) { "ring"->2;"vibrate"->1;"silent"->0;else->throw IllegalArgumentException("未知铃声模式") }
    fun name(mode: Int) = arrayOf("silent","vibrate","ring")[mode]
    fun minutes(o: JSONObject, default: Int = 0): Int {
        if (!o.has("notice_minutes")) return default
        val v=o.get("notice_minutes")
        require(v is Int && v in 0..1440) { "提前分钟必须是 0–1440 的整数，0 表示关闭" }
        return v
    }
    fun short(o: JSONObject):String = if(o.has("location_short")) string(o,"location_short").trim().also { require(it.length<=16) { "卡片地点简称最多 16 个字符" } } else ""
    fun location(o: JSONObject) = if(o.has("location")) string(o,"location").trim().also { require(it.length <= 160) } else ""
    fun read(o: JSONObject): Plan {
        require(!o.has("enabled") || o.get("enabled") is Boolean) { "enabled 必须是布尔值" }
        return Plan(if(o.has("id"))string(o,"id") else UUID.randomUUID().toString(),PlanRules.instant(string(o,"start")),PlanRules.instant(string(o,"end")),mode(string(o,"mode")),string(o,"label").trim(),if(o.has("enabled"))o.getBoolean("enabled") else true, location(o), minutes(o),short(o))
    }
    fun write(p: Plan): JSONObject = JSONObject().put("id",p.id).put("start",Instant.ofEpochMilli(p.start).toString()).put("end",Instant.ofEpochMilli(p.end).toString()).put("mode",name(p.mode)).put("label",p.label).put("enabled",p.enabled).put("location",p.location).put("location_short",p.locationShort).put("notice_minutes",p.noticeMinutes)
    fun array(plans: List<Plan>) = JSONArray().apply { plans.forEach { put(write(it)) } }
    fun candidate(o: JSONObject, current: List<Plan>): List<Plan> {
        require(o.opt("schema_version")==1) { "不支持的 schema_version" }
        require(o.getString("status")=="plan_ready") { "此响应未生成可执行计划" }
        ZoneId.of(o.getString("time_zone"))
        val a=o.getJSONArray("intervals")
        require(a.length()<=2000) { "记录过多" }
        val incoming=(0 until a.length()).map { a.getJSONObject(it).let { row -> read(row).copy(noticeMinutes=minutes(row, minutes(o))) } }
        val next=when(o.getString("operation")) {
            "replace" -> {
                val scope=o.getJSONObject("scope"); val start=PlanRules.instant(scope.getString("start")); val end=PlanRules.instant(scope.getString("end"))
                require(start<end && end-start<=366L*86400000) { "替换范围无效" }
                require(incoming.all { it.start>=start && it.end<=end }) { "计划超出替换范围" }
                require(current.none { it.start<end && it.end>start && (it.start<start || it.end>end) }) { "替换范围切断已有时间段，请先编辑该时间段" }
                current.filterNot { it.start<end && it.end>start }+incoming
            }
            "add" -> current+incoming
            else -> throw IllegalArgumentException("JSON 仅支持 add / replace；编辑删除请使用计划表")
        }
        return PlanRules.validate(next)
    }
}

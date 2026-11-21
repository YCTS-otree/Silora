package com.silora.app

/** A classroom belongs to an occurrence, not necessarily to every class with the same name. */
internal object LocationEdits {
    data class Edit(val id:String?, val start:Long?, val location:String, val label:String?=null, val end:Long?=null)
    fun resolve(plans:List<Plan>, edits:List<Edit>):Map<String,String> {
        require(edits.size in 1..2000) { "没有可核对的地点修改" }
        val changes=mutableMapOf<String,String>()
        for(edit in edits) {
            require(edit.location.isNotBlank() && edit.location.length<=160 && (edit.id!=null || edit.start!=null)) { "每个地点必须对应具体课程时段" }
            val match=plans.singleOrNull { (edit.id==null || edit.id==it.id) && (edit.start==null || edit.start==it.start) &&
                (edit.label==null || edit.label==it.label) && (edit.end==null || edit.end==it.end) }
                ?: error("教室未能唯一对应已有课程，请核对日期、开始时间和课名")
            require(changes.put(match.id,edit.location)==null) { "同一时段收到多条地点，请核对" }
        }
        return changes
    }
}

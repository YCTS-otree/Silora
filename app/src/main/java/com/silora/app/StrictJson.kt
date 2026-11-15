package com.silora.app

/** Syntax gate ahead of Android's lenient JSONObject parser. No evaluation or coercion. */
object StrictJson {
    fun validate(text:String) {
        require(text.length<=1_000_000) { "JSON 过大" }
        Parser(text).parse()
    }
    private class Parser(val text:String) {
        var pos=0
        fun whitespace(){while(pos<text.length && text[pos] in " \t\r\n")pos++}
        fun take(c:Char):Boolean {whitespace();if(pos<text.length&&text[pos]==c){pos++;return true};return false}
        fun need(c:Char){require(take(c)){"JSON 格式错误（位置 $pos）"}}
        fun string():String {
            need('"');val result=StringBuilder()
            while(pos<text.length) {
                val c=text[pos++]
                if(c=='"')return result.toString()
                require(c>=' ') { "JSON 字符串含控制字符" }
                if(c!='\\'){result.append(c);continue}
                require(pos<text.length){"JSON 字符串不完整"}
                val e=text[pos++]
                result.append(when(e){'"','\\','/'->e;'b'->'\b';'f'->'\u000c';'n'->'\n';'r'->'\r';'t'->'\t';'u'->{require(pos+4<=text.length);val hex=text.substring(pos,pos+4);require(hex.all { it in '0'..'9'||it in 'a'..'f'||it in 'A'..'F' }){"无效 Unicode 转义"};pos+=4;hex.toInt(16).toChar()};else->throw IllegalArgumentException("无效 JSON 转义")})
            }
            throw IllegalArgumentException("JSON 字符串不完整")
        }
        fun value(depth:Int) {
            require(depth<=32){"JSON 嵌套过深"};whitespace();require(pos<text.length){"JSON 不完整"}
            when(text[pos]) {
                '{'->{pos++;val keys=HashSet<String>();if(take('}'))return;do{val key=string();require(keys.add(key)){"JSON 字段重复"};need(':');value(depth+1)}while(take(','));need('}')}
                '['->{pos++;if(take(']'))return;do{value(depth+1)}while(take(','));need(']')}
                '"'->string()
                't','f','n'->{val literal=when(text[pos]){'t'->"true";'f'->"false";else->"null"};require(text.startsWith(literal,pos)){"JSON 常量无效"};pos+=literal.length}
                else->{val number=Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").find(text,pos);require(number!=null&&number.range.first==pos){"JSON 值无效"};pos+=number.value.length}
            }
        }
        fun parse(){value(0);whitespace();require(pos==text.length){"JSON 包含额外内容"}}
    }
}

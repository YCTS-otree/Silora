# Silora 1.2.0 课表与本地规则协议

沿用 `schema_version: 1`，增加 `period_table_ready`、`recurrence_ready` 和 `script_ready` 状态。完整字段见 `plan.schema.json` 与实际使用的 `app/src/main/assets/system-prompt.txt`；Kotlin 校验器是最终依据。

## 课时表

任何状态均可带 `period_table`：

```json
{"name":"学校作息","time_zone":"Asia/Shanghai","periods":[{"number":1,"start":"08:00","end":"08:45"},{"number":2,"start":"08:55","end":"09:40"}]}
```

原生层校验 IANA 时区、1–32 个唯一课节、同日 HH:mm、开始早于结束和无重叠。首次带上传图片得到的有效表自动保存；已存在且不同的表只提供候选，用户确认覆盖。文字提取也需要确认。保存的是结构化时间，不保留图片文件。课时表和执行计划独立，修改课时表仅影响后续生成。

## 周期规则

```json
{
  "schema_version":1,
  "status":"recurrence_ready",
  "recurrence":{
    "name":"本学期课程",
    "anchor_monday":"2026-11-16",
    "valid_from":"2026-11-16",
    "valid_until":"2026-12-13",
    "time_zone":"Asia/Shanghai",
    "entries":[
      {"weekday":1,"weeks":"odd","start":"08:00","end":"09:40","mode":"vibrate","label":"数学"},
      {"weekday":2,"weeks":"even","start":"08:55","end":"09:40","mode":"vibrate","label":"物理"}
    ]
  }
}
```

周一为 1、周日为 7；weeks 为 all / odd / even。anchor 必须周一，学期第 1 周由此开始，跨公历年仍连续计算。生效日期首尾均包含，from 不早于 anchor，until 距 anchor 不超过 366 天。最多 100 条规则、2000 个计划；时区偏移不唯一或不存在的夏令时端点直接拒绝。冲突、范围错误或空结果不保存。

## 本地脚本

状态改为 `script_ready`，对象键改为 `script`，公共日期 / 名称 / 时区字段相同；用 `source` 字符串代替 entries：

```text
when weekday == 1 and week % 2 == 1 and date != "2026-11-30" -> vibrate("08:00", "09:40", "数学")
when weekday == 3 and week >= 2 and week <= 4 -> silent("10:00", "10:45", "实验")
```

- 一行一条 `when 条件 -> ring/vibrate/silent("HH:mm", "HH:mm", "标签")`；字符串遵循 JSON 转义。
- 条件为 `always` 或整数变量 week / weekday / month / day 的比较，可先 `% 1–366`；date 与双引号 YYYY-MM-DD 比较。
- 比较符为 `== != >= <= > <`；`and` 优先于 `or`，没有括号；运算符两侧按示例保留空格。
- 支持空行和以 `#` 开头的整行注释。最多 20000 字符、100 条规则、每条 20 个条件。
- 无循环、赋值、通用函数、导入、文件、网络、密钥或 Android API。Kotlin 解释器只遍历有限日期并产生候选区间，后台调度执行已保存区间，不运行动态宿主代码。

## 本地桥接接口

接口仅向应用自带 WebView 页面开放，并非外部网络服务。调用入口为现有 `native(action, payload)`。

| action | payload | 结果 / 行为 |
| --- | --- | --- |
| period_save | table, periodVersion | 校验后保存课时表；拒绝过期版本 |
| period_clear | periodVersion | 删除课时表，不删除计划 |
| automation_preview | rule | 返回规范化 rule、全部 candidate、revision，不写入 |
| automation_save | rule, revision | 再次校验，事务替换旧规则生成的计划与规则元数据 |
| automation_clear | revision | 删除当前规则及其区间，保留手动计划 |
| reminder_settings | enabled, hours | hours 为 2–6 个唯一 08–18 整数，保存并重新调度 |
| reminder_test | 无 | 检查授权并发送可见测试通知 |

当前仅保存一套周期或脚本；由本地分配稳定规则 ID。修改必须给出完整规则，不能遗漏旧课程。保存保留手动计划，并对完整集合进行冲突校验。重新生成会覆盖原规则各次课程的独立编辑。脚本 / 周期源码、展开区间、课时表在 SQLite 中持久化；没有模型请求也可执行。

## 提醒语义

当前计划最后一个启用区间的结束时间定义到期点。只选择到期前 24 小时内的配置整点，默认 09、14、18 点；设备本地时区 08:00–18:59 才可投递。以结束时间和时点去重；重启后多个错过时点只补最近一次，夜间和到期后不补发。修改计划改变到期点则重新计算。

独立 AlarmManager 非精确窗口负责提醒，通知需系统及渠道授权；省电或强行停止可导致延迟 / 缺失，不能承诺每个时点准时送达。单元测试覆盖时钟边界；真机验证渠道、权限和测试通知，完整 24 小时多次送达尚未实测。

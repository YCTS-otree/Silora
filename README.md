# Silora · 静律

**v1.6.0**：新增主题日期 / 时间和确认弹层、可选后台守护；保留私有诊断日志与手动 ZIP 导出；支持可跳过、可继续、可重温的交互式新手教学；可见发送状态、保留图片上下文、先给方案后确认；支持课时表、单双周、本地脚本和到期提醒。系统 WebView UI，蓝色夜空 / 绿色自然主题。无需自建服务器；已保存计划离线运行。

## 已实现

- SQLite 持久化计划、执行状态、策略与最近 500 条执行记录；事务更新与乐观版本检查。
- 手动新增、编辑、删除、启停，跨日绝对时间，JSON 导入预览 / 确认 / 导出。
- 严格 JSON 语法、字段、UTC 偏移、时间范围、重复及冲突校验。区间采用 `[start,end)`。
- AlarmManager `setExactAndAllowWhileIdle` 安排下一个边界，广播接收器重新计算当前状态；不依赖 WebView 或轮询；可选前台守护服务补充进程保护和单个边界定时回调。
- 每个闹钟关联计划集合版本、计划 ID 和时间标识；旧事件 / 重复事件失效。重启、升级、时间 / 时区变化及授权恢复后重新调度，绝对时间不平移。
- AudioManager 真实切换响铃 / 振动 / 静音并读取实际结果；连续区间不错误恢复。支持结束恢复策略、尊重外部修改 / 下一节点恢复 / 自动计划优先。
- 精确闹钟、通知策略访问的状态显示和系统设置入口；未获权限时明确显示不可用。
- DeepSeek `deepseek-flash`：免费余额接口验证 Key、文字与系统文件选择器图片输入、多轮追问、JSON 候选计划、确认后保存、Token 用量、取消请求及错误分类。
- 密钥由 Android Keystore AES-GCM 加密；网络只在 Kotlin 层使用 OkHttp 访问固定 DeepSeek HTTPS 地址，禁用重定向。AI 线程独立于调度线程。
- 本地对话保留最近 12 条消息，待发图片位于进程内存，点击发送立即清空输入框；成功发送的图片进入应用私有对话目录，与输入框分离。最近 12 条历史中的最多 6 张、合计 16 MB 图片随续聊重新提供给模型，重开应用后仍可引用；超出保留范围或清除对话时删除。失败或取消恢复待发附件。PNG 处理最长边 2560 像素、每张最多 8 MB；最多 6 张、合计最多 16 MB，保留文字清晰度；大图建议裁剪。

首次从上传图片识别出的有效课时表会自动保存；已有课时表的覆盖需确认。候选铃声计划必须经过原生校验及明确确认。旧版 Demo 的 localStorage 演示记录不会自动迁移为可执行计划。

## 时间弹层与后台守护（v1.6.0）

手动计划和课时表 / 周期日期时间统一使用主题弹层：月历、24 小时直接输入、小时 / 分钟加减、快捷分钟。取消和返回键不改原值；跨天与无效日期仍严格校验。确认保存展示名称、起止日期时间、铃声模式和时区，允许返回修改；删除、覆盖、清除等确认也使用主题弹层。确认前捕获版本，后台数据变化时由原生版本检查拒绝旧操作。

设置 → 后台守护 → 开启后台守护。默认关闭，开启后使用低优先级常驻通知，可在通知中关闭；保存的计划继续由精确闹钟执行。服务使用 START_STICKY 和 stopWithTask=false，活动恢复 / 开机时按用户设置启动；无 WakeLock、无循环轮询，仅为下一个时间边界安排一次内存回调。系统休眠仍依靠 AlarmManager 唤醒。相同进程内的重复同步不反复撤销同一闹钟，冷启动会重新注册。

界面展示系统报告的后台限制、电池优化豁免及通知状态，提供电池优化和应用详情入口。请在手机管家中允许自启动与后台活动，必要时锁定最近任务。系统接口的“未报告限制”不代表厂商没有额外限制。前台服务不能保证绕过厂商清理；系统“强行停止”后需重新打开。部分设备会把划掉最近任务也处理为停止应用，这种情况同样需要系统侧放行。参考 [前台服务类型](https://developer.android.com/develop/background-work/services/fgs/service-types)、[定时调度](https://developer.android.com/develop/background-work/services/alarms) 和 [停止状态限制](https://developer.android.com/about/versions/15/behavior-changes-all#stopped-state)。本版使用 specialUse 类型；未来上架 Google Play 时需按平台要求声明用途并审核。

新增守护生命周期、最近进程退出的时间 / 原因码 / 重要性日志，不记录系统退出描述或用户正文。本轮提供的 Android 17 日志中，14:38 节点没有收到闹钟广播，14:56:43 重新打开后才补执行；旧日志不足以判断具体清理原因。

独立真机验证安装（仅用于测试，包名 com.silora.app.devicecheck，名称“静律验证”，不访问正式安装的数据 / Key）：

```powershell
gradle --offline --project-cache-dir Temp/gradle-project -PdeviceCheckBuild=true :app:assembleDebug
adb install -r Temp/build/app/outputs/apk/debug/app-debug.apk
adb shell am start -n com.silora.app.devicecheck/com.silora.app.MainActivity
# 在测试安装中授权并启用守护，测试会真实改变手机铃声。
node tests/guard-device.mjs setup
# 退到后台或进行待验证的清理，再观察约 3 分钟。
node tests/guard-device.mjs monitor
# 验证结束后卸载测试安装；分发前不带 deviceCheckBuild 重新构建。
```

`tests/dialogs-smoke.mjs` 通过已转发的 WebView 调试端口验证弹层；先暂停教学、确保无待编辑表单。保存操作使用模拟桥接，不写用户计划；验证结束比较计划版本、课时表版本和待确认方案。界面检查使用真机双主题截图和真实数字键盘。

本轮验证：43 项 JVM 测试、16 项弹层检查、原输入框 / 教学回归通过；隔离安装的 14 项 Android JSON / SQLite 检查及 7 项前台服务 / 通知关闭检查通过。Lint 0 错误、6 提示。正式安装原有计划版本 45、课时表版本 13、待确认方案和配置保留，测试安装已移除。

Android 12 华为 CET-AL00：退到后台并锁屏后，15:21:28 振动、15:22:43 静音、15:23:58 恢复响铃成功，守护持续为前台服务；通知关闭后服务停止，重复启动只保留一条通知。另一轮划掉最近任务后，系统将测试包设为 stopped=true，服务和闹钟均被清除，未能在节点切换。该设备的清理问题不能宣称已由前台服务修复；需要系统侧允许后台活动 / 自启动并避免将任务清成停止状态。未完成厂商自动省电清理、系统回收后的 START_STICKY 重启、开机恢复、长时深度休眠及 Android 14–17 实机验证。

守护原生检查使用 `-PdeviceCheckBuild=true '-PtestRunner=com.silora.app.GuardChecks' :app:assembleDebugAndroidTest`，安装测试 APK 后运行 `adb shell am instrument -w com.silora.app.devicecheck.test/com.silora.app.GuardChecks`。该入口拒绝正式包名，避免测试误改用户安装。


## 私有日志与主动导出（v1.5.0）

默认启用，无需存储权限。日志位于应用内部 `files/logs/`（主用户通常为 `/data/user/0/com.silora.app/files/logs/`），应用备份保持禁用，不自动写入下载目录或上传。设置 → 诊断日志 → 导出日志 ZIP，使用 Android 系统保存界面主动选择目的地；取消不导出，成功 / 失败有明确反馈。ZIP 包含当时保留的日志文件，不包含数据库、对话图片或密钥文件。

日志核心移植自父级 `CPVC_for_Android/app/src/main/java/top/ycts/cpvc/RollingLog.kt`，并沿用 `AppLog.kt` 的单线程写入、时间戳及有界异常类型 / 调用栈记录方式。仅将 CPVC 的公开 Downloads 存储适配器替换为 Silora 的私有文件适配器，加上显式 SAF ZIP 导出。每次进程启动新建文件，按 UTF-8 字节在超过 8 MiB 前滚动，最多保留 4 份；最旧日志自动清理。当前固定使用 CPVC 默认容量，不新增日志配置项。

记录启动版本 / Android API、权限检查、原生操作结果、铃声执行与调度状态、AI 请求阶段、HTTP 状态码 / 耗时、导出结果和 Java 未捕获异常。只记录固定操作名称和必要状态，不记录 API Key、请求头、聊天文字、图片、课表正文、接口响应正文或异常 message；未知操作名统一写为 unknown，异常最多 8 层原因、每层 32 个调用位置。写入和导出在独立线程串行执行，日志失败不会中断计划调度。

未捕获 Java 异常会尽力刷新队列（最多等待 3 秒）；系统强杀、断电或原生层崩溃前的最后排队记录不保证落盘。导出失败的目的文件可能不完整，界面会提示重试。

- 43 项 JVM 测试通过，覆盖继承自 CPVC 的文件保留 / 字节边界 / 滚动，以及私有文件筛选、ZIP 内容、写入失败、异常原文排除、未知操作名与循环原因链。
- Android 12 真机检查 `LoggingChecks` 通过：实际私有目录写入、异常原文和未知输入不进入日志、无效导出目的地返回失败且不影响继续记录。
- 真机系统文件选择器验证取消和成功保存 ZIP，解压完整性与内容检查通过；多次冷启动后实际保留 4 份。已有 Key 的免费余额接口测试成功，日志只记录状态码和耗时。
- 深 / 浅主题截图与教学回归通过；原有计划、课时表、待确认方案和配置保留。Lint 0 错误、4 提示。

真机日志检查入口：`-PtestRunner=com.silora.app.LoggingChecks`。测试不修改计划、对话或密钥，只向诊断日志追加合成测试事件。

## 交互式新手教学（v1.4.0）

首次使用显示欢迎页，支持配置并验证 DeepSeek Key；已配置的用户直接继续。Key 可显示 / 隐藏，空值不可提交，网络或认证失败可重试，余额不足单独说明。帮助链接通过外部浏览器打开官方文档，不在带原生桥的 WebView 中加载。

随后在真实界面中高亮输入框、选图入口、发送 / 待确认方案、计划表、权限和执行策略。示例按钮只填入草稿；选择文件、发送请求、确认启用计划均由用户主动操作。可以不上传图片、不调用 AI，直接了解后续功能；没有计划时展示真实空状态。

每步进度保存在 Android 本地偏好中，中断重开继续当前步骤；主动退出后不自动弹回，可从设置 → 使用帮助 → 继续上次教学恢复。完成后不再自动启动；“重温新手教学”从欢迎页重新开始，不清空 Key、计划、课时表、对话或待确认方案。输入框的未发送文字和附件仍遵循原有草稿生命周期，不属于教学进度备份。

- Android 12 真机逐步截图检查欢迎页、输入、选图、空计划、权限、执行策略及双主题；键盘弹出后重新定位输入框，根页面保持视口高度，教学面板与底部导航无重叠。
- 真机验证外部浏览器帮助、系统文件选择器取消返回、升级 / 进程重开后的进度恢复、暂停后不自动弹回、完成后不再弹出。
- `node tests/tutorial-smoke.mjs` 检查真实教学状态存储和无效输入拒绝、导航交互、跳过 / 继续 / 完成 / 重温，以及计划、课时表、候选方案和配置保持不变。Key 错误 / 网络失败 / 余额不足分支使用前端模拟响应，不替换设备现有 Key、不消耗模型额度。
- 31 项 JVM 测试、原输入框 WebView 回归及 Lint 通过（0 错误、4 提示）。截图验证机型为 Android 12，其他系统和屏幕尺寸仍需后续实机覆盖。

## 对话与确认

发送立即显示状态、等待秒数和取消入口。原生网络事件区分准备、发送、请求已发出、等待完整回复与本地校验；没有虚构完成百分比，也不把本地点击当作服务端收到。失败说明保留在界面，恢复文字和附件供重试。

模型优先把作息图和课程图合起来提出可确认方案；本周 / 下周日期由本地提供，多季节作息按目标日期选择。一次性的周课表不再自动要求学期起止日期。可合理推断的默认值和日期偏移必须在方案中说明，具体时间看不清时仍不能编造。

目标首轮给出方案、通常 3 轮内完成；第 4 轮仍不能确定时转为结果核对，列出具体缺口，停止同一需求的循环追问。这个上限约束追问，不保证任意模糊图片都能生成有效计划。

方案卡展示摘要、推断依据和全部时间明细。回复“是 / 可以 / 确认”等或点击“是，按此执行”，直接在本地确认，不额外请求 AI。计划、待覆盖课时表和结束恢复策略在一个 SQLite 事务中保存；重复确认或过期计划 / 作息版本会被拒绝。新的 AI 请求使旧方案失效。

## AI 思考设置

设置 → DeepSeek API → 思考强度，可选 low、high、max，默认 high，原生请求始终携带 `thinking.type=enabled`。不提供关闭选项；设置独立持久化，清除对话不重置。参数依照 [DeepSeek 官方说明](https://api-docs.deepseek.com/guides/thinking_mode/)。

给思考及最终 JSON 共预留最多 32768 输出 token，网络总超时 240 秒、前端等待 270 秒，仍可主动取消。高强度可能增加时间和用量，实际消耗以返回 usage 为准，不显示或保存模型思考正文。

## 课表、脚本与到期提醒

- 可同时选择课时表和课程安排图片。课时表保存各节上下课时间，后续 AI 自动引用；设置中允许编辑、覆盖、删除，修改不会反向改动已确认计划。
- 模型可输出每周 / 单双周规则；周数依据明确的“第 1 周周一”，生效范围包含结束日。缺少学期日期会追问，确认后一次展开并持久化，学期内无需每周调用模型。
- 模型也可编写 Silora 计划脚本，支持周数、星期、日期、取模和条件组合。脚本由 Kotlin 本地解释器展开成普通计划，不加载 Python / JavaScript 运行时；无文件、网络、凭据或系统 API。详见 [计划规则协议](docs/academic-protocol.md)。
- 当前支持一套周期 / 脚本，重新应用会替换原规则生成的区间并保留手动计划；对某次课程的单独编辑可能在下次重新生成时被覆盖。
- 当期结束取所有启用计划中最后一个结束时间。默认在其前 24 小时内的 09、14、18 点提醒；可设置 2–6 个 08–18 点整点。正式提醒和补发仅在 08:00–18:59，错过多个时点只补最新一次，结束后不补发。新增后续计划后按新结束时间重新计算。
- 设置中开启系统通知，并可发送测试通知；测试按钮不受白天限制。提醒使用独立的非精确定时器，系统休眠可能延迟或错过提醒，不占用铃声精确调度路径。

## 构建与安装

现有 JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20；AndroidX WebKit 1.12.1、OkHttp 4.12.0。minSdk 29 / targetSdk 34 / compileSdk 35，SDK Build Tools 35.0.0。

设置 `ANDROID_HOME` 后执行：

```powershell
.\gradlew.bat --project-cache-dir Temp/gradle-project :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
adb install -r Release/v1.6.0/Silora-v1.6.0-debug.apk
adb shell am start -n com.silora.app/.MainActivity
```

缓存齐全时增加 `--offline`。构建中间文件放在 `Temp/build/`。交付 APK 使用开发签名，不是正式发布签名。旧版本保留于各自的 `Release/v版本号/`，不得覆盖。

## 使用与授权

启动 / 从后台返回时会检查通知策略访问、精确闹钟和通知权限，缺少时主动显示授权引导。点击“开始授权”后逐项进入系统页面，返回后复查；Android 13+ 通知权限可弹系统运行时授权框，已拒绝到无法再弹窗时转设置页。拒绝或选择“稍后”会停止本轮引导，不循环弹窗。已全部授权时直接使用。无需申请“修改系统设置”（WRITE_SETTINGS）；铃声使用音频权限和通知策略访问。


1. 设置 → 精确闹钟授权，允许静律安排闹钟。
2. 设置 → 通知策略访问，授予静律访问权限。这不等于开启系统勿扰模式，也不代表媒体、闹钟等所有声音都会静音。
3. 在计划表创建时间段，核对日期 / 时区 / 模式后确认。若当前已处于区间内，会立即重新计算模式。
4. 在设置粘贴个人 DeepSeek API Key 并验证，或使用已配置 Key。可从 [DeepSeek 开放平台](https://platform.deepseek.com/) 获取；[官方 API 文档](https://api-docs.deepseek.com/zh-cn/)说明接口。
5. AI 页面发送文字或选择课表图片；必要内容直接提交到 DeepSeek。信息不足会追问，完整候选计划确认后才执行。

删除 Key 不影响已保存计划。密钥不会打包进 APK；不要提交 `.key`、签名库或凭据。Debug APK 支持 WebView 调试，仅在可信开发设备使用。

## v1.3.1 界面与授权（2026-11-16）

- 移除顶部品牌栏，版本仍可在设置 → 关于查看；页面滚动限制于中部内容区域，底部导航不随整页滚走，文本输入不再显示拖拽缩放角。
- 附件位于文本上方，取消“移除全部图片”和图片下方按钮，使用右上角 ×。缩略图最大高度由 100px 降至 90px、最大宽度 135px，按原图比例贴合，自动换行；× 保留无障碍名称与扩大的触控区域。
- Android 12 真机检查横图 / 竖图 / 方图及 6 张混排、单图移除、双主题与键盘：根页面高度随视口从 794px 到 532px 再恢复，始终没有额外滚动空间，导航底边与视口一致。
- 真机撤销精确闹钟权限后验证启动提示、稍后退出、再次启动授权和返回自动复查，结束恢复原权限；计划未变。Android 13+ 运行时通知授权分支尚未在对应系统实测。
- 构建、WebView 输入框回归及 Lint 通过（0 错误、4 提示）。

## v1.3.0 验证（2026-11-15）

- 31 项 JVM 测试通过（新增周日、本周 / 下周和跨年日期上下文）；Lint 0 错误、4 提示。
- 隔离 Android SQLite 检查：预览不执行，确认原子保存计划 / 作息 / 恢复策略，重复确认和过期版本拒绝；图片跨实例读取、数量边界及淘汰检查通过。
- 使用用户授权的冬夏作息图与课程截图，真实 API 得到 13 个连堂课时段，冬季下午第 5 节为 14:00；原图日期 11 月 9–15 日按明确要求保留，并提示已经过去。
- 更新重开后不再上传图片，只说“改为下周，其他不变”，真实 API 生成 11 月 16–22 日相同 13 个时段、冬季作息和下课响铃。实际观察到完整原生进度阶段；仅保留待确认方案，没有启用用户课程。
- WebView 检查立即反馈、请求已发出提示、失败恢复、纯图片 / 带文字附件清空，以及“是”直接本地确认；真机检查进度和方案卡，无水平溢出。

原生回归入口：`-PtestRunner=com.silora.app.ConversationChecks`。`ConversationReplay` 是显式收费的真实 API 回归入口，需要用户授权图片放在应用 `cache/Temp/replay-winter.jpg` 与 `replay-courses.jpg`；`-e nextWeek true` 验证已保存图片的无附件续聊。它会更新对话及待确认方案，但不会启用计划；不要对无关个人安装自动执行。

## v1.2.1 验证（2026-11-15）

- 原 29 项 JVM 测试通过，构建和 Lint 通过（0 错误、6 提示）。
- Android 仪器检查三档实际请求体始终启用思考，并拒绝 none / disabled / medium / 空值。
- 真机 WebView 检查三档保存、关闭思考被拒绝、纯图片 / 带文字图片立即移除、成功保持空附件、模拟失败恢复附件；更新重开后保留设置。
- 实际 high 档纯图片 DeepSeek 请求返回有效追问及 reasoning_tokens 用量，随后界面与原生附件均为 0；设置界面实际屏幕检查无水平溢出。测试未新增计划。

复测输入框可在已有 WebView 调试转发后执行 `node tests/ai-composer-smoke.mjs`（使用模拟回复，不收费；要求无待发图片）。请求体仪器检查通过 `-PtestRunner=com.silora.app.AiConfigChecks` 构建并运行同名 instrumentation。

## v1.2.0 及此前验证记录（2026-11-15）

设备：CET-AL00，Android 12 / API 31。

- v1.2.0 构建、29 项 JVM 测试通过，覆盖原时间 / 状态机 / JSON，以及单双周跨年、夏令时歧义、脚本边界 / 冲突、白天提醒和去重。
- 14 项实机检查通过：Android JSON、SQLite 事务回滚、版本冲突、旧事件 / 重复事件拦截、当前区间立即执行、手动修改保护。
- 退出界面、终止后台进程、关闭屏幕后，真实闹钟依次执行振动 → 静音 → 恢复原始响铃；系统模式值为 1 → 0 → 2，执行记录与系统实际读数一致。
- 精确闹钟授权撤销时未调度；恢复后重新调度。测试结束恢复原模式并清理测试计划。
- WebView 检查通过：三菜单、双主题、页面重载后 SQLite 数据恢复、过期版本拒绝、非法区间拒绝、用户文字安全显示。
- 真实 DeepSeek：Key 验证与余额可用、无效 Key 返回 401 且保留旧 Key、取消请求、文字追问与补充后生成计划、系统文件选择器选图和真实图片识别、候选计划界面确认后持久化并注册闹钟。
- Keystore 加密配置在应用升级后仍有效。未测试付费余额耗尽、服务端故障或所有断网场景。
- 新增 7 项 Android JSON / SQLite 检查通过；真机 WebView 检查覆盖课时表版本冲突、规则替换保留手动计划、日期排除、拒绝宿主代码和拒绝夜间配置。
- 真实 DeepSeek 联合读取两张合成课表图片，首次保存课时表并生成单双周规则；不再上传图片后成功引用本地课时表生成带排除日期的脚本。终止并重开应用后课时表和周期计划仍在。测试计划和课时表已清理。
- 真机通知权限开启、通知渠道和可见测试通知验证通过；多个白天时点 / 夜间抑制由可控时钟单元测试验证，未等待完整 24 小时实测。课时表编辑和脚本预览经屏幕检查，无水平溢出。
- Lint 无错误，保留 6 项提示：targetSdk 34、本地 UI 启用 JavaScript、两个仅持有 Application Context 的单例、两个需要明确落盘的 SharedPreferences commit。

可重复验证：

```powershell
# 单元测试与真实 Android JSON / SQLite / 模式保护测试（仅空计划测试安装）
.\gradlew.bat --project-cache-dir Temp/gradle-project :app:testDebugUnitTest :app:assembleDebugAndroidTest
adb install -r Temp/build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.silora.app.test/com.silora.app.NativeChecks

# 隔离数据库的课时表 / 周期 / 脚本 Android 检查
.\gradlew.bat --project-cache-dir Temp/gradle-project '-PtestRunner=com.silora.app.AcademicChecks' :app:assembleDebugAndroidTest
adb install -r Temp/build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.silora.app.test/com.silora.app.AcademicChecks

# WebView 冒烟检查，只新增再删除一条停用的测试计划（Node 22+）
$siloraPid = (adb shell pidof com.silora.app).Trim()
adb forward tcp:9222 localabstract:webview_devtools_remote_$siloraPid
node tests/core-smoke.mjs
# 需要没有现存课时表和周期规则；结束自动清理自身测试数据
node tests/academic-smoke.mjs
adb forward --remove tcp:9222

# 真实定时切换测试：仅空计划安装，约 3 分钟，会改变实际铃声
node tests/native-device.mjs
```

原生仪器测试和定时测试需要精确闹钟及通知策略访问已授权。定时脚本结束保留已过期测试记录供检查；删除记录后才可重新执行空计划测试。

## 结构与协议

- `Plan.kt`：纯时间校验与模式状态机。
- `PlanStore.kt`：SQLite `plans`、`meta`、`events`；`meta` 保存全局 revision、恢复基线、预期铃声、外部修改抑制状态和下一闹钟标识。
- `Core.kt`、`ScheduleReceiver.kt`：串行调度与系统执行，失败不假报成功。
- `DeepSeek.kt`、`KeyVault.kt`：独立网络线程、限长历史和凭据加密。
- `assets/system-prompt.txt`：实际使用的系统提示词。
- `docs/plan.schema.json`、`docs/example-plan.json`：协议及默认停用的导入示例。原生代码额外校验偏移、时间顺序、冲突和范围；不只依赖 Schema。

JSON 支持 add、按 scope replace（空 intervals 可清空范围）；按 ID 编辑 / 删除通过原生计划管理接口实现。替换范围不能切断原有区间，需先编辑跨界时间段。

## 实际限制与后续工作

本版完成原生核心、AI 接入与课表规则扩展，**不是 Work.md 的全部 MVP 验收完成**：周视图、间隔合并、缺失下周计划策略、网络校时尚未实现。JSON 导入导出目前通过文本框粘贴 / 复制。

尚未完成实机重启、长期深度休眠、厂商后台限制、Android 10–11 / 13–17、横屏大字体、WebView 进程故障、全部网络错误场景验证。重启 / 时区恢复分支已实现，但不能把模拟或代码检查当作实机验证。

Android 12+ 需要精确闹钟授权，Android 14 新装应用常默认无此授权。Android 15+ 对勿扰策略的能力可能不同；本版仍 targetSdk 34，不假设未来系统行为一致。系统休眠会限制密集的 allow-while-idle 闹钟，短间隔不能承诺零延迟；误点系统“强行停止”后需重开应用。参考 [Android 定时说明](https://developer.android.com/develop/background-work/services/alarms) 和 [AudioManager 限制](https://developer.android.com/reference/android/media/AudioManager)。

外部修改检测依赖实际模式与应用预期模式比较。进程不在时无法持续检测，更不能可靠识别是谁改的；若模式改动后又回到相同值，可能无法观察到。AI 网络失败不会影响已保存计划。网络失败不自动重发收费请求，避免重复消耗，可手动重试。

APK 大小通过 `Get-Item Release/v1.6.0/*.apk` 检查；前后台内存通过 `adb shell dumpsys meminfo com.silora.app` 比较；调度可检查 `adb shell dumpsys alarm`。日志仅按应用进程查看，不记录 API Key、Authorization 头或原始网络错误正文。

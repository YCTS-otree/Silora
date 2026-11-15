# Silora · 静律

版本 **1.0.0 Demo**。Android 系统 WebView 渲染的可安装交互演示，深色蓝色夜空 / 浅色绿色自然，底部为 AI、计划表、设置。

## 本轮交付

- AI 页面：文字输入、快捷提示、明确的 Demo 回复，不调用模型。
- 计划表：新增、删除、今天筛选、本地保存；校验结束时间及区间冲突。
- 设置：系统 / 深色 / 浅色主题，保存主题偏好，清除演示计划。
- 单 WebView，使用 WebViewAssetLoader HTTPS 本地来源；禁用文件访问、外部导航和非本地资源；用户文字通过 textContent 渲染。
- 无联网权限、账号、服务器、遥测和常驻服务。

这不是 Work.md 中的完整 MVP。DeepSeek、图片识别、Keystore、Room、AlarmManager、铃声切换、权限引导和完整教学尚未实现。演示计划保存在 WebView localStorage，不作为未来原生执行器的数据来源；卸载会删除数据。不支持编辑、跨日、JSON 导入导出或周视图。

## 构建和安装

使用已有 JDK 17、Gradle 8.11.1、AGP 8.9.2、Kotlin 2.1.20、AndroidX WebKit 1.12.1。minSdk 29、targetSdk 34、compileSdk 35。

配置 `ANDROID_HOME` 指向现有 Android SDK，安装 platform 35 / build-tools 35.0.0。首次依赖下载需要网络；缓存齐全时可添加 `--offline`。

```powershell
.\gradlew.bat --project-cache-dir Temp/gradle-project :app:assembleDebug :app:lintDebug
adb install -r Release/v1.0.0/Silora-v1.0.0-demo-debug.apk
adb shell am start -n com.silora.app/.MainActivity
```

构建产物位于 `Temp/build/app/outputs/apk/debug/`。交付 APK 位于 `Release/v1.0.0/`，使用开发签名，不是正式发布签名。后续版本不得覆盖已有 Release 目录。Gradle 缓存锁出现拒绝访问时需给构建进程现有缓存写入权限，无需安装另一套工具链。

## 验证

2026-11-15：assembleDebug、lintDebug、JavaScript 语法检查通过；已在 CET-AL00（Android 12 / API 31）安装运行并检查深色首页、浅色设置页。Lint 保留 targetSdk 34 与启用 JavaScript 两项警告：前者遵循需求，后者用于本地 UI，并通过 CSP 和本地来源限制降低风险。

真机 WebView 自动检查覆盖三个菜单、默认 AI 页、主题切换、计划保存、重叠阻止、非法时间阻止、文字安全显示、页面重载后数据恢复、删除及无水平溢出。运行方式（Node 22+，在无个人演示数据的测试安装中执行）：

```powershell
$siloraPid = (adb shell pidof com.silora.app).Trim()
adb forward tcp:9222 localabstract:webview_devtools_remote_$siloraPid
node tests/demo-smoke.mjs
adb forward --remove tcp:9222
```

WebView 调试仅在 debuggable APK 中启用。测试会创建再删除一条演示记录，切换至浅色主题。真实 AI 请求与铃声调度不在本轮验证范围。Android 10、11、13–17、横屏、大字体、键盘遮挡及 WebView 渲染进程故障尚未完整验证。

## 后续 MVP 与测量

后续需先核验 DeepSeek 官方模型及图片协议，再实施原生存储、校验器、调度和真实 AI。Demo 不接受或保存 API Key，也无需授予精确闹钟、通知或通知策略权限；完整授权说明随真实功能提供。

APK 大小可用 `Get-Item Release/v1.0.0/*.apk` 检查；前台与退后台内存用 `adb shell dumpsys meminfo com.silora.app` 对照记录；后台进程用 `adb shell pidof com.silora.app` 检查。Demo 没有后台任务，不保证进程驻留。日志检查限应用进程，未来实现不得记录 API Key 或请求认证头。

# Silora Android 使用统计接入 v1

接入 YCTS_USAGE_PROTOCOL_V1.md（1.0.0），软件 silora / Silora，固定 HTTPS POST https://license.ycts.top/api/v1/usage。服务端部署与本轮客户端开发同步进行；发布暂停，未发送生产测试事件。

## Android 设备方案 YCTS_ANDROID_V1

Android 10+ 使用 Settings.Secure.ANDROID_ID，去除两端空白并转小写，必须是 16 位十六进制，拒绝全零及已知无效值 9774d56d682e549c。指纹为 UTF-8 字符串 `YCTS_ANDROID_V1\n`（末尾一个 LF）与规范化 ID 拼接后 SHA-256 的 64 位小写十六进制。取不到则跳过，不生成随机设备 ID，不发送原始值，不加软件名或版本号。

Android ID 的范围是签名密钥、Android 用户、设备组合；同签名应用可采用此方案，但不同签名、用户、恢复出厂设置可能改变指纹。不承诺跨 Windows 指纹或不同签名去重。依据：https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID

## 启动与网络

进程内第一次成功响应前台 WebUI 的 status 请求后，上报一次；Application、服务、广播、页面刷新、Activity 重建和前后台切换不产生额外事件。结束进程后重新进入可用 UI 才产生新 UUID。仍存活的进程中退出界面再打开不另计启动，这是本实现的启动口径。日活由服务端按北京时间启动事件去重；不添加心跳。

独立后台调度器；总请求超时 5 秒，不跟随重定向，正常 TLS 校验。仅四字段，无凭据、时间、版本、课表、聊天或日志。204 不读 JSON；网络异常、408、429、5xx 最多重试三次，5/30/120 秒加随机抖动，429 尊重秒数或 HTTP 日期 Retry-After；十分钟内有限重试，原请求体与 UUID 不变，不持久化事件。不影响本地计划，无后台常驻统计服务。

日志仅记录尝试次数和 HTTP 状态，不记录设备指纹、UUID、原始标识、请求体或响应体。设置及教程披露统计目的、字段和 IP 可见性。隔离 devicecheck 包不发送统计；JVM 测试不连接生产服务。

## 发布

按用户要求保持 1.8.1 / versionCode 13 并覆盖本地 Release/v1.8.1，重新生成 SHA256SUMS.txt。此前 GitHub 发布的旧 APK 暂不替换；Gitee 未发布。相同版本不会触发旧版客户端升级提示。正式恢复发行时须在两边替换为本轮新 APK 和校验文件。

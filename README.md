<img src="Aesst/silora-moon-bell-logo.png" alt="Silora 月铃图标" width="96">

# Silora · 静律

静律是一款开源 Android 应用，可以按课表、会议或自习安排自动切换手机的响铃、振动和静音模式。例如，设置上课时振动，下课后恢复原来的铃声模式。

[下载 APK](https://github.com/YCTS-otree/Silora/releases/latest) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/YCTS-otree/Silora/issues)

## 主要功能

- **定时切换铃声**：手动添加、编辑或停用计划，支持跨午夜时段和结束后恢复铃声。
- **七日计划表**：从今天起查看接下来七天的安排，也可以跳转到指定日期。支持按教学周设置单双周课程。
- **AI 辅助排课**：用自然语言描述安排，或上传课表截图。配置自己的 DeepSeek API Key 后，即可生成候选计划，核对并确认后保存。
- **本地执行**：已保存的计划可以离线执行，支持通过 JSON 导入和导出。

## 安装与使用

1. 在 [GitHub Releases](https://github.com/YCTS-otree/Silora/releases/latest) 下载 APK。应用支持 Android 10 及以上版本。
2. 首次打开时，按引导授予精确闹钟和勿扰模式访问权限。使用到期提醒或守护通知时，请允许应用发送通知。
3. 在「计划表」里添加一个时段并选择铃声模式，也可以在 AI 页面生成计划再确认保存。

如果锁屏后切换不准时，请检查系统是否限制静律的后台活动，并在应用设置里查看「后台守护」。系统强行停止应用后，需要重新打开才能恢复调度。

## 数据与网络

计划保存在本机。使用 AI 时，请求及所附课表图片会发送给 DeepSeek。应用还会联网检查更新，并向 YCTS 上报用于统计启动次数和日活的数据。统计上报的字段见 [YCTS 使用统计说明](docs/YCTS_USAGE_ANDROID_V1.md)。

## 从源码构建

需要 JDK 17 和 Android SDK Platform 35。用 Android Studio 打开仓库，或在仓库根目录运行：

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS 和 Linux 可运行 `./gradlew :app:assembleDebug`。构建产物位于 `Temp/build/app/outputs/apk/debug/`。

## 许可证

本项目以 [GPL-3.0-only](LICENSE) 发布。历史功能说明和测试记录见 [旧版 README](https://github.com/YCTS-otree/Silora/blob/6ea06233efe657f8802dc9d671e5407c58db7933/README.md)。

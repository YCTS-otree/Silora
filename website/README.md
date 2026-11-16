# Silora 发布网页

网页版本：1.0.0。应用随页版本：1.8.1（联网后从更新清单读取）。

## 本地查看与上传

直接打开 index.html 即可查看。推荐在此目录执行：

    python -m http.server 8765 --bind 127.0.0.1

然后访问 http://127.0.0.1:8765 。上传时将 index.html、style.css、site.js 和 assets/ 保持相对结构放入静态托管目录，无需构建、Node.js、服务器端代码或 CDN。支持部署在子目录。README.md 不需要上传。

## 下载和版本更新

- 国内下载：https://gitee.com/otreee/Silora/releases
- 国际下载：https://github.com/YCTS-otree/Silora/releases
- 国内清单：https://gitee.com/otreee/Silora/raw/main/update.json
- 国际清单：https://raw.githubusercontent.com/YCTS-otree/Silora/main/update.json

首次优先 Gitee，每个源 5 秒超时；网络、跨域或数据错误时切换 GitHub。之后优先上次成功源。成功或失败均缓存一小时，刷新不会立即重复请求；保持页面打开时每小时自动检查，重新进入前台也会检查缓存是否过期。缓存按浏览器和站点区分；禁用存储时仅当前页面有效，同时首次打开多个标签可能各自发起请求。

清单校验 app=Silora 与三段版本 latest，展示 published、notes；更新内容仅以文本渲染。清单中的下载地址不会覆盖固定的两个 Releases 入口。全部源不可用时显示上次获取版本或随页版本，不假称已确认最新版本。

纯静态页面受来源服务器 CORS 和访问网络影响；部署后需在目标域名验证。Gitee 若拒绝跨域则会自动转用 GitHub；若两者均不可达，下载入口仍可使用。不使用第三方代理。仓库维护者需要另行在两个 Releases 页面发布附件；仓库 Release/ 文件夹与 Releases 页面不是同一位置。

## 素材

Logo 来自应用正式资源。app-dark.webp、app-light.webp 来自荣耀手机 ADB 实机截图；拍摄时临时隐藏已有对话并在结束后恢复，裁去系统状态栏和导航区域，缩小为 WebP。没有添加虚构计划或修改真实计划。配色直接沿用应用 CSS 变量。

## 版本维护

网页使用独立版本 1.0.0（site.js 文件头）。本次不修改 Android 应用版本。需要修改离线随页版本时，同步 index.html 和 site.js 中的回退文本。

## 验证与打包

在仓库根目录运行 `node tests/website-updates.mjs` 验证自动切源、缓存、过期刷新、离线与无效清单处理。网页已检查桌面与手机 320/390px 布局。当前网络实测 GitHub 清单可用，Gitee 未成功返回可用浏览器响应，已自动回退。

独立网页上传包保存在 `Release/website/v1.0.0/Silora-website-v1.0.0.zip`，解压后上传全部内容即可。网站无需部署整个 Android 仓库。

作者入口：Bilibili https://space.bilibili.com/668497683 ，主页 https://readme.ycts.top 。顶部及页脚均有链接。

课表素材 plans-dark.webp、plans-light.webp 同样取自手机 ADB 截图，只展示当前已有课表，未修改课程或执行设置。

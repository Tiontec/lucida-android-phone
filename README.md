# 澄影 · 安卓手机版工程（11.1.5，含原生双摄）
全屏 WebView 装入 `app/src/main/assets/web/index.html`（= lucida-android-phone_11.1.5.html，手机版网页，铺满屏幕、竖屏）。
包名 com.lucida.camera，最低安卓 7.0（API 24）。含原生双摄：Camera2 同时开前后摄像头 + OpenGL 合成画中画/上下分屏 + MediaCodec 录像（带声音）；设备不支持时网页自动回退到「分时双摄」。
打包：上传到 GitHub → Actions → 澄影 安卓手机版自动打包 → 下载 APK（固定签名，可覆盖安装）。本地：`gradle assembleDebug`（JDK17 + Android SDK 34）。
接口见 BRIDGE.md，状态见 STATUS.md。

---

## 下载安装包

所有版本在 **[Releases](https://github.com/Tiontec/lucida-android-phone/releases)**。

| 版本 | 主要内容 |
|---|---|
| v11.1.5 | 手机版 Android 壳，Camera2 双摄 + WebView |
| v11.1.1 | 早期手写外壳版（含 Android 8.0 兼容构建） |
| v11.1.0 | 早期外壳，内置手机版网页 |

每个版本包含：

- `*.apk` —— 已用固定密钥签名，可直接安装
- `*-web.html` —— 该版本内置的网页源文件

> 手表版在另一个仓库：[lucida-watch-android](https://github.com/Tiontec/lucida-watch-android)

---

## 协议与作者

本项目由 **雾峥奕** 创作，以《澄影非商业开源许可协议 1.0》开源。

| | |
|---|---|
| ✅ 允许 | 学习、研究、修改、非商业分享、自行打包安装 |
| ✅ 必须 | 保留作者署名；衍生作品沿用同一协议；分发时附带协议全文 |
| ❌ 禁止 | **任何商业用途**、**倒卖 / 转售 / 付费分发**、冒充原创、违法用途 |

完整条款见 [LICENSE](LICENSE)。

> 简单说：随便改、随便玩、随便传，但**不能拿去卖钱**，也不能说是你写的。

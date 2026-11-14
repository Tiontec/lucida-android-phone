# 澄影 · 安卓手机版工程（11.1.6，含原生双摄）
全屏 WebView 装入 `app/src/main/assets/web/index.html`（= lucida-android-phone_11.1.5.html，手机版网页，铺满屏幕、竖屏）。
包名 com.lucida.camera，最低安卓 7.0（API 24）。含原生双摄：Camera2 同时开前后摄像头 + OpenGL 合成画中画/上下分屏 + MediaCodec 录像（带声音）；设备不支持时网页自动回退到「分时双摄」。
打包：上传到 GitHub → Actions → 澄影 安卓手机版自动打包 → 下载 APK（固定签名，可覆盖安装）。本地：`gradle assembleDebug`（JDK17 + Android SDK 34）。
接口见 BRIDGE.md，状态见 STATUS.md。

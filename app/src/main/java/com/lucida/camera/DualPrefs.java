package com.lucida.camera;

import android.content.Context;
import android.content.SharedPreferences;

/** 原生双摄的设置（记住上次的选择）。 */
final class DualPrefs {
    final SharedPreferences sp;
    int res = 1080;            // 720 / 1080 / 2160（目标分辨率档）
    int fps = 30;              // 24 / 30 / 60
    boolean fullGate = false;  // 片门全开：前后摄都用 4:3 最大规格，成品 3:4
    int pipBorder = 0;         // 0 无边框 / 1 白色 / 2 黑色
    boolean mirror = true;     // 前摄镜像
    boolean audio = true;      // 录像带声音
    int style = DualState.STYLE_PIP;
    boolean gridLines = false; // 取景网格线
    boolean live = false;      // 实况（动态照片）
    int liveDur = 3;           // 3 / 5 / 6 / 8 秒
    boolean liveSeparate = false; // 照片 + 视频分开保存
    boolean heif = false;      // 照片格式 HEIF（Android 14+）
    int pipShape = 0;          // 小窗形状：0 圆角 / 1 大圆角 / 2 直角
    boolean liveTimer = true;  // 实况计时（取景框左上角）
    boolean glass = false;     // 毛玻璃界面（半透明 + 高光描边；不是系统液态玻璃）
    int gradeTarget = 2;       // 调色板正在改谁：0 后置 / 1 前置 / 2 同时
    float pipW = 0.32f;        // 小窗宽度占比（双击放大 / 缩小）
    boolean fullRate = false;  // 满帧画面运行录制：开 = 发热也不降帧；关 = 过热时自动降到 30 帧
    String order = "";         // 调色盘预设顺序（逗号分隔的 sid，空 = 默认顺序；「标准」永远排第一）

    DualPrefs(Context c) {
        sp = c.getSharedPreferences("lucida_dual", Context.MODE_PRIVATE);
        res = pick(sp.getInt("res", 1080), 720, 1080, 2160);
        fps = pick(sp.getInt("fps", 30), 24, 30, 60);
        fullGate = sp.getBoolean("fullGate", false);
        pipBorder = Math.max(0, Math.min(2, sp.getInt("pipBorder", 0)));
        mirror = sp.getBoolean("mirror", true);
        audio = sp.getBoolean("audio", true);
        style = sp.getInt("style", DualState.STYLE_PIP) == DualState.STYLE_SPLIT ? DualState.STYLE_SPLIT : DualState.STYLE_PIP;
        gridLines = sp.getBoolean("grid", false);
        live = sp.getBoolean("live", false);
        int d = sp.getInt("liveDur", 3); liveDur = (d == 3 || d == 5 || d == 6 || d == 8) ? d : 3;
        liveSeparate = sp.getBoolean("liveSep", false);
        heif = sp.getBoolean("heif", false);
        pipShape = Math.max(0, Math.min(2, sp.getInt("pipShape", 0)));
        liveTimer = sp.getBoolean("liveTimer", true);
        glass = sp.getBoolean("glass", false);
        gradeTarget = Math.max(0, Math.min(2, sp.getInt("gradeTarget", 2)));
        pipW = Math.max(0.2f, Math.min(0.6f, sp.getFloat("pipW", 0.32f)));
        fullRate = sp.getBoolean("fullRate", false);
        order = sp.getString("presetOrder", "");
    }

    private static int pick(int v, int a, int b, int c) { return v == a || v == b || v == c ? v : b; }

    void save() {
        sp.edit().putInt("res", res).putInt("fps", fps).putBoolean("fullGate", fullGate).putInt("pipBorder", pipBorder)
                .putBoolean("mirror", mirror).putBoolean("audio", audio).putInt("style", style).putBoolean("grid", gridLines)
                .putBoolean("live", live).putInt("liveDur", liveDur).putBoolean("liveSep", liveSeparate).putBoolean("heif", heif).putInt("pipShape", pipShape)
                .putBoolean("liveTimer", liveTimer).putBoolean("glass", glass).putInt("gradeTarget", gradeTarget).putFloat("pipW", pipW)
                .putBoolean("fullRate", fullRate).putString("presetOrder", order).apply();
    }
}

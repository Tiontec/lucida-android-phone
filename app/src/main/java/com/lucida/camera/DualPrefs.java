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
    }

    private static int pick(int v, int a, int b, int c) { return v == a || v == b || v == c ? v : b; }

    void save() {
        sp.edit().putInt("res", res).putInt("fps", fps).putBoolean("fullGate", fullGate).putInt("pipBorder", pipBorder)
                .putBoolean("mirror", mirror).putBoolean("audio", audio).putInt("style", style).putBoolean("grid", gridLines)
                .putBoolean("live", live).putInt("liveDur", liveDur).putBoolean("liveSep", liveSeparate).putBoolean("heif", heif).putInt("pipShape", pipShape).apply();
    }
}

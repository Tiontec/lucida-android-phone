package com.lucida.camera;

import android.content.Context;

/**
 * 面板样式（设置 →「界面与外观 → 面板样式」）：
 *   glass = 默认：跟随「界面风格」（毛玻璃 / 液态玻璃，走 DualUi.surface）
 *   dark  = 黑色面板 UI：纯色底，去掉玻璃和模糊
 *   light = 白色面板 UI：纯白底，深色字，去掉玻璃和模糊
 * 与 iOS 的 PanelTheme.swift 一一对应；设置页的卡片、文字、页面底色都从这里取颜色。
 * 相机界面的面板（DualUi / DualFunctionsView，A 的文件）要跟着变：见 CHANGES.md「给 A 的接口」。
 */
final class PanelTheme {
    private PanelTheme() {}
    static final String KEY = "panelTheme";

    /** 当前面板样式："glass" / "dark" / "light" */
    static String mode(Context c) {
        try {
            String v = c.getSharedPreferences("lucida_dual", Context.MODE_PRIVATE).getString(KEY, "glass");
            return "dark".equals(v) || "light".equals(v) ? v : "glass";
        } catch (Throwable t) { return "glass"; }
    }
    static boolean isLight(Context c) { return "light".equals(mode(c)); }
    static boolean isGlass(Context c) { return "glass".equals(mode(c)); }

    /** 纯色面板的底色（卡片 / 面板） */
    static int surface(Context c) { return isLight(c) ? 0xFFFFFFFF : 0xFF1A1A1C; }
    /** 页面底色 */
    static int pageBg(Context c) { return isLight(c) ? 0xFFEDEDED : 0xFF000000; }
    static int text(Context c) { return isLight(c) ? 0xFF141414 : 0xFFFFFFFF; }
    static int sub(Context c) { return isLight(c) ? 0x99141414 : 0x80FFFFFF; }
    static int note(Context c) { return isLight(c) ? 0x80141414 : 0x73FFFFFF; }
    static int value(Context c) { return isLight(c) ? 0x8C141414 : 0x8CFFFFFF; }
    static int divider(Context c) { return isLight(c) ? 0x1A000000 : 0x1AFFFFFF; }
    static int hover(Context c) { return isLight(c) ? 0x0D000000 : 0x14FFFFFF; }
    /** 危险操作（清空日志）和错误级别的红色 */
    static int danger(Context c) { return isLight(c) ? 0xFFC62E28 : 0xFFFF6159; }
    /** 玻璃模式下卡片的半透明底色（交给 DualUi.surface 当 flat 参数）。纯色模式不用 */
    static int glassTint(Context c) { return isLight(c) ? 0x14000000 : 0x14FFFFFF; }
    /** 提示条：固定深色底、白字（不随面板样式变） */
    static int toastBg() { return 0xEB1E1E20; }
    static int toastText() { return 0xFFFFFFFF; }
}

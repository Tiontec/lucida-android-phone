package com.lucida.camera;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 多功能面板（相机底部快门旁的按键）：以后新增的功能开关、入口都放在这里。
 * 1.0.0：更多设置 / 调色盘预设顺序 / 关于。
 */
final class DualFunctionsView extends FrameLayout {
    private final DualPrefs prefs;
    private final Runnable onClose, onSettings;

    DualFunctionsView(final Context c, DualPrefs prefs, Runnable onClose, Runnable onSettings) {
        super(c);
        this.prefs = prefs; this.onClose = onClose; this.onSettings = onSettings;
        setBackgroundColor(DualUi.glass ? 0xCC101010 : 0xF2101010); setClickable(true);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 56)));
        bar.addView(DualUi.label(c, "功能", 18, Color.WHITE), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView done = DualUi.label(c, "完成", 16, DualUi.YELLOW);
        done.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 10), DualUi.dp(c, 18), DualUi.dp(c, 10));
        done.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualFunctionsView.this.onClose.run(); } });
        bar.addView(done, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        ScrollView sv = new ScrollView(c);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
        int pad = DualUi.dp(c, 16);
        list.setPadding(pad, DualUi.dp(c, 6), pad, DualUi.dp(c, 40));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        add(list, DualSettingsView.linkRow(c, "更多设置", "实况、片门、小窗边框、镜像、录像声音、毛玻璃…", new Runnable() { @Override public void run() { DualFunctionsView.this.onSettings.run(); } }));
        add(list, DualSettingsView.linkRow(c, "调色盘预设顺序", "拖动调整预设的先后，「标准」固定在第一位", new Runnable() { @Override public void run() {
            final DualOrderView[] ov = new DualOrderView[1];
            ov[0] = new DualOrderView(getContext(), DualFunctionsView.this.prefs, new Runnable() { @Override public void run() { removeView(ov[0]); } });
            addView(ov[0], new FrameLayout.LayoutParams(-1, -1));
        } }));
        LinearLayout about = new LinearLayout(c); about.setOrientation(LinearLayout.VERTICAL);
        about.setBackground(DualUi.surface(0x14FFFFFF, 14, c)); about.setPadding(DualUi.dp(c, 14), DualUi.dp(c, 12), DualUi.dp(c, 14), DualUi.dp(c, 12));
        about.addView(DualUi.label(c, "关于", 15, Color.WHITE));
        about.addView(DualUi.label(c, "澄影重构版 " + BuildConfig.VERSION_NAME, 12, 0x80FFFFFF));
        add(list, about);
    }

    private static void add(LinearLayout l, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = DualUi.dp(l.getContext(), 12);
        l.addView(v, lp);
    }

    @Override public android.view.WindowInsets onApplyWindowInsets(android.view.WindowInsets in) {
        int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
        if (android.os.Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null) top = Math.max(top, in.getDisplayCutout().getSafeInsetTop());
        setPadding(0, Math.max(top, DualUi.dp(getContext(), 24)), 0, bottom);
        return super.onApplyWindowInsets(in);
    }
}

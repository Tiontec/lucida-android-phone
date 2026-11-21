package com.lucida.camera;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 设置页的小部件构建器：卡片、行、标题。和 iOS 的 SettingsRows.swift 一一对应。
 * 颜色全部从 PanelTheme 取（面板样式会影响它们）；玻璃模式的底走 DualUi.surface，纯色模式用 PanelTheme.surface。
 */
final class SettingsUi {
    private SettingsUi() {}

    static int dp(Context c, float v) { return DualUi.dp(c, v); }

    /** 黄色集合标题 + 一条细线 */
    static View heading(Context c, String title) {
        LinearLayout h = new LinearLayout(c);
        h.setOrientation(LinearLayout.HORIZONTAL); h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(c, 6), 0, 0, 0);
        TextView t = DualUi.label(c, title, 18, DualUi.YELLOW);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        h.addView(t, new LinearLayout.LayoutParams(-2, -2));
        View line = new View(c);
        line.setBackgroundColor(0x47FFD60A);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Math.max(1, dp(c, 1)), 1f);
        lp.leftMargin = dp(c, 12);
        h.addView(line, lp);
        return h;
    }

    /** 卡片上方的灰色小标题 */
    static View subhead(Context c, String s) {
        TextView t = DualUi.label(c, s, 14, PanelTheme.value(c));
        t.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        return t;
    }

    /** 卡片下方的灰色说明 */
    static View note(Context c, String s) {
        TextView t = DualUi.label(c, s, 12.5f, PanelTheme.note(c));
        t.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        return t;
    }

    /** 正文段落（关于页用） */
    static View body(Context c, String s) {
        TextView t = DualUi.label(c, s, 14.5f, PanelTheme.sub(c));
        t.setPadding(dp(c, 10), 0, dp(c, 10), 0);
        return t;
    }

    /** 圆角卡片（里面一行一行，行与行之间有分割线）。玻璃模式走 DualUi.surface，纯色模式用面板样式的底色 */
    static LinearLayout card(Context c) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        if (PanelTheme.isGlass(c)) col.setBackground(DualUi.surface(PanelTheme.glassTint(c), 14, c));
        else col.setBackground(DualUi.round(PanelTheme.surface(c), 14, c));
        col.setClipToOutline(true);
        col.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        return col;
    }

    static void addRow(LinearLayout card, View row) {
        Context c = card.getContext();
        if (card.getChildCount() > 0) {
            View d = new View(c);
            d.setBackgroundColor(PanelTheme.divider(c));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 0.5f)));
            lp.leftMargin = dp(c, 16);
            card.addView(d, lp);
        }
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static StateListDrawable pressed(Context c) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, new ColorDrawable(PanelTheme.hover(c)));
        sl.addState(new int[]{}, new ColorDrawable(0));
        return sl;
    }

    private static LinearLayout titleColumn(Context c, String title, String sub, int titleColor) {
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(DualUi.label(c, title, 16, titleColor));
        if (sub != null && sub.length() > 0) {
            TextView s = DualUi.label(c, sub, 12.5f, PanelTheme.sub(c));
            s.setPadding(0, dp(c, 3), 0, 0);
            left.addView(s);
        }
        return left;
    }

    /** 一行：左边 标题 + 说明，右边 当前值 + 附件（开关 / 对勾 / 箭头）。onClick 为 null 时整行不可点 */
    static LinearLayout row(Context c, String title, String sub, String value, View accessory, final Runnable onClick) {
        return row(c, title, sub, value, accessory, onClick, PanelTheme.text(c));
    }

    /** 同上，标题颜色可指定（红色「清空」等） */
    static LinearLayout row(Context c, String title, String sub, String value, View accessory, final Runnable onClick, int titleColor) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        row.setMinimumHeight(dp(c, 54));
        row.addView(titleColumn(c, title, sub, titleColor), new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null && value.length() > 0) {
            TextView v = DualUi.label(c, value, 15, PanelTheme.value(c));
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2); vp.leftMargin = dp(c, 12);
            row.addView(v, vp);
        }
        if (accessory != null) {
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-2, -2); ap.leftMargin = dp(c, 8);
            row.addView(accessory, ap);
        }
        if (onClick != null) {
            row.setBackground(pressed(c));
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClick.run(); } });
        }
        return row;
    }

    /** 控件在下面的一行（分段选择 / 滑条）：标题 + 说明 + 控件 */
    static View controlRow(Context c, String title, String sub, String value, View control) {
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(c, 16), dp(c, 13), dp(c, 16), dp(c, 13));
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.addView(DualUi.label(c, title, 16, PanelTheme.text(c)), new LinearLayout.LayoutParams(0, -2, 1f));
        if (value != null && value.length() > 0) top.addView(DualUi.label(c, value, 15, PanelTheme.value(c)));
        col.addView(top, new LinearLayout.LayoutParams(-1, -2));
        if (sub != null && sub.length() > 0) {
            TextView s = DualUi.label(c, sub, 12.5f, PanelTheme.sub(c));
            s.setPadding(0, dp(c, 3), 0, 0);
            col.addView(s, new LinearLayout.LayoutParams(-1, -2));
        }
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.topMargin = dp(c, 10);
        col.addView(control, cp);
        return col;
    }

    /** 灰色（soon）：整行变暗，盖一层透明的点击层，点了只提示「不可用」 */
    static View soon(Context c, View row, final Runnable toast) {
        FrameLayout f = new FrameLayout(c);
        f.addView(row, new FrameLayout.LayoutParams(-1, -2));
        View cover = new View(c);
        cover.setClickable(true);
        cover.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toast.run(); } });
        f.addView(cover, new FrameLayout.LayoutParams(-1, -1));
        f.setAlpha(0.4f);
        return f;
    }

    static TextView chevron(Context c) { return DualUi.label(c, "›", 22, PanelTheme.value(c)); }

    static TextView check(Context c, boolean on) {
        TextView t = DualUi.label(c, "✓", 20, DualUi.YELLOW);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        return t;
    }
}

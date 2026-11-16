package com.lucida.camera;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 双摄界面的小工具：分段选择器 / 开关行。 */
final class DualUi {
    private DualUi() {}
    interface IntCb { void on(int i); }
    interface BoolCb { void on(boolean v); }
    static final int YELLOW = 0xFFFFD60A;
    /** 毛玻璃界面（半透明 + 高光描边）；安卓没有系统液态玻璃，这是用绘制模拟的 */
    static boolean glass = false;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float rDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, rDp)); return g;
    }

    /** 面板 / 按钮底：玻璃模式 = 半透明渐变 + 高光描边，否则是平的底色 */
    static GradientDrawable surface(int flat, float rDp, Context c) {
        if (!glass) return round(flat, rDp, c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x47FFFFFF, 0x14FFFFFF, 0x2EFFFFFF});
        g.setCornerRadius(dp(c, rDp)); g.setStroke(Math.max(1, dp(c, 1)), 0x73FFFFFF);
        return g;
    }

    /** 分段选择器：返回容器；选中项黄色底、黑字 */
    static LinearLayout seg(final Context c, final String[] labels, int sel, final IntCb cb) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(surface(0x26FFFFFF, 10, c));
        row.setPadding(dp(c, 2), dp(c, 2), dp(c, 2), dp(c, 2));
        final TextView[] ts = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = new TextView(c);
            t.setText(labels[i]); t.setTextSize(13); t.setGravity(Gravity.CENTER);
            t.setPadding(dp(c, 6), dp(c, 7), dp(c, 6), dp(c, 7));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            row.addView(t, lp);
            ts[i] = t;
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { paintSeg(c, ts, idx); cb.on(idx); } });
        }
        paintSeg(c, ts, sel);
        return row;
    }

    static void paintSeg(Context c, TextView[] ts, int sel) {
        for (int i = 0; i < ts.length; i++) {
            ts[i].setTextColor(i == sel ? Color.BLACK : Color.WHITE);
            ts[i].setBackground(i == sel ? (glass ? surface(0x80FFFFFF, 8, c) : round(YELLOW, 8, c)) : null);
            if (glass && i == sel) ts[i].setTextColor(Color.WHITE);
        }
    }

    /** 重新选中某一项（外部同步用） */
    static void setSeg(LinearLayout seg, int sel) {
        TextView[] ts = new TextView[seg.getChildCount()];
        for (int i = 0; i < ts.length; i++) ts[i] = (TextView) seg.getChildAt(i);
        paintSeg(seg.getContext(), ts, sel);
    }

    static TextView label(Context c, String t, float sp, int color) {
        TextView v = new TextView(c); v.setText(t); v.setTextSize(sp); v.setTextColor(color); return v;
    }

    /** 开关行：左边标题（+说明），右边一个胶囊开关 */
    static LinearLayout toggleRow(final Context c, String title, String sub, boolean on, final BoolCb cb) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(surface(0x14FFFFFF, 14, c)); row.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout left = new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label(c, title, 15, Color.WHITE));
        if (sub != null) left.addView(label(c, sub, 12, 0x80FFFFFF));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        DualSwitch sw = new DualSwitch(c, on, new DualSwitch.Cb() { @Override public void on(boolean v) { cb.on(v); } });
        row.addView(sw, new LinearLayout.LayoutParams(dp(c, 50), dp(c, 28)));
        return row;
    }

    static LinearLayout card(Context c, String title, View body) {
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(surface(0x14FFFFFF, 14, c)); col.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        col.addView(label(c, title, 15, Color.WHITE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 10);
        col.addView(body, lp);
        return col;
    }
}

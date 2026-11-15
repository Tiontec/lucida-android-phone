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

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float rDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, rDp)); return g;
    }

    /** 分段选择器：返回容器；选中项黄色底、黑字 */
    static LinearLayout seg(final Context c, final String[] labels, int sel, final IntCb cb) {
        final LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackground(round(0x26FFFFFF, 10, c));
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
            ts[i].setBackground(i == sel ? round(YELLOW, 8, c) : null);
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
        row.setBackground(round(0x14FFFFFF, 14, c)); row.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        LinearLayout left = new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label(c, title, 15, Color.WHITE));
        if (sub != null) left.addView(label(c, sub, 12, 0x80FFFFFF));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        final boolean[] st = {on};
        final TextView sw = new TextView(c);
        sw.setGravity(Gravity.CENTER); sw.setTextSize(12);
        paintSwitch(c, sw, on);
        sw.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { st[0] = !st[0]; paintSwitch(c, sw, st[0]); cb.on(st[0]); } });
        row.addView(sw, new LinearLayout.LayoutParams(dp(c, 56), dp(c, 30)));
        return row;
    }

    private static void paintSwitch(Context c, TextView sw, boolean on) {
        sw.setText(on ? "开" : "关"); sw.setTextColor(on ? Color.BLACK : Color.WHITE);
        sw.setBackground(round(on ? YELLOW : 0x33FFFFFF, 15, c));
    }

    static LinearLayout card(Context c, String title, View body) {
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(round(0x14FFFFFF, 14, c)); col.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        col.addView(label(c, title, 15, Color.WHITE));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(c, 10);
        col.addView(body, lp);
        return col;
    }
}

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
    static int YELLOW = 0xFFFFD60A;     // 强调色：原中版黄色；大肥鱼蓝色（皮肤切换时由 SettingsStore 更新）
    /** 毛玻璃界面（半透明 + 高光描边）；安卓没有系统液态玻璃，这是用绘制模拟的 */
    static boolean glass = false;
    /** 玻璃样式（设置 → 界面风格，安卓）：frosted = 毛玻璃；solid = 固态玻璃（上一版的液态玻璃：取景画面 mesh 折弯）；liquid2 = 液态玻璃（新：SDF 折射，AGSL，安卓 13 及以上）；plain = 默认面板 */
    static String glassStyle = "plain";
    /** 设置里「面板样式」是不是默认（玻璃）；选了黑色 / 白色纯色时相机界面的玻璃改成深色纯色 */
    static boolean panelGlass = true;
    static boolean liquid() { return glass && panelGlass && !"frosted".equals(glassStyle); }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(int color, float rDp, Context c) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(c, rDp)); return g;
    }

    /** 面板 / 按钮底：玻璃模式 = 半透明渐变 + 高光描边，否则是平的底色 */
    static GradientDrawable surface(int flat, float rDp, Context c) {
        if (!glass) return round(flat, rDp, c);
        if (!panelGlass) return round(0xF5101010, rDp, c);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x47FFFFFF, 0x14FFFFFF, 0x2EFFFFFF});
        g.setCornerRadius(dp(c, rDp)); g.setStroke(Math.max(1, dp(c, 1)), 0x73FFFFFF);
        return g;
    }

    /** 给「host」这个控件用的玻璃底：液态玻璃开着时是折射底（取景还没出画时它自己退回毛玻璃），否则同 surface() */
    static android.graphics.drawable.Drawable surfaceFor(View host, int flat, float rDp, Context c) {
        if (!liquid()) return surface(flat, rDp, c);
        return new RefractBg(host, surface(flat, rDp, c), dp(c, rDp));
    }

    /** 折射底：能折射就画折射，不能就画 fallback（普通毛玻璃） */
    static final class RefractBg extends android.graphics.drawable.Drawable {
        private final View host; private final android.graphics.drawable.Drawable fb; private final float r;
        RefractBg(View host, android.graphics.drawable.Drawable fb, float r) { this.host = host; this.fb = fb; this.r = r; }
        @Override public void draw(android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            fb.setBounds(b);
            c.save(); c.translate(b.left, b.top);
            boolean ok = GlassRefract.I.draw(c, host, b.width(), b.height(), Math.min(r, Math.min(b.width(), b.height()) / 2f));
            c.restore();
            if (!ok) fb.draw(c);
        }
        @Override public void setAlpha(int a) { fb.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { fb.setColorFilter(f); }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
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

    // ───────── 皮肤（设置 → 外观 → 皮肤）─────────
    /** 大肥鱼的吉祥物（由 applySkin 加到根布局上） */
    private static android.widget.ImageView skinMasc;

    /** 按当前皮肤设置强调色、根背景和吉祥物。skin：mid = 原中版（黄色、黑底）；fish = 大肥鱼（蓝色、深蓝底 + 吉祥物） */
    static void applySkin(Context c, android.view.ViewGroup root) {
        boolean fish = "fish".equals(c.getSharedPreferences("lucida_dual", Context.MODE_PRIVATE).getString("skin", "mid"));
        YELLOW = fish ? 0xFF7FB6FF : 0xFFFFD60A;
        if (root != null) root.setBackgroundColor(fish ? 0xFF060A18 : 0xFF000000);
        if (root == null) return;
        if (fish) {
            if (skinMasc == null) {
                skinMasc = new android.widget.ImageView(c);
                skinMasc.setImageResource(R.drawable.skinmasc);
                skinMasc.setScaleType(android.widget.ImageView.ScaleType.FIT_START);
                skinMasc.setClickable(false);
            }
            if (skinMasc.getParent() != root) {
                if (skinMasc.getParent() != null) ((android.view.ViewGroup) skinMasc.getParent()).removeView(skinMasc);
                android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(dp(c, 96), dp(c, 96 * 287 / 460), android.view.Gravity.BOTTOM | android.view.Gravity.LEFT);
                lp.leftMargin = dp(c, 12); lp.bottomMargin = dp(c, 150);
                root.addView(skinMasc, lp);
            }
            skinMasc.setVisibility(android.view.View.VISIBLE);
            skinMasc.bringToFront();
        } else if (skinMasc != null) {
            skinMasc.setVisibility(android.view.View.GONE);
        }
    }

    /** 相册里澄影拍的格子用的橙色细框（克劳德主题色 #FF8A3D，2.5 dp 描边，圆角 3 dp） */
    static android.graphics.drawable.GradientDrawable mineFrame(Context c) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(0x00000000);
        g.setStroke(Math.max(1, dp(c, 2.5f)), 0xFFFF8A3D);
        g.setCornerRadius(dp(c, 3));
        return g;
    }
}

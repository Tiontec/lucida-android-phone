package com.lucida.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * 调色面板（悬浮深色圆角面板，对照苹果相机）：
 *  顶部「后置 / 前置 / 同时」选择改谁；中间随标签变化（调色板 = 小二维色盘 + 强度；质感；颗粒）；底部标签：调色板 · 质感 · 颗粒。
 *  每条滑条后面都有复位按钮。后置 / 前置各有一套 GradeState，「同时」= 两套一起改。
 */
final class DualPalette extends LinearLayout {
    interface Host { void onClose(); void onShutter(); void onChange(); }
    private interface Op { void run(GradeState g); }
    private final GradeState back, front;
    private final DualPrefs prefs;
    private final Host host;
    private final FrameLayout body;
    private LinearLayout targetSeg, tabSeg;
    private int tab = 0;
    private boolean singleMode;
    private Pad pad;

    DualPalette(final Context c, GradeState back, GradeState front, DualPrefs prefs, Host h) {
        super(c);
        this.back = back; this.front = front; this.prefs = prefs; this.host = h;
        setOrientation(VERTICAL);
        setPadding(DualUi.dp(c, 16), DualUi.dp(c, 14), DualUi.dp(c, 16), DualUi.dp(c, 16));
        targetSeg = DualUi.seg(c, new String[]{"后置", "前置", "同时"}, prefs.gradeTarget, new DualUi.IntCb() {
            @Override public void on(int i) { DualPalette.this.prefs.gradeTarget = i; DualPalette.this.prefs.save(); rebuild(); DualPalette.this.host.onChange(); } });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(DualUi.dp(c, 220), -2); tl.gravity = Gravity.CENTER_HORIZONTAL;
        addView(targetSeg, tl);
        body = new FrameLayout(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1f); bl.topMargin = DualUi.dp(c, 8); bl.bottomMargin = DualUi.dp(c, 8);
        addView(body, bl);
        tabSeg = DualUi.seg(c, new String[]{"调色板", "质感", "颗粒"}, 0, new DualUi.IntCb() { @Override public void on(int i) { tab = i; rebuild(); } });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(DualUi.dp(c, 250), -2); sl.gravity = Gravity.CENTER_HORIZONTAL;
        addView(tabSeg, sl);
        restyle();
        rebuild();
    }

    /** 单摄时不分「后置 / 前置 / 同时」：直接调当前这颗摄像头的画面 */
    void setSingle(boolean single, boolean front) {
        singleMode = single;
        prefs.gradeTarget = single ? (front ? 1 : 0) : 2;
        targetSeg.setVisibility(single ? View.INVISIBLE : View.VISIBLE);
    }

    /** 玻璃开关变了：重画底板和里面所有控件 */
    void restyle() {
        Context c = getContext();
        if (DualUi.glass) {
            GradientDrawable dark = DualUi.round(0x8C0A0A0A, 34, c);
            setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[]{dark, DualUi.surface(0, 34, c)}));
        } else setBackground(DualUi.round(0xF5121212, 34, c));
        // 分段选择器重建（背景在创建时定下）
        int tsel = tab;
        LinearLayout nt = DualUi.seg(c, new String[]{"后置", "前置", "同时"}, prefs.gradeTarget, new DualUi.IntCb() {
            @Override public void on(int i) { prefs.gradeTarget = i; prefs.save(); rebuild(); host.onChange(); } });
        LinearLayout nb = DualUi.seg(c, new String[]{"调色板", "质感", "颗粒"}, tsel, new DualUi.IntCb() { @Override public void on(int i) { tab = i; rebuild(); } });
        swapSeg(targetSeg, nt); targetSeg = nt; targetSeg.setVisibility(singleMode ? View.INVISIBLE : View.VISIBLE);
        swapSeg(tabSeg, nb); tabSeg = nb;
    }

    private void swapSeg(View old, View neu) {
        int idx = indexOfChild(old);
        if (idx < 0) return;
        android.view.ViewGroup.LayoutParams lp = old.getLayoutParams();
        removeViewAt(idx); addView(neu, idx, lp);
    }

    /** 当前正在改的目标 */
    private GradeState[] targets() {
        switch (prefs.gradeTarget) { case 0: return new GradeState[]{back}; case 1: return new GradeState[]{front}; default: return new GradeState[]{back, front}; }
    }
    GradeState shown() { return prefs.gradeTarget == 1 ? front : back; }
    private void edit(Op op) { for (GradeState g : targets()) op.run(g); host.onChange(); }

    /** 切预设（左右滑动）：改当前目标的全部 */
    void selectPreset(int i) { final int ii = i; edit(new Op() { @Override public void run(GradeState g) { g.pick(ii); } }); rebuild(); }

    void sync() { rebuild(); }

    // ------------------------------------------------------------ 构件

    private int dp(float v) { return DualUi.dp(getContext(), v); }

    private DualIcon iconBtn(int kind, int sizeDp, final Runnable r) {
        DualIcon i = new DualIcon(getContext(), kind);
        i.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { r.run(); } });
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return i;
    }

    private View shutter() {
        Context c = getContext();
        FrameLayout w = new FrameLayout(c);
        View ring = new View(c);
        GradientDrawable rg = new GradientDrawable(); rg.setShape(GradientDrawable.OVAL); rg.setColor(Color.TRANSPARENT); rg.setStroke(dp(3.5f), Color.WHITE);
        ring.setBackground(rg);
        w.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64)));
        View in = new View(c);
        GradientDrawable ig = new GradientDrawable(); ig.setShape(GradientDrawable.OVAL); ig.setColor(Color.WHITE); in.setBackground(ig);
        in.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onShutter(); } });
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.CENTER);
        w.addView(in, ip);
        w.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(64)));
        return w;
    }

    /** 一行：名称 | 滑条 | 复位 */
    private View sliderRow(String title, float min, float max, float value, final SliderSet set, final Op reset) {
        return sliderRow(title, min, max, value, set, reset, DualUi.YELLOW);
    }
    private View sliderRow(String title, float min, float max, float value, final SliderSet set, final Op reset, int fill) {
        Context c = getContext();
        LinearLayout row = new LinearLayout(c); row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        if (title != null) {
            android.widget.TextView l = DualUi.label(c, title, 13, 0xD9FFFFFF);
            row.addView(l, new LinearLayout.LayoutParams(dp(46), -2));
        }
        DualSlider s = new DualSlider(c, min, max, value, new DualSlider.Cb() {
            @Override public void on(float v, boolean u) { final float vv = v; edit(new Op() { @Override public void run(GradeState g) { set.set(g, vv); } }); }
        });
        s.fillColor = fill;
        row.addView(s, new LinearLayout.LayoutParams(0, dp(34), 1f));
        DualIcon r = iconBtn(DualIcon.RESET, 30, new Runnable() { @Override public void run() { edit(reset); rebuild(); } });
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(30), dp(30)); rp.leftMargin = dp(8);
        row.addView(r, rp);
        return row;
    }
    private interface SliderSet { void set(GradeState g, float v); }

    private void rebuild() {
        final Context c = getContext();
        body.removeAllViews(); pad = null;
        DualUi.setSeg(targetSeg, prefs.gradeTarget);
        final GradeState g = shown();
        LinearLayout row = new LinearLayout(c); row.setOrientation(HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(row, new FrameLayout.LayoutParams(-1, -1));
        DualIcon down = iconBtn(DualIcon.DOWN, 44, new Runnable() { @Override public void run() { host.onClose(); } });
        row.addView(down);
        View sh = shutter();
        if (tab == 0) {
            pad = new Pad(c, g);
            LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
            col.addView(pad, new LinearLayout.LayoutParams(dp(118), dp(118)));
            View sr = sliderRow(null, 0f, 1f, g.str, new SliderSet() { @Override public void set(GradeState x, float v) { x.str = v; } },
                    new Op() { @Override public void run(GradeState x) { x.resetStr(); } }, PadImg.sliderColor(g.cur()));
            LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(dp(150), dp(34)); srp.topMargin = dp(2);
            col.addView(sr, srp);
            View sp1 = new View(c); row.addView(sp1, new LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(col);
            DualIcon padReset = iconBtn(DualIcon.RESET, 40, new Runnable() { @Override public void run() {
                edit(new Op() { @Override public void run(GradeState x) { x.resetPad(); } }); rebuild(); } });
            LinearLayout.LayoutParams prp = new LinearLayout.LayoutParams(dp(40), dp(40)); prp.leftMargin = dp(8);
            row.addView(padReset, prp);
            View sp2 = new View(c); row.addView(sp2, new LinearLayout.LayoutParams(0, 1, 1f));
        } else {
            LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
            if (tab == 1) {
                col.addView(DualUi.seg(c, GradeState.TEX_NAME, g.tex, new DualUi.IntCb() { @Override public void on(final int i) {
                    edit(new Op() { @Override public void run(GradeState x) { x.tvm[x.tex] = x.tv; x.tex = i; x.tv = x.tvm[i]; } }); rebuild(); } }));
                col.addView(sliderRow("质感", 0f, 1f, g.tv, new SliderSet() { @Override public void set(GradeState x, float v) { x.tv = v; x.tvm[x.tex] = v; } },
                        new Op() { @Override public void run(GradeState x) { x.resetTv(); } }), mt(8));
            } else {
                col.addView(sliderRow("颗粒", 0f, 1f, g.gr, new SliderSet() { @Override public void set(GradeState x, float v) { x.gr = v; } },
                        new Op() { @Override public void run(GradeState x) { x.resetGr(); } }));
            }
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f); cp.leftMargin = dp(12); cp.rightMargin = dp(12);
            row.addView(col, cp);
        }
        row.addView(sh);
    }

    private LinearLayout.LayoutParams mt(int dp) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2); l.topMargin = dp(dp); return l; }

    /** 每个预设自己的调色盘底图（和主页面 drawPadBg 同一个公式）+ 滑条颜色 */
    static final class PadImg {
        private static final java.util.HashMap<String, Bitmap> cache = new java.util.HashMap<String, Bitmap>();
        static synchronized Bitmap make(GradeState.Preset p) {
            Bitmap b = cache.get(p.sid);
            if (b != null) return b;
            final int W = 80;
            int[] px = new int[W * W];
            float[] t = p.tint, t2 = p.tint2;
            for (int j = 0; j < W; j++) for (int i = 0; i < W; i++) {
                double u = i / (double) (W - 1), v = 1 - j / (double) (W - 1);
                double g = .1 + .68 * Math.pow(v, .9), m = Math.pow(u, 1.5) * (.25 + .75 * Math.pow(v, .8)), m2 = t2 != null ? Math.pow(u, 1.5) * Math.pow(1 - v, 1.1) : 0;
                int[] ch = new int[3];
                for (int c = 0; c < 3; c++) {
                    double val = g * (1 - m - m2) + t[c] * (.55 + .7 * v) * m;
                    if (t2 != null) val += t2[c] * (.5 + .6 * v) * m2;
                    ch[c] = (int) Math.max(0, Math.min(255, Math.round(val * 255)));
                }
                px[j * W + i] = 0xFF000000 | (ch[0] << 16) | (ch[1] << 8) | ch[2];
            }
            b = Bitmap.createBitmap(px, W, W, Bitmap.Config.ARGB_8888);
            cache.put(p.sid, b);
            return b;
        }
        /** 滑条颜色：有第二色就取两色平均，否则取风格色提亮 25% */
        static int sliderColor(GradeState.Preset p) {
            float[] t = p.tint, t2 = p.tint2;
            float r, g, b;
            if (t2 != null) { r = (t[0] + t2[0]) / 2; g = (t[1] + t2[1]) / 2; b = (t[2] + t2[2]) / 2; }
            else { r = Math.min(1f, t[0] * 1.25f); g = Math.min(1f, t[1] * 1.25f); b = Math.min(1f, t[2] * 1.25f); }
            return Color.rgb(Math.round(r * 255), Math.round(g * 255), Math.round(b * 255));
        }
    }

    /** 二维色盘：横 = 颜色，纵 = 色调（上为正）；每个预设自己的彩色底图 + 13×13 点阵（和落点同行同列的点更亮）+ 白色旋钮 */
    private final class Pad extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Path clip = new Path();
        private final RectF rf = new RectF();
        private final GradeState gs;
        Pad(Context c, GradeState gs) { super(c); this.gs = gs; }
        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight(), d = getResources().getDisplayMetrics().density, rad = 24 * d, pad = 11 * d;
            GradeState sh = shown();
            rf.set(0, 0, W, H);
            clip.reset(); clip.addRoundRect(rf, rad, rad, Path.Direction.CW);
            c.save(); c.clipPath(clip);
            c.drawBitmap(PadImg.make(sh.cur()), null, rf, bp);
            c.restore();
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d); p.setColor(DualUi.glass ? 0x73FFFFFF : 0x1FFFFFFF);
            rf.set(d / 2, d / 2, W - d / 2, H - d / 2); c.drawRoundRect(rf, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
            final int N = 13;
            float ds = (W - W * .2f) / (N - 1), dpad = W * .1f;
            int ci = Math.round((sh.x * .5f + .5f) * (N - 1)), cj = Math.round((1 - sh.y) / 2 * (N - 1));
            for (int a = 0; a < N; a++) for (int b = 0; b < N; b++) {
                boolean hot = a == ci || b == cj, mid = a == (N - 1) / 2 || b == (N - 1) / 2;
                p.setColor(hot ? 0xE6FFFFFF : mid ? 0x80FFFFFF : 0x42FFFFFF);
                c.drawCircle(dpad + a * ds, dpad + b * ds, W * (hot ? .0085f : .0065f) * 1.6f, p);
            }
            float cx = pad + (sh.x * .5f + .5f) * (W - 2 * pad), cy = pad + (1 - sh.y) / 2 * (H - 2 * pad), r = 12 * d;
            p.setColor(0x59000000); c.drawCircle(cx, cy + 1.8f * d, r + d, p);
            p.setColor(Color.WHITE); c.drawCircle(cx, cy, r, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) return true;
            float d = getResources().getDisplayMetrics().density, pad = 11 * d;
            float nx = GradeState.clamp(((e.getX() - pad) / Math.max(1, getWidth() - 2 * pad) - .5f) * 2, -1, 1);
            float ny = GradeState.clamp(-((e.getY() - pad) / Math.max(1, getHeight() - 2 * pad) - .5f) * 2, -1, 1);
            if (Math.abs(nx) < .04f) nx = 0; if (Math.abs(ny) < .04f) ny = 0;        // 靠近中线自动吸附到 0
            final float x = nx, y = ny;
            edit(new Op() { @Override public void run(GradeState g) { g.x = x; g.y = y; } });
            invalidate(); return true;
        }
    }
}

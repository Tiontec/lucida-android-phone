package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** 调色面板（底部深色圆角）：调色板（二维圆点盘 + 强度）/ 质感 / 颗粒。参数写进 GradeState。 */
final class DualPalette extends LinearLayout {
    interface Host { void onClose(); void onShutter(); void onChange(); }
    private final GradeState g;
    private final Host host;
    private final FrameLayout body;
    private int tab = 0;
    private Pad pad;
    private SeekBar strBar;

    DualPalette(final Context c, GradeState g, Host h) {
        super(c);
        this.g = g; this.host = h;
        setOrientation(VERTICAL);
        GradientDrawableHolder.apply(this, c);
        setPadding(DualUi.dp(c, 16), DualUi.dp(c, 14), DualUi.dp(c, 16), DualUi.dp(c, 18));
        LinearLayout tabs = DualUi.seg(c, new String[]{"调色板", "质感", "颗粒"}, 0, new DualUi.IntCb() { @Override public void on(int i) { tab = i; rebuild(); } });
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(DualUi.dp(c, 240), -2); tl.gravity = Gravity.CENTER_HORIZONTAL;
        addView(tabs, tl);
        body = new FrameLayout(c);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, 0, 1f); bl.topMargin = DualUi.dp(c, 12);
        addView(body, bl);
        rebuild();
    }

    private static final class GradientDrawableHolder {
        static void apply(View v, Context c) {
            android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
            d.setColor(0xF5121212); float r = DualUi.dp(c, 28); d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
            v.setBackground(d);
        }
    }

    void sync() { if (pad != null) pad.invalidate(); if (strBar != null) strBar.setProgress(Math.round(g.str * 100)); }

    private SeekBar bar(int v, final java.util.function.IntConsumer f) {
        SeekBar s = new SeekBar(getContext()); s.setMax(100); s.setProgress(v);
        s.setProgressTintList(android.content.res.ColorStateList.valueOf(DualUi.YELLOW));
        s.setThumbTintList(android.content.res.ColorStateList.valueOf(DualUi.YELLOW));
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) { if (u) { f.accept(p); host.onChange(); } }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        return s;
    }

    private void rebuild() {
        final Context c = getContext();
        body.removeAllViews(); pad = null; strBar = null;
        if (tab == 0) {
            DualIcon down = new DualIcon(c, DualIcon.DOWN); down.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onClose(); } });
            DualIcon reset = new DualIcon(c, DualIcon.RESET); reset.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { g.reset(); sync(); host.onChange(); } });
            body.addView(down, new FrameLayout.LayoutParams(DualUi.dp(c, 44), DualUi.dp(c, 44), Gravity.TOP | Gravity.LEFT));
            body.addView(reset, new FrameLayout.LayoutParams(DualUi.dp(c, 44), DualUi.dp(c, 44), Gravity.BOTTOM | Gravity.LEFT));
            pad = new Pad(c);
            FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-1, -1);
            pl.leftMargin = DualUi.dp(c, 56); pl.rightMargin = DualUi.dp(c, 112);
            body.addView(pad, pl);
            View sh = new View(c); sh.setBackground(DualUi.round(Color.WHITE, 30, c));
            sh.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onShutter(); } });
            FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(DualUi.dp(c, 60), DualUi.dp(c, 60), Gravity.TOP | Gravity.RIGHT);
            sp.rightMargin = DualUi.dp(c, 18);
            body.addView(sh, sp);
            TextView sl = DualUi.label(c, "强度", 12, 0xB3FFFFFF);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
            lp.rightMargin = DualUi.dp(c, 50); lp.bottomMargin = DualUi.dp(c, 36);
            body.addView(sl, lp);
            strBar = bar(Math.round(g.str * 100), new java.util.function.IntConsumer() { @Override public void accept(int p) { g.str = p / 100f; } });
            FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(DualUi.dp(c, 104), -2, Gravity.BOTTOM | Gravity.RIGHT);
            body.addView(strBar, bp);
        } else {
            LinearLayout col = new LinearLayout(c); col.setOrientation(VERTICAL);
            if (tab == 1) {
                col.addView(DualUi.seg(c, GradeState.TEX_NAME, g.tex, new DualUi.IntCb() { @Override public void on(int i) {
                    g.tvm[g.tex] = g.tv; g.tex = i; g.tv = g.tvm[i]; host.onChange(); rebuild(); } }));
                col.addView(DualUi.label(c, "质感强度", 13, 0xCCFFFFFF), mt(c, 14));
                col.addView(bar(Math.round(g.tv * 100), new java.util.function.IntConsumer() { @Override public void accept(int p) { g.tv = p / 100f; } }));
            } else {
                col.addView(DualUi.label(c, "颗粒强度", 13, 0xCCFFFFFF));
                col.addView(bar(Math.round(g.gr * 100), new java.util.function.IntConsumer() { @Override public void accept(int p) { g.gr = p / 100f; } }));
            }
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL); cp.leftMargin = DualUi.dp(c, 56);
            body.addView(col, cp);
            DualIcon down = new DualIcon(c, DualIcon.DOWN); down.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onClose(); } });
            body.addView(down, new FrameLayout.LayoutParams(DualUi.dp(c, 44), DualUi.dp(c, 44), Gravity.TOP | Gravity.LEFT));
        }
    }

    private static LinearLayout.LayoutParams mt(Context c, int dp) { LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, -2); l.topMargin = DualUi.dp(c, dp); return l; }

    /** 二维圆点盘：横 = 颜色，纵 = 色调（上为正） */
    private final class Pad extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Pad(Context c) { super(c); }
        @Override protected void onDraw(Canvas c) {
            float W = getWidth(), H = getHeight();
            p.setStyle(Paint.Style.FILL); p.setColor(0x10FFFFFF);
            c.drawRoundRect(0, 0, W, H, DualUi.dp(getContext(), 18), DualUi.dp(getContext(), 18), p);
            p.setColor(0x47FFFFFF);
            for (int i = 0; i < 11; i++) for (int j = 0; j < 9; j++) c.drawCircle(W * (i + .5f) / 11, H * (j + .5f) / 9, 2.2f * getResources().getDisplayMetrics().density, p);
            float cx = W * (g.x + 1) / 2, cy = H * (1 - g.y) / 2, r = DualUi.dp(getContext(), 11);
            p.setColor(DualUi.YELLOW); c.drawCircle(cx, cy, r, p);
            p.setStyle(Paint.Style.STROKE); p.setColor(Color.WHITE); p.setStrokeWidth(2 * getResources().getDisplayMetrics().density); c.drawCircle(cx, cy, r, p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
            g.x = GradeState.clamp((e.getX() / Math.max(1, getWidth()) - .5f) * 2, -1, 1);
            g.y = GradeState.clamp(-(e.getY() / Math.max(1, getHeight()) - .5f) * 2, -1, 1);
            invalidate(); host.onChange(); return true;
        }
    }
}

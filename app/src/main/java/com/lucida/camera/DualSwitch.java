package com.lucida.camera;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * 苹果样式的紧凑开关（50×28dp）：长胶囊轨道 + 旋钮。
 * 按住 / 点一下 / 拖动时旋钮变成「水滴」（毛玻璃界面开着时是半透明玻璃水滴，带高光描边；关着时只是稍微拉长的白色旋钮），
 * 松手后变回纯白旋钮。可以按住旋钮左右拖，松手时按位置决定开 / 关。
 */
final class DualSwitch extends View {
    interface Cb { void on(boolean v); }
    private boolean on;
    private float t;                       // 0 = 关，1 = 开（动画中间值）
    private float press;                   // 0 = 松开，1 = 按下（水滴）
    private Cb cb;
    private boolean dragging;
    private float downX, downT;
    private ValueAnimator posAnim, pressAnim;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    DualSwitch(Context c, boolean on, Cb cb) {
        super(c);
        this.on = on; this.t = on ? 1f : 0f; this.cb = cb;
        setClickable(true);
    }

    boolean isOn() { return on; }

    void set(boolean v, boolean animate) {
        on = v;
        if (posAnim != null) posAnim.cancel();
        if (!animate) { t = v ? 1f : 0f; invalidate(); return; }
        posAnim = ValueAnimator.ofFloat(t, v ? 1f : 0f);
        posAnim.setDuration(280); posAnim.setInterpolator(new OvershootInterpolator(1.0f));
        posAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { t = (Float) va.getAnimatedValue(); invalidate(); }
        });
        posAnim.start();
    }

    private void pressTo(float to) {
        if (pressAnim != null) pressAnim.cancel();
        pressAnim = ValueAnimator.ofFloat(press, to);
        pressAnim.setDuration(to > press ? 140 : 260);
        pressAnim.setInterpolator(new OvershootInterpolator(to > press ? 1.4f : 0.8f));
        pressAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator va) { press = (Float) va.getAnimatedValue(); invalidate(); }
        });
        pressAnim.start();
    }

    private float usable() { return Math.max(1f, getWidth() - knobW(0) - dp(4)); }
    private float knobH() { return getHeight() - dp(4); }
    private float knobW(float pr) { return knobH() * (1f + 0.55f * Math.max(0f, pr)) ; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return true;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                if (posAnim != null) posAnim.cancel();
                downX = e.getX(); downT = t; dragging = false; pressTo(1f);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                if (!dragging && Math.abs(dx) > dp(4)) dragging = true;
                if (dragging) { t = Math.max(0f, Math.min(1f, downT + dx / usable())); invalidate(); }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean want = dragging ? t > .5f : !on;
                pressTo(0f);
                boolean changed = want != on;
                set(want, true);
                if (changed && cb != null) cb.on(on);
                performClick();
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressTo(0f); set(on, true);
                return true;
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight();
        float tt = Math.max(0f, Math.min(1f, t));
        p.setShader(null); p.setStyle(Paint.Style.FILL);
        p.setColor(blend(0x33FFFFFF, DualUi.YELLOW, tt));
        r.set(0, 0, W, H); c.drawRoundRect(r, H / 2, H / 2, p);
        boolean glass = DualUi.glass;
        float kh = knobH(), kw = knobW(press);
        float cx = dp(2) + knobW(0) / 2 + usable() * tt;          // 旋钮中心跟着位置走，变水滴时向两边长
        float left = Math.max(dp(1), Math.min(W - kw - dp(1), cx - kw / 2));
        float top = (H - kh) / 2;
        r.set(left, top, left + kw, top + kh);
        if (glass && press > .02f) {
            // 液态玻璃水滴：半透明底 + 上亮下暗渐变 + 高光描边（越按越透）
            int a = (int) (0xFF - (0xFF - 0x5C) * Math.min(1f, press));
            p.setColor((a << 24) | 0xFFFFFF); c.drawRoundRect(r, kh / 2, kh / 2, p);
            p.setShader(new LinearGradient(0, r.top, 0, r.bottom, 0x66FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(r, kh / 2, kh / 2, p);
            p.setShader(null); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.2f)); p.setColor(0xCCFFFFFF);
            c.drawRoundRect(r.left + dp(.6f), r.top + dp(.6f), r.right - dp(.6f), r.bottom - dp(.6f), kh / 2, kh / 2, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            p.setColor(0x33000000); c.drawRoundRect(r.left - 0.5f, r.top + dp(1), r.right + 0.5f, r.bottom + dp(1.5f), kh / 2, kh / 2, p);
            p.setColor(0xFFFFFFFF); c.drawRoundRect(r, kh / 2, kh / 2, p);
        }
    }

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private static int blend(int a, int b, float t) {
        int aa = (int) (((a >>> 24) & 255) * (1 - t) + ((b >>> 24) & 255) * t);
        int rr = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (aa << 24) | (rr << 16) | (gg << 8) | bb;
    }
}

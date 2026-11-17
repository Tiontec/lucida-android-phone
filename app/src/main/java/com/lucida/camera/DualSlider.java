package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 苹果样式的滑动条：细轨道 + 黄色已填部分 + 白色长圆旋钮（玻璃模式下旋钮半透明、带高光描边）。 */
final class DualSlider extends View {
    interface Cb { void on(float v, boolean fromUser); }
    float min = 0f, max = 1f, value = 0f;
    int fillColor = DualUi.YELLOW;        // 已填部分的颜色（调色盘里跟着当前底色变）
    private Cb cb;
    private boolean down;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    DualSlider(Context c, float min, float max, float value, Cb cb) {
        super(c);
        this.min = min; this.max = max; this.value = value; this.cb = cb;
        setClickable(true);
    }

    void setValue(float v) { value = Math.max(min, Math.min(max, v)); invalidate(); }
    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }
    private float knobW() { return DualUi.glass ? dp(44) : dp(38); }
    private float knobH() { return DualUi.glass ? dp(28) : dp(24); }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(resolveSize((int) dp(120), w), resolveSize((int) dp(34), h));
    }

    private void touch(float x) {
        float kw = knobW(), usable = Math.max(1f, getWidth() - kw);
        float f = Math.max(0f, Math.min(1f, (x - kw / 2) / usable));
        value = min + f * (max - min);
        invalidate();
        if (cb != null) cb.on(value, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: down = true; getParent().requestDisallowInterceptTouchEvent(true); touch(e.getX()); return true;
            case MotionEvent.ACTION_MOVE: touch(e.getX()); return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: down = false; invalidate(); return true;
        }
        return true;
    }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), kw = knobW(), kh = knobH();
        float f = max > min ? (value - min) / (max - min) : 0f;
        float x = (W - kw) * f;
        float ty = H / 2, th = dp(4);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x38FFFFFF); r.set(0, ty - th / 2, W, ty + th / 2); c.drawRoundRect(r, th / 2, th / 2, p);
        p.setColor(fillColor); r.set(0, ty - th / 2, x + kw / 2, ty + th / 2); c.drawRoundRect(r, th / 2, th / 2, p);
        float s = down ? 1.08f : 1f;
        float kcx = x + kw / 2, hw = kw * s / 2, hh = kh * s / 2;
        r.set(kcx - hw, ty - hh, kcx + hw, ty + hh);
        if (DualUi.glass) {
            p.setColor(0x66FFFFFF); c.drawRoundRect(r, hh, hh, p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(0xCCFFFFFF); c.drawRoundRect(r, hh, hh, p);
        } else {
            p.setColor(0x33000000); c.drawRoundRect(r.left, r.top + dp(1.5f), r.right, r.bottom + dp(1.5f), hh, hh, p);
            p.setColor(0xFFFFFFFF); c.drawRoundRect(r, hh, hh, p);
        }
    }
}

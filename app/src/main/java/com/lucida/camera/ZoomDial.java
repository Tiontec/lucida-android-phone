package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

import java.util.Locale;

/**
 * 变焦滑动转盘（和澄影主页面那条横向刻度转盘一样的用法）：
 * 左右拖动刻度尺即可连续变焦，松手后带惯性；点中间的倍数读数回到 1×；点刻度可直接跳转。
 * 刻度按对数分布（0.5→1→2→5 的间距相等），和主页面一致。
 */
final class ZoomDial extends View {
    interface Listener { void onZoom(float z, boolean fromUser); }

    private float zMin = 1f, zMax = 5f, z = 1f;
    private float pxLn;                       // 每 ln(1) 对应多少像素
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG), txt = new Paint(Paint.ANTI_ALIAS_FLAG),
            big = new Paint(Paint.ANTI_ALIAS_FLAG), pin = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Listener listener;
    private VelocityTracker vt;
    private float lastX, vLn;                 // vLn：惯性速度（ln/秒）
    private boolean dragging, fling;
    private long lastT;
    private final float dens;

    ZoomDial(Context c) {
        super(c);
        dens = c.getResources().getDisplayMetrics().density;
        pxLn = 150f * dens;
        tick.setStrokeWidth(1.5f * dens); tick.setColor(0xCCFFFFFF);
        txt.setColor(0xCCFFFFFF); txt.setTextSize(11f * dens); txt.setTextAlign(Paint.Align.CENTER);
        big.setColor(0xFFFFD60A); big.setTextSize(17f * dens); big.setTextAlign(Paint.Align.CENTER); big.setTypeface(Typeface.DEFAULT_BOLD);
        pin.setColor(0xFFFFD60A); pin.setStrokeWidth(2.5f * dens);
    }

    void setListener(Listener l) { listener = l; }

    /** 设置可用范围和当前值。 */
    void setRange(float min, float max, float cur) {
        zMin = Math.max(0.1f, min); zMax = Math.max(zMin, max);
        z = clamp(cur);
        invalidate();
    }

    float zoom() { return z; }

    private float clamp(float v) { return Math.min(zMax, Math.max(zMin, v)); }

    void setZoom(float v, boolean user) {
        float nz = clamp(v);
        if (Math.abs(nz - z) < 1e-4f) return;
        z = nz;
        invalidate();
        if (listener != null) listener.onZoom(z, user);
    }

    @Override
    protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), cx = W / 2f, lnz = (float) Math.log(z);
        // 读数
        String s = z >= 10 ? String.format(Locale.US, "%.0f×", z) : String.format(Locale.US, "%.1f×", z);
        c.drawText(s, cx, 18f * dens, big);
        // 刻度：每 0.1 一小格（<2），每 0.5 一小格（<5），其后每 1
        float base = 24f * dens, hMinor = 8f * dens, hMajor = 15f * dens;
        float lo = (float) Math.exp(lnz - cx / pxLn) - 0.05f, hi = (float) Math.exp(lnz + cx / pxLn) + 0.05f;
        for (float v = 0.1f; v <= zMax + 0.001f; v += (v < 2f ? 0.1f : v < 5f ? 0.5f : 1f)) {
            if (v < zMin - 0.001f || v < lo || v > hi) continue;
            float x = cx + ((float) Math.log(v) - lnz) * pxLn;
            boolean major = nearInt(v) || Math.abs(v - 0.5f) < 0.01f;
            c.drawLine(x, base, x, base + (major ? hMajor : hMinor), tick);
            if (major && (Math.abs(v - Math.round(v)) < 0.01f || Math.abs(v - 0.5f) < 0.01f) && Math.abs(x - cx) > 22f * dens) {
                String t = Math.abs(v - 0.5f) < 0.01f ? "0.5" : String.valueOf(Math.round(v));
                c.drawText(t, x, base + hMajor + 13f * dens, txt);
            }
        }
        // 中心指针
        c.drawLine(cx, base - 2f * dens, cx, base + hMajor + 2f * dens, pin);
    }

    private boolean nearInt(float v) { return Math.abs(v - Math.round(v)) < 0.01f && v >= 1f; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float cx = getWidth() / 2f;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                fling = false; dragging = false;
                lastX = e.getX();
                if (vt != null) vt.recycle();
                vt = VelocityTracker.obtain(); vt.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                vt.addMovement(e);
                float dx = e.getX() - lastX;
                if (!dragging && Math.abs(dx) > 3f * dens) dragging = true;
                if (dragging) {
                    lastX = e.getX();
                    setZoom((float) Math.exp(Math.log(z) - dx / pxLn), true);
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!dragging && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    // 点按：点读数 → 回到 1×；点刻度上的位置 → 直接跳到那个倍数
                    float x = e.getX();
                    if (Math.abs(x - cx) < 36f * dens && e.getY() < 30f * dens) setZoom(1f, true);
                    else setZoom((float) Math.exp(Math.log(z) + (x - cx) / pxLn), true);
                } else if (dragging) {
                    vt.computeCurrentVelocity(1000);
                    vLn = -vt.getXVelocity() / pxLn;
                    if (Math.abs(vLn) > 0.15f) { fling = true; lastT = System.nanoTime(); postOnAnimation(step); }
                }
                if (vt != null) { vt.recycle(); vt = null; }
                return true;
            }
        }
        return true;
    }

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (!fling) return;
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastT) / 1e9f); lastT = now;
            setZoom((float) Math.exp(Math.log(z) + vLn * dt), true);
            vLn *= (float) Math.pow(0.04, dt);          // 约 0.5 秒衰减到很小
            boolean edge = (z <= zMin + 1e-3f && vLn < 0) || (z >= zMax - 1e-3f && vLn > 0);
            if (Math.abs(vLn) < 0.03f || edge) { fling = false; return; }
            postOnAnimation(this);
        }
    };

    @Override protected void onDetachedFromWindow() { fling = false; super.onDetachedFromWindow(); }
}

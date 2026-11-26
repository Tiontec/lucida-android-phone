package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * 变焦放大取景框（设置 → 变焦放大取景框，同 iOS 的 DualZoomMiniView）：
 * 放大到设定的倍数（10 / 20 / 25 / 30 / 50 / 80 / 100，默认 10）以后，左上角出现一个小框，
 * 用黄色方框标出现在放大到的画面位置（变焦是从画面中心放大的，所以黄框在小框中心）。
 */
final class DualZoomMini extends View {
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF(), rb = new RectF();
    private float zoom = 1f;
    private boolean show = false;

    DualZoomMini(Context c) {
        super(c);
        setClickable(false);
        bg.setColor(0x73000000);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(DualUi.dp(c, 2));
        setVisibility(GONE);
    }

    /** z：当前变焦倍数；threshold：设置里的档位（超过它才显示） */
    void update(float z, float threshold) {
        zoom = z;
        boolean s = z > threshold + 0.001f;
        if (s != show) {
            show = s;
            setVisibility(s ? VISIBLE : GONE);
        }
        box.setColor(DualUi.YELLOW);
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0 || zoom <= 0) return;
        float r = DualUi.dp(getContext(), 6);
        rf.set(0, 0, w, h);
        cv.drawRoundRect(rf, r, r, bg);
        float bw = Math.max(4f, w / zoom), bh = Math.max(4f, h / zoom);
        float x = (w - bw) / 2f, y = (h - bh) / 2f;
        rb.set(x, y, x + bw, y + bh);
        float rr = DualUi.dp(getContext(), 2);
        cv.drawRoundRect(rb, rr, rr, box);
    }
}

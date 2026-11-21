package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** 预设页码圆点：最多显示 5 个，当前的白色放大；窗口没到头时，边上的点缩小，表示后面还有（和主页面同款）。 */
final class DualDots extends View {
    private int n = 1, cur = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    DualDots(Context c) { super(c); }
    void set(int count, int current) { n = Math.max(1, count); cur = current; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, gap = 14 * d;
        int show = Math.min(5, n), s0 = Math.max(0, Math.min(n - show, cur - 2));
        float x0 = (getWidth() - gap * (show - 1)) / 2, y = getHeight() / 2f;
        for (int k = 0; k < show; k++) {
            int i = s0 + k;
            boolean on = i == cur, edge = (k == 0 && s0 > 0) || (k == show - 1 && s0 + show < n);
            p.setColor(on ? 0xFFFFFFFF : 0x66FFFFFF);
            c.drawCircle(x0 + gap * k, y, (on ? 4f : edge ? 2.2f : 3f) * d, p);
        }
    }
}

package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** 预设页码圆点（当前页白色，其它半透明）。 */
final class DualDots extends View {
    private int n = 1, cur = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    DualDots(Context c) { super(c); }
    void set(int count, int current) { n = Math.max(1, count); cur = current; invalidate(); }
    @Override protected void onDraw(Canvas c) {
        float d = getResources().getDisplayMetrics().density, gap = 11 * d, r = 3 * d;
        float total = gap * (n - 1), x0 = (getWidth() - total) / 2, y = getHeight() / 2f;
        for (int i = 0; i < n; i++) {
            p.setColor(i == cur ? 0xFFFFFFFF : 0x59FFFFFF);
            c.drawCircle(x0 + gap * i, y, i == cur ? r * 1.15f : r, p);
        }
    }
}

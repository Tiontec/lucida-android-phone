package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/**
 * 水平仪：竖着拿手机时，中间一条线始终跟地平线平行，歪在 ±0.8° 以内变黄；
 * 手机平放朝下拍（俯拍）时，变成一个圆圈 + 小圆点，点进圈里就是水平。
 * 输入的是「重力在手机坐标系里的分量」（约定和 iOS 一致：竖拿时 gy = -1），Activity 里把 Android 传感器的值取反再传进来。
 */
final class DualLevelView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), f = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float gx, gy = -1f, gz;
    private final float d;

    DualLevelView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeWidth(1.8f * d);
        f.setStyle(Paint.Style.FILL);
        setClickable(false);
    }

    void setGravity(float x, float y, float z) { gx = x; gy = y; gz = z; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        boolean flat = Math.abs(gz) > 0.93f;
        if (flat) {
            boolean ok = Math.hypot(gx, gy) < 0.026;
            int col = ok ? DualUi.YELLOW : 0xDDFFFFFF;
            p.setColor(col); f.setColor(col);
            c.drawCircle(cx, cy, 30 * d, p);
            c.drawCircle(cx + gx * 220 * d, cy + (-gy) * 220 * d, 7 * d, f);
            return;
        }
        double roll = Math.atan2(gx, -gy);                  // 手机顺时针歪了多少（弧度）
        double deg = Math.abs(roll) * 180 / Math.PI;
        int col = deg < 0.8 ? DualUi.YELLOW : 0xDDFFFFFF;
        p.setColor(col);
        c.drawLine(cx - 98 * d, cy, cx - 80 * d, cy, p);
        c.drawLine(cx + 80 * d, cy, cx + 98 * d, cy, p);
        if (deg > 25) return;                               // 歪太多说明在故意横着 / 斜着拍，不显示
        c.save(); c.rotate((float) (-roll * 180 / Math.PI), cx, cy);
        c.drawLine(cx - 62 * d, cy, cx + 62 * d, cy, p);
        c.restore();
    }
}

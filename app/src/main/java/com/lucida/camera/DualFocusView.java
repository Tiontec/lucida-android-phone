package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.widget.FrameLayout;

/** 取景框里的对焦框：黄色方框 + 旁边带太阳的曝光滑轨（上拉变亮、下拉变暗）。长按后显示「AE/AF 锁定」，一直保留到再点一下。 */
final class DualFocusView extends View {
    private boolean locked, shown;
    private float t = .5f;               // 曝光滑块位置 0（最暗）…1（最亮）
    private int side = 1;                // 1 = 滑轨在右边，-1 = 在左边
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Runnable hideRun = new Runnable() { @Override public void run() { if (!locked) animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
        @Override public void run() { if (getAlpha() < .05f) { shown = false; setVisibility(GONE); } } }).start(); } };

    DualFocusView(Context c) {
        super(c);
        setClickable(false); setFocusable(false); setVisibility(GONE);
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    private float d() { return getResources().getDisplayMetrics().density; }
    int boxPx() { return Math.round(76 * d()); }
    private int sidePx() { return Math.round(40 * d()); }
    int wPx() { return boxPx() + sidePx(); }
    int hPx() { return Math.round(150 * d()); }

    boolean isShown2() { return shown && getVisibility() == VISIBLE && getAlpha() > .05f; }
    boolean isLocked() { return locked; }
    float t() { return t; }

    /** (px, py)：对焦点（取景框坐标）；areaW / areaH：取景框尺寸，用来避免跑出画面 */
    void show(float px, float py, int areaW, int areaH, boolean lock) {
        locked = lock; t = .5f; shown = true;
        int bx = boxPx(), sw = sidePx();
        side = (px + bx / 2f + sw > areaW - 4 * d()) ? -1 : 1;
        int boxX = side > 0 ? 0 : sw;
        int ox = Math.round(px - bx / 2f - boxX), oy = Math.round(py - hPx() / 2f);
        ox = Math.max(Math.round(2 * d()) - boxX, Math.min(ox, areaW - bx - boxX - Math.round(2 * d())));
        oy = Math.max(Math.round(2 * d()), Math.min(oy, areaH - hPx() - Math.round(2 * d())));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wPx(), hPx());
        lp.leftMargin = ox; lp.topMargin = oy; setLayoutParams(lp);
        removeCallbacks(hideRun); animate().cancel();
        setVisibility(VISIBLE); setAlpha(1f); setScaleX(1.25f); setScaleY(1.25f);
        animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        invalidate(); keepAlive();
    }

    void setT(float v) { t = Math.max(0f, Math.min(1f, v)); invalidate(); }

    void keepAlive() { removeCallbacks(hideRun); if (!locked && shown) postDelayed(hideRun, 2800); }

    void hideNow() { removeCallbacks(hideRun); animate().cancel(); locked = false; shown = false; setVisibility(GONE); }

    @Override protected void onDraw(Canvas c) {
        float d = d(), W = getWidth(), H = getHeight();
        float bx = boxPx(), sw = sidePx();
        float boxX = side > 0 ? 0 : sw;
        float top = (H - bx) / 2f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f * d); p.setColor(DualUi.YELLOW);
        r.set(boxX + d, top, boxX + bx - d, top + bx); c.drawRect(r, p);
        float tk = 6 * d;
        c.drawLine(r.centerX(), r.top, r.centerX(), r.top + tk, p);
        c.drawLine(r.centerX(), r.bottom, r.centerX(), r.bottom - tk, p);
        c.drawLine(r.left, r.centerY(), r.left + tk, r.centerY(), p);
        c.drawLine(r.right, r.centerY(), r.right - tk, r.centerY(), p);
        // 曝光滑轨 + 太阳
        float tx = side > 0 ? r.right + 20 * d : r.left - 20 * d;
        float y0 = 8 * d, y1 = H - 8 * d;
        p.setStrokeWidth(1.2f * d); c.drawLine(tx, y0, tx, y1, p);
        float sy = y1 - t * (y1 - y0), rad = 4.5f * d;
        p.setStyle(Paint.Style.FILL); c.drawCircle(tx, sy, rad, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.4f * d);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            c.drawLine(tx + (float) Math.cos(a) * (rad + 3 * d), sy + (float) Math.sin(a) * (rad + 3 * d),
                    tx + (float) Math.cos(a) * (rad + 6 * d), sy + (float) Math.sin(a) * (rad + 6 * d), p);
        }
        if (locked) {
            String s = "AE/AF 锁定";
            p.setStyle(Paint.Style.FILL); p.setTextSize(11 * d); p.setFakeBoldText(true); p.setTextAlign(Paint.Align.CENTER);
            float tw = p.measureText(s), pw = tw + 14 * d, ph = 18 * d, cx = r.centerX(), py = r.top - 22 * d;
            p.setColor(DualUi.YELLOW);
            RectF pill = new RectF(cx - pw / 2, py, cx + pw / 2, py + ph); c.drawRoundRect(pill, ph / 2, ph / 2, p);
            p.setColor(0xFF000000); c.drawText(s, cx, py + ph / 2 - (p.ascent() + p.descent()) / 2, p);
            p.setFakeBoldText(false);
        }
    }
}

package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 多功能面板里的圆形图标：圆底（毛玻璃开关遵循 DualUi.glass）+ 自绘线条图标。全部 Canvas 画，不依赖图片资源。 */
final class PanelGlyph extends View {
    static final int FILTER = 0, BOLT = 1, PALETTE = 2, RATIO = 3, TIMER = 4, GLOBE = 5, DUAL = 6, CUBE = 7, FISH = 8, AIEXT = 9, PRO = 10, FILM = 11;

    private final int kind;
    private boolean active;
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    PanelGlyph(Context c, int kind) {
        super(c);
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND); line.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
        setClickable(false);
    }

    void setActive(boolean a) { if (active != a) { active = a; invalidate(); } }

    @Override protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), s = Math.min(W, H), cx = W / 2, cy = H / 2;
        // 圆底
        bg.setStyle(Paint.Style.FILL);
        if (active) bg.setColor(0x4DFFD60A); else bg.setColor(DualUi.glass ? 0x33FFFFFF : 0x29FFFFFF);
        c.drawCircle(cx, cy, s / 2, bg);
        if (DualUi.glass) {
            bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(Math.max(1f, s * 0.02f)); bg.setColor(active ? 0x99FFD60A : 0x73FFFFFF);
            c.drawCircle(cx, cy, s / 2 - bg.getStrokeWidth() / 2, bg);
        }
        // 图标：画在 24×24 的格子里，居中缩放到圆的 40%
        int col = active ? DualUi.YELLOW : 0xFFFFFFFF;
        float box = s * 0.42f, u = box / 24f, ox = cx - box / 2, oy = cy - box / 2;
        line.setColor(col); fill.setColor(col);
        c.save();
        c.translate(ox, oy); c.scale(u, u);
        line.setStrokeWidth(Math.max(1.8f, 1.5f / u));      // 画布已缩放，线宽按 24 格计
        draw24(c);
        c.restore();
    }

    private void rr(Canvas c, float l, float t, float rt, float b, float rad, Paint p) { r.set(l, t, rt, b); c.drawRoundRect(r, rad, rad, p); }

    private void draw24(Canvas c) {
        switch (kind) {
            case FILTER:
                c.drawCircle(12f, 8.5f, 5f, line); c.drawCircle(8.2f, 15f, 5f, line); c.drawCircle(15.8f, 15f, 5f, line);
                break;
            case BOLT:
                path.reset(); path.moveTo(13.5f, 2.5f); path.lineTo(5.5f, 13.5f); path.lineTo(11.5f, 13.5f); path.lineTo(10.5f, 21.5f);
                path.lineTo(18.5f, 10.5f); path.lineTo(12.5f, 10.5f); path.close();
                c.drawPath(path, line);
                break;
            case PALETTE:
                rr(c, 3.5f, 3.5f, 20.5f, 20.5f, 5f, line);
                for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) c.drawCircle(8.5f + i * 3.5f, 8.5f + j * 3.5f, 1f, fill);
                break;
            case RATIO: {
                float a = 3.5f, b = 20.5f, k = 5f;
                path.reset();
                path.moveTo(a, a + k); path.lineTo(a, a); path.lineTo(a + k, a);
                path.moveTo(b - k, a); path.lineTo(b, a); path.lineTo(b, a + k);
                path.moveTo(b, b - k); path.lineTo(b, b); path.lineTo(b - k, b);
                path.moveTo(a + k, b); path.lineTo(a, b); path.lineTo(a, b - k);
                c.drawPath(path, line);
                break;
            }
            case TIMER:
                c.drawCircle(12f, 13.5f, 7.5f, line);
                c.drawLine(9.5f, 3f, 14.5f, 3f, line); c.drawLine(12f, 3f, 12f, 6f, line); c.drawLine(12f, 13.5f, 12f, 9.5f, line);
                break;
            case GLOBE:
                c.drawCircle(12f, 12f, 8.5f, line);
                r.set(8.2f, 3.5f, 15.8f, 20.5f); c.drawOval(r, line);
                c.drawLine(3.5f, 12f, 20.5f, 12f, line);
                break;
            case DUAL:
                rr(c, 3f, 6f, 21f, 18f, 3.5f, line);
                rr(c, 12f, 8.5f, 17.5f, 12.5f, 1.5f, fill);
                break;
            case CUBE:
                path.reset(); path.moveTo(12f, 3f); path.lineTo(19.5f, 7.5f); path.lineTo(19.5f, 16.5f); path.lineTo(12f, 21f);
                path.lineTo(4.5f, 16.5f); path.lineTo(4.5f, 7.5f); path.close();
                path.moveTo(4.5f, 7.5f); path.lineTo(12f, 12f); path.lineTo(19.5f, 7.5f); path.moveTo(12f, 12f); path.lineTo(12f, 21f);
                c.drawPath(path, line);
                break;
            case FISH:
                r.set(3.5f, 7.5f, 17.5f, 16.5f); c.drawOval(r, line);
                path.reset(); path.moveTo(16.5f, 12f); path.lineTo(21f, 8f); path.lineTo(21f, 16f); path.close();
                c.drawPath(path, line);
                c.drawCircle(7.5f, 11f, 1f, fill);
                break;
            case AIEXT:
                rr(c, 8f, 8f, 16f, 16f, 1.8f, line);
                c.drawLine(12f, 2.5f, 12f, 8f, line); c.drawLine(12f, 16f, 12f, 21.5f, line);
                c.drawLine(2.5f, 12f, 8f, 12f, line); c.drawLine(16f, 12f, 21.5f, 12f, line);
                break;
            case PRO:
                c.drawCircle(12f, 13f, 8f, line); c.drawCircle(12f, 13f, 3.2f, line);
                c.drawLine(12f, 2.5f, 12f, 5f, line);
                break;
            case FILM:
                rr(c, 4.5f, 3.5f, 19.5f, 20.5f, 2.5f, line);
                c.drawLine(8.5f, 3.5f, 8.5f, 20.5f, line); c.drawLine(15.5f, 3.5f, 15.5f, 20.5f, line);
                for (int i = 0; i < 3; i++) {
                    float y = 8f + i * 4f;
                    c.drawLine(4.5f, y, 8.5f, y, line); c.drawLine(15.5f, y, 19.5f, y, line);
                }
                break;
            default: break;
        }
    }
}

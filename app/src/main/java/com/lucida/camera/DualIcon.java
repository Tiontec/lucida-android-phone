package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 双摄界面的圆形图标按钮（全部用 Canvas 画，不依赖图片资源）。 */
final class DualIcon extends View {
    static final int BACK = 0, LIVE = 1, PALETTE = 2, GEAR = 3, PIP = 4, SPLIT = 5, SHAPE = 6, MIRROR = 7, TORCH = 8, SWAP = 9, PHOTO = 10, DOWN = 11, RESET = 12, MORE = 13;
    int kind;
    boolean on, round = true;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    int tintOn = 0xFFFFD60A, tintOff = 0xFFFFFFFF, bgColor = 0x29FFFFFF;
    int shapeStyle = 0;
    float padX = 0f, padY = 0f;      // 调色板图标：中心大点的位置（-1…1，跟着色盘落点走）
    void setPad(float x, float y) { padX = x; padY = y; invalidate(); }

    DualIcon(Context c, int kind) {
        super(c);
        this.kind = kind;
        setClickable(true);
        p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
    }

    // ---- 液态按压效果：按下有一团光跟着手指，拖动时朝手指方向拉伸，松手弹回
    private float glowX, glowY, glowA;
    private boolean touching;
    private android.graphics.RadialGradient glowShader;
    private final android.graphics.Matrix glowM = new android.graphics.Matrix();
    private final Paint glowP = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!round || !isEnabled()) return super.onTouchEvent(e);
        int a = e.getActionMasked();
        float W = Math.max(1, getWidth()), H = Math.max(1, getHeight());
        if (a == android.view.MotionEvent.ACTION_DOWN || a == android.view.MotionEvent.ACTION_MOVE) {
            glowX = e.getX(); glowY = e.getY();
            float dx = Math.max(-1.2f, Math.min(1.2f, (glowX - W / 2) / W)), dy = Math.max(-1.2f, Math.min(1.2f, (glowY - H / 2) / H));
            animate().cancel();
            setScaleX(1.06f + Math.abs(dx) * 0.3f); setScaleY(1.06f + Math.abs(dy) * 0.3f);
            setTranslationX(dx * W * 0.16f); setTranslationY(dy * H * 0.16f);
            if (a == android.view.MotionEvent.ACTION_DOWN) { touching = true; glowA = 0f; fadeGlow(1f, 170); }
            invalidate();
        } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            touching = false; fadeGlow(0f, 320);
            animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(520)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(4f)).start();
        }
        return super.onTouchEvent(e);
    }

    private void fadeGlow(float to, int ms) {
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(glowA, to);
        va.setDuration(ms);
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(android.animation.ValueAnimator an) {
            glowA = (Float) an.getAnimatedValue(); invalidate(); } });
        va.start();
    }

    private void drawGlow(Canvas c, float cx, float cy, float s) {
        if (glowA <= 0.01f) return;
        if (glowShader == null) glowShader = new android.graphics.RadialGradient(0f, 0f, 1f, new int[]{0x9EFFFFFF, 0x2EFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP);
        float rad = s * 0.78f;
        glowM.reset(); glowM.setScale(rad, rad); glowM.postTranslate(glowX, glowY);
        glowShader.setLocalMatrix(glowM);
        glowP.setShader(glowShader); glowP.setAlpha((int) (255 * Math.min(1f, glowA)));
        c.save(); path.reset(); path.addCircle(cx, cy, s / 2, Path.Direction.CW); c.clipPath(path);
        c.drawCircle(glowX, glowY, rad, glowP); c.restore();
    }

    void set(boolean on) { this.on = on; invalidate(); }
    void setKind(int k) { kind = k; invalidate(); }

    @Override
    protected void onDraw(Canvas c) {
        float W = getWidth(), H = getHeight(), s = Math.min(W, H), cx = W / 2, cy = H / 2;
        if (round && bgColor != 0) {
            bg.setStyle(Paint.Style.FILL);
            boolean refracted = false;
            if (DualUi.liquid()) {      // 液态玻璃：背后的取景画面按透镜折弯（取景没出画时 draw 返回 false，退回下面的毛玻璃）
                c.save(); c.translate(cx - s / 2, cy - s / 2);
                refracted = GlassRefract.I.draw(c, this, s, s, s / 2);
                c.restore();
            }
            if (refracted) { /* 已画好 */ }
            else if (DualUi.glass) {
                bg.setColor(0x33FFFFFF); c.drawCircle(cx, cy, s / 2, bg);
                bg.setStyle(Paint.Style.STROKE); bg.setStrokeWidth(Math.max(1f, s * 0.026f)); bg.setColor(0x8CFFFFFF);
                c.drawCircle(cx, cy, s / 2 - bg.getStrokeWidth() / 2, bg); bg.setStyle(Paint.Style.FILL);
            } else { bg.setColor(bgColor); c.drawCircle(cx, cy, s / 2, bg); }
        }
        if (round && bgColor != 0) drawGlow(c, cx, cy, s);
        int col = on ? tintOn : tintOff;
        if (!isEnabled()) col = (col & 0x00FFFFFF) | 0x66000000;
        p.setColor(col); p.setStrokeWidth(Math.max(2f, s * 0.055f));
        p.setStyle(Paint.Style.STROKE);
        float u = s * 0.2f;      // 图标半尺寸 ≈ 0.2s
        switch (kind) {
            case BACK:
                path.reset(); path.moveTo(cx + u * .45f, cy - u); path.lineTo(cx - u * .55f, cy); path.lineTo(cx + u * .45f, cy + u);
                c.drawPath(path, p); break;
            case DOWN:
                path.reset(); path.moveTo(cx - u, cy - u * .45f); path.lineTo(cx, cy + u * .55f); path.lineTo(cx + u, cy - u * .45f);
                c.drawPath(path, p); break;
            case LIVE:
                c.drawCircle(cx, cy, u * 1.05f, p);
                p.setStyle(Paint.Style.FILL); c.drawCircle(cx, cy, u * .38f, p); p.setStyle(Paint.Style.STROKE);
                r.set(cx - u * 1.6f, cy - u * 1.6f, cx + u * 1.6f, cy + u * 1.6f);
                p.setPathEffect(new android.graphics.DashPathEffect(new float[]{s * .04f, s * .06f}, 0));
                c.drawOval(r, p); p.setPathEffect(null); break;
            case PALETTE: {
                // 和主界面一样：圆角方框 + 5×5 点阵，中心大点跟着色盘落点移动；开启黄色、关闭白色
                float half = s * 0.34f;
                p.setStrokeWidth(Math.max(2f, s * 0.05f));
                r.set(cx - half, cy - half, cx + half, cy + half);
                c.drawRoundRect(r, half * 0.56f, half * 0.56f, p);
                float in = half * 0.6f, step = in * 2 / 4, dot = s * 0.03f;
                p.setStyle(Paint.Style.FILL);
                int base = p.getColor(); p.setColor((base & 0x00FFFFFF) | 0xD9000000);
                for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) c.drawCircle(cx - in + step * i, cy - in + step * j, dot, p);
                p.setColor(base);
                c.drawCircle(cx + padX * in, cy - padY * in, s * 0.075f, p);
                p.setStyle(Paint.Style.STROKE);
                break;
            }
            case GEAR:
                c.drawCircle(cx, cy, u * .55f, p);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    c.drawLine(cx + (float) Math.cos(a) * u * 1.0f, cy + (float) Math.sin(a) * u * 1.0f,
                            cx + (float) Math.cos(a) * u * 1.45f, cy + (float) Math.sin(a) * u * 1.45f, p);
                }
                c.drawCircle(cx, cy, u * 1.0f, p); break;
            case MORE: {
                float g = u * .62f, q = u * .5f;
                p.setStyle(Paint.Style.FILL);
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) {
                    r.set(cx + (i == 0 ? -g - q : g - q), cy + (j == 0 ? -g - q : g - q), cx + (i == 0 ? -g + q : g + q), cy + (j == 0 ? -g + q : g + q));
                    c.drawRoundRect(r, q * .35f, q * .35f, p);
                }
                p.setStyle(Paint.Style.STROKE); break;
            }
            case PIP:
                r.set(cx - u * 1.3f, cy - u * 1.3f, cx + u * 1.3f, cy + u * 1.3f); c.drawRoundRect(r, u * .4f, u * .4f, p);
                r.set(cx + u * .1f, cy - u * .9f, cx + u * .9f, cy - u * .1f); p.setStyle(Paint.Style.FILL); c.drawRoundRect(r, u * .2f, u * .2f, p); break;
            case SPLIT:
                r.set(cx - u * 1.3f, cy - u * 1.3f, cx + u * 1.3f, cy + u * 1.3f); c.drawRoundRect(r, u * .4f, u * .4f, p);
                c.drawLine(cx - u * 1.3f, cy, cx + u * 1.3f, cy, p); break;
            case SHAPE: {
                float rad = shapeStyle == 0 ? u * .5f : shapeStyle == 1 ? u * 1.2f : 0f;
                r.set(cx - u * 1.1f, cy - u * 1.1f, cx + u * 1.1f, cy + u * 1.1f); c.drawRoundRect(r, rad, rad, p); break;
            }
            case MIRROR:
                c.drawLine(cx, cy - u * 1.3f, cx, cy + u * 1.3f, p);
                path.reset(); path.moveTo(cx - u * .3f, cy - u * .9f); path.lineTo(cx - u * 1.3f, cy + u * .9f); path.lineTo(cx - u * .3f, cy + u * .9f); path.close(); c.drawPath(path, p);
                path.reset(); path.moveTo(cx + u * .3f, cy - u * .9f); path.lineTo(cx + u * 1.3f, cy + u * .9f); path.lineTo(cx + u * .3f, cy + u * .9f); path.close(); c.drawPath(path, p); break;
            case TORCH:
                path.reset(); path.moveTo(cx + u * .3f, cy - u * 1.4f); path.lineTo(cx - u * .8f, cy + u * .2f); path.lineTo(cx, cy + u * .2f);
                path.lineTo(cx - u * .3f, cy + u * 1.4f); path.lineTo(cx + u * .8f, cy - u * .3f); path.lineTo(cx, cy - u * .3f); path.close();
                p.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE); c.drawPath(path, p); break;
            case SWAP:
                r.set(cx - u * 1.2f, cy - u * 1.2f, cx + u * 1.2f, cy + u * 1.2f);
                c.drawArc(r, 200, 140, false, p); c.drawArc(r, 20, 140, false, p);
                path.reset(); path.moveTo(cx + u * 1.2f * .94f - u * .5f, cy - u * .4f); path.lineTo(cx + u * 1.2f * .94f, cy - u * .4f); path.lineTo(cx + u * 1.2f * .94f, cy - u * .95f);
                c.drawPath(path, p);
                path.reset(); path.moveTo(cx - u * 1.2f * .94f + u * .5f, cy + u * .4f); path.lineTo(cx - u * 1.2f * .94f, cy + u * .4f); path.lineTo(cx - u * 1.2f * .94f, cy + u * .95f);
                c.drawPath(path, p); break;
            case PHOTO:
                r.set(cx - u * 1.3f, cy - u * 1.0f, cx + u * 1.3f, cy + u * 1.0f); c.drawRoundRect(r, u * .3f, u * .3f, p);
                c.drawCircle(cx - u * .5f, cy - u * .3f, u * .22f, p);
                path.reset(); path.moveTo(cx - u * 1.2f, cy + u * .9f); path.lineTo(cx - u * .2f, cy); path.lineTo(cx + u * .4f, cy + u * .6f); path.lineTo(cx + u * .8f, cy + u * .2f); path.lineTo(cx + u * 1.2f, cy + u * .7f);
                c.drawPath(path, p); break;
            case RESET:
                r.set(cx - u * 1.1f, cy - u * 1.1f, cx + u * 1.1f, cy + u * 1.1f);
                c.drawArc(r, -60, 290, false, p);
                path.reset(); path.moveTo(cx + u * .2f, cy - u * 1.5f); path.lineTo(cx + u * .6f, cy - u * 1.1f); path.lineTo(cx + u * .1f, cy - u * .75f);
                c.drawPath(path, p); break;
            default: break;
        }
    }
}

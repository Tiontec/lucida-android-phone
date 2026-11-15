package com.lucida.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 变焦「药丸条 + 圆弧转盘」：照抄主页面的实现（原生 Canvas，不是网页）。
 *  药丸条：.5 1 2 3 5 10 15 20 25 30（当前档位放大、黄字、深色圆底，显示实际倍数）
 *  长按 300ms / 拖动 → 展开圆弧转盘（刻度、MM 焦距、黄色三角指针、读数）
 *  拖动：弹簧阻尼（拖动时 ω=48，松手 11.5）、档位磁吸（mag / detent）、边界橡皮筋（rub）、松手惯性（衰减 e^(-3.6t)）、1.7 秒后自动收起
 * 单位：所有数值都按「CSS 像素 = dp」写，和主页面一一对应；绘制时整体按屏幕密度缩放。
 */
final class ZoomBar extends View {
    interface Listener { void onZoom(float zoom); }

    private static final double[] STOPS = {.5, 1, 2, 3, 5, 10, 15, 20, 25, 30};
    private static final String[] LPRI = {"1", "10", "30", "5", "2", "3", "0.5", "0.6", "20", "15", "25"};
    private static final double[] MAJ_BASE = {1, 2, 3, 5, 10, 15, 20, 25, 30};

    private Listener listener;
    private final float dens;
    // 物理状态（对应主页面 S.*）
    private double lz = 0, lt = 0, lv = 0, raw = 0, om = 11.5, fling = 0;
    private boolean drag = false;
    // 范围
    private double zMax = 30, uw = 0;       // uw>0 = 有超广角，值为最小倍数（≥0.5）
    private double lmax = Math.log(30);
    private double PXLN = 150;
    // 刻度
    private static final class Tick { double l; boolean maj; String txt; int mm; }
    private final ArrayList<Tick> ticks = new ArrayList<>();
    private final ArrayList<Double> majs = new ArrayList<>();
    // 转盘 / 药丸
    private boolean dialOpen = false, g_on = false, g_moved = false, g_opened = false;
    private String g_src = "";
    private double g_x0, g_y0, g_px, g_s0, g_raw0, g_v; private long g_pt;
    private float dialP = 0f;
    private final float[] btnSize = new float[STOPS.length];
    private boolean sized = false;
    private float lastZoomOut = -1;
    private boolean looping = false;
    private long lastNs = 0;
    private int zkey = -1;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    private Typeface semi;
    private final Runnable hideRun = new Runnable() { @Override public void run() { closeDial(); } };
    private final Runnable holdRun = new Runnable() { @Override public void run() { if (g_on && !g_moved) { openDial(); g_opened = true; } } };

    ZoomBar(Context c) {
        super(c);
        dens = c.getResources().getDisplayMetrics().density;
        try { semi = Typeface.create("sans-serif-medium", Typeface.NORMAL); } catch (Throwable t) { semi = Typeface.DEFAULT_BOLD; }
        setRange(1f, 30f, true);
    }

    void setListener(Listener l) { listener = l; }

    /** hwMin/hwMax = 当前这颗摄像头的真实倍数范围；hasUw = 有没有 <1× 的超广角可用。 */
    void setRange(float hwMin, float hwMax, boolean hasUw) {
        uw = hasUw && hwMin < 0.98f ? Math.max(0.5, hwMin) : 0;
        zMax = Math.max(1.5, Math.min(30, hwMax));
        lmax = Math.log(zMax);
        buildTicks();
        raw = lt = lz = clampLn(lz);
        sized = false;
        zkey = -1;
        invalidate();
    }

    /** 外部设置倍数（比如换主画面）：不触发回调。 */
    void setZoom(float z) {
        fling = 0; drag = false;
        raw = lt = lz = clampLn(Math.log(Math.max(0.05, z)));
        lv = 0;
        invalidate();
    }

    float zoom() { return (float) Math.exp(lz); }
    private double minLn() { return uw > 0 ? Math.log(uw) : 0; }
    private double clampLn(double l) { return Math.min(lmax, Math.max(minLn(), l)); }

    private void buildTicks() {
        ticks.clear(); majs.clear();
        double lo = uw > 0 ? uw : 1;
        if (uw > 0) majs.add(lo);
        for (double m : MAJ_BASE) if (m <= zMax + .001) majs.add(m);
        double v = lo;
        int guard = 0;
        while (v <= zMax + .001 && guard++ < 400) {
            double k = Math.round(v * 100) / 100.0;
            Tick t = new Tick();
            t.l = Math.log(k);
            t.maj = false;
            for (double m : majs) if (Math.abs(m - k) < 1e-6) t.maj = true;
            t.txt = fmtNum(k);
            t.mm = mmOf(k);
            ticks.add(t);
            v = k < 2 ? k + .1 : k < 5 ? k + .2 : k < 10 ? k + .5 : k < 30 ? k + 1 : k < 50 ? k + 5 : k + 10;
        }
    }

    private static String fmtNum(double k) {
        if (Math.abs(k - Math.rint(k)) < 1e-9) return String.valueOf((long) Math.rint(k));
        String s = String.format(Locale.US, "%.2f", k);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s;
    }

    private static int mmOf(double k) {
        if (Math.abs(k - .5) < 1e-6) return 13;
        if (Math.abs(k - .6) < 1e-6) return 16;
        if (Math.abs(k - 1) < 1e-6) return 26;
        if (Math.abs(k - 2) < 1e-6) return 52;
        if (Math.abs(k - 3) < 1e-6) return 77;
        if (Math.abs(k - 5) < 1e-6) return 120;
        if (Math.abs(k - 10) < 1e-6) return 240;
        if (Math.abs(k - 15) < 1e-6) return 360;
        if (Math.abs(k - 20) < 1e-6) return 480;
        if (Math.abs(k - 25) < 1e-6) return 600;
        if (Math.abs(k - 30) < 1e-6) return 720;
        return (int) Math.round(26 * k);
    }

    // ---------------------------------------------------------------- 物理（对应主页面 rub / mag / detent / step）

    private double rub(double x) {
        double lo = minLn();
        if (x < lo) return lo - .12 * Math.tanh((lo - x) / .3);
        if (x > lmax) return lmax + .12 * Math.tanh((x - lmax) / .3);
        return x;
    }

    private double mag(double l) {
        for (double s : majs) {
            double c = Math.log(s), d = l - c, W = .04;
            if (Math.abs(d) < W) return c + Math.signum(d) * W * Math.pow(Math.abs(d) / W, 2.2);
        }
        return l;
    }

    private void detent() {
        double lo = minLn();
        for (double s : majs) {
            double c = Math.log(s);
            if (c >= lo - .001 && Math.abs(raw - c) < .06) { raw = c; lt = c; return; }
        }
    }

    private void zoomTo(double z) { fling = 0; raw = lt = clampLn(Math.log(z)); startLoop(); }

    private void step(double dt) {
        om += ((drag ? 48 : 11.5) - om) * Math.min(1, dt * 20);
        double w = om, ze = drag ? 1 : .9;
        int n = Math.max(1, (int) Math.ceil(dt / .006));
        double h = dt / n;
        for (int i = 0; i < n; i++) {
            double a = w * w * (lt - lz) - 2 * ze * w * lv;
            lv += a * h; lz += lv * h;
        }
    }

    private boolean moving() { return drag || g_on || fling != 0 || Math.abs(lt - lz) > .004 || Math.abs(lv) > .012; }

    // ---------------------------------------------------------------- 主循环

    private void startLoop() {
        if (looping) return;
        looping = true; lastNs = 0;
        Choreographer.getInstance().postFrameCallback(frame);
    }

    private final Choreographer.FrameCallback frame = new Choreographer.FrameCallback() {
        @Override public void doFrame(long ns) {
            double dt = lastNs == 0 ? .016 : Math.min((ns - lastNs) / 1e9, .05);
            lastNs = ns;
            if (!drag && fling != 0) {
                raw += fling * dt; fling *= Math.exp(-dt * 3.6);
                double lo = minLn();
                if (raw > lmax || raw < lo) { raw = clampLn(raw); fling = 0; lt = raw; scheduleHide(); }
                else { lt = mag(raw); if (Math.abs(fling) < .03) { fling = 0; detent(); scheduleHide(); } }
            }
            step(dt);
            // 药丸 / 转盘的显隐动画
            float tgt = dialOpen ? 1f : 0f;
            dialP += (tgt - dialP) * (float) Math.min(1, dt * 11);
            if (Math.abs(tgt - dialP) < .004f) dialP = tgt;
            boolean animBtn = animateButtons((float) dt);
            float z = (float) Math.exp(lz);
            if (listener != null && Math.abs(z - lastZoomOut) > 1e-4f) { lastZoomOut = z; listener.onZoom(z); }
            invalidate();
            if (moving() || dialP != tgt || dialOpen || animBtn) Choreographer.getInstance().postFrameCallback(this);
            else looping = false;
        }
    };

    // ---------------------------------------------------------------- 药丸条

    private int[] visibleStops() {
        ArrayList<Integer> a = new ArrayList<>();
        for (int i = 0; i < STOPS.length; i++) {
            double s = STOPS[i];
            if (i == 0 && uw <= 0) continue;
            if (s > zMax + .001) continue;
            a.add(i);
        }
        int[] r = new int[a.size()];
        for (int i = 0; i < r.length; i++) r[i] = a.get(i);
        return r;
    }

    private int activeIdx() {
        double z = Math.exp(lz);
        int a = 0;
        for (int i = 0; i < STOPS.length; i++) if (z >= STOPS[i] - .001) a = i;
        if (uw <= 0 && a == 0) a = 1;
        return a;
    }

    private static String fmtZ(double z) {
        double r = Math.round(z * 10) / 10.0;
        return (r % 1 == 0 ? String.valueOf((long) r) : String.format(Locale.US, "%.1f", r)) + "×";
    }

    private float baseBtn() {
        int[] vs = visibleStops();
        float W = getWidth() / dens - 12 - 8;
        float need = (vs.length - 1) * 34f + 44f + (vs.length - 1) * 2f;
        return need > W ? Math.max(24f, 34f * W / need) : 34f;
    }

    private boolean animateButtons(float dt) {
        float bs = baseBtn(), big = bs * 44f / 34f;
        int a = activeIdx();
        boolean any = false;
        for (int i = 0; i < btnSize.length; i++) {
            float t = i == a ? big : bs;
            if (!sized) btnSize[i] = t;
            float d = t - btnSize[i];
            if (Math.abs(d) > .05f) { btnSize[i] += d * Math.min(1f, dt * 14f); any = true; } else btnSize[i] = t;
        }
        sized = true;
        return any;
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void onDraw(Canvas c) {
        float W = getWidth() / dens, H = getHeight() / dens;
        if (W <= 0) return;
        PXLN = Math.max(25, W * .347 / Math.log(30));
        if (!sized) animateButtons(0f);
        c.save();
        c.scale(dens, dens);
        drawPill(c, W, H);
        if (dialP > 0.003f) drawDial(c, W, H);
        c.restore();
    }

    private void drawPill(Canvas c, float W, float H) {
        float a = 1f - dialP;
        if (a <= 0.01f) return;
        int[] vs = visibleStops();
        if (vs.length == 0) return;
        float gap = 2f, pad = 4f, w = pad * 2;
        float maxB = 0;
        for (int i = 0; i < vs.length; i++) { w += btnSize[vs[i]]; maxB = Math.max(maxB, btnSize[vs[i]]); if (i > 0) w += gap; }
        float h = maxB + pad * 2;
        float sc = .7f + .3f * a, ty = 14f * (1f - a);
        c.save();
        float cx = W / 2f, bottom = H - 14f;
        c.translate(0, ty);
        c.scale(sc, sc, cx, bottom);
        float left = cx - w / 2f, top = bottom - h;
        rf.set(left, top, left + w, bottom);
        p.setStyle(Paint.Style.FILL); p.setShader(null);
        p.setColor(argb(a * .62f, 24, 24, 28));
        c.drawRoundRect(rf, 30, 30, p);
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f);
        p.setShader(new LinearGradient(left, top, left + w, bottom, new int[]{argb(a * .75f, 255, 255, 255), argb(0, 255, 255, 255), argb(a * .45f, 255, 255, 255)}, new float[]{0, .5f, 1f}, Shader.TileMode.CLAMP));
        rf.inset(.5f, .5f);
        c.drawRoundRect(rf, 30, 30, p);
        p.setShader(null);
        float x = left + pad;
        int act = activeIdx();
        double z = Math.exp(lz);
        for (int i = 0; i < vs.length; i++) {
            int k = vs[i];
            float s = btnSize[k], cy = top + h / 2f, bx = x + s / 2f;
            boolean on = k == act;
            if (on) {
                p.setStyle(Paint.Style.FILL); p.setColor(argb(a * .42f, 0, 0, 0));
                c.drawCircle(bx, cy, s / 2f, p);
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(argb(a * .12f, 255, 255, 255));
                c.drawCircle(bx, cy, s / 2f - .5f, p);
            }
            String t = on ? fmtZ(z) : (i == 0 && vs[0] == 0 ? ".5" : fmtNum(STOPS[k]));
            p.setStyle(Paint.Style.FILL); p.setTypeface(semi);
            p.setTextSize(on ? 13f : 12f); p.setTextAlign(Paint.Align.CENTER);
            p.setColor(on ? argb(a, 255, 214, 10) : argb(a, 255, 255, 255));
            c.drawText(t, bx, cy - (p.ascent() + p.descent()) / 2f, p);
            x += s + gap;
        }
        c.restore();
    }

    private double dR(float W, float dH) {
        double d = Math.max(60, dH - 8);
        return Math.max(160, ((W / 2 + 8) * (W / 2 + 8) + d * d) / (2 * d));
    }

    private void drawDial(Canvas c, float W, float H) {
        float dH = 140f, top = H - dH;
        double R = dR(W, dH);
        float cx = W / 2f, cy = (float) (8 + R);
        float a0 = dialP;
        c.save();
        c.translate(0, top);
        float sc = .94f + .06f * a0, ty = 50f * (1f - a0);
        c.translate(0, ty);
        c.scale(sc, sc, W / 2f, dH);
        // 底板：圆形裁剪的渐变玻璃
        path.reset(); path.addCircle(cx, cy, (float) R, Path.Direction.CW);
        c.save();
        c.clipPath(path);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0, 0, 0, dH, argb(a0 * .34f, 40, 40, 44), argb(a0 * .52f, 14, 14, 16), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, dH, p);
        p.setShader(null);
        c.restore();
        double lim = Math.asin(Math.min(1, (W / 2 + 34) / R));
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1f); p.setColor(argb(a0 * .2f, 255, 255, 255));
        rf.set((float) (cx - R + .5), (float) (cy - R + .5), (float) (cx + R - .5), (float) (cy + R - .5));
        c.drawArc(rf, (float) Math.toDegrees(-Math.PI / 2 - lim), (float) Math.toDegrees(2 * lim), false, p);
        // 数字摆放：档位越高刻度越密，按优先级摆放，间距不足 34 的不画
        ArrayList<Tick> show = new ArrayList<>();
        ArrayList<Double> placed = new ArrayList<>();
        double cur = lz;
        for (String k : LPRI) {
            Tick t = null;
            for (Tick q : ticks) if (q.maj && q.txt.equals(k)) { t = q; break; }
            if (t == null) continue;
            double px = (t.l - cur) * PXLN;
            if (Math.abs(px) < 22 || Math.abs(px) > W) continue;
            boolean ok = true;
            for (double pp : placed) if (Math.abs(pp - px) < 34) { ok = false; break; }
            if (ok) { placed.add(px); show.add(t); }
        }
        double lo = minLn();
        p.setTextAlign(Paint.Align.CENTER); p.setTypeface(semi); p.setStrokeCap(Paint.Cap.ROUND);
        for (Tick t : ticks) {
            double th = (t.l - cur) * PXLN / R;
            if (Math.abs(th) > lim) continue;
            float a = (float) (Math.max(0, 1 - Math.pow(Math.abs(th) / lim, 2) * .8) * (t.l < lo - .001 ? .3 : 1)) * a0;
            c.save();
            c.translate(cx, cy);
            c.rotate((float) Math.toDegrees(th));
            p.setStyle(Paint.Style.STROKE);
            p.setColor(argb(a * (t.maj ? .92f : .45f), 255, 255, 255));
            p.setStrokeWidth(t.maj ? 1.8f : 1f);
            c.drawLine(0, (float) (-R + 14), 0, (float) (-R + 14 + (t.maj ? 18 : 9)), p);
            if (show.contains(t)) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(argb(a * .95f, 255, 255, 255)); p.setTextSize(15f);
                c.drawText(t.txt, 0, (float) (-R + 54) - (p.ascent() + p.descent()) / 2f, p);
                p.setColor(argb(a * .5f, 255, 255, 255)); p.setTextSize(8.5f);
                c.drawText(t.mm + "MM", 0, (float) (-R + 67) - (p.ascent() + p.descent()) / 2f, p);
            }
            c.restore();
        }
        // 黄色指针 + 读数
        p.setStyle(Paint.Style.FILL); p.setColor(argb(a0, 255, 214, 10));
        path.reset(); path.moveTo(cx - 5, 2); path.lineTo(cx + 5, 2); path.lineTo(cx, 10.5f); path.close();
        c.drawPath(path, p);
        p.setTextSize(16f);
        c.drawText(fmtZ(Math.exp(lz)), cx, 48 - (p.ascent() + p.descent()) / 2f, p);
        c.restore();
    }

    private static int argb(float a, int r, int g, int b) { return ((int) (Math.max(0, Math.min(1, a)) * 255) << 24) | (r << 16) | (g << 8) | b; }

    // ---------------------------------------------------------------- 转盘开合

    private void openDial() {
        removeCallbacks(hideRun);
        if (!dialOpen) { dialOpen = true; startLoop(); }
    }

    private void closeDial() {
        if (g_on || fling != 0) return;
        dialOpen = false; startLoop();
    }

    private void scheduleHide() { removeCallbacks(hideRun); postDelayed(hideRun, 1700); }

    // ---------------------------------------------------------------- 手势（对应主页面 gStart / gMove / gEnd）

    private double arcPos(float xDp, float yDp) {
        float W = getWidth() / dens, H = getHeight() / dens;
        double R = dR(W, 140f);
        double cx = W / 2.0, cy = H - 140 + 8 + R;
        return Math.atan2(xDp - cx, cy - yDp) * R;
    }

    private boolean inPill(float x, float y) {
        float W = getWidth() / dens, H = getHeight() / dens;
        int[] vs = visibleStops();
        float w = 8;
        for (int i = 0; i < vs.length; i++) w += btnSize[vs[i]] + (i > 0 ? 2 : 0);
        float left = W / 2 - w / 2 - 6, right = W / 2 + w / 2 + 6, bottom = H - 14 + 6, top = H - 14 - 52 - 6;
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX() / dens, y = e.getY() / dens, H = getHeight() / dens;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                boolean inDial = dialOpen && dialP > .3f && y >= H - 140;
                boolean pill = !dialOpen && inPill(x, y);
                if (!inDial && !pill) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                g_on = true; g_src = inDial ? "dial" : "pill";
                g_x0 = g_px = x; g_y0 = y; g_pt = System.nanoTime(); g_v = 0; g_moved = false; g_opened = false;
                fling = 0; g_raw0 = lz;
                removeCallbacks(hideRun);
                if (g_src.equals("pill")) postDelayed(holdRun, 300);
                startLoop();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!g_on) return false;
                if (!g_moved && Math.hypot(x - g_x0, y - g_y0) > 6) {
                    g_moved = true; g_s0 = g_px = arcPos(x, y); g_raw0 = lz; raw = lz; drag = true; fling = 0;
                    removeCallbacks(holdRun);
                    openDial();
                }
                if (!g_moved) return true;
                long now = System.nanoTime();
                double dt = (now - g_pt) / 1e9, sp = arcPos(x, y);
                if (dt > 0) {
                    double inst = -(sp - g_px) / PXLN / dt;
                    g_v = g_v * .6 + Math.max(-14, Math.min(14, inst)) * .4;
                }
                g_px = sp; g_pt = now;
                raw = g_raw0 - (sp - g_s0) / PXLN;
                lt = rub(mag(raw));
                startLoop();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!g_on) return false;
                g_on = false;
                removeCallbacks(holdRun);
                if (g_moved) {
                    drag = false;
                    double lo = minLn();
                    if (raw < lo || raw > lmax) { raw = clampLn(raw); lt = raw; fling = 0; scheduleHide(); }
                    else {
                        boolean stale = (System.nanoTime() - g_pt) / 1e6 > 90;
                        double v = stale ? 0 : g_v;
                        if (Math.abs(v) > .12) fling = Math.max(-10, Math.min(10, v));
                        else { detent(); scheduleHide(); }
                    }
                } else if (g_src.equals("dial") && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    float W = getWidth() / dens;
                    double R = dR(W, 140f), cx = W / 2.0;
                    for (Tick t : ticks) {
                        if (!t.maj) continue;
                        double th = (t.l - lz) * PXLN / R, sx = cx + R * Math.sin(th);
                        if (Math.abs(x - sx) < 30 && Math.abs(th) < 1) { zoomTo(Math.exp(t.l)); break; }
                    }
                    scheduleHide();
                } else if (g_opened) {
                    scheduleHide();
                } else if (g_src.equals("pill") && e.getActionMasked() == MotionEvent.ACTION_UP) {
                    // 点药丸上的某一档
                    float W = getWidth() / dens, H2 = getHeight() / dens;
                    int[] vs = visibleStops();
                    float w = 8;
                    for (int i = 0; i < vs.length; i++) w += btnSize[vs[i]] + (i > 0 ? 2 : 0);
                    float px = W / 2 - w / 2 + 4;
                    for (int i = 0; i < vs.length; i++) {
                        float s = btnSize[vs[i]];
                        if (x >= px - 1 && x <= px + s + 1) { zoomTo(STOPS[vs[i]]); break; }
                        px += s + 2;
                    }
                }
                startLoop();
                return true;
            }
        }
        return g_on;
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { sized = false; invalidate(); }
    @Override protected void onDetachedFromWindow() { removeCallbacks(hideRun); removeCallbacks(holdRun); looping = false; super.onDetachedFromWindow(); }
}

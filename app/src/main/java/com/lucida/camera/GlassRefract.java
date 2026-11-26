package com.lucida.camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.PixelCopy;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceView;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * 液态玻璃（自绘折射版）。
 * 做法：每约 100 毫秒用 PixelCopy 把取景画面（SurfaceView）缩成一张小图；每个「小玻璃控件」从里面裁出自己背后的那一块，
 * 画的时候用 drawBitmapMesh 把这一块按「玻璃透镜」的规律扭一下——中间略放大，越靠边缘越被挤压，
 * 就像透过一块厚玻璃看背后的画面；再叠一层很淡的白 + 左上亮右下次亮的高光描边。
 * 取景还没出画 / 控件太大 / 拷贝失败时，draw 返回 false，调用方自己画普通毛玻璃，不会出错。
 */
final class GlassRefract {
    static final GlassRefract I = new GlassRefract();
    private GlassRefract() {}

    private static final int BMP_W = 360;
    private static final long PERIOD_MS = 100;

    private SurfaceView sv;
    private Bitmap bmp;
    private boolean copying, ticking;
    private long lastDraw;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final WeakHashMap<View, Slot> slots = new WeakHashMap<View, Slot>();
    private final int[] loc = new int[2], svLoc = new int[2];
    private final Paint meshP = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG), rimP = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rf = new RectF();

    private static final class Slot { Bitmap crop; int w, h; float[] verts; int mw, mh; Bitmap pad; float ml, mt, innerW, innerH; }

    void attach(SurfaceView s) { sv = s; bmp = null; }
    void detach() { sv = null; bmp = null; slots.clear(); ticking = false; ui.removeCallbacksAndMessages(null); }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            ticking = false;
            if (sv == null || System.currentTimeMillis() - lastDraw > 1500) return;     // 没人在画折射了就停
            copyOnce();
            ticking = true; ui.postDelayed(this, PERIOD_MS);
        }
    };

    private void copyOnce() {
        final SurfaceView s = sv;
        if (s == null || copying || !s.getHolder().getSurface().isValid() || s.getWidth() <= 0 || s.getHeight() <= 0) return;
        int w = BMP_W, h = Math.max(1, Math.round(BMP_W * (float) s.getHeight() / s.getWidth()));
        if (bmp == null || bmp.getWidth() != w || bmp.getHeight() != h) bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        copying = true;
        try {
            PixelCopy.request(s, bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                @Override public void onPixelCopyFinished(int result) {
                    copying = false;
                    if (result != PixelCopy.SUCCESS) { return; }
                    recropAll();
                }
            }, ui);
        } catch (Throwable t) { copying = false; }
    }

    /** 新的取景小图到了：每个控件重新裁自己的那一块 */
    private void recropAll() {
        if (sv == null || bmp == null) return;
        sv.getLocationOnScreen(svLoc);
        float kx = bmp.getWidth() / (float) Math.max(1, sv.getWidth()), ky = bmp.getHeight() / (float) Math.max(1, sv.getHeight());
        ArrayList<View> vs = new ArrayList<View>(slots.keySet());
        for (View v : vs) {
            Slot sl = slots.get(v);
            if (sl == null || !v.isShown() || v.getWidth() <= 0) continue;
            v.getLocationOnScreen(loc);
            int x0 = Math.round((loc[0] - svLoc[0]) * kx), y0 = Math.round((loc[1] - svLoc[1]) * ky);
            int w = Math.max(2, Math.round(v.getWidth() * kx)), h = Math.max(2, Math.round(v.getHeight() * ky));
            int cx = Math.max(0, Math.min(bmp.getWidth() - 2, x0)), cy = Math.max(0, Math.min(bmp.getHeight() - 2, y0));
            w = Math.min(w, bmp.getWidth() - cx); h = Math.min(h, bmp.getHeight() - cy);
            if (w < 2 || h < 2 || x0 + w < 0 || y0 + h < 0 || x0 > bmp.getWidth() || y0 > bmp.getHeight()) { sl.crop = null; continue; }
            try { sl.crop = Bitmap.createBitmap(bmp, cx, cy, w, h); } catch (Throwable t) { sl.crop = null; }
            // 带边距的小图（给新液态玻璃用）：边距 8dp，比最大折射位移大
            try {
                float dens = v.getResources().getDisplayMetrics().density;
                int mp = Math.round(8f * dens * kx);
                int l = Math.max(0, cx - mp), t = Math.max(0, cy - mp);
                int rr = Math.min(bmp.getWidth(), cx + w + mp), bb = Math.min(bmp.getHeight(), cy + h + mp);
                sl.pad = Bitmap.createBitmap(bmp, l, t, rr - l, bb - t);
                sl.ml = cx - l; sl.mt = cy - t; sl.innerW = w; sl.innerH = h;
            } catch (Throwable t) { sl.pad = null; }
            v.invalidate();
        }
    }

    /**
     * 在 host 的画布上画一块玻璃：矩形 (0,0)-(w,h)，圆角半径 r（胶囊 = 短边一半）。
     * 返回 false = 现在没法折射（调用方画毛玻璃）。
     */
    boolean draw(Canvas c, View host, float w, float h, float r) {
        if (sv == null || Math.min(w, h) > host.getResources().getDisplayMetrics().density * 90) return false;
        lastDraw = System.currentTimeMillis();
        if (!ticking) { ticking = true; ui.postDelayed(tick, 0); }
        Slot sl = slots.get(host);
        if (sl == null) { sl = new Slot(); slots.put(host, sl); return false; }
        Bitmap crop = sl.crop;
        if (crop == null) return false;
        float m = Math.min(w, h);
        int mw = Math.max(6, Math.min(36, Math.round(6 * w / m))), mh = Math.max(6, Math.min(36, Math.round(6 * h / m)));
        if (sl.verts == null || sl.mw != mw || sl.mh != mh || sl.w != Math.round(w) || sl.h != Math.round(h)) {
            sl.mw = mw; sl.mh = mh; sl.w = Math.round(w); sl.h = Math.round(h);
            sl.verts = lensMesh(w, h, mw, mh);
        }
        // 新的液态玻璃（AGSL）：只在选了「液态玻璃」且系统 13 及以上时用；否则走下面的固态玻璃
        if ("liquid2".equals(DualUi.glassStyle) && GlassSdf.supported() && sl.pad != null) {
            if (GlassSdf.draw(c, sl.pad, sl.ml, sl.mt, sl.innerW, sl.innerH, w, h, r, host.getResources().getDisplayMetrics().density)) return true;
        }
        rf.set(0, 0, w, h);
        clip.reset(); clip.addRoundRect(rf, r, r, Path.Direction.CW);
        c.save(); c.clipPath(clip);
        if (meshP.getColorFilter() == null) { ColorMatrix cm = new ColorMatrix(); cm.setSaturation(1.2f); meshP.setColorFilter(new ColorMatrixColorFilter(cm)); }
        // drawBitmapMesh 把整张小图均匀铺在网格上，网格顶点就是「透镜」后的位置
        c.drawBitmapMesh(crop, mw, mh, sl.verts, 0, null, 0, meshP);
        fillP.setStyle(Paint.Style.FILL); fillP.setColor(0x14FFFFFF); c.drawRoundRect(rf, r, r, fillP);
        c.restore();
        float sw = Math.max(1f, host.getResources().getDisplayMetrics().density * 1.1f);
        rimP.setStyle(Paint.Style.STROKE); rimP.setStrokeWidth(sw);
        rimP.setShader(new LinearGradient(0, 0, w, h, new int[]{0xDDFFFFFF, 0x1AFFFFFF, 0x00FFFFFF, 0x66FFFFFF}, new float[]{0f, 0.32f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        rf.inset(sw / 2, sw / 2); c.drawRoundRect(rf, Math.max(0, r - sw / 2), Math.max(0, r - sw / 2), rimP);
        rimP.setShader(null);
        return true;
    }

    /**
     * 网格顶点：图上均匀的点 t，落到玻璃里的位置 g(t) = 轴线上最近点 + d·ρ'/ρ，ρ' = 1-(1-ρ)^1.4。
     * 中心放大、越靠边缘画面被挤得越紧（折弯），玻璃外沿处和原图重合。胶囊 / 圆形通用（圆 = 轴线退化成一个点）。
     */
    private static float[] lensMesh(float w, float h, int mw, int mh) {
        float[] v = new float[(mw + 1) * (mh + 1) * 2];
        float R = Math.min(w, h) / 2f;
        boolean horiz = w >= h;
        float ax0 = horiz ? R : w / 2f, ay0 = horiz ? h / 2f : R;
        float ax1 = horiz ? w - R : w / 2f, ay1 = horiz ? h / 2f : h - R;
        int k = 0;
        for (int j = 0; j <= mh; j++) {
            for (int i = 0; i <= mw; i++) {
                float tx = w * i / mw, ty = h * j / mh;
                float nx = horiz ? Math.max(ax0, Math.min(ax1, tx)) : ax0;
                float ny = horiz ? ay0 : Math.max(ay0, Math.min(ay1, ty));
                float dx = tx - nx, dy = ty - ny;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float rho = d / R;
                float nr = rho >= 1f ? rho : (float) (1.0 - Math.pow(1.0 - rho, 1.4));
                float f = d < 0.0001f ? 1f : nr / rho;
                v[k++] = nx + dx * f; v[k++] = ny + dy * f;
            }
        }
        return v;
    }
}

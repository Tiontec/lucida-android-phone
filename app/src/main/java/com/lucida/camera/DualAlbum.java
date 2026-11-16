package com.lucida.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.List;

/** 双摄相册：这次拍到的照片 / 视频网格，点开全屏查看；「导入」= 关闭相机并把它们导入澄影相册。 */
final class DualAlbum extends FrameLayout {
    private final List<File> files;
    private final List<Boolean> videos;
    private final Runnable onClose, onImport;

    DualAlbum(final Context c, List<File> files, List<Boolean> videos, Runnable onClose, Runnable onImport) {
        super(c);
        this.files = files; this.videos = videos; this.onClose = onClose; this.onImport = onImport;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 52)));
        DualIcon back = new DualIcon(c, DualIcon.BACK); back.round = false;
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualAlbum.this.onClose.run(); } });
        bar.addView(back, new FrameLayout.LayoutParams(DualUi.dp(c, 48), DualUi.dp(c, 48), Gravity.CENTER_VERTICAL | Gravity.LEFT));
        TextView t = DualUi.label(c, "相册 " + files.size() + " 项", 17, Color.WHITE);
        bar.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView imp = DualUi.label(c, "导入", 16, DualUi.YELLOW);
        imp.setPadding(DualUi.dp(c, 16), DualUi.dp(c, 8), DualUi.dp(c, 16), DualUi.dp(c, 8));
        imp.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualAlbum.this.onImport.run(); } });
        bar.addView(imp, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        if (files.isEmpty()) {
            TextView e = DualUi.label(c, "还没有拍摄内容", 15, 0x80FFFFFF); e.setGravity(Gravity.CENTER);
            col.addView(e, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 300)));
            return;
        }
        // 最新的排在左上角；默认 2 列（一屏约 6 张大图），双指捏合 / 张开改列数（和苹果相册一样）
        for (int i = files.size() - 1; i >= 0; i--) shown.add(i);
        sv = new ScrollView(c) {
            @Override public boolean dispatchTouchEvent(MotionEvent ev) { pinch.onTouchEvent(ev); return super.dispatchTouchEvent(ev); }
        };
        sv.setVerticalScrollBarEnabled(false);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        grid = new GridLayout(c);
        sv.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            float acc = 1f;
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { acc = 1f; return true; }
            @Override public boolean onScale(ScaleGestureDetector d) {
                acc *= d.getScaleFactor();
                int n = cols;
                if (acc > 1.25f) n = Math.max(1, cols - 1); else if (acc < 0.8f) n = Math.min(6, cols + 1); else return true;
                acc = 1f;
                if (n != cols) { cols = n; layoutGrid(); }
                return true;
            }
        });
        post(new Runnable() { @Override public void run() { layoutGrid(); } });
    }

    private final java.util.ArrayList<Integer> shown = new java.util.ArrayList<Integer>();
    private int cols = 2;
    private GridLayout grid;
    private ScrollView sv;
    private ScaleGestureDetector pinch;
    private final android.util.LruCache<Integer, Bitmap> cache = new android.util.LruCache<Integer, Bitmap>(40);

    private void layoutGrid() {
        final Context c = getContext();
        grid.removeAllViews();
        grid.setColumnCount(cols);
        int gap = DualUi.dp(c, 2);
        int W = Math.max(1, getWidth() > 0 ? getWidth() : c.getResources().getDisplayMetrics().widthPixels);
        int cell = (W - gap * (cols + 1)) / cols;
        for (int k = 0; k < shown.size(); k++) {
            final int idx = shown.get(k);
            final ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setBackgroundColor(0xFF262626);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = cell; lp.height = cell; lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            final FrameLayout box = new FrameLayout(c);
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if (videos.get(idx)) {
                TextView badge = DualUi.label(c, "▶", 16, Color.WHITE);
                box.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT));
                badge.setPadding(DualUi.dp(c, 8), 0, 0, DualUi.dp(c, 4));
            }
            grid.addView(box, lp);
            Bitmap hit = cache.get(idx);
            if (hit != null) iv.setImageBitmap(hit);
            else {
                final File f = files.get(idx); final boolean vid = videos.get(idx);
                new Thread(new Runnable() { @Override public void run() {
                    final Bitmap b = thumb(f, vid, 700);
                    if (b != null) cache.put(idx, b);
                    iv.post(new Runnable() { @Override public void run() { if (b != null) iv.setImageBitmap(b); } });
                } }).start();
            }
            box.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { open(shown.indexOf(idx)); } });
        }
    }

    static Bitmap thumb(File f, boolean video, int max) {
        try {
            if (video) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                r.setDataSource(f.getAbsolutePath());
                Bitmap b = r.getFrameAtTime(0);
                r.release();
                if (b == null) return null;
                float k = Math.min(1f, max / (float) Math.max(b.getWidth(), b.getHeight()));
                return k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
            }
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1; while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) { return null; }
    }

    /** 全屏查看（idx 是在「新 → 旧」顺序里的位置）：左右滑动翻页，双指缩放，双击放大 / 还原；视频点开即播 */
    private void open(final int pos) {
        final Context c = getContext();
        final FrameLayout v = new FrameLayout(c); v.setBackgroundColor(Color.BLACK); v.setClickable(true);
        final int[] cur = {pos};
        final FrameLayout holder = new FrameLayout(c);
        v.addView(holder, new FrameLayout.LayoutParams(-1, -1));
        final TextView count = DualUi.label(c, "", 14, 0xB3FFFFFF);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); cp.topMargin = DualUi.dp(c, 44);
        v.addView(count, cp);
        final Runnable[] show = new Runnable[1];
        show[0] = new Runnable() { @Override public void run() {
            holder.removeAllViews();
            int idx = shown.get(cur[0]);
            File f = files.get(idx);
            count.setText((cur[0] + 1) + " / " + shown.size());
            if (videos.get(idx)) {
                VideoView vv = new VideoView(c); vv.setVideoURI(Uri.fromFile(f));
                vv.setMediaController(new MediaController(c));
                holder.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); vv.start();
            } else {
                ZoomImage zi = new ZoomImage(c);
                Bitmap b = thumb(f, false, 2400); if (b != null) zi.setImageBitmap(b);
                zi.onSwipe = new ZoomImage.Swipe() { @Override public void on(int dir) {
                    int n = cur[0] + dir; if (n < 0 || n >= shown.size()) return; cur[0] = n; show[0].run(); } };
                holder.addView(zi, new FrameLayout.LayoutParams(-1, -1));
            }
        } };
        show[0].run();
        DualIcon back = new DualIcon(c, DualIcon.BACK);
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { removeView(v); } });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(DualUi.dp(c, 40), DualUi.dp(c, 40), Gravity.TOP | Gravity.LEFT);
        bp.topMargin = DualUi.dp(c, 36); bp.leftMargin = DualUi.dp(c, 14);
        v.addView(back, bp);
        addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    /** 可双指缩放 / 拖动 / 双击放大的图片；没放大时左右快速滑动 = 翻页 */
    static final class ZoomImage extends ImageView {
        interface Swipe { void on(int dir); }
        Swipe onSwipe;
        private final Matrix m = new Matrix();
        private float scale = 1f, base = 1f;
        private final ScaleGestureDetector sg;
        private final GestureDetector gd;
        ZoomImage(Context c) {
            super(c);
            setScaleType(ImageView.ScaleType.MATRIX);
            sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector d) { zoomBy(d.getScaleFactor(), d.getFocusX(), d.getFocusY()); return true; }
            });
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) { return true; }
                @Override public boolean onDoubleTap(MotionEvent e) {
                    if (scale > 1.05f) fit(); else zoomBy(2.5f, e.getX(), e.getY());
                    return true;
                }
                @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) {
                    if (scale > 1.05f) { m.postTranslate(-dx, -dy); clampPan(); setImageMatrix(m); }
                    return true;
                }
                @Override public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                    if (scale <= 1.05f && Math.abs(vx) > 900 && Math.abs(vx) > Math.abs(vy) * 1.5f && onSwipe != null) onSwipe.on(vx < 0 ? 1 : -1);
                    return true;
                }
            });
        }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); fit(); }
        @Override public void setImageBitmap(Bitmap b) { super.setImageBitmap(b); fit(); }
        private void fit() {
            if (getDrawable() == null || getWidth() == 0) return;
            float dw = getDrawable().getIntrinsicWidth(), dh = getDrawable().getIntrinsicHeight();
            base = Math.min(getWidth() / dw, getHeight() / dh);
            m.reset(); m.postScale(base, base); m.postTranslate((getWidth() - dw * base) / 2, (getHeight() - dh * base) / 2);
            scale = 1f; setImageMatrix(m);
        }
        private void zoomBy(float f, float px, float py) {
            float ns = Math.max(1f, Math.min(6f, scale * f)); f = ns / scale; scale = ns;
            m.postScale(f, f, px, py); clampPan(); setImageMatrix(m);
        }
        private void clampPan() {
            if (getDrawable() == null) return;
            float[] v = new float[9]; m.getValues(v);
            float w = getDrawable().getIntrinsicWidth() * v[Matrix.MSCALE_X], h = getDrawable().getIntrinsicHeight() * v[Matrix.MSCALE_Y];
            float x = v[Matrix.MTRANS_X], y = v[Matrix.MTRANS_Y], dx = 0, dy = 0;
            if (w <= getWidth()) dx = (getWidth() - w) / 2 - x; else if (x > 0) dx = -x; else if (x + w < getWidth()) dx = getWidth() - x - w;
            if (h <= getHeight()) dy = (getHeight() - h) / 2 - y; else if (y > 0) dy = -y; else if (y + h < getHeight()) dy = getHeight() - y - h;
            m.postTranslate(dx, dy);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            sg.onTouchEvent(e); if (!sg.isInProgress()) gd.onTouchEvent(e);
            getParent().requestDisallowInterceptTouchEvent(scale > 1.05f);
            return true;
        }
    }
}

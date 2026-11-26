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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 双摄相册：澄影双摄拍到的所有照片 / 动态照片 / 视频（取自系统相册，重开 App 还在，最新的排在左上角）。
 * 2 列大图，双指捏合 / 张开改列数；点开全屏查看（动态照片可播放、可删除）；「选择」可多选删除；
 * 删除会同时删掉系统相册里的那一份，在系统相册里删掉的也会自动从这里消失。「导入」= 关闭相机并把本次拍的导入澄影相册。
 */
final class DualAlbum extends FrameLayout {
    interface Host { void requestDelete(List<Uri> uris, Runnable done); }

    private final Host host;
    private final Runnable onClose, onImport;
    private ArrayList<DualLibrary.Entry> entries = new ArrayList<DualLibrary.Entry>();
    private int cols = 2;
    private GridLayout grid;
    private ScrollView sv;
    private ScaleGestureDetector pinch;
    private TextView title, selectBtn, delBtn, emptyView;
    private boolean selecting, viewing;
    private final HashSet<String> picked = new HashSet<String>();
    private final android.util.LruCache<String, Bitmap> cache = new android.util.LruCache<String, Bitmap>(60);
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    DualAlbum(final Context c, Host host, Runnable onClose, Runnable onImport) {
        super(c);
        // 没有读媒体权限：申请一次（授权后重新打开相册，就能看到系统相册里的全部照片）
        if (!DualLibrary.canReadSystem(c) && c instanceof android.app.Activity) {
            try { ((android.app.Activity) c).requestPermissions(DualLibrary.readPerms(), 4417); } catch (Throwable ignore) { }
        }
        this.host = host; this.onClose = onClose; this.onImport = onImport;
        setBackgroundColor(Color.BLACK);
        setClickable(true);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 52)));
        DualIcon back = new DualIcon(c, DualIcon.BACK); back.round = false;
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualAlbum.this.onClose.run(); } });
        bar.addView(back, new FrameLayout.LayoutParams(DualUi.dp(c, 48), DualUi.dp(c, 48), Gravity.CENTER_VERTICAL | Gravity.LEFT));
        title = DualUi.label(c, "相册", 17, Color.WHITE);
        bar.addView(title, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        LinearLayout right = new LinearLayout(c); right.setOrientation(LinearLayout.HORIZONTAL);
        selectBtn = DualUi.label(c, "选择", 16, Color.WHITE);
        selectBtn.setPadding(DualUi.dp(c, 12), DualUi.dp(c, 8), DualUi.dp(c, 12), DualUi.dp(c, 8));
        selectBtn.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { toggleSelect(); } });
        TextView imp = DualUi.label(c, "导入", 16, DualUi.YELLOW);
        imp.setPadding(DualUi.dp(c, 12), DualUi.dp(c, 8), DualUi.dp(c, 16), DualUi.dp(c, 8));
        imp.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualAlbum.this.onImport.run(); } });
        right.addView(selectBtn); right.addView(imp);
        bar.addView(right, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));

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
        emptyView = DualUi.label(c, "还没有拍摄内容", 15, 0x80FFFFFF); emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(GONE);
        addView(emptyView, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        delBtn = DualUi.label(c, "删除", 16, Color.WHITE); delBtn.setGravity(Gravity.CENTER);
        delBtn.setBackground(DualUi.round(0xF2EB3833, 22, c)); delBtn.setPadding(DualUi.dp(c, 28), DualUi.dp(c, 11), DualUi.dp(c, 28), DualUi.dp(c, 11));
        delBtn.setVisibility(GONE);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); dp.bottomMargin = DualUi.dp(c, 30);
        addView(delBtn, dp);
        delBtn.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { deletePicked(); } });
        reload();
    }

    /** 重新从系统相册取一遍（打开时、从别的 App 回来时、删除之后） */
    void reload() {
        final Context c = getContext();
        new Thread(new Runnable() { @Override public void run() {
            final ArrayList<DualLibrary.Entry> l = DualLibrary.load(c);
            ui.post(new Runnable() { @Override public void run() {
                entries = l;
                HashSet<String> alive = new HashSet<String>();
                for (DualLibrary.Entry e : l) alive.add(e.id);
                picked.retainAll(alive);
                title.setText("相册 " + l.size() + " 项");
                emptyView.setVisibility(l.isEmpty() ? VISIBLE : GONE);
                selectBtn.setVisibility(l.isEmpty() ? INVISIBLE : VISIBLE);
                if (l.isEmpty() && selecting) { selecting = false; selectBtn.setText("选择"); }
                syncDelete();
                layoutGrid();
            } });
        } }).start();
    }

    private void toggleSelect() {
        selecting = !selecting; picked.clear();
        selectBtn.setText(selecting ? "取消" : "选择");
        syncDelete(); layoutGrid();
    }

    private void syncDelete() {
        delBtn.setVisibility(selecting && !picked.isEmpty() ? VISIBLE : GONE);
        delBtn.setText("删除（" + picked.size() + "）");
    }

    private void deletePicked() {
        ArrayList<DualLibrary.Entry> list = new ArrayList<DualLibrary.Entry>();
        for (DualLibrary.Entry e : entries) if (picked.contains(e.id)) list.add(e);
        deleteEntries(list, new Runnable() { @Override public void run() {
            picked.clear(); selecting = false; selectBtn.setText("选择"); reload();
        } });
    }

    /** 删系统相册里的这些项；有的要系统弹确认框（不是本 App 创建的），交给 Activity 去办 */
    private void deleteEntries(final List<DualLibrary.Entry> list, final Runnable after) {
        final Context c = getContext();
        new Thread(new Runnable() { @Override public void run() {
            final ArrayList<Uri> need = DualLibrary.delete(c, list);
            ui.post(new Runnable() { @Override public void run() {
                if (need.isEmpty()) after.run();
                else host.requestDelete(need, after);
            } });
        } }).start();
    }

    private void layoutGrid() {
        final Context c = getContext();
        grid.removeAllViews();
        grid.setColumnCount(cols);
        int gap = DualUi.dp(c, 2);
        int W = Math.max(1, getWidth() > 0 ? getWidth() : c.getResources().getDisplayMetrics().widthPixels);
        int cell = (W - gap * (cols + 1)) / cols;
        for (int k = 0; k < entries.size(); k++) {
            final DualLibrary.Entry e = entries.get(k);
            final int pos = k;
            final ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setBackgroundColor(0xFF262626);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = cell; lp.height = cell; lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2);
            final FrameLayout box = new FrameLayout(c);
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            if (e.video) {
                TextView badge = DualUi.label(c, "▶", 16, Color.WHITE);
                box.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT));
                badge.setPadding(DualUi.dp(c, 8), 0, 0, DualUi.dp(c, 4));
            } else if (e.live) {
                TextView badge = DualUi.label(c, "◉", 15, Color.WHITE);
                box.addView(badge, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
                badge.setPadding(DualUi.dp(c, 8), DualUi.dp(c, 4), 0, 0);
            }
            if (selecting) {
                boolean on = picked.contains(e.id);
                if (on) { View dim = new View(c); dim.setBackgroundColor(0x59000000); box.addView(dim, new FrameLayout.LayoutParams(-1, -1)); }
                TextView ck = DualUi.label(c, on ? "✓" : "", 14, Color.BLACK); ck.setGravity(Gravity.CENTER);
                ck.setBackground(DualUi.round(on ? DualUi.YELLOW : 0x66000000, 11, c));
                FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(DualUi.dp(c, 22), DualUi.dp(c, 22), Gravity.TOP | Gravity.RIGHT);
                cp.topMargin = DualUi.dp(c, 6); cp.rightMargin = DualUi.dp(c, 6);
                box.addView(ck, cp);
            }
            grid.addView(box, lp);
            Bitmap hit = cache.get(e.id);
            if (hit != null) iv.setImageBitmap(hit);
            else {
                new Thread(new Runnable() { @Override public void run() {
                    final Bitmap b = thumb(c, e.uri, e.video, 700);
                    if (b != null) cache.put(e.id, b);
                    iv.post(new Runnable() { @Override public void run() { if (b != null) iv.setImageBitmap(b); } });
                } }).start();
            }
            box.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) {
                if (selecting) {
                    if (!picked.remove(e.id)) picked.add(e.id);
                    syncDelete(); layoutGrid();
                } else open(pos);
            } });
        }
    }

    static Bitmap thumb(File f, boolean video, int max) { return thumb(null, Uri.fromFile(f), video, max, f); }
    static Bitmap thumb(Context c, Uri u, boolean video, int max) { return thumb(c, u, video, max, null); }

    private static Bitmap thumb(Context c, Uri u, boolean video, int max, File f) {
        try {
            if (video) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                if (f != null) r.setDataSource(f.getAbsolutePath()); else r.setDataSource(c, u);
                Bitmap b = r.getFrameAtTime(0);
                r.release();
                if (b == null) return null;
                float k = Math.min(1f, max / (float) Math.max(b.getWidth(), b.getHeight()));
                return k < 1f ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true) : b;
            }
            BitmapFactory.Options o = new BitmapFactory.Options(); o.inJustDecodeBounds = true;
            InputStream in = f != null ? new java.io.FileInputStream(f) : c.getContentResolver().openInputStream(u);
            try { BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
            int s = 1; while (Math.max(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2;
            o = new BitmapFactory.Options(); o.inSampleSize = s;
            in = f != null ? new java.io.FileInputStream(f) : c.getContentResolver().openInputStream(u);
            try { return BitmapFactory.decodeStream(in, null, o); } finally { if (in != null) in.close(); }
        } catch (Throwable t) { return null; }
    }

    /** 全屏查看：左右滑动翻页，双指缩放，双击放大 / 还原；视频点开即播；动态照片翻到时自动播一次，点「◉ 实况」再播；右上角可删除 */
    private void open(final int pos) {
        final Context c = getContext();
        final FrameLayout v = new FrameLayout(c); v.setBackgroundColor(Color.BLACK); v.setClickable(true);
        final ArrayList<DualLibrary.Entry> list = new ArrayList<DualLibrary.Entry>(entries);
        final int[] cur = {pos};
        final int[] token = {0};
        final FrameLayout holder = new FrameLayout(c);
        v.addView(holder, new FrameLayout.LayoutParams(-1, -1));
        final TextView count = DualUi.label(c, "", 14, 0xB3FFFFFF);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL); cp.topMargin = DualUi.dp(c, 44);
        v.addView(count, cp);
        final Runnable[] show = new Runnable[1];
        show[0] = new Runnable() { @Override public void run() {
            holder.removeAllViews();
            final int my = ++token[0];
            final DualLibrary.Entry e = list.get(cur[0]);
            count.setText((cur[0] + 1) + " / " + list.size());
            if (e.video) {
                VideoView vv = new VideoView(c); vv.setVideoURI(e.uri);
                vv.setMediaController(new MediaController(c));
                holder.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); vv.start();
            } else {
                final ZoomImage zi = new ZoomImage(c);
                zi.onSwipe = new ZoomImage.Swipe() { @Override public void on(int dir) {
                    int n = cur[0] + dir; if (n < 0 || n >= list.size()) return; cur[0] = n; show[0].run(); } };
                holder.addView(zi, new FrameLayout.LayoutParams(-1, -1));
                Bitmap hit = cache.get("big" + e.id);
                if (hit != null) zi.setImageBitmap(hit);
                else new Thread(new Runnable() { @Override public void run() {
                    final Bitmap b = thumb(c, e.uri, false, 2400);
                    if (b != null) cache.put("big" + e.id, b);
                    zi.post(new Runnable() { @Override public void run() { if (b != null && my == token[0]) zi.setImageBitmap(b); } });
                } }).start();
                if (e.live) {
                    TextView tag = DualUi.label(c, "◉ 实况 · 点这里播放", 13, Color.WHITE); tag.setGravity(Gravity.CENTER);
                    tag.setBackground(DualUi.round(0x73000000, 12, c)); tag.setPadding(DualUi.dp(c, 12), DualUi.dp(c, 5), DualUi.dp(c, 12), DualUi.dp(c, 5));
                    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); tp.bottomMargin = DualUi.dp(c, 40);
                    holder.addView(tag, tp);
                    tag.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { playLive(e, holder, my, token); } });
                    playLive(e, holder, my, token);        // 翻到这一张就自动播一次
                }
            }
        } };
        show[0].run();
        DualIcon back = new DualIcon(c, DualIcon.BACK);
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { removeView(v); viewing = false; } });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(DualUi.dp(c, 40), DualUi.dp(c, 40), Gravity.TOP | Gravity.LEFT);
        bp.topMargin = DualUi.dp(c, 36); bp.leftMargin = DualUi.dp(c, 14);
        v.addView(back, bp);
        TextView trash = DualUi.label(c, "删除", 15, Color.WHITE); trash.setGravity(Gravity.CENTER);
        trash.setBackground(DualUi.round(0x66000000, 18, c)); trash.setPadding(DualUi.dp(c, 16), DualUi.dp(c, 8), DualUi.dp(c, 16), DualUi.dp(c, 8));
        FrameLayout.LayoutParams tp2 = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT); tp2.topMargin = DualUi.dp(c, 38); tp2.rightMargin = DualUi.dp(c, 14);
        v.addView(trash, tp2);
        trash.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) {
            final DualLibrary.Entry e = list.get(cur[0]);
            ArrayList<DualLibrary.Entry> one = new ArrayList<DualLibrary.Entry>(); one.add(e);
            deleteEntries(one, new Runnable() { @Override public void run() {
                reload();
                int i = list.indexOf(e);
                if (i >= 0) list.remove(i);
                if (list.isEmpty()) { removeView(v); viewing = false; return; }
                cur[0] = Math.min(cur[0], list.size() - 1); show[0].run();
            } });
        } });
        viewing = true;
        addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    /** 播放动态照片里内嵌的那段视频（盖在照片上，播完 / 点一下就收起） */
    private void playLive(final DualLibrary.Entry e, final FrameLayout holder, final int my, final int[] token) {
        final Context c = getContext();
        new Thread(new Runnable() { @Override public void run() {
            final File clip = DualLibrary.extractClip(c, e);
            if (clip == null) {
                AppLog.error("相册", "取不出动态照片里的视频：" + e.id + "（内嵌长度 " + e.videoLen + "）");
                ui.post(new Runnable() { @Override public void run() { android.widget.Toast.makeText(c, "这张动态照片里的视频读不出来", android.widget.Toast.LENGTH_SHORT).show(); } });
                return;
            }
            ui.post(new Runnable() { @Override public void run() {
                if (my != token[0]) return;                       // 已经翻页了
                // 用 TextureView + MediaPlayer：以前的 VideoView 是独立的绘制层，个别机型上嵌在这种有动画 / 圆角的页面里只出声音、画面全黑
                final LiveVideo vv = new LiveVideo(c);
                holder.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
                vv.onDone = new Runnable() { @Override public void run() { holder.removeView(vv); } };
                vv.onFail = new LiveVideo.Fail() { @Override public void on(int w, int x) {
                    AppLog.error("相册", "动态照片播放出错 what=" + w + " extra=" + x); holder.removeView(vv);
                    android.widget.Toast.makeText(c, "动态照片播放失败（" + w + "/" + x + "）", android.widget.Toast.LENGTH_SHORT).show(); } };
                vv.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { vv.release(); holder.removeView(vv); } });
                vv.play(clip.getAbsolutePath());
            } });
        } }).start();
    }

    /** 播放动态照片里那段短视频：TextureView + MediaPlayer，等比放进容器里 */
    static final class LiveVideo extends android.view.TextureView implements android.view.TextureView.SurfaceTextureListener {
        interface Fail { void on(int what, int extra); }
        Runnable onDone; Fail onFail;
        private android.media.MediaPlayer mp;
        private String path;
        private int vw, vh;
        LiveVideo(Context c) { super(c); setSurfaceTextureListener(this); setOpaque(true); }
        void play(String p) { path = p; if (isAvailable()) start(getSurfaceTexture()); }
        void release() {
            if (mp != null) { try { mp.stop(); } catch (Throwable ignore) {} try { mp.release(); } catch (Throwable ignore) {} mp = null; }
        }
        private void start(android.graphics.SurfaceTexture st) {
            if (path == null || mp != null) return;
            try {
                mp = new android.media.MediaPlayer();
                mp.setDataSource(path);
                mp.setSurface(new android.view.Surface(st));
                mp.setOnVideoSizeChangedListener(new android.media.MediaPlayer.OnVideoSizeChangedListener() {
                    @Override public void onVideoSizeChanged(android.media.MediaPlayer m, int w, int h) { vw = w; vh = h; fit(); } });
                mp.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(android.media.MediaPlayer m) { vw = m.getVideoWidth(); vh = m.getVideoHeight(); fit(); m.start(); } });
                mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(android.media.MediaPlayer m) { release(); if (onDone != null) onDone.run(); } });
                mp.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(android.media.MediaPlayer m, int w, int x) { release(); if (onFail != null) onFail.on(w, x); return true; } });
                mp.prepareAsync();
            } catch (Throwable e) { release(); if (onFail != null) onFail.on(-1, -1); }
        }
        /** 视频等比放大到刚好放进容器（和上面那张照片一样的比例，重合） */
        private void fit() {
            int W = getWidth(), H = getHeight();
            if (W <= 0 || H <= 0 || vw <= 0 || vh <= 0) return;
            float s = Math.min((float) W / vw, (float) H / vh);
            float w = vw * s, h = vh * s;
            Matrix m = new Matrix();
            m.setScale(w / W, h / H, W / 2f, H / 2f);
            setTransform(m);
        }
        @Override public void onSurfaceTextureAvailable(android.graphics.SurfaceTexture st, int w, int h) { start(st); }
        @Override public void onSurfaceTextureSizeChanged(android.graphics.SurfaceTexture st, int w, int h) { fit(); }
        @Override public boolean onSurfaceTextureDestroyed(android.graphics.SurfaceTexture st) { release(); return true; }
        @Override public void onSurfaceTextureUpdated(android.graphics.SurfaceTexture st) {}
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

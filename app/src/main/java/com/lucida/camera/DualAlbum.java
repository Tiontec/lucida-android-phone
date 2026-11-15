package com.lucida.camera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.view.Gravity;
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
        ScrollView sv = new ScrollView(c);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (files.isEmpty()) {
            TextView e = DualUi.label(c, "还没有拍摄内容", 15, 0x80FFFFFF); e.setGravity(Gravity.CENTER);
            sv.addView(e, new FrameLayout.LayoutParams(-1, DualUi.dp(c, 300)));
            return;
        }
        GridLayout grid = new GridLayout(c); grid.setColumnCount(4);
        sv.addView(grid, new FrameLayout.LayoutParams(-1, -2));
        int cell = (c.getResources().getDisplayMetrics().widthPixels - DualUi.dp(c, 6)) / 4;
        for (int i = files.size() - 1; i >= 0; i--) {
            final int idx = i;
            final ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.CENTER_CROP); iv.setBackgroundColor(0xFF262626);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams(); lp.width = cell; lp.height = cell; lp.setMargins(1, 1, 1, 1);
            grid.addView(iv, lp);
            final File f = files.get(i); final boolean vid = videos.get(i);
            new Thread(new Runnable() { @Override public void run() {
                final Bitmap b = thumb(f, vid, 360);
                iv.post(new Runnable() { @Override public void run() { if (b != null) iv.setImageBitmap(b); } });
            } }).start();
            iv.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { open(idx); } });
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

    private void open(int idx) {
        final Context c = getContext();
        final FrameLayout v = new FrameLayout(c); v.setBackgroundColor(Color.BLACK); v.setClickable(true);
        File f = files.get(idx);
        if (videos.get(idx)) {
            VideoView vv = new VideoView(c); vv.setVideoURI(Uri.fromFile(f));
            vv.setMediaController(new MediaController(c));
            v.addView(vv, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)); vv.start();
        } else {
            ImageView iv = new ImageView(c); iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap b = thumb(f, false, 2400); if (b != null) iv.setImageBitmap(b);
            v.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        }
        DualIcon back = new DualIcon(c, DualIcon.BACK);
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { removeView(v); } });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(DualUi.dp(c, 40), DualUi.dp(c, 40), Gravity.TOP | Gravity.LEFT);
        bp.topMargin = DualUi.dp(c, 36); bp.leftMargin = DualUi.dp(c, 14);
        v.addView(back, bp);
        addView(v, new FrameLayout.LayoutParams(-1, -1));
    }
}

package com.lucida.camera;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * 原生双摄（11.1.8 重写，纯原生界面，没有网页）：
 *  Camera2 同时打开前后摄像头 → GlComposer（OpenGL）合成画中画 / 上下分屏 + 调色 → 预览、拍照、录像（H.264 + AAC）。
 *  规格：分辨率 720p / 1080p / 4K × 帧率 24 / 30 / 60（每颗摄像头各自挑能达到的最大画面，达不到自动降档并提示）；
 *  片门全开 = 前后摄都用 4:3；变焦用主页面同款弧形转盘（ZoomBar），超广角 / 广角 / 主摄由缩放比切换。
 */
public class DualCameraActivity extends Activity implements SurfaceHolder.Callback {
    static File outDir(Context c) { File d = new File(c.getCacheDir(), "dual"); d.mkdirs(); return d; }
    private static final int REQ_PERM = 21;

    private final DualState state = new DualState();
    private DualPrefs prefs;
    private final GradeState grade = new GradeState();
    private GlComposer gl;
    private HandlerThread glThread, camThread;
    private Handler glH, camH, ui;
    private SurfaceView sv;
    private CameraManager cm;
    private final CameraDevice[] dev = new CameraDevice[2];
    private final CameraCaptureSession[] ses = new CameraCaptureSession[2];
    private String backId, frontId;
    private volatile boolean glReady, camsOpened, finished, recording, videoMode, torchOn, failedReported, clipping;
    private int gen = 0, openedCount = 0, degradeCount = 0;
    private DualRecorder recorder;
    private long recStartNs;
    private int recW = 720, recH = 1280;
    private File recFile;
    private final JSONArray items = new JSONArray();
    private final ArrayList<File> itemFiles = new ArrayList<File>();
    private final ArrayList<Boolean> itemVideo = new ArrayList<Boolean>();
    private int pending;
    private String failCode, failMsg;
    // 每个摄像头各记各的倍数（0 = 后置，1 = 前置）；转盘控制当前「主画面」的那个
    private final float[] zoomMin = {1f, 1f}, zoomMax = {1f, 1f}, zoomCur = {1f, 1f};
    private final Rect[] activeArr = new Rect[2];
    private final CaptureRequest.Builder[] reqB = new CaptureRequest.Builder[2];
    private final int[] camFps = {30, 30};
    private final int[] streamW = {1280, 1280}, streamH = {720, 720};
    private boolean lastBackMain = true;
    // 当前有效规格（可能比设置里选的低）
    private int effTier, effFps;
    private String infoText = "";

    // 界面
    private FrameLayout root, bottomBar, stageTouch;
    private ZoomBar zoomBar;
    private DualIcon btnBack, btnLive, btnPalette, btnSettings, btnLayout, btnShape, btnMirror, btnTorch, btnSwap;
    private TextView specPill, tabPhoto, tabVideo, toast, timer, readout, presetLabel;
    private LinearLayout specPanel, segRes, segFps, segFmt;
    private View shutterInner, shutterRing, gridView, spinnerView;
    private FrameLayout thumbBox;
    private ImageView thumbImg;
    private TextView thumbCount;
    private DualPalette palette;
    private View overlay;
    private boolean paletteOpen;
    private int stageX, stageY, stageW = 1, stageH = 1, scrW, scrH, insetTop, insetBottom;
    private int lastLayoutKey;

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        MainActivity.useWholeScreen(this);
        ui = new Handler(Looper.getMainLooper());
        prefs = new DualPrefs(this);
        grade.load(prefs.sp);
        state.style = prefs.style; state.mirrorFront = prefs.mirror;
        videoMode = "video".equals(getIntent().getStringExtra("mode"));
        effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res);
        buildUi();
        immersive();
        cm = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (!pickCameras()) { fail("unsupported", "没有找到前置和后置摄像头"); return; }
        glThread = new HandlerThread("dual-gl"); glThread.start(); glH = new Handler(glThread.getLooper());
        camThread = new HandlerThread("dual-cam"); camThread.start(); camH = new Handler(camThread.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_PERM);
        }
        syncDial();
    }

    @Override
    public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        if (c == REQ_PERM) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) fail("denied", "没有相机权限");
            else tryOpen();
        }
    }

    @SuppressWarnings("deprecation")
    private void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override protected void onResume() { super.onResume(); immersive(); if (glReady) tryOpen(); }

    @Override
    protected void onPause() {
        if (recording) stopRecording();
        closeCameras();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        closeCameras();
        if (glH != null) {
            final GlComposer g = gl;
            glH.post(new Runnable() { @Override public void run() { if (g != null) g.release(); } });
            glThread.quitSafely();
        }
        if (camThread != null) camThread.quitSafely();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI 搭建

    private int dp(float v) { return DualUi.dp(this, v); }

    private DualIcon icon(int kind, View.OnClickListener l) {
        DualIcon i = new DualIcon(this, kind); i.setOnClickListener(l); return i;
    }

    private void place(View v, int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h, Gravity.TOP | Gravity.LEFT);
        lp.leftMargin = x; lp.topMargin = y;
        v.setLayoutParams(lp);
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));

        gridView = new View(this) {
            final Paint p = new Paint();
            @Override protected void onDraw(Canvas c) {
                p.setColor(0x66FFFFFF); p.setStrokeWidth(Math.max(1, getResources().getDisplayMetrics().density * .8f));
                float W = getWidth(), H = getHeight();
                for (int i = 1; i < 3; i++) { c.drawLine(W * i / 3, 0, W * i / 3, H, p); c.drawLine(0, H * i / 3, W, H * i / 3, p); }
            }
        };
        gridView.setVisibility(prefs.gridLines ? View.VISIBLE : View.GONE);
        root.addView(gridView);

        stageTouch = new FrameLayout(this);
        root.addView(stageTouch);
        stageTouch.setOnTouchListener(new View.OnTouchListener() {
            float lx, ly, dx, dy, sx, sy; boolean drag, inPip;
            @Override public boolean onTouch(View v, MotionEvent e) {
                float W = v.getWidth(), H = v.getHeight();
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN: {
                        specPanel.setVisibility(View.GONE);
                        lx = sx = e.getX(); ly = sy = e.getY(); dx = dy = 0; drag = false;
                        float[] r = state.rect(1, W, H);
                        inPip = !paletteOpen && state.style == DualState.STYLE_PIP && e.getX() >= r[0] * W && e.getX() <= (r[0] + r[2]) * W && e.getY() >= r[1] * H && e.getY() <= (r[1] + r[3]) * H;
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE:
                        if (inPip) {
                            dx += Math.abs(e.getX() - lx); dy += Math.abs(e.getY() - ly);
                            if (dx + dy > dp(8)) drag = true;
                            if (drag) {
                                float w = Math.round(W * state.pipW), h = Math.round(w * 4f / 3f), m = Math.round(W * 0.04f);
                                state.pipX = Math.min(1f, Math.max(0f, state.pipX + (e.getX() - lx) / Math.max(1f, W - w - 2 * m)));
                                state.pipY = Math.min(1f, Math.max(0f, state.pipY + (e.getY() - ly) / Math.max(1f, H - h - 2 * m)));
                            }
                            lx = e.getX(); ly = e.getY();
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (inPip && !drag) { state.backIsMain = !state.backIsMain; syncDial(); }
                        else if (paletteOpen && Math.abs(e.getX() - sx) > dp(60) && Math.abs(e.getX() - sx) > Math.abs(e.getY() - sy) * 1.3f) {
                            grade.pick(grade.preset + (e.getX() < sx ? 1 : -1)); pushGrade(); palette.sync();
                        }
                        return true;
                }
                return true;
            }
        });

        zoomBar = new ZoomBar(this);
        zoomBar.setListener(new ZoomBar.Listener() { @Override public void onZoom(float z) {
            int c = mainCam(); zoomCur[c] = z; applyZoom(c);
        } });
        root.addView(zoomBar);

        // 顶部：返回 + 规格胶囊 | 实况 · 调色盘 · 设置
        btnBack = icon(DualIcon.BACK, new View.OnClickListener() { @Override public void onClick(View v) { closeTapped(); } });
        btnLive = icon(DualIcon.LIVE, new View.OnClickListener() { @Override public void onClick(View v) { toggleLive(); } });
        btnPalette = icon(DualIcon.PALETTE, new View.OnClickListener() { @Override public void onClick(View v) { setPalette(!paletteOpen); } });
        btnSettings = icon(DualIcon.GEAR, new View.OnClickListener() { @Override public void onClick(View v) { openSettings(); } });
        specPill = DualUi.label(this, "", 13, Color.WHITE);
        specPill.setGravity(Gravity.CENTER); specPill.setBackground(DualUi.round(0x29FFFFFF, 18, this));
        specPill.setPadding(dp(14), 0, dp(14), 0);
        specPill.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            specPanel.setVisibility(specPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE); } });
        root.addView(btnBack); root.addView(specPill); root.addView(btnLive); root.addView(btnPalette); root.addView(btnSettings);
        buildSpecPanel();

        readout = DualUi.label(this, "", 13, Color.WHITE); readout.setGravity(Gravity.CENTER);
        readout.setBackground(DualUi.round(0x73000000, 13, this)); readout.setVisibility(View.GONE);
        presetLabel = DualUi.label(this, "", 13, Color.WHITE); presetLabel.setGravity(Gravity.CENTER);
        presetLabel.setBackground(DualUi.round(0x73000000, 13, this)); presetLabel.setVisibility(View.GONE);
        timer = DualUi.label(this, "", 14, Color.WHITE); timer.setGravity(Gravity.CENTER);
        timer.setBackground(DualUi.round(0xD9E61A1A, 12, this)); timer.setVisibility(View.GONE);
        root.addView(readout); root.addView(presetLabel); root.addView(timer);

        // 底部：纯黑平面
        bottomBar = new FrameLayout(this);
        bottomBar.setBackgroundColor(Color.BLACK);
        root.addView(bottomBar);
        btnLayout = icon(DualIcon.PIP, new View.OnClickListener() { @Override public void onClick(View v) {
            if (recording) { say("录像中不能切换布局"); return; }
            state.style = state.style == DualState.STYLE_PIP ? DualState.STYLE_SPLIT : DualState.STYLE_PIP;
            prefs.style = state.style; prefs.save(); syncButtons(); } });
        btnShape = icon(DualIcon.SHAPE, new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.pipShape = (prefs.pipShape + 1) % 3; prefs.save(); applyShape(); say(new String[]{"小窗：圆角", "小窗：大圆角", "小窗：直角"}[prefs.pipShape]); } });
        btnMirror = icon(DualIcon.MIRROR, new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.mirror = !prefs.mirror; state.mirrorFront = prefs.mirror; prefs.save(); syncButtons(); } });
        btnTorch = icon(DualIcon.TORCH, new View.OnClickListener() { @Override public void onClick(View v) { torch(); } });
        btnSwap = icon(DualIcon.SWAP, new View.OnClickListener() { @Override public void onClick(View v) { state.backIsMain = !state.backIsMain; syncDial(); } });
        tabPhoto = tab("照片", false); tabVideo = tab("视频", true);
        shutterRing = new View(this);
        android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
        rg.setShape(android.graphics.drawable.GradientDrawable.OVAL); rg.setColor(Color.TRANSPARENT); rg.setStroke(dp(4), Color.WHITE);
        shutterRing.setBackground(rg);
        shutterInner = new View(this);
        shutterInner.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { shutterTapped(); } });
        thumbBox = new FrameLayout(this);
        thumbBox.setBackground(DualUi.round(0xFF2A2A2A, 10, this));
        thumbBox.setClipToOutline(true);
        thumbImg = new ImageView(this); thumbImg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumbBox.addView(thumbImg, new FrameLayout.LayoutParams(-1, -1));
        DualIcon ph = new DualIcon(this, DualIcon.PHOTO); ph.round = false; ph.setClickable(false);
        thumbBox.addView(ph, new FrameLayout.LayoutParams(-1, -1));
        thumbCount = DualUi.label(this, "", 12, Color.WHITE); thumbCount.setGravity(Gravity.CENTER);
        thumbCount.setBackground(DualUi.round(0xB3000000, 9, this));
        thumbBox.addView(thumbCount, new FrameLayout.LayoutParams(dp(22), dp(18), Gravity.BOTTOM | Gravity.RIGHT));
        thumbCount.setVisibility(View.GONE);
        thumbBox.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openAlbum(); } });
        for (View v : new View[]{btnLayout, btnShape, btnMirror, btnTorch, btnSwap, tabPhoto, tabVideo, shutterRing, shutterInner, thumbBox}) bottomBar.addView(v);

        palette = new DualPalette(this, grade, new DualPalette.Host() {
            @Override public void onClose() { setPalette(false); }
            @Override public void onShutter() { shutterTapped(); }
            @Override public void onChange() { pushGrade(); }
        });
        palette.setVisibility(View.GONE);
        root.addView(palette);

        toast = DualUi.label(this, "", 14, Color.WHITE); toast.setGravity(Gravity.CENTER);
        toast.setPadding(dp(14), dp(7), dp(14), dp(7)); toast.setBackground(DualUi.round(0xB3000000, 14, this)); toast.setAlpha(0f);
        toast.setMaxWidth(dp(320));
        root.addView(toast);
        spinnerView = new android.widget.ProgressBar(this);
        root.addView(spinnerView, new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER));

        setContentView(root);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @SuppressWarnings("deprecation")
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                insetTop = in.getSystemWindowInsetTop(); insetBottom = in.getSystemWindowInsetBottom(); relayout(); return in;
            }
        });
        root.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (r - l != scrW || b - t != scrH) relayout();
            }
        });
        applyShape(); pushGrade(); syncButtons(); paint(); syncSpecPill();
    }

    private TextView tab(String t, final boolean video) {
        TextView v = DualUi.label(this, t, 15, Color.WHITE); v.setGravity(Gravity.CENTER); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
            if (recording || clipping) return; videoMode = video; paint(); syncButtons(); } });
        return v;
    }

    private void buildSpecPanel() {
        specPanel = new LinearLayout(this);
        specPanel.setOrientation(LinearLayout.VERTICAL);
        specPanel.setBackground(DualUi.round(0xF0161616, 18, this));
        specPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        specPanel.setVisibility(View.GONE);
        final int[] resV = {720, 1080, 2160}, fpsV = {24, 30, 60};
        int ri = 1, fi = 1;
        for (int i = 0; i < 3; i++) { if (resV[i] == prefs.res) ri = i; if (fpsV[i] == prefs.fps) fi = i; }
        segRes = DualUi.seg(this, new String[]{"720p", "1080p", "4K"}, ri, new DualUi.IntCb() { @Override public void on(int i) {
            prefs.res = resV[i]; prefs.save(); restartWithPrefs(); } });
        segFps = DualUi.seg(this, new String[]{"24", "30", "60"}, fi, new DualUi.IntCb() { @Override public void on(int i) {
            prefs.fps = fpsV[i]; prefs.save(); restartWithPrefs(); } });
        boolean heifOk = Build.VERSION.SDK_INT >= 34;
        segFmt = DualUi.seg(this, new String[]{"JPEG", "HEIF"}, prefs.heif && heifOk ? 1 : 0, new DualUi.IntCb() { @Override public void on(int i) {
            if (i == 1 && Build.VERSION.SDK_INT < 34) { DualUi.setSeg(segFmt, 0); say("HEIF 需要 Android 14 及以上"); return; }
            prefs.heif = i == 1; prefs.save(); syncSpecPill(); } });
        String[] names = {"分辨率", "帧率", "照片格式"};
        LinearLayout[] segs = {segRes, segFps, segFmt};
        for (int i = 0; i < 3; i++) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
            TextView l = DualUi.label(this, names[i], 13, 0xB3FFFFFF);
            row.addView(l, new LinearLayout.LayoutParams(dp(64), -2));
            row.addView(segs[i], new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); if (i > 0) lp.topMargin = dp(10);
            specPanel.addView(row, lp);
        }
        TextView n = DualUi.label(this, "双摄同时工作时系统不提供 RAW；实际规格以相机报告为准", 11, 0x73FFFFFF);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(10);
        specPanel.addView(n, np);
        root.addView(specPanel);
    }

    /** 屏幕尺寸 / 安全区变了就重新摆放所有东西 */
    private void relayout() {
        int W = root.getWidth(), H = root.getHeight();
        if (W <= 0 || H <= 0) return;
        scrW = W; scrH = H;
        int top = Math.max(insetTop, dp(24)), bot = Math.max(insetBottom, dp(12));
        int barH = dp(178) + bot;
        int stageTop = top + dp(54);
        int avail = H - stageTop - barH;
        int sw = W, sh;
        if (prefs.fullGate) { sh = Math.min(W * 4 / 3, avail); sw = sh * 3 / 4; }
        else sh = Math.min(W * 16 / 9, avail);
        stageW = sw; stageH = sh; stageX = (W - sw) / 2; stageY = stageTop;
        if (gl != null) { gl.vfX = stageX; gl.vfY = stageY; gl.vfW = stageW; gl.vfH = stageH; }
        place(stageTouch, stageX, stageY, stageW, stageH);
        place(gridView, stageX, stageY, stageW, stageH);
        place(zoomBar, stageX, stageY + stageH - dp(160), stageW, dp(160));
        // 顶部
        int y0 = top + dp(4), s = dp(40);
        place(btnBack, dp(14), y0, s, s);
        place(specPill, dp(14) + s + dp(8), y0 + dp(2), ViewGroup_WRAP, dp(36));
        place(btnSettings, W - dp(14) - s, y0, s, s);
        place(btnPalette, W - dp(14) - 2 * s - dp(8), y0, s, s);
        place(btnLive, W - dp(14) - 3 * s - dp(16), y0, s, s);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(300), -2, Gravity.TOP | Gravity.LEFT);
        sp.leftMargin = dp(14); sp.topMargin = y0 + s + dp(6); specPanel.setLayoutParams(sp);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(210), dp(26), Gravity.TOP | Gravity.LEFT);
        rp.leftMargin = stageX + (stageW - dp(210)) / 2; rp.topMargin = stageY + dp(10); readout.setLayoutParams(rp);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(150), dp(26), Gravity.TOP | Gravity.LEFT);
        pp.leftMargin = stageX + (stageW - dp(150)) / 2; pp.topMargin = stageY + stageH - dp(200); presetLabel.setLayoutParams(pp);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(92), dp(26), Gravity.TOP | Gravity.LEFT);
        tp.leftMargin = stageX + (stageW - dp(92)) / 2; tp.topMargin = stageY + dp(10); timer.setLayoutParams(tp);
        // 底栏
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(W, barH, Gravity.BOTTOM); bottomBar.setLayoutParams(bp);
        int r1 = dp(8), b34 = dp(34);
        place(btnLayout, dp(16), r1, b34, b34);
        place(btnShape, dp(58), r1, b34, b34);
        place(btnMirror, W - dp(92), r1, b34, b34);
        place(btnTorch, W - dp(50), r1, b34, b34);
        place(tabVideo, W / 2 - dp(64), r1, dp(64), b34);
        place(tabPhoto, W / 2, r1, dp(64), b34);
        int cy = dp(8 + 34 + 16 + 38);
        place(shutterRing, W / 2 - dp(38), cy - dp(38), dp(76), dp(76));
        place(shutterInner, W / 2 - dp(30), cy - dp(30), dp(60), dp(60));
        place(thumbBox, dp(26), cy - dp(27), dp(54), dp(54));
        place(btnSwap, W - dp(26) - dp(46), cy - dp(23), dp(46), dp(46));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(W, dp(290) + bot, Gravity.BOTTOM); palette.setLayoutParams(pl);
        applyOutputSize();
    }
    private static final int ViewGroup_WRAP = -2;

    // ---------------------------------------------------------------- 状态同步

    private void paint() {
        tabPhoto.setTextColor(!videoMode ? DualUi.YELLOW : 0xBFFFFFFF);
        tabVideo.setTextColor(videoMode ? DualUi.YELLOW : 0xBFFFFFFF);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(videoMode ? 0xFFEB3333 : Color.WHITE);
        shutterInner.setBackground(g);
        shutterInner.animate().scaleX(recording ? .55f : 1f).scaleY(recording ? .55f : 1f).setDuration(150).start();
    }

    private void syncButtons() {
        btnLive.set(prefs.live); btnLive.setEnabled(!videoMode); btnLive.setAlpha(videoMode ? .4f : 1f);
        btnPalette.set(grade.active() || paletteOpen);
        btnTorch.set(torchOn); btnMirror.set(prefs.mirror);
        btnLayout.setKind(state.style == DualState.STYLE_PIP ? DualIcon.PIP : DualIcon.SPLIT);
        boolean pip = state.style == DualState.STYLE_PIP;
        btnShape.setEnabled(pip); btnShape.setAlpha(pip ? 1f : .4f); btnShape.shapeStyle = prefs.pipShape; btnShape.invalidate();
    }

    private void syncSpecPill() {
        String r = prefs.res == 2160 ? "4K" : prefs.res + "p";
        specPill.setText(r + " · " + prefs.fps + " · " + (prefs.heif && Build.VERSION.SDK_INT >= 34 ? "HEIF" : "JPEG") + (prefs.fullGate ? " · 片门" : ""));
        specPill.requestLayout();
    }

    private void applyShape() { if (gl != null) gl.pipCorner = new float[]{.085f, .2f, 0f}[prefs.pipShape]; btnShape.shapeStyle = prefs.pipShape; btnShape.invalidate(); }

    private void pushGrade() {
        if (gl != null) { gl.grade = grade.params(); gl.gradeOn = grade.active(); }
        grade.save(prefs.sp);
        if (readout != null) {
            readout.setText("色调 " + Math.round(grade.y * 100) + "   颜色 " + Math.round(grade.x * 100) + "   调色盘 " + Math.round(grade.str * 100));
            presetLabel.setText(grade.cur().name + "  (" + (grade.preset + 1) + "/" + GradeState.PRESETS.length + ")");
        }
        if (btnPalette != null) syncButtons();
    }

    private void setPalette(boolean on) {
        paletteOpen = on;
        palette.setVisibility(on ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        zoomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        readout.setVisibility(on ? View.VISIBLE : View.GONE);
        presetLabel.setVisibility(on ? View.VISIBLE : View.GONE);
        specPanel.setVisibility(View.GONE);
        if (on) palette.sync();
        pushGrade();
    }

    private void toggleLive() {
        prefs.live = !prefs.live; prefs.save(); syncButtons();
        say(prefs.live ? "实况已开启（动态照片 " + prefs.liveDur + " 秒，从按下快门开始，无声音）" : "实况已关闭");
    }

    private void openSettings() {
        specPanel.setVisibility(View.GONE);
        if (overlay != null) return;
        final DualSettingsView v = new DualSettingsView(this, prefs, new DualSettingsView.Host() {
            @Override public void onClose() { closeOverlay(); }
            @Override public void onChanged(boolean reconfigure) {
                state.mirrorFront = prefs.mirror; if (gl != null) gl.borderMode = prefs.pipBorder;
                gridView.setVisibility(prefs.gridLines ? View.VISIBLE : View.GONE);
                syncSpecPill(); syncButtons();
                if (reconfigure) { relayout(); restartWithPrefs(); }
            }
        });
        overlay = v; root.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openAlbum() {
        if (overlay != null) return;
        final DualAlbum a = new DualAlbum(this, new ArrayList<File>(itemFiles), new ArrayList<Boolean>(itemVideo),
                new Runnable() { @Override public void run() { closeOverlay(); } },
                new Runnable() { @Override public void run() { closeOverlay(); closeTapped(); } });
        overlay = a; root.addView(a, new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeOverlay() { if (overlay != null) { root.removeView(overlay); overlay = null; } }

    private void say(final String t) {
        ui.post(new Runnable() { @Override public void run() {
            toast.setText(t); toast.setAlpha(1f);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            lp.bottomMargin = Math.max(dp(170), scrH - (stageY + stageH) + dp(130)); toast.setLayoutParams(lp);
            toast.animate().cancel();
            toast.animate().alpha(0f).setStartDelay(2600).setDuration(300).start();
        } });
    }

    // ---------------------------------------------------------------- 摄像头 / 规格

    private boolean pickCameras() {
        try {
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer f = c.get(CameraCharacteristics.LENS_FACING);
                if (f == null) continue;
                if (f == CameraCharacteristics.LENS_FACING_BACK && backId == null) backId = id;
                if (f == CameraCharacteristics.LENS_FACING_FRONT && frontId == null) frontId = id;
            }
        } catch (Exception e) { return false; }
        if (backId == null || frontId == null) return false;
        for (int cam = 0; cam < 2; cam++) {
            try {
                CameraCharacteristics c = cm.getCameraCharacteristics(cam == 0 ? backId : frontId);
                activeArr[cam] = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                float lo = 1f, hi = 1f;
                if (Build.VERSION.SDK_INT >= 30) {
                    Range<Float> r = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                    if (r != null) { lo = r.getLower(); hi = r.getUpper(); }
                }
                if (hi <= 1.01f) {
                    Float m = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
                    hi = m == null ? 1f : m; lo = 1f;
                }
                zoomMin[cam] = Math.max(0.1f, lo);
                zoomMax[cam] = Math.max(zoomMin[cam], Math.min(hi, 30f));
                zoomCur[cam] = Math.max(zoomMin[cam], Math.min(zoomMax[cam], 1f));
            } catch (Throwable ignore) {}
        }
        return true;
    }

    private int mainCam() { return state.backIsMain ? 0 : 1; }

    private void syncDial() {
        if (zoomBar == null) return;
        int c = mainCam();
        zoomBar.setRange(zoomMin[c], zoomMax[c], zoomMin[c] < 0.98f);
        zoomBar.setZoom(zoomCur[c]);
        lastBackMain = state.backIsMain;
    }

    /** 给一颗摄像头算规格：最大画面 + 能达到的帧率 + AE 帧率范围 */
    private int[] plan(int cam) {
        try {
            CameraCharacteristics c = cm.getCameraCharacteristics(cam == 0 ? backId : frontId);
            StreamConfigurationMap m = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] all = m == null ? null : m.getOutputSizes(SurfaceTexture.class);
            if (all == null || all.length == 0) return new int[]{1280, 720, 30, 15, 30};
            int[][] sizes = new int[all.length][2]; long[] dur = new long[all.length];
            for (int i = 0; i < all.length; i++) {
                sizes[i][0] = all[i].getWidth(); sizes[i][1] = all[i].getHeight();
                dur[i] = m.getOutputMinFrameDuration(SurfaceTexture.class, all[i]);
            }
            DualPlan.SizePick pk = DualPlan.pickSize(sizes, dur, prefs.fullGate, DualPlan.cap(effTier), effFps);
            int fps = pk.fpsOk ? effFps : Math.max(15, (int) Math.floor(pk.maxFps));
            Range<Integer>[] rs = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
            int lo = Math.min(fps, 30), hi = fps;
            if (rs != null && rs.length > 0) {
                int[][] ra = new int[rs.length][2];
                for (int i = 0; i < rs.length; i++) { ra[i][0] = rs[i].getLower(); ra[i][1] = rs[i].getUpper(); }
                int k = DualPlan.pickFpsRange(ra, fps);
                if (k >= 0) { lo = ra[k][0]; hi = ra[k][1]; }
            }
            return new int[]{pk.w, pk.h, Math.min(fps, hi), lo, hi};
        } catch (Throwable t) { return new int[]{1280, 720, 30, 15, 30}; }
    }
    private final int[][] fpsRange = {{15, 30}, {15, 30}};

    /** 成品尺寸：和取景框同比例（片门全开 3:4），长边按分辨率档，不超过流的长边 */
    private void applyOutputSize() {
        float asp = prefs.fullGate ? 0.75f : Math.min(1f, Math.max(0.4f, stageW / (float) Math.max(1, stageH)));
        int tierLong = prefs.res >= 2160 ? (prefs.fullGate ? 2880 : 3840) : prefs.res >= 1080 ? 1920 : 1280;
        int streamLong = Math.max(streamW[0], streamH[0]);
        int lg = Math.min(tierLong, Math.max(480, streamLong));
        if (asp > 0.5625f && !prefs.fullGate) lg = Math.min(lg, Math.max(480, Math.round(Math.min(streamW[0], streamH[0]) / asp)));
        recH = lg & ~1; recW = Math.round(lg * asp) & ~1;
    }

    private void restartWithPrefs() {
        if (recording || clipping) { say("录像中不能改规格"); return; }
        effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res); degradeCount = 0;
        restart();
    }

    private void restart() {
        spinnerView.setVisibility(View.VISIBLE);
        closeCameras();
        ui.postDelayed(new Runnable() { @Override public void run() { tryOpen(); } }, 350);
    }

    /** 带宽 / 配置撑不住：先把帧率降到 30，再逐级降分辨率 */
    private void degrade(String why) {
        if (finished || failedReported) return;
        degradeCount++;
        if (degradeCount > 6) { fail("unsupported", "这台设备配置不出前后摄像头同时输出（" + why + "）"); return; }
        if (effFps > 30) effFps = 30;
        else if (effTier < 3) effTier++;
        else { fail("unsupported", "这台设备配置不出前后摄像头同时输出（" + why + "）"); return; }
        say("同时开前后摄像头时硬件撑不住所选规格，已自动降档");
        restart();
    }

    @Override public void surfaceCreated(final SurfaceHolder h) {
        glH.post(new Runnable() { @Override public void run() {
            try {
                if (gl != null) gl.release();
                gl = new GlComposer(state);
                gl.init(h.getSurface());
                gl.dispDeg = 0;
                gl.vfX = stageX; gl.vfY = stageY; gl.vfW = stageW; gl.vfH = stageH;
                gl.borderMode = prefs.pipBorder;
                for (int cam = 0; cam < 2; cam++) {
                    CameraCharacteristics c = cm.getCameraCharacteristics(cam == 0 ? backId : frontId);
                    Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    gl.sensor[cam] = so == null ? 90 : so;
                    final int which = cam;
                    gl.st[cam].setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                        @Override public void onFrameAvailable(SurfaceTexture t) { onFrame(which); }
                    }, glH);
                }
                glReady = true;
                ui.post(new Runnable() { @Override public void run() { applyShape(); pushGrade(); tryOpen(); } });
            } catch (Throwable t) { fail("error", "初始化画面合成失败：" + t.getMessage()); }
        } });
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { glReady = false; closeCameras(); }

    @SuppressWarnings("MissingPermission")
    private synchronized void tryOpen() {
        if (camsOpened || !glReady || finished || failedReported) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
        camsOpened = true; openedCount = 0;
        final int myGen = ++gen;
        int[][] pl = {plan(0), plan(1)};
        for (int cam = 0; cam < 2; cam++) {
            streamW[cam] = pl[cam][0]; streamH[cam] = pl[cam][1]; camFps[cam] = pl[cam][2];
            fpsRange[cam][0] = pl[cam][3]; fpsRange[cam][1] = pl[cam][4];
            gl.setBufferSize(cam, pl[cam][0], pl[cam][1]);
        }
        infoText = "后置 " + pl[0][0] + "×" + pl[0][1] + "@" + pl[0][2] + "  前置 " + pl[1][0] + "×" + pl[1][1] + "@" + pl[1][2];
        applyOutputSize();
        for (int cam = 0; cam < 2; cam++) {
            final int which = cam;
            try {
                cm.openCamera(cam == 0 ? backId : frontId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice d) {
                        if (myGen != gen) { d.close(); return; }
                        dev[which] = d; startSession(which, myGen);
                    }
                    @Override public void onDisconnected(CameraDevice d) { d.close(); if (myGen == gen) { dev[which] = null; if (!finished) say("摄像头被占用或断开"); } }
                    @Override public void onError(CameraDevice d, int err) {
                        d.close(); if (myGen != gen) return; dev[which] = null;
                        boolean busy = err == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE || err == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE;
                        fail(busy ? "unsupported" : "error", busy ? "系统不允许前后摄像头同时工作（硬件限制）" : "摄像头出错（代码 " + err + "）");
                    }
                }, camH);
            } catch (Exception e) {
                fail(e instanceof CameraAccessException ? "unsupported" : "error", "打开摄像头失败：" + e.getMessage());
                return;
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void startSession(final int cam, final int myGen) {
        try {
            final CameraDevice d = dev[cam];
            if (d == null || gl == null) return;
            final Surface s = gl.surface(cam);
            d.createCaptureSession(Arrays.asList(s), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession cs) {
                    if (myGen != gen) { try { cs.close(); } catch (Throwable ignore) {} return; }
                    ses[cam] = cs;
                    try {
                        CaptureRequest.Builder rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        rb.addTarget(s);
                        rb.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        rb.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(fpsRange[cam][0], fpsRange[cam][1]));
                        reqB[cam] = rb;
                        cs.setRepeatingRequest(rb.build(), null, camH);
                        applyZoom(cam);
                    } catch (Exception e) { fail("error", "开启预览失败：" + e.getMessage()); return; }
                    synchronized (DualCameraActivity.this) { openedCount++; }
                    if (openedCount >= 2) ui.post(new Runnable() { @Override public void run() {
                        spinnerView.setVisibility(View.GONE);
                        String want = (prefs.res == 2160 ? "4K" : prefs.res + "p") + "@" + prefs.fps;
                        say((camFps[0] < prefs.fps || effFps < prefs.fps || effTier > DualPlan.startTier(prefs.res) ? "实际规格低于所选（" + want + "）：" : "实际规格：") + infoText);
                    } });
                }
                @Override public void onConfigureFailed(CameraCaptureSession cs) {
                    if (myGen != gen) return;
                    ui.post(new Runnable() { @Override public void run() { degrade("会话配置失败"); } });
                }
            }, camH);
        } catch (Exception e) { ui.post(new Runnable() { @Override public void run() { degrade("建立会话失败：" + e0(e)); } }); }
    }
    private static String e0(Exception e) { return String.valueOf(e.getMessage()); }

    private void closeCameras() {
        camsOpened = false; gen++;
        for (int i = 0; i < 2; i++) {
            try { if (ses[i] != null) ses[i].close(); } catch (Throwable ignore) {}
            try { if (dev[i] != null) dev[i].close(); } catch (Throwable ignore) {}
            ses[i] = null; dev[i] = null; reqB[i] = null;
        }
    }

    /** 把某个摄像头的倍数写进它的预览请求（Android 11+ 用缩放比，可以 <1 切超广角；更老的系统用裁切区域） */
    private void applyZoom(final int cam) {
        if (camH == null) return;
        camH.post(new Runnable() { @Override public void run() {
            try {
                CaptureRequest.Builder rb = reqB[cam];
                CameraCaptureSession cs = ses[cam];
                if (rb == null || cs == null) return;
                float z = zoomCur[cam];
                if (Build.VERSION.SDK_INT >= 30) rb.set(CaptureRequest.CONTROL_ZOOM_RATIO, z);
                else {
                    Rect a = activeArr[cam];
                    if (a != null) {
                        float zz = Math.max(1f, z);
                        int w = Math.round(a.width() / zz), h = Math.round(a.height() / zz);
                        int l = a.left + (a.width() - w) / 2, t = a.top + (a.height() - h) / 2;
                        rb.set(CaptureRequest.SCALER_CROP_REGION, new Rect(l, t, l + w, t + h));
                    }
                }
                cs.setRepeatingRequest(rb.build(), null, camH);
            } catch (Throwable ignore) {}
        } });
    }

    private void torch() {
        torchOn = !torchOn;
        try { cm.setTorchMode(backId, torchOn); say(torchOn ? "手电筒：开" : "手电筒：关"); }
        catch (Throwable e) { torchOn = false; say("手电筒不可用（双摄时系统常占用闪光灯）"); }
        syncButtons();
    }

    // ---------------------------------------------------------------- 渲染（GL 线程）

    private void onFrame(int cam) {
        if (gl == null || finished) return;
        boolean both = gl.update(cam);
        if (!both || cam != mainCam()) return;        // 以主画面那颗摄像头的帧率出画面 / 编码
        if (lastBackMain != state.backIsMain) { lastBackMain = state.backIsMain; ui.post(new Runnable() { @Override public void run() { syncDial(); } }); }
        gl.drawPreview();
        if (recording && recorder != null && gl.hasEncoder()) gl.drawEncoder(System.nanoTime() - recStartNs, recW, recH);
    }

    // ---------------------------------------------------------------- 拍照 / 录像

    private File newFile(String ext) {
        return new File(outDir(this), "澄影双摄_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + (100 + new Random().nextInt(900)) + "." + ext);
    }

    private void shutterTapped() {
        if (!glReady || gl == null || clipping) return;
        if (!videoMode) takePhoto(); else if (recording) stopRecording(); else startRecording(false, 0);
    }

    private void takePhoto() {
        final boolean live = prefs.live;
        final boolean heif = prefs.heif && Build.VERSION.SDK_INT >= 34 && !live;
        final File f = newFile(heif ? "heic" : "jpg");
        final int w = recW, h = recH;
        final View flash = new View(this); flash.setBackgroundColor(Color.WHITE);
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(stageW, stageH, Gravity.TOP | Gravity.LEFT); fp.leftMargin = stageX; fp.topMargin = stageY;
        root.addView(flash, fp); flash.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() { @Override public void run() { root.removeView(flash); } }).start();
        glH.post(new Runnable() { @Override public void run() {
            try {
                Bitmap b = gl.capture(w, h);
                try (FileOutputStream o = new FileOutputStream(f)) {
                    if (heif) b.compress(Bitmap.CompressFormat.valueOf("HEIC"), 92, o);
                    else b.compress(Bitmap.CompressFormat.JPEG, 94, o);
                }
                b.recycle();
                if (live) ui.post(new Runnable() { @Override public void run() { startClip(f); } });
                else store(f, false);
            } catch (Throwable t) { say("拍照失败：" + t.getMessage()); }
        } });
    }

    /** 实况（动态照片）：按下快门后录 liveDur 秒短视频，完成后和照片合成一个文件（或分开保存） */
    private void startClip(final File photo) {
        clipping = true;
        say("实况录制中…（" + prefs.liveDur + " 秒）");
        startRecording(true, prefs.liveDur);
        final File clip = recFile;
        ui.postDelayed(new Runnable() { @Override public void run() {
            stopClip(photo, clip);
        } }, prefs.liveDur * 1000L);
    }

    private void stopClip(final File photo, final File clip) {
        final DualRecorder r = recorder;
        recording = false; recorder = null;
        pending++;
        glH.post(new Runnable() { @Override public void run() {
            try { gl.setEncoderSurface(null); } catch (Throwable ignore) {}
            boolean ok = r != null && r.stop();
            if (ok && !prefs.liveSeparate) {
                File out = newFile("jpg");
                if (MotionPhoto.embed(photo, clip, out, 0)) { photo.delete(); clip.delete(); store(out, false); }
                else { say("动态照片合成失败，已分开保存"); store(photo, false); store(clip, true); }
            } else if (ok) { store(photo, false); store(clip, true); }
            else { say("实况视频失败，只保存照片"); store(photo, false); clip.delete(); }
            clipping = false; pending--;
        } });
    }

    private void startRecording(final boolean silent, int unused) {
        recW &= ~1; recH &= ~1;
        final File f = newFile("mp4");
        final boolean wantAudio = !silent && prefs.audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        final int fps = Math.max(24, Math.min(60, camFps[mainCam()]));
        recFile = f;
        glH.post(new Runnable() { @Override public void run() {
            try {
                DualRecorder r = new DualRecorder(f, recW, recH, wantAudio, fps);
                Surface in = r.start();
                gl.setEncoderSurface(in);
                recStartNs = System.nanoTime();
                recorder = r; recording = true;
                final boolean audio = r.hasAudio;
                if (!silent) ui.post(new Runnable() { @Override public void run() {
                    paint(); timer.setVisibility(View.VISIBLE); tick();
                    say(audio ? "开始录像（带声音）" : "开始录像（只录画面）");
                } });
            } catch (Throwable t) { recording = false; clipping = false; say("无法开始录像：" + t.getMessage()); }
        } });
    }

    private void tick() {
        if (!recording || clipping) { timer.setVisibility(View.GONE); return; }
        long s = (System.nanoTime() - recStartNs) / 1000000000L;
        timer.setText(String.format(Locale.US, "● %02d:%02d", s / 60, s % 60));
        ui.postDelayed(new Runnable() { @Override public void run() { tick(); } }, 500);
    }

    private void stopRecording() {
        if (!recording || clipping) return;
        recording = false;
        final DualRecorder r = recorder; final File f = recFile;
        recorder = null;
        ui.post(new Runnable() { @Override public void run() { paint(); timer.setVisibility(View.GONE); } });
        pending++;
        glH.post(new Runnable() { @Override public void run() {
            try { gl.setEncoderSurface(null); } catch (Throwable ignore) {}
            boolean ok = r != null && r.stop();
            if (ok) store(f, true); else { say("录像失败：" + (r == null ? "" : r.error)); f.delete(); }
            pending--;
        } });
    }

    /** 存进系统相册 + 记入清单（后台线程） */
    private void store(final File f, final boolean video) {
        pending++;
        new Thread(new Runnable() { @Override public void run() {
            MediaSaver.Result r = MediaSaver.save(DualCameraActivity.this, f, f.getName(), video ? "video/mp4" : f.getName().endsWith(".heic") ? "image/heic" : "image/jpeg");
            try {
                JSONObject o = new JSONObject();
                o.put("kind", video ? "video" : "photo");
                o.put("name", f.getName());
                o.put("url", "https://" + MainActivity.HOST + "/__dual/" + java.net.URLEncoder.encode(f.getName(), "UTF-8").replace("+", "%20"));
                o.put("saved", r.ok);
                synchronized (items) { items.put(o); itemFiles.add(f); itemVideo.add(video); }
            } catch (Exception ignore) {}
            final int n; synchronized (items) { n = items.length(); }
            say(r.ok ? (video ? "视频已存入系统相册" : "照片已存入系统相册") : "没存进系统相册：" + r.error + "（仍会导入澄影相册）");
            final Bitmap tb = DualAlbum.thumb(f, video, 200);
            ui.post(new Runnable() { @Override public void run() {
                if (tb != null) thumbImg.setImageBitmap(tb);
                thumbCount.setText(String.valueOf(n)); thumbCount.setVisibility(View.VISIBLE);
            } });
            pending--;
        } }).start();
    }

    // ---------------------------------------------------------------- 关闭 / 结果

    private void closeTapped() {
        if (recording && !clipping) stopRecording();
        finishWhenIdle();
    }

    private void finishWhenIdle() {
        if (pending > 0 || clipping) { ui.postDelayed(new Runnable() { @Override public void run() { finishWhenIdle(); } }, 200); return; }
        finishWith();
    }

    @Override public void onBackPressed() {
        if (overlay != null) { closeOverlay(); return; }
        if (paletteOpen) { setPalette(false); return; }
        closeTapped();
    }

    private void fail(String code, String msg) {
        if (failedReported) return;
        failedReported = true;
        failCode = code; failMsg = msg;
        ui.post(new Runnable() { @Override public void run() { finishWhenIdle(); } });
    }

    private void finishWith() {
        if (finished) return;
        finished = true;
        try {
            JSONObject o = new JSONObject();
            boolean none; synchronized (items) { none = items.length() == 0; }
            if (failCode != null && none) { o.put("ok", false); o.put("code", failCode); o.put("error", failMsg); }
            else { o.put("ok", true); synchronized (items) { o.put("items", items); } }
            if (torchOn) try { cm.setTorchMode(backId, false); } catch (Throwable ignore) {}
            Intent i = new Intent();
            i.putExtra("result", o.toString());
            setResult(RESULT_OK, i);
        } catch (Exception ignore) {}
        finish();
    }
}

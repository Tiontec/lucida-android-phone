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
import android.graphics.Typeface;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Choreographer;
import android.util.Range;
import android.util.Size;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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
 * 原生双摄（11.1.8 重写，11.2.0 扩展，11.2.1 对照苹果补齐；纯原生界面，没有网页）：
 *  Camera2 同时打开前后摄像头 → GlComposer（OpenGL）合成画中画 / 上下分屏 + 调色 → 预览、拍照、录像（H.264 + AAC）。
 *  规格：分辨率 720p / 1080p / 4K × 帧率 24 / 30 / 60（每颗摄像头各自挑能达到的最大画面，达不到自动降档并提示）；
 *  片门全开 = 前后摄都用 4:3；变焦用主页面同款弧形转盘（ZoomBar），超广角 / 广角 / 主摄由缩放比切换。
 */
public class DualCameraActivity extends Activity implements SurfaceHolder.Callback {
    static File outDir(Context c) { File d = new File(c.getCacheDir(), "dual"); d.mkdirs(); return d; }
    private static final int REQ_PERM = 21;

    private final DualState state = new DualState();
    private DualPrefs prefs;
    private final GradeState gradeBack = new GradeState(), gradeFront = new GradeState();   // 前 / 后摄各一套调色
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
    /** 双摄开关：true = 前后摄像头同时工作；false = 单摄（只开 state.backIsMain 指的那一颗） */
    private volatile boolean dual;
    private volatile String lastDualFail = "";
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
    private final float[] hwMax = {1f, 1f};        // 硬件能做到的最大变焦（超出的部分用软件裁剪补，见 fillReq）
    private final Rect[] activeArr = new Rect[2];
    private final CaptureRequest.Builder[] reqB = new CaptureRequest.Builder[2];
    private final int[] camFps = {30, 30};
    private final int[] streamW = {1280, 1280}, streamH = {720, 720};
    private boolean lastBackMain = true;
    // 当前有效规格（可能比设置里选的低）
    private int effTier, effFps;
    private String infoText = "";
    // ---- 11.5.0：防「画面不动」——请求分级 + 看门狗 + 诊断（联发科 / ColorOS 机型上，多余的请求项可能让相机一帧都不出）----
    private volatile int reqStage = 0;                       // 0 完整请求；1 最简请求（不带测光区域 / 曝光补偿 / AE 锁）；2 再去掉帧率范围
    private final long[] frameCnt = new long[2], capCnt = new long[2], capFail = new long[2];
    private volatile long drawnCnt;
    private long wdLastDrawn = -1, wdSince;
    private int wdTries;
    private boolean wdRunning;
    private final CameraCaptureSession.CaptureCallback[] capCb = new CameraCaptureSession.CaptureCallback[2];

    // 界面
    private FrameLayout root, bottomBar, stageTouch;
    private ZoomBar zoomBar;
    private DualIcon btnBack, btnLive, btnPalette, btnSettings, btnLayout, btnShape, btnMirror, btnTorch, btnSwap, btnMore;
    private TextView specPill, tabPhoto, tabVideo, toast, timer, readout, presetLabel;
    private LinearLayout specPanel, segRes, segFps, segFmt, segRatio;
    private View shutterInner, shutterRing, gridView, spinnerView;
    private FrameLayout thumbBox;
    private ImageView thumbImg;
    private TextView thumbCount;
    private DualPalette palette;
    private DualDots dots;
    private LinearLayout liveStack;
    // 实况连拍：每张各自一行进度（取景框左上角），全部完成后一起收起（只在界面线程读写）
    private static final int MAX_LIVE = 3;
    private static final class LiveTask {
        final int n, total; final File photo;
        File clip; DualRecorder rec; GlComposer.Enc enc;
        long t0Ns = System.nanoTime();
        int phase;                       // 0 录制中 · 1 合成保存中 · 2 完成 · 3 失败
        String note = "";
        TextView row;
        LiveTask(int n, int total, File photo) { this.n = n; this.total = total; this.photo = photo; }
    }
    private final ArrayList<LiveTask> liveTasks = new ArrayList<LiveTask>();
    private int liveSeq, liveHideToken;
    private boolean liveTicking;
    private static final int REQ_DEL = 31;
    private Runnable delDone;
    private DualFocusView focusView;
    // 对焦 / 曝光（每颗摄像头各一份）
    private final MeteringRectangle[][] focusRegion = new MeteringRectangle[2][];
    private final int[] maxAf = new int[2], maxAe = new int[2];
    private final boolean[] evDragging = new boolean[2];
    private int focusToken, relockToken, toastToken;
    private GestureDetector gd;
    private boolean gDownPip, specOpen, lastGlass;
    private String lastStyle = "liquid";
    private boolean lastPanelGlass = true;
    private LinearLayout hintStack;
    private final boolean[] afLock = new boolean[2], aeLock = new boolean[2];
    private final int[] evIdx = new int[2], evLo = new int[2], evHi = new int[2], senOri = {90, 270};
    private final float[] evStep = {1f, 1f};
    private int focusCam = 0, previewN = 0;
    private PowerManager.OnThermalStatusChangedListener thermalListener;
    private boolean throttled, pendingThrottle;
    private View overlay;
    private boolean paletteOpen;
    private int stageX, stageY, stageW = 1, stageH = 1, scrW, scrH, insetTop, insetBottom;
    private int lastLayoutKey;

    // ---------------------------------------------------------------- 生命周期

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        AppLog.init(this);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        useWholeScreen(this);
        ui = new Handler(Looper.getMainLooper());
        prefs = new DualPrefs(this);
        gradeBack.load(prefs.sp, "g_"); gradeFront.load(prefs.sp, "gf_");
        DualUi.glass = prefs.glass; DualUi.glassStyle = prefs.sp.getString("glassStyle", "liquid"); lastStyle = DualUi.glassStyle; DualUi.panelGlass = PanelTheme.isGlass(this); lastPanelGlass = DualUi.panelGlass;
        state.style = prefs.style; state.mirrorFront = prefs.mirror; state.pipW = prefs.pipW;
        videoMode = "video".equals(getIntent().getStringExtra("mode"));
        dual = prefs.dualOn;
        state.backIsMain = dual ? true : !prefs.singleFront;
        effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res);
        buildUi();
        immersive();
        cm = (CameraManager) getSystemService(CAMERA_SERVICE);
        if (!pickCameras()) { fail("unsupported", "没有找到摄像头"); return; }
        if (dual && (backId == null || frontId == null)) { dual = false; prefs.dualOn = false; prefs.save(); state.backIsMain = !prefs.singleFront; }
        if (backId == null) state.backIsMain = false;          // 只有前置：单摄就用前置
        if (frontId == null) state.backIsMain = true;
        glThread = new HandlerThread("dual-gl"); glThread.start(); glH = new Handler(glThread.getLooper());
        camThread = new HandlerThread("dual-cam"); camThread.start(); camH = new Handler(camThread.getLooper());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_PERM);
        }
        syncDial();
        watchThermal();
        refreshThumb();
    }

    static void useWholeScreen(android.app.Activity a) {
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = a.getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            a.getWindow().setAttributes(lp);
        }
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

    @Override protected void onResume() {
        super.onResume(); immersive(); if (glReady) tryOpen();
        syncLevel();
        if (overlay instanceof DualAlbum) ((DualAlbum) overlay).reload();      // 从系统相册回来：那边删掉的这里也要消失
    }

    @Override
    protected void onPause() {
        if (recording) stopRecording();
        cancelCountdown(false);
        if (levelListening) { sensors.unregisterListener(levelListener); levelListening = false; }
        if (screenFlashView != null) screenFlash(false);
        flashBusy = false;
        closeCameras();
        NativeKv.of(this).flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 29 && thermalListener != null) {
            try { ((PowerManager) getSystemService(POWER_SERVICE)).removeThermalStatusListener(thermalListener); } catch (Throwable ignore) {}
        }
        GlassRefract.I.detach();
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
        GlassRefract.I.attach(sv);
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

        levelView = new DualLevelView(this);
        levelView.setVisibility(prefs.level ? View.VISIBLE : View.GONE);
        root.addView(levelView);
        countView = DualUi.label(this, "", 110, Color.WHITE);
        countView.setGravity(Gravity.CENTER); countView.setTypeface(Typeface.DEFAULT_BOLD);
        countView.setShadowLayer(8f, 0f, 3f, 0x80000000);
        countView.setVisibility(View.GONE);
        root.addView(countView);

        stageTouch = new FrameLayout(this);
        root.addView(stageTouch);
        focusView = new DualFocusView(this);
        stageTouch.addView(focusView);
        // 单击：小窗 → 互换主 / 副画面；主画面 → 对焦 + 测光（出现对焦框和曝光滑轨）；锁定状态下再点一下解除
        // 双击小窗：放大 / 缩小；长按主画面：AE / AF 锁定；拖小窗：挪位置；对焦框在时上下拖：调曝光（向上变亮）
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) { gDownPip = inPip(e.getX(), e.getY()); return true; }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) { tapAt(e.getX(), e.getY()); return true; }
            @Override public boolean onDoubleTap(MotionEvent e) { if (inPip(e.getX(), e.getY())) togglePipSize(); return true; }
            @Override public void onLongPress(MotionEvent e) { if (!gDownPip && !paletteOpen) focusAt(e.getX(), e.getY(), true); }
            @Override public boolean onScroll(MotionEvent a, MotionEvent b, float dx, float dy) { dragBy(-dx, -dy); return true; }
        });
        stageTouch.setOnTouchListener(new View.OnTouchListener() {
            float sx, sy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                gd.onTouchEvent(e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        setSpecPanel(false);
                        sx = e.getX(); sy = e.getY();
                        if (gDownPip) startPipDrag();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        stopPipDrag();
                        return true;
                    case MotionEvent.ACTION_UP:
                        stopPipDrag();
                        // 调色盘打开时左右滑：按「预设顺序」换预设
                        if (paletteOpen && Math.abs(e.getX() - sx) > dp(60) && Math.abs(e.getX() - sx) > Math.abs(e.getY() - sy) * 1.3f) stepPreset(e.getX() < sx ? 1 : -1);
                        return true;
                }
                return true;
            }
        });

        zoomBar = new ZoomBar(this);
        zoomBar.setListener(new ZoomBar.Listener() { @Override public void onZoom(float z) {
            int c = mainCam(); zoomCur[c] = z; if (c == 0) maybeSwitchLens(z); applyZoom(c);
        } });
        root.addView(zoomBar);

        // 顶部：返回 + 规格胶囊 | 实况 · 调色盘 · 设置
        btnBack = icon(DualIcon.BACK, new View.OnClickListener() { @Override public void onClick(View v) { closeTapped(); } });
        btnLive = icon(DualIcon.LIVE, new View.OnClickListener() { @Override public void onClick(View v) { toggleLive(); } });
        btnPalette = icon(DualIcon.PALETTE, new View.OnClickListener() { @Override public void onClick(View v) { setPalette(!paletteOpen); } });
        btnSettings = icon(DualIcon.GEAR, new View.OnClickListener() { @Override public void onClick(View v) { openSettings(); } });
        specPill = DualUi.label(this, "", 13, Color.WHITE);
        specPill.setGravity(Gravity.CENTER); specPill.setSingleLine(true); specPill.setIncludeFontPadding(false);
        specPill.setPadding(dp(16), 0, dp(16), 0); specPill.setMinWidth(dp(120));
        stylePill();
        specPill.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setSpecPanel(!specOpen); } });
        btnBack.setVisibility(View.GONE);      // 重构版没有上一级，相机就是主界面
        root.addView(btnBack); root.addView(specPill); root.addView(btnLive); root.addView(btnPalette); root.addView(btnSettings);
        buildSpecPanel();

        readout = DualUi.label(this, "", 13, Color.WHITE); readout.setGravity(Gravity.CENTER);
        readout.setBackground(DualUi.round(0x73000000, 13, this)); readout.setVisibility(View.GONE);
        presetLabel = DualUi.label(this, "", 13, Color.WHITE); presetLabel.setGravity(Gravity.CENTER);
        presetLabel.setBackground(DualUi.round(0x73000000, 13, this)); presetLabel.setVisibility(View.GONE);
        timer = DualUi.label(this, "", 14, Color.WHITE); timer.setGravity(Gravity.CENTER);
        timer.setBackground(DualUi.round(0xD9E61A1A, 12, this)); timer.setVisibility(View.GONE);
        dots = new DualDots(this); dots.setVisibility(View.GONE);
        root.addView(readout); root.addView(presetLabel); root.addView(dots); root.addView(timer);

        // 底部：纯黑平面
        bottomBar = new FrameLayout(this);
        bottomBar.setBackgroundColor(Color.BLACK);
        root.addView(bottomBar);
        btnLayout = icon(DualIcon.PIP, new View.OnClickListener() { @Override public void onClick(View v) {
            if (recording) { say("录像中不能切换布局"); return; }
            state.style = state.style == DualState.STYLE_PIP ? DualState.STYLE_SPLIT : DualState.STYLE_PIP;
            clearFocus(); prefs.style = state.style; prefs.save(); syncButtons(); } });
        btnShape = icon(DualIcon.SHAPE, new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.pipShape = (prefs.pipShape + 1) % 3; prefs.save(); applyShape(); say(new String[]{"小窗：圆角", "小窗：大圆角", "小窗：直角"}[prefs.pipShape]); } });
        btnMirror = icon(DualIcon.MIRROR, new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.mirror = !prefs.mirror; state.mirrorFront = prefs.mirror; prefs.save(); syncButtons(); } });
        btnTorch = icon(DualIcon.TORCH, new View.OnClickListener() { @Override public void onClick(View v) { torch(); } });
        btnSwap = icon(DualIcon.SWAP, new View.OnClickListener() { @Override public void onClick(View v) { swapMain(); } });
        btnMore = icon(DualIcon.MORE, new View.OnClickListener() { @Override public void onClick(View v) { openMore(); } });
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
        for (View v : new View[]{btnLayout, btnShape, btnMirror, btnTorch, btnSwap, btnMore, tabPhoto, tabVideo, shutterRing, shutterInner, thumbBox}) bottomBar.addView(v);

        palette = new DualPalette(this, gradeBack, gradeFront, prefs, new DualPalette.Host() {
            @Override public void onClose() { setPalette(false); }
            @Override public void onShutter() { shutterTapped(); }
            @Override public void onChange() { pushGrade(); }
        });
        palette.setVisibility(View.GONE);
        root.addView(palette);

        // 取景框左上角：实况计时 + 提示条（以后所有提示都放这里）。毛玻璃界面开着时是毛玻璃，关着时是普通深色面板
        hintStack = new LinearLayout(this); hintStack.setOrientation(LinearLayout.VERTICAL);
        liveStack = new LinearLayout(this); liveStack.setOrientation(LinearLayout.VERTICAL); liveStack.setVisibility(View.GONE);
        toast = DualUi.label(this, "", 13, Color.WHITE); toast.setGravity(Gravity.CENTER_VERTICAL);
        toast.setPadding(dp(12), dp(7), dp(12), dp(7)); toast.setVisibility(View.GONE);
        styleToast();
        hintStack.addView(liveStack, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2); tlp.topMargin = dp(6);
        hintStack.addView(toast, tlp);
        root.addView(hintStack, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT));
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
        lastGlass = DualUi.glass;
        applyShape(); pushGrade(); syncButtons(); paint(); syncSpecPill();
    }

    private TextView tab(String t, final boolean video) {
        TextView v = DualUi.label(this, t, 15, Color.WHITE); v.setGravity(Gravity.CENTER); v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) {
            if (recording || clipping) return; videoMode = video; paint(); syncButtons(); } });
        return v;
    }

    private static int idxOf(int[] a, int v) { for (int i = 0; i < a.length; i++) if (a[i] == v) return i; return 1; }

    private void buildSpecPanel() {
        specPanel = new LinearLayout(this);
        specPanel.setOrientation(LinearLayout.VERTICAL);
        specPanel.setBackground(panelBg(18));
        specPanel.setPadding(dp(14), dp(12), dp(14), dp(12));
        final int[] resV = {720, 1080, 2160}, fpsV = {24, 30, 60};
        int ri = 1, fi = 1;
        for (int i = 0; i < 3; i++) { if (resV[i] == prefs.res) ri = i; if (fpsV[i] == prefs.fps) fi = i; }
        segRes = DualUi.seg(this, new String[]{"720p", "1080p", "4K"}, ri, new DualUi.IntCb() { @Override public void on(int i) {
            if (recording || clipping) { say("录像 / 实况拍摄中不能改规格"); DualUi.setSeg(segRes, idxOf(resV, prefs.res)); return; }
            prefs.res = resV[i]; prefs.save(); restartWithPrefs(); syncSpecPill(); } });
        segFps = DualUi.seg(this, new String[]{"24", "30", "60"}, fi, new DualUi.IntCb() { @Override public void on(int i) {
            if (recording || clipping) { say("录像 / 实况拍摄中不能改规格"); DualUi.setSeg(segFps, idxOf(fpsV, prefs.fps)); return; }
            prefs.fps = fpsV[i]; prefs.save(); restartWithPrefs(); syncSpecPill(); } });
        boolean heifOk = Build.VERSION.SDK_INT >= 34;
        segFmt = DualUi.seg(this, new String[]{"JPEG", "HEIF"}, prefs.heif && heifOk ? 1 : 0, new DualUi.IntCb() { @Override public void on(int i) {
            if (i == 1 && Build.VERSION.SDK_INT < 34) { DualUi.setSeg(segFmt, 0); say("HEIF 需要 Android 14 及以上"); return; }
            prefs.heif = i == 1; prefs.save(); syncSpecPill(); } });
        segRatio = DualUi.seg(this, new String[]{"16:9", "4:3", "1:1"}, prefs.ratio, new DualUi.IntCb() { @Override public void on(int i) {
            if (i == prefs.ratio) return;
            if (prefs.fullGate) { DualUi.setSeg(segRatio, prefs.ratio); say("「片门全开」开着时画幅固定为 4:3，关掉后才能改"); return; }
            if (recording || clipping) { DualUi.setSeg(segRatio, prefs.ratio); say("录像 / 实况拍摄中不能改画幅"); return; }
            prefs.ratio = i; prefs.save(); relayout(); } });
        String[] names = {"分辨率", "帧率", "照片格式", "画幅"};
        LinearLayout[] segs = {segRes, segFps, segFmt, segRatio};
        for (int i = 0; i < segs.length; i++) {
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
        specPanel.setVisibility(specOpen ? View.VISIBLE : View.GONE);
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
        if (prefs.fullGate || prefs.ratio == 1) { sh = Math.min(W * 4 / 3, avail); sw = sh * 3 / 4; }     // 片门全开固定 3:4
        else if (prefs.ratio == 2) { sh = Math.min(W, avail); sw = sh; }
        else sh = Math.min(W * 16 / 9, avail);
        stageW = sw; stageH = sh; stageX = (W - sw) / 2; stageY = stageTop;
        if (gl != null) { gl.vfX = stageX; gl.vfY = stageY; gl.vfW = stageW; gl.vfH = stageH; }
        place(stageTouch, stageX, stageY, stageW, stageH);
        place(gridView, stageX, stageY, stageW, stageH);
        place(levelView, stageX, stageY, stageW, stageH);
        place(countView, stageX, stageY, stageW, stageH);
        place(zoomBar, stageX, stageY + stageH - dp(160), stageW, dp(160));
        // 顶部
        int y0 = top + dp(4), s = dp(40);
        place(btnBack, dp(14), y0, s, s);
        place(specPill, dp(14) + s + dp(8), y0 + dp(2), ViewGroup_WRAP, dp(36));
        // 药丸最宽只能到「实况」按钮左边 8dp，窄屏手机上自动换更短的写法 / 更小的字
        pillMaxPx = Math.max(dp(90), (W - dp(14) - 3 * s - dp(16)) - dp(8) - (dp(14) + s + dp(8)));
        syncSpecPill();
        place(btnSettings, W - dp(14) - s, y0, s, s);
        place(btnPalette, W - dp(14) - 2 * s - dp(8), y0, s, s);
        place(btnLive, W - dp(14) - 3 * s - dp(16), y0, s, s);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(300), -2, Gravity.TOP | Gravity.LEFT);
        sp.leftMargin = dp(14); sp.topMargin = y0 + s + dp(6); specPanel.setLayoutParams(sp);
        // 取景框左上角：实况计时 + 提示条
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        hp.leftMargin = stageX + dp(12); hp.topMargin = stageY + dp(10); hintStack.setLayoutParams(hp);
        toast.setMaxWidth(Math.max(dp(160), stageW * 6 / 10));
        // 录像计时：取景框右上角
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(92), dp(26), Gravity.TOP | Gravity.LEFT);
        tp.leftMargin = stageX + stageW - dp(92) - dp(12); tp.topMargin = stageY + dp(10); timer.setLayoutParams(tp);
        // 调色盘：悬浮面板（离屏幕边 8dp），上面是预设名 + 页码圆点 + 数值
        int ph = dp(280), pm = dp(8);
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(W - 2 * pm, ph, Gravity.BOTTOM | Gravity.LEFT);
        pl.leftMargin = pm; pl.bottomMargin = bot + pm; palette.setLayoutParams(pl);
        int panelTop = H - bot - pm - ph;
        place(dots, W / 2 - dp(80), panelTop - dp(20), dp(160), dp(14));
        place(presetLabel, W / 2 - dp(80), panelTop - dp(20) - dp(30), dp(160), dp(26));
        place(readout, W / 2 - dp(110), panelTop - dp(20) - dp(30) - dp(30), dp(220), dp(26));
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
        int moreMid = (W / 2 + dp(38) + W - dp(26) - dp(46)) / 2;      // 快门和翻转按钮之间
        place(btnMore, moreMid - dp(23), cy - dp(23), dp(46), dp(46));
        applyOutputSize();
    }
    private static final int ViewGroup_WRAP = -2;

    // ---------------------------------------------------------------- 状态同步

    private void paint() {
        tabPhoto.setTextColor(!videoMode ? DualUi.YELLOW : 0xBFFFFFFF);
        tabVideo.setTextColor(videoMode ? DualUi.YELLOW : 0xBFFFFFFF);
        // 视频模式：深色玻璃圈 + 红色圆（没录像）⇄ 红色圆角方块（录像中），中间用带回弹的动画衔接；照片模式保持白圈白圆
        android.graphics.drawable.GradientDrawable rg = new android.graphics.drawable.GradientDrawable();
        rg.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        if (videoMode) { rg.setColor(0xEB1C1C1E); rg.setStroke(dp(2), 0x47FFFFFF); }
        else { rg.setColor(Color.TRANSPARENT); rg.setStroke(dp(4), Color.WHITE); }
        shutterRing.setBackground(rg);
        final android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        g.setColor(videoMode ? 0xFFEF5350 : Color.WHITE);
        shutterInner.setBackground(g);
        final boolean rec = videoMode && recording;
        final float r0 = dp(30), r1 = dp(20);
        if (shutterRadius < 0) shutterRadius = r0;
        final float from = shutterInner.getScaleX(), to = rec ? .55f : 1f;
        g.setCornerRadius(shutterRadius);
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(380); va.setInterpolator(new android.view.animation.OvershootInterpolator(1.6f));
        final float fromR = shutterRadius;
        final float toR = rec ? r1 : r0;
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(android.animation.ValueAnimator an) {
            float f = (Float) an.getAnimatedValue();
            float s = from + (to - from) * f; shutterInner.setScaleX(s); shutterInner.setScaleY(s);
            shutterRadius = Math.max(0f, fromR + (toR - fromR) * f); g.setCornerRadius(shutterRadius);
        } });
        va.start();
    }

    private float shutterRadius = -1f;

    private void syncButtons() {
        btnLive.set(prefs.live); btnLive.setEnabled(!videoMode); btnLive.setAlpha(videoMode ? .4f : 1f);
        btnPalette.set(gradeBack.active() || gradeFront.active() || paletteOpen);
        btnTorch.set(torchOn); btnMirror.set(prefs.mirror);
        btnLayout.setKind(state.style == DualState.STYLE_PIP ? DualIcon.PIP : DualIcon.SPLIT);
        boolean pip = state.style == DualState.STYLE_PIP;
        btnShape.setEnabled(pip); btnShape.setAlpha(pip ? 1f : .4f); btnShape.shapeStyle = prefs.pipShape; btnShape.invalidate();
        // 单摄时没有布局 / 小窗形状可调；前置单摄没有手电筒
        btnLayout.setVisibility(dual ? View.VISIBLE : View.INVISIBLE); btnShape.setVisibility(dual ? View.VISIBLE : View.INVISIBLE);
        boolean noTorch = !dual && !state.backIsMain;
        btnTorch.setEnabled(!noTorch); btnTorch.setAlpha(noTorch ? .4f : 1f);
    }

    /** 顶部规格药丸：显示「实际在用」的规格（过热 / 带宽不够自动降档后，显示降档后的），没降档时就是所选的 */
    private void syncSpecPill() {
        if (Looper.myLooper() != Looper.getMainLooper()) { ui.post(new Runnable() { @Override public void run() { syncSpecPill(); } }); return; }
        if (specPill == null) return;
        String r = prefs.res == 2160 ? "4K" : prefs.res + "p";
        int f = prefs.fps;
        if (effTier > DualPlan.startTier(prefs.res)) r = new String[]{"4K", "2.8K", "1080p", "720p"}[Math.max(0, Math.min(3, effTier))] + "↓";
        if (effFps < f) { f = effFps; r = r.endsWith("↓") ? r : r + "↓"; }
        String fmt = prefs.heif && Build.VERSION.SDK_INT >= 34 ? "HEIF" : "JPEG";
        String gate = prefs.fullGate ? "片门" : "";
        String fl = new String[]{"", "⚡自动", "⚡开"}[prefs.flash], tm = prefs.timerSec > 0 ? "⏱" + prefs.timerSec + "s" : "";
        // 依次试：完整 → 紧凑（去空格）→ 缩小字号 → 去掉闪光 / 倒计时 → 去掉格式；总之不能盖住右边的按钮
        String[] cands = {
            join(" · ", r, "" + f, fmt, gate, fl, tm),
            join("·", r, "" + f, fmt, gate, fl, tm),
            join("·", r, "" + f, fmt, gate),
            join("·", r, "" + f, gate),
            join("·", r, "" + f)
        };
        int max = pillMaxPx > 0 ? pillMaxPx : Integer.MAX_VALUE;
        specPill.setPadding(dp(12), 0, dp(12), 0); specPill.setMinWidth(0);
        String pick = cands[cands.length - 1]; float size = 12f;
        outer:
        for (int ci = 0; ci < cands.length; ci++) {
            for (float sz : new float[]{13f, 12f, 11f}) {
                if (ci == 0 && sz < 13f) continue;
                specPill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sz);
                float w = specPill.getPaint().measureText(cands[ci]) + 2 * dp(12);
                if (w <= max) { pick = cands[ci]; size = sz; break outer; }
            }
        }
        specPill.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, size);
        specPill.setEllipsize(android.text.TextUtils.TruncateAt.END);
        specPill.setMaxWidth(max == Integer.MAX_VALUE ? Integer.MAX_VALUE : max);
        specPill.setText(pick);
        specPill.requestLayout();
    }

    private int pillMaxPx;

    private static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String s : parts) { if (s == null || s.isEmpty()) continue; if (b.length() > 0) b.append(sep); b.append(s); }
        return b.toString();
    }

    private void applyShape() { if (gl != null) gl.pipCorner = new float[]{.085f, .2f, 0f}[prefs.pipShape]; btnShape.shapeStyle = prefs.pipShape; btnShape.invalidate(); }

    /** 把前 / 后摄各自的调色写进 GL（每颗摄像头各用各的），并存档；顺便刷新预设名、页码、数值、调色板图标 */
    private void pushGrade() {
        if (gl != null) {
            gl.gradeCam[0] = gradeBack.params(); gl.gradeOnCam[0] = gradeBack.active();
            gl.gradeCam[1] = gradeFront.params(); gl.gradeOnCam[1] = gradeFront.active();
        }
        gradeBack.save(prefs.sp, "g_"); gradeFront.save(prefs.sp, "gf_");
        GradeState sh = palette != null ? palette.shown() : gradeBack;
        if (readout != null) {
            readout.setText("色调 " + sgn(sh.y) + "   颜色 " + sgn(sh.x) + "   调色盘 " + Math.round(sh.str * 100));
            int[] ord = PresetOrder.indices(this);
            int pos = PresetOrder.posOf(ord, sh.preset);
            presetLabel.setText(sh.cur().name + "  (" + (pos + 1) + "/" + ord.length + ")");
            if (dots != null) dots.set(ord.length, pos);
        }
        if (btnPalette != null) { btnPalette.setPad(sh.x, sh.y); syncButtons(); }
    }

    /** 数值显示（和主页面一样：0 显示成「00」，负数带负号） */
    private static String sgn(float v) { int n = Math.round(v * 100); return n == 0 ? "00" : String.valueOf(n); }

    /** 左右滑：按「预设顺序」往后 / 往前一个 */
    private void stepPreset(int dir) {
        int[] ord = PresetOrder.indices(this);
        int pos = PresetOrder.posOf(ord, palette.shown().preset);
        int np = pos + dir;
        if (np < 0 || np >= ord.length) return;            // 首尾断开：到头了不再循环
        palette.selectPreset(ord[np]);
    }

    private void setPalette(boolean on) {
        paletteOpen = on;
        palette.setVisibility(on ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        zoomBar.setVisibility(on ? View.GONE : View.VISIBLE);
        readout.setVisibility(on ? View.VISIBLE : View.GONE);
        presetLabel.setVisibility(on ? View.VISIBLE : View.GONE);
        dots.setVisibility(on ? View.VISIBLE : View.GONE);
        setSpecPanel(false);
        if (on) { clearFocus(); palette.setSingle(!dual, !state.backIsMain); palette.sync(); }
        pushGrade();
    }

    private void toggleLive() {
        prefs.live = !prefs.live; prefs.save(); syncButtons();
        say(prefs.live ? "实况已开启（动态照片 " + liveSec() + " 秒，从按下快门开始，" + (prefs.audio ? "带声音" : "无声音（录像声音开关已关）") + "）" : "实况已关闭");
    }

    private void openSettings() {
        setSpecPanel(false);
        if (overlay != null) return;
        final DualSettingsView v = new DualSettingsView(this, prefs, new DualSettingsView.Host() {
            @Override public void onClose() { closeOverlay(); }
            @Override public void onChanged(boolean reconfigure) {
                state.mirrorFront = prefs.mirror; if (gl != null) gl.borderMode = prefs.pipBorder;
                gridView.setVisibility(prefs.gridLines ? View.VISIBLE : View.GONE);
                syncLevel();
                if (lastGlass != prefs.glass || !lastStyle.equals(DualUi.glassStyle) || lastPanelGlass != DualUi.panelGlass) applyGlass();
                if (prefs.fullRate) {
                    pendingThrottle = false;
                    if (throttled && !recording && !clipping) { throttled = false; effFps = prefs.fps; say("满帧画面运行录制已开启，恢复所选帧率"); restart(); }
                }
                pushGrade();     // 预设顺序可能变了
                syncSpecPill(); syncButtons();
                if (reconfigure) { relayout(); restartWithPrefs(); }
            }
        });
        overlay = v; root.addView(v, new FrameLayout.LayoutParams(-1, -1));
    }

    private void openMore() {
        setSpecPanel(false);
        if (overlay != null) return;
        final DualFunctionsView v = new DualFunctionsView(this, prefs, new Runnable() { @Override public void run() { closeOverlay(); } },
                new Runnable() { @Override public void run() { closeOverlay(); openSettings(); } },
                new DualFunctionsView.Host() {
                    @Override public boolean isDual() { return dual; }
                    @Override public String lastFail() { return lastDualFail; }
                    @Override public void setDual(boolean on) { setDualMode(on); }
                    @Override public void openPalette() { closeOverlay(); setPalette(true); }
                    @Override public void openRatio() { closeOverlay(); setSpecPanel(true); }
                    @Override public int flashMode() { return prefs.flash; }
                    @Override public int timerSec() { return prefs.timerSec; }
                    @Override public int cycleTimer() {
                        int[] seq = {0, 3, 5, 10}; int k = 0; for (int i = 0; i < 4; i++) if (seq[i] == prefs.timerSec) k = i;
                        prefs.timerSec = seq[(k + 1) % 4]; prefs.save(); syncSpecPill(); return prefs.timerSec; }
                    @Override public int cycleFlash() { prefs.flash = (prefs.flash + 1) % 3; prefs.save(); syncSpecPill(); return prefs.flash; }
                });
        overlay = v; root.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.requestApplyInsets();
    }

    private void openAlbum() {
        if (overlay != null) return;
        final DualAlbum a = new DualAlbum(this, new DualAlbum.Host() {
                @Override public void requestDelete(java.util.List<Uri> uris, Runnable done) { askDelete(uris, done); } },
                new Runnable() { @Override public void run() { closeOverlay(); refreshThumb(); } },
                new Runnable() { @Override public void run() { closeOverlay(); } });
        overlay = a; root.addView(a, new FrameLayout.LayoutParams(-1, -1));
    }

    /** 不是本 App 创建的相册项，删除要系统弹确认框（Android 11+） */
    private void askDelete(java.util.List<Uri> uris, Runnable done) {
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.PendingIntent pi = android.provider.MediaStore.createDeleteRequest(getContentResolver(), uris);
                delDone = done;
                startIntentSenderForResult(pi.getIntentSender(), REQ_DEL, null, 0, 0, 0);
                return;
            } catch (Throwable t) { say("无法删除：" + t.getMessage()); }
        } else say("这几项不是澄影创建的，系统不允许在这里删除");
        done.run();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_DEL && delDone != null) { Runnable r = delDone; delDone = null; r.run(); }
    }

    /** 相册入口的缩略图 = 系统相册里最近一张双摄拍的，右下角数字 = 总数（重开 App 也在） */
    private void refreshThumb() {
        new Thread(new Runnable() { @Override public void run() {
            final java.util.ArrayList<DualLibrary.Entry> l = DualLibrary.load(DualCameraActivity.this);
            final Bitmap tb = l.isEmpty() ? null : DualAlbum.thumb(DualCameraActivity.this, l.get(0).uri, l.get(0).video, 200);
            ui.post(new Runnable() { @Override public void run() {
                thumbImg.setImageBitmap(tb);
                thumbCount.setText(String.valueOf(l.size())); thumbCount.setVisibility(l.isEmpty() ? View.GONE : View.VISIBLE);
            } });
        } }).start();
    }

    private void closeOverlay() { if (overlay != null) { root.removeView(overlay); overlay = null; } }

    /** 提示条：取景框左上角。毛玻璃界面开着 = 毛玻璃，关着 = 普通深色面板 */
    private void say(final String t) {
        ui.post(new Runnable() { @Override public void run() {
            toast.setText(t); toast.setVisibility(View.VISIBLE);
            toast.animate().cancel(); toast.setAlpha(1f);
            final int tok = ++toastToken;
            ui.postDelayed(new Runnable() { @Override public void run() {
                if (tok != toastToken) return;
                toast.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() { @Override public void run() {
                    if (tok == toastToken) toast.setVisibility(View.GONE); } }).start();
            } }, 2600);
        } });
    }

    private android.graphics.drawable.Drawable panelBg(float rDp) {
        if (DualUi.glass) {
            return new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                    DualUi.round(0x8C0A0A0A, rDp, this), DualUi.surface(0, rDp, this)});
        }
        return DualUi.round(0xF0161616, rDp, this);
    }
    private void stylePill() { specPill.setBackground(DualUi.surfaceFor(specPill, 0x29FFFFFF, 18, this)); }
    private void styleToast() {
        toast.setBackground(DualUi.glass ? panelBg(13) : DualUi.round(0xD9121212, 13, this));
    }

    /** 毛玻璃界面开关变了：重画所有控件 */
    private void applyGlass() {
        lastGlass = prefs.glass; DualUi.glass = prefs.glass; lastStyle = DualUi.glassStyle; lastPanelGlass = DualUi.panelGlass;
        stylePill(); styleToast();
        DualIcon[] ics = {btnBack, btnLive, btnPalette, btnSettings, btnLayout, btnShape, btnMirror, btnTorch, btnSwap, btnMore};
        for (DualIcon i : ics) if (i != null) i.invalidate();
        root.removeView(specPanel); buildSpecPanel(); relayout();
        palette.restyle();
    }

    /** 规格面板展开 / 收起：毛玻璃开着时是果冻（Q 弹）动画，关着时直接出现 / 消失 */
    private android.animation.ValueAnimator specAx, specAy;
    private void setSpecPanel(final boolean open) {
        if (open == specOpen && (specPanel.getVisibility() == View.VISIBLE) == open) return;
        specOpen = open;
        specPanel.animate().cancel();
        if (specAx != null) { specAx.cancel(); specAx = null; }      // 展开动画没跑完就收起 / 再展开：旧的缩放动画必须停掉，否则它会继续改缩放，面板卡在半途
        if (specAy != null) { specAy.cancel(); specAy = null; }
        if (!DualUi.glass) { specPanel.setScaleX(1f); specPanel.setScaleY(1f); specPanel.setAlpha(1f); specPanel.setVisibility(open ? View.VISIBLE : View.GONE); return; }
        // 缩放支点 = 「帧率画质」胶囊下沿的中点：弹出从它下面冒出来，收起也缩回到它下面（不盖住它）
        specPanel.setPivotX(specPill.getX() + specPill.getWidth() / 2f - specPanel.getX());
        specPanel.setPivotY(specPill.getY() + specPill.getHeight() - 2 * getResources().getDisplayMetrics().density - specPanel.getY());
        if (open) {
            specPanel.setVisibility(View.VISIBLE);
            specPanel.setScaleX(.1f); specPanel.setScaleY(.1f); specPanel.setAlpha(0f);
            specPanel.animate().alpha(1f).setDuration(160).start();
            android.animation.ValueAnimator ax = android.animation.ValueAnimator.ofFloat(.1f, 1f), ay = android.animation.ValueAnimator.ofFloat(.1f, 1f);
            ax.setDuration(520); ax.setInterpolator(new OvershootInterpolator(2.2f));
            ay.setDuration(560); ay.setStartDelay(40); ay.setInterpolator(new OvershootInterpolator(3.2f));
            ax.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(android.animation.ValueAnimator a) { specPanel.setScaleX((Float) a.getAnimatedValue()); } });
            ay.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(android.animation.ValueAnimator a) { specPanel.setScaleY((Float) a.getAnimatedValue()); } });
            specAx = ax; specAy = ay;
            ax.start(); ay.start();
        } else {
            specPanel.animate().scaleX(.1f).scaleY(.1f).alpha(0f).setDuration(260).setInterpolator(new AccelerateInterpolator(1.4f))
                    .withEndAction(new Runnable() { @Override public void run() {
                        if (!specOpen) { specPanel.setVisibility(View.GONE); specPanel.setScaleX(1f); specPanel.setScaleY(1f); specPanel.setAlpha(1f); } } }).start();
        }
    }

    // ---------------------------------------------------------------- 手势：对焦 / 曝光 / 小窗

    private boolean inPip(float x, float y) {
        if (!dual || paletteOpen || state.style != DualState.STYLE_PIP || stageW <= 1) return false;
        float W = stageW, H = stageH;
        float[] r = state.rect(1, W, H);
        return x >= r[0] * W && x <= (r[0] + r[2]) * W && y >= r[1] * H && y <= (r[1] + r[3]) * H;
    }

    private void swapMain() {
        clearFocus();
        if (!dual) {
            // 单摄：翻转 = 前后摄像头切换（要重开摄像头）
            if (recording || clipping) { say("录像 / 实况拍摄中不能切换摄像头"); return; }
            if ((state.backIsMain ? frontId : backId) == null) { say("这台手机没有另一颗摄像头"); return; }
            state.backIsMain = !state.backIsMain; prefs.singleFront = !state.backIsMain; prefs.save();
            if (torchOn) { torchOn = false; try { torchSet(false); } catch (Throwable ignore) {} }
            syncDial(); syncButtons(); restart();
            return;
        }
        state.backIsMain = !state.backIsMain; syncDial();
    }

    private void togglePipSize() {
        final float from = state.pipW, to = from < .45f ? .55f : .32f;
        prefs.pipW = to; prefs.save();
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(from, to);
        a.setDuration(220); a.setInterpolator(new OvershootInterpolator(1.2f));
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(android.animation.ValueAnimator v) {
            state.pipW = (Float) v.getAnimatedValue(); } });
        a.start();
    }

    private void tapAt(float x, float y) {
        if (paletteOpen) return;
        if (inPip(x, y)) { swapMain(); return; }
        if (focusView.isLocked()) { clearFocus(); return; }          // 锁定后再点一下解除
        focusAt(x, y, false);
    }

    // ---- 小窗拖动：按设置的帧率（60 / 120）重画取景。摄像头只有 30 / 60 帧，不重画的话小窗只能跟着摄像头一格一格地动
    private volatile boolean pipDragging;
    private volatile long lastDrawNs;
    private long dragGapNs;
    private final java.util.concurrent.atomic.AtomicBoolean dragPending = new java.util.concurrent.atomic.AtomicBoolean();
    private final Runnable dragRedraw = new Runnable() { @Override public void run() {
        dragPending.set(false);
        if (gl == null || finished || !pipDragging) return;
        if (System.nanoTime() - lastDrawNs < dragGapNs) return;     // 摄像头刚来了新画面，已经画过了
        try { gl.drawPreview(); lastDrawNs = System.nanoTime(); } catch (Throwable ignore) {}
    } };
    private final Choreographer.FrameCallback dragTick = new Choreographer.FrameCallback() { @Override public void doFrame(long frameTimeNanos) {
        if (!pipDragging) return;
        Choreographer.getInstance().postFrameCallback(this);
        if (glH != null && dragPending.compareAndSet(false, true)) glH.post(dragRedraw);
    } };

    private void startPipDrag() {
        int fps = new SettingsStore(prefs).pipDragFps();
        if (fps <= 30 || !dual || state.style != DualState.STYLE_PIP || pipDragging) return;
        dragGapNs = (long) (1e9 / fps * 0.8);
        pipDragging = true;
        try { WindowManager.LayoutParams a = getWindow().getAttributes(); a.preferredRefreshRate = fps; getWindow().setAttributes(a); } catch (Throwable ignore) {}
        Choreographer.getInstance().postFrameCallback(dragTick);
    }

    private void stopPipDrag() {
        if (!pipDragging) return;
        pipDragging = false;
        Choreographer.getInstance().removeFrameCallback(dragTick);
        try { WindowManager.LayoutParams a = getWindow().getAttributes(); a.preferredRefreshRate = 0f; getWindow().setAttributes(a); } catch (Throwable ignore) {}
    }

    private void dragBy(float mx, float my) {
        if (gDownPip) {
            float W = stageW, H = stageH, w = Math.round(W * state.pipW), h = Math.round(w * 4f / 3f), m = Math.round(W * 0.04f);
            state.pipX = Math.min(1f, Math.max(0f, state.pipX + mx / Math.max(1f, W - w - 2 * m)));
            state.pipY = Math.min(1f, Math.max(0f, state.pipY + my / Math.max(1f, H - h - 2 * m)));
        } else if (!paletteOpen && focusView.isShown2()) {
            focusView.setT(focusView.t() - my / dp(200)); focusView.keepAlive();
            applyEv(focusCam, focusView.t());
            rearmRelease(focusCam);
        }
    }

    /** 点击点（取景框坐标）→ 传感器活动区域里的测光矩形。步骤：取景框 → 该画面的矩形 → 去掉封面裁切 → 按传感器方向旋转 → 流画面在活动区域里的位置 → 除以倍数 */
    private MeteringRectangle[] region(int cam, float nx, float ny, float viewAsp) {
        if (maxAf[cam] <= 0 && maxAe[cam] <= 0) return null;
        Rect a = activeArr[cam];
        if (a == null) return null;
        int rot = senOri[cam];
        float u = nx, v = ny;
        if (cam == 1 && state.mirrorFront) u = 1f - u;
        boolean sw = rot == 90 || rot == 270;
        float iw = sw ? streamH[cam] : streamW[cam], ih = sw ? streamW[cam] : streamH[cam], ia = iw / ih;
        if (viewAsp > ia) v = .5f + (v - .5f) * (ia / viewAsp); else u = .5f + (u - .5f) * (viewAsp / ia);
        float su, sv;
        switch (rot) {
            case 90: su = v; sv = 1f - u; break;
            case 180: su = 1f - u; sv = 1f - v; break;
            case 270: su = 1f - v; sv = u; break;
            default: su = u; sv = v;
        }
        float aw = a.width(), ah = a.height(), sa = (float) streamW[cam] / Math.max(1, streamH[cam]), cw, ch;
        if (sa > aw / ah) { cw = aw; ch = aw / sa; } else { ch = ah; cw = ah * sa; }
        float z = Math.max(1f, reqZoom(cam)); cw /= z; ch /= z;
        float cx = a.left + aw / 2f + (su - .5f) * cw, cy = a.top + ah / 2f + (sv - .5f) * ch;
        int half = Math.max(8, Math.round(Math.min(aw, ah) * .08f));
        int l = Math.max(a.left, Math.min(a.right - 2 * half - 1, Math.round(cx) - half));
        int t = Math.max(a.top, Math.min(a.bottom - 2 * half - 1, Math.round(cy) - half));
        return new MeteringRectangle[]{new MeteringRectangle(l, t, 2 * half, 2 * half, MeteringRectangle.METERING_WEIGHT_MAX)};
    }

    private void focusAt(float x, float y, boolean lock) {
        if (paletteOpen || stageW <= 1 || stageH <= 1) return;
        float W = stageW, H = stageH;
        int cam; float[] r;
        if (state.style == DualState.STYLE_SPLIT && y >= H / 2f) { cam = 1 - mainCam(); r = state.rect(1, W, H); }
        else { cam = mainCam(); r = state.rect(0, W, H); }
        float nx = Math.max(0f, Math.min(1f, (x - r[0] * W) / Math.max(1f, r[2] * W)));
        float ny = Math.max(0f, Math.min(1f, (y - r[1] * H) / Math.max(1f, r[3] * H)));
        clearFocusState(1 - cam);
        focusCam = cam;
        focusRegion[cam] = region(cam, nx, ny, (r[2] * W) / Math.max(1f, r[3] * H));
        afLock[cam] = aeLock[cam] = lock; evIdx[cam] = 0; evDragging[cam] = false;
        focusView.show(x, y, stageW, stageH, lock);
        pushReq(cam); triggerAf(cam);
        if (lock) say("AE / AF 已锁定（再点一下解除）"); else rearmRelease(cam);
    }

    /** 没锁定时：框消失的同时让对焦回到连续自动（取消触发 + 去掉测光区域；曝光补偿保持到下次点击） */
    private void rearmRelease(final int cam) {
        final int tok = ++focusToken;
        if (afLock[cam]) return;
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (tok != focusToken || afLock[cam]) return;
            focusRegion[cam] = null; cancelAf(cam); pushReq(cam);
        } }, 2800);
    }

    private void clearFocusState(int cam) {
        focusRegion[cam] = null; afLock[cam] = aeLock[cam] = false; evIdx[cam] = 0; evDragging[cam] = false;
    }

    /** 清掉所有对焦 / 锁定 / 曝光补偿（切换主画面、切布局、重启相机、打开调色板时） */
    private void clearFocus() {
        focusToken++; relockToken++;
        if (focusView != null) focusView.hideNow();
        for (int c = 0; c < 2; c++) {
            boolean had = focusRegion[c] != null || aeLock[c] || afLock[c] || evIdx[c] != 0;
            clearFocusState(c);
            if (had) { cancelAf(c); pushReq(c); }
        }
    }

    private void applyEv(int cam, float t) {
        if (evLo[cam] == 0 && evHi[cam] == 0) return;
        float s = (t - .5f) * 2f;
        evIdx[cam] = Math.round(s >= 0 ? s * evHi[cam] : s * -evLo[cam]);
        if (aeLock[cam]) {                      // 锁定中拖曝光：先解锁让它生效，松手约 0.9 秒后重新锁住
            evDragging[cam] = true;
            final int tok = ++relockToken, c = cam;
            ui.postDelayed(new Runnable() { @Override public void run() { if (tok == relockToken) { evDragging[c] = false; pushReq(c); } } }, 900);
        }
        pushReq(cam);
    }

    private void triggerAf(final int cam) {
        if (camH == null) return;
        camH.post(new Runnable() { @Override public void run() {
            try {
                CaptureRequest.Builder rb = reqB[cam]; CameraCaptureSession cs = ses[cam];
                if (rb == null || cs == null) return;
                rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                cs.capture(rb.build(), null, camH);
                rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            } catch (Throwable ignore) {}
        } });
    }

    private void cancelAf(final int cam) {
        if (camH == null) return;
        camH.post(new Runnable() { @Override public void run() {
            try {
                CaptureRequest.Builder rb = reqB[cam]; CameraCaptureSession cs = ses[cam];
                if (rb == null || cs == null) return;
                rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
                cs.capture(rb.build(), null, camH);
                rb.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            } catch (Throwable ignore) {}
        } });
    }

    // ---------------------------------------------------------------- 发热 / 满帧

    private void watchThermal() {
        if (Build.VERSION.SDK_INT < 29) return;
        try {
            thermalListener = new PowerManager.OnThermalStatusChangedListener() {
                @Override public void onThermalStatusChanged(final int status) { ui.post(new Runnable() { @Override public void run() { onThermal(status); } }); }
            };
            ((PowerManager) getSystemService(POWER_SERVICE)).addThermalStatusListener(thermalListener);
        } catch (Throwable ignore) { thermalListener = null; }
    }

    /** 过热（严重及以上）且帧率 > 30：满帧画面运行录制没开就自动降到 30 帧；正在录像 / 拍实况就等结束后再降，不打断 */
    private void onThermal(int status) {
        if (Build.VERSION.SDK_INT < 29 || finished || prefs.fullRate) return;
        if (status < PowerManager.THERMAL_STATUS_SEVERE || effFps <= 30) return;
        if (recording || clipping) { pendingThrottle = true; return; }
        throttleNow();
    }

    private void throttleNow() {
        pendingThrottle = false; throttled = true; effFps = 30; syncSpecPill();
        say("手机过热，已自动降到 30 帧保护手机（想一直满帧：更多设置 → 满帧画面运行录制）");
        restart();
    }

    // ---------------------------------------------------------------- 摄像头 / 规格

    /** 读一颗摄像头的特性（倍数范围 / 传感器方向 / 曝光补偿 / 测光区域上限）。initZoom = 第一次读，顺便把倍数放到 1× */
    private void loadChars(int cam, boolean initZoom) {
        if ((cam == 0 ? backId : frontId) == null) return;
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
            hwMax[cam] = hi;
            float setMax = (float) Math.max(30, new SettingsStore(prefs).zoomRange());
            zoomMax[cam] = Math.max(zoomMin[cam], cam == 0 ? setMax : Math.min(setMax, 10f));
            if (initZoom) zoomCur[cam] = Math.max(zoomMin[cam], Math.min(zoomMax[cam], 1f));
            Integer so = c.get(CameraCharacteristics.SENSOR_ORIENTATION);
            senOri[cam] = so == null ? (cam == 0 ? 90 : 270) : so;
            Range<Integer> er = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            if (er != null) { evLo[cam] = er.getLower(); evHi[cam] = er.getUpper(); }
            android.util.Rational es = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP);
            evStep[cam] = es == null ? 1f : es.floatValue();
            Integer ma = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF), me = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
            maxAf[cam] = ma == null ? 0 : ma; maxAe[cam] = me == null ? 0 : me;
        } catch (Throwable ignore) {}
    }

    // ---------------------------------------------------------------- 多镜头（超广角 / 长焦）

    private String mainBackId, uwId, teleId;       // 主镜头（广角）/ 超广角 / 长焦（找不到就是 null）
    private float uwRatio = .5f, teleRatio = 3f;   // 相对主镜头的等效倍数
    private float extMin = 1f, extMax = 1f;        // 单摄后置时转盘的范围（含超广角 / 长焦）
    private int curLens;                           // 0 主 / -1 超广角 / 1 长焦
    private volatile long lensBusyUntil;
    String lensReport = "";

    /** 等效 35mm 焦距；读不到返回 -1 */
    private float eqFocal(CameraCharacteristics c) {
        try {
            float[] fl = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            android.util.SizeF sz = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (fl == null || fl.length == 0 || sz == null) return -1f;
            double diag = Math.hypot(sz.getWidth(), sz.getHeight());
            return diag <= 0 ? -1f : (float) (fl[0] * 43.27 / diag);
        } catch (Throwable t) { return -1f; }
    }

    /**
     * 找超广角 / 长焦：枚举所有后置镜头，按等效焦距分类（比主镜头短一大截 = 超广角；长 1.5 倍以上 = 长焦）。
     * 画素太低的（微距 / 景深 2MP）和黑白镜头不要。手机把多镜头合成一颗「逻辑摄像头」、倍数范围已经含 <1× 的，系统自己会换镜头，这里不用管。
     * 只有一颗后置镜头的手机（比如 OPPO A57）找不到就什么都不加，行为和以前一样。
     */
    private void classifyLenses() {
        uwId = null; teleId = null; extMin = zoomMin[0]; extMax = zoomMax[0];
        StringBuilder rep = new StringBuilder();
        try {
            if (mainBackId == null) return;
            if (zoomMin[0] < 0.98f) { lensReport = "后置是合成摄像头，系统自动切换超广角 / 长焦（倍数 " + zoomMin[0] + "–" + zoomMax[0] + "）"; return; }
            float mainEq = eqFocal(cm.getCameraCharacteristics(mainBackId));
            if (mainEq <= 0) { lensReport = "读不到主镜头焦距，不启用多镜头"; return; }
            long bestUw = 0, bestTele = 0;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer f = c.get(CameraCharacteristics.LENS_FACING);
                if (f == null || f != CameraCharacteristics.LENS_FACING_BACK) continue;
                float eq = eqFocal(c);
                Rect ar = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
                long px = ar == null ? 0 : (long) ar.width() * ar.height();
                int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean ok = false, mono = false;
                if (caps != null) for (int k : caps) { if (k == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) ok = true; if (k == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME) mono = true; }
                rep.append(id).append(": 等效").append(Math.round(eq)).append("mm ").append(px / 1000000f).append("MP").append(ok && !mono ? "" : "（不可用）").append("；");
                if (id.equals(mainBackId) || !ok || mono || eq <= 0 || px < 2500000L) continue;
                float r = eq / mainEq;
                if (r >= .2f && r <= .85f && px > bestUw) { bestUw = px; uwId = id; uwRatio = Math.round(r * 10f) / 10f; }
                else if (r >= 1.5f && px > bestTele) { bestTele = px; teleId = id; teleRatio = Math.round(r * 2f) / 2f; }
            }
        } catch (Throwable t) { rep.append("枚举出错：").append(t); }
        SettingsStore ss = new SettingsStore(prefs);
        String uws = ss.get("uwScale"), tls = ss.get("teleScale");
        if ("off".equals(uws)) uwId = null;                                   // 设置「超广角倍数」= 关闭
        else if (uwId != null && !uws.isEmpty()) { try { uwRatio = Math.max(uwRatio, Float.parseFloat(uws)); } catch (Exception ignore) {} }
        if (teleId != null && !"auto".equals(tls) && !tls.isEmpty()) { try { teleRatio = Float.parseFloat(tls); } catch (Exception ignore) {} }
        float zcap = Math.max(30, ss.zoomRange());
        if (uwId != null) extMin = Math.min(extMin, uwRatio);
        if (teleId != null) extMax = Math.min(zcap, Math.max(extMax, teleRatio * 4f));
        lensReport = (uwId != null ? "超广角 " + uwId + "（" + uwRatio + "×）" : "无超广角") + "，" + (teleId != null ? "长焦 " + teleId + "（" + teleRatio + "×）" : "无长焦") + "。后置镜头：" + rep;
        AppLog.info("镜头", lensReport);
    }

    private boolean lensExtOn() { return !dual && state.backIsMain && (uwId != null || teleId != null); }

    /** 这颗摄像头请求里该写的倍数：超广角 / 长焦镜头自己的倍数 = 总倍数 ÷ 它相对主镜头的倍率（主镜头不变） */
    private float reqZoom(int cam) {
        float z = zoomCur[cam];
        if (cam != 0) return z;
        float r = curLens < 0 ? Math.max(1f, z / uwRatio) : curLens > 0 ? Math.max(1f, z / teleRatio) : z;
        if (lensExtOn()) r = Math.max(zoomMin[0], Math.min(zoomMax[0], r));       // 别超出当前这颗镜头自己的倍数范围
        return r;
    }

    /** 转盘倍数变了：该换镜头就换（单摄后置才换；双摄 / 录像中不换） */
    private void maybeSwitchLens(float z) {
        if (!lensExtOn()) return;
        // 合成摄像头（系统自己无缝切换超广角 / 长焦）：这里不主动换，免得和系统冲突
        if (zoomMin[0] < 0.98f) return;
        // 阈值放宽：超广角 z < 0.95（以前 0.995）；长焦 z >= 倍率 × 0.9（以前 × 0.995），减少变焦时来回抖动
        int want = (uwId != null && z < 0.95f) ? -1 : (teleId != null && z >= teleRatio * 0.9f) ? 1 : 0;
        if (want == curLens) return;
        if (recording || clipping) return;
        if (System.currentTimeMillis() < lensBusyUntil) return;
        AppLog.info("镜头", "缩放 " + (Math.round(z * 100) / 100f) + "×，换到" + (want < 0 ? "超广角" : want > 0 ? "长焦" : "主镜头"));
        switchLens(want);
    }

    private void switchLens(int want) {
        String id = want < 0 ? uwId : want > 0 ? teleId : mainBackId;
        if (id == null) return;
        curLens = want; backId = id;
        lensBusyUntil = System.currentTimeMillis() + 900;
        AppLog.info("镜头", "切换到" + (want < 0 ? "超广角" : want > 0 ? "长焦" : "主镜头") + "（id " + id + "）");
        loadChars(0, false);
        restart();
    }

    /** 回到主镜头（双摄 / 切换前后时用） */
    private void forceMainLens() {
        if (curLens == 0 || mainBackId == null) return;
        curLens = 0; backId = mainBackId; loadChars(0, false);
    }

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
        if (backId == null && frontId == null) return false;
        mainBackId = backId;
        for (int cam = 0; cam < 2; cam++) loadChars(cam, true);
        classifyLenses();
        return true;
    }

    private int mainCam() { return state.backIsMain ? 0 : 1; }

    /** 这次要开的摄像头：双摄 = 前后都开；单摄 = 只开主画面那一颗 */
    private int[] usedCams() { return dual ? new int[]{0, 1} : new int[]{mainCam()}; }
    private int needCount() { return dual ? 2 : 1; }
    private boolean zeroCam() { return dual ? (frameCnt[0] == 0 || frameCnt[1] == 0) : frameCnt[mainCam()] == 0; }

    /** 这台设备跑不了所选的摄像头配置：双摄 → 自动退回单摄并提示；单摄 → 报错 */
    private void unsupported(final String msg) {
        if (!dual) { fail("unsupported", msg); return; }
        dual = false; prefs.dualOn = false; prefs.save();
        state.backIsMain = !prefs.singleFront;
        lastDualFail = msg;
        AppLog.warn("双摄", "双摄不可用，退回单摄：" + msg);
        final String shortMsg = msg.indexOf("［诊断］") > 0 ? msg.substring(0, msg.indexOf("［诊断］")) : msg;
        ui.post(new Runnable() { @Override public void run() {
            degradeCount = 0; reqStage = 0; effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res); syncSpecPill();
            if (gl != null) gl.single = true;
            syncButtons(); syncDial(); restart();
            say("双摄不可使用，已退回单摄：" + shortMsg + "（详细原因在「功能」面板里）");
        } });
    }

    /** 功能面板里的双摄开关（界面线程） */
    private void setDualMode(boolean on) {
        if (on == dual) return;
        if (recording || clipping) { say("录像 / 实况拍摄中不能切换"); return; }
        dual = on; prefs.dualOn = on; prefs.save(); lastDualFail = "";
        if (on) { forceMainLens(); zoomCur[0] = Math.max(zoomMin[0], Math.min(zoomMax[0], Math.max(1f, zoomCur[0]))); }     // 双摄时后置只用主镜头
        state.backIsMain = on ? true : !prefs.singleFront;
        if (gl != null) gl.single = !on;
        if (torchOn) { torchOn = false; try { torchSet(false); } catch (Throwable ignore) {} }
        degradeCount = 0; reqStage = 0; effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res); syncSpecPill();
        clearFocusAll();
        syncButtons(); syncDial(); restart();
        say(on ? "正在开启双摄…（这台手机不支持时会自动退回单摄）" : "已切换到单摄");
    }

    private void syncDial() {
        if (zoomBar == null) return;
        int c = mainCam();
        boolean ext = c == 0 && lensExtOn();             // 单摄后置：转盘范围含超广角 / 长焦
        float lo = ext ? extMin : zoomMin[c], hi = ext ? extMax : zoomMax[c];
        { int sp = new SettingsStore(prefs).intValue("zoomSpeed"); ZoomBar.spdMax = new double[]{10, 15, 20, 25, 30}[Math.max(0, Math.min(4, sp))]; }
        if (c == 0 && lo < 0.98f) { String uws = new SettingsStore(prefs).get("uwScale"); if ("off".equals(uws)) lo = 1f; else { try { lo = Math.max(lo, Float.parseFloat(uws)); } catch (Exception ignore) {} } }
        zoomBar.setRange(lo, hi, lo < 0.98f);
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
        int rc = dual ? 0 : mainCam();
        int streamLong = Math.max(streamW[rc], streamH[rc]);
        int lg = Math.min(tierLong, Math.max(480, streamLong));
        if (asp > 0.5625f && !prefs.fullGate) lg = Math.min(lg, Math.max(480, Math.round(Math.min(streamW[rc], streamH[rc]) / asp)));
        recH = lg & ~1; recW = Math.round(lg * asp) & ~1;
    }

    private void clearFocusAll() {
        focusToken++; relockToken++;
        if (focusView != null) focusView.hideNow();
        for (int c = 0; c < 2; c++) clearFocusState(c);
    }

    private void restartWithPrefs() {
        if (recording || clipping) { say("录像中不能改规格"); return; }
        throttled = false; pendingThrottle = false;
        effFps = prefs.fps; effTier = DualPlan.startTier(prefs.res); degradeCount = 0;
        syncSpecPill();
        restart();
    }

    private void restart() {
        clearFocusAll();
        spinnerView.setVisibility(View.VISIBLE);
        closeCameras();
        ui.postDelayed(new Runnable() { @Override public void run() { tryOpen(); } }, 350);
    }

    /** 带宽 / 配置撑不住：先把帧率降到 30，再逐级降分辨率 */
    private void degrade(String why) {
        if (finished || failedReported) return;
        degradeCount++;
        if (degradeCount > 6) { unsupported(dual ? "这台设备配置不出前后摄像头同时输出（" + why + "）" : "这颗摄像头配置不出可用的规格（" + why + "）"); return; }
        if (effFps > 30) effFps = 30;
        else if (effTier < 3) effTier++;
        else { unsupported(dual ? "这台设备配置不出前后摄像头同时输出（" + why + "）" : "这颗摄像头配置不出可用的规格（" + why + "）"); return; }
        AppLog.warn("降档", "原因：" + why + "；现在 " + effTier + " 档@" + effFps);
        say(dual ? "同时开前后摄像头时硬件撑不住所选规格，已自动降档" : "摄像头撑不住所选规格，已自动降档");
        syncSpecPill();
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
                gl.single = !dual;
                for (int cam = 0; cam < 2; cam++) {
                    String cid = cam == 0 ? backId : frontId;
                    CameraCharacteristics c = cid == null ? null : cm.getCameraCharacteristics(cid);
                    Integer so = c == null ? null : c.get(CameraCharacteristics.SENSOR_ORIENTATION);
                    gl.sensor[cam] = so == null ? (cam == 0 ? 90 : 270) : so;
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
        frameCnt[0] = frameCnt[1] = capCnt[0] = capCnt[1] = capFail[0] = capFail[1] = 0;
        wdLastDrawn = drawnCnt; wdSince = System.currentTimeMillis();
        startWatchdog();
        gl.single = !dual;
        final int[] used = usedCams();
        int[][] pl = new int[2][];
        for (int cam : used) {
            pl[cam] = plan(cam);
            streamW[cam] = pl[cam][0]; streamH[cam] = pl[cam][1]; camFps[cam] = pl[cam][2];
            fpsRange[cam][0] = pl[cam][3]; fpsRange[cam][1] = pl[cam][4];
            gl.setBufferSize(cam, pl[cam][0], pl[cam][1]);
        }
        if (dual) infoText = "后置 " + pl[0][0] + "×" + pl[0][1] + "@" + pl[0][2] + "  前置 " + pl[1][0] + "×" + pl[1][1] + "@" + pl[1][2];
        else { int c0 = used[0]; infoText = (c0 == 0 ? "后置 " : "前置 ") + pl[c0][0] + "×" + pl[c0][1] + "@" + pl[c0][2]; }
        applyOutputSize();
        for (int cam : used) {
            final int which = cam;
            try {
                cm.openCamera(cam == 0 ? backId : frontId, new CameraDevice.StateCallback() {
                    @Override public void onOpened(CameraDevice d) {
                        if (myGen != gen) { d.close(); return; }
                        dev[which] = d; startSession(which, myGen);
                    }
                    @Override public void onDisconnected(CameraDevice d) {
                        d.close();
                        if (myGen != gen) return;
                        dev[which] = null;
                        if (finished) return;
                        // 还没出画面就被系统断开 = 这台手机不让前后摄像头同时开（后开的把先开的顶掉）；已经在出画面才断开 = 被别的应用占用
                        if (zeroCam()) { if (dual) unsupported("系统在开前后摄像头时断开了" + (which == 0 ? "后置" : "前置") + "摄像头（这台手机不允许同时开前后摄像头）。" + diag()); else fail("error", "摄像头被系统断开。" + diag()); }
                        else say("摄像头被占用或断开");
                    }
                    @Override public void onError(CameraDevice d, int err) {
                        d.close(); if (myGen != gen) return; dev[which] = null;
                        boolean busy = err == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE || err == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE;
                        String em = (busy ? (dual ? "系统不允许前后摄像头同时工作（硬件限制）" : "摄像头正被别的应用占用") : "摄像头出错（代码 " + err + "）") + "。" + diag();
                        if (busy) unsupported(em); else fail("error", em);
                    }
                }, camH);
            } catch (Exception e) {
                if (e instanceof CameraAccessException) unsupported("打开摄像头失败：" + e.getMessage()); else fail("error", "打开摄像头失败：" + e.getMessage());
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
                    boolean minimal = false;      // 完整请求建不出来时置 true
                    try {
                        CaptureRequest.Builder rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        rb.addTarget(s);
                        reqB[cam] = rb;
                        fillReq(cam, rb);
                        cs.setRepeatingRequest(rb.build(), capCallback(cam), camH);
                    } catch (Exception e) {
                        // 完整请求建不出来（某些定制 ROM 不认某个字段）：先用「只留目标 + 连续对焦」的最简请求把画面拉起来
                        try {
                            CaptureRequest.Builder mb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                            mb.addTarget(s);
                            safeSet(mb, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            reqB[cam] = mb;
                            cs.setRepeatingRequest(mb.build(), capCallback(cam), camH);
                            AppLog.warn("相机", "完整预览请求失败，已改用最简请求：" + e);
                            minimal = true;
                        } catch (Exception e2) { fail("error", "开启预览失败：" + e2.getMessage() + "。" + diag()); return; }
                    }
                    final boolean usedMinimal = minimal;
                    synchronized (DualCameraActivity.this) { openedCount++; }
                    if (openedCount >= needCount()) ui.post(new Runnable() { @Override public void run() {
                        spinnerView.setVisibility(View.GONE);
                        String want = (prefs.res == 2160 ? "4K" : prefs.res + "p") + "@" + prefs.fps;
                        say((camFps[0] < prefs.fps || effFps < prefs.fps || effTier > DualPlan.startTier(prefs.res) ? "实际规格低于所选（" + want + "）：" : "实际规格：") + infoText
                                + (usedMinimal ? "（这台手机不接受完整相机参数，已用基础参数打开：点按对焦 / 曝光补偿 / 手电筒可能受限）" : ""));
                    } });
                }
                @Override public void onConfigureFailed(CameraCaptureSession cs) {
                    if (myGen != gen) return;
                    final String dg = diag();
                    ui.post(new Runnable() { @Override public void run() { degrade("会话配置失败 " + dg); } });
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

    /**
     * 把这颗摄像头当前的所有状态写进预览请求（相机线程调用）。
     * 规则（见 vivo_crash_analysis / guangjiao_adaptation_analysis）：
     *  - 能写显式值的一律写显式值，不写 null（定制 ROM 对 set(key, null) 没有保护，会直接 NPE）；
     *  - 每一项单独 safeSet，单点失败不拖垮整帧预览；
     *  - 测光区域 / 曝光补偿 / AE 锁「用到才写」：不写 = 系统默认。这些请求每次都在新建的构建器上写（见 pushReq），不需要用 null 清掉。
     */
    private void fillReq(int cam, CaptureRequest.Builder rb) {
        safeSet(rb, CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 闪光模式永远显式写：开 = TORCH，关 = OFF（不再写 null）
        if (cam == 0) safeSet(rb, CaptureRequest.FLASH_MODE, (torchByReq && torchOn) ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
        if (reqStage < 2) safeSet(rb, CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(fpsRange[cam][0], fpsRange[cam][1]));
        float z = reqZoom(cam);
        // 注意：Camera2 规定设置了 CONTROL_ZOOM_RATIO 后 SCALER_CROP_REGION 会被忽略，所以两者不能混用：
        //  - 没超过硬件上限：用 CONTROL_ZOOM_RATIO（画质最好）；
        //  - 超过硬件上限（设置里的变焦扩充）：不写 CONTROL_ZOOM_RATIO，整段倍数都用 SCALER_CROP_REGION 完成。
        boolean overHw = Build.VERSION.SDK_INT >= 30 && z > hwMax[cam] + 0.0001f;
        if (Build.VERSION.SDK_INT >= 30 && !overHw) safeSet(rb, CaptureRequest.CONTROL_ZOOM_RATIO, Math.max(zoomMin[cam], z));
        if (overHw || Build.VERSION.SDK_INT < 30) {
            Rect a = activeArr[cam];
            if (a != null) {
                float zz = Math.max(1f, z);
                int w = Math.round(a.width() / zz), h = Math.round(a.height() / zz);
                int l = a.left + (a.width() - w) / 2, t = a.top + (a.height() - h) / 2;
                safeSet(rb, CaptureRequest.SCALER_CROP_REGION, new Rect(l, t, l + w, t + h));
            }
        }
        // 测光区域 / 曝光补偿 / AE 锁：只在真的用到时才写（平时不写，走系统默认）。最简请求（reqStage >= 1）一律不写
        if (reqStage < 1) {
            MeteringRectangle[] mr = focusRegion[cam];
            if (mr != null && maxAf[cam] > 0) safeSet(rb, CaptureRequest.CONTROL_AF_REGIONS, mr);
            if (mr != null && maxAe[cam] > 0) safeSet(rb, CaptureRequest.CONTROL_AE_REGIONS, mr);
            if (evIdx[cam] != 0) safeSet(rb, CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, Integer.valueOf(evIdx[cam]));
            if (aeLock[cam] && !evDragging[cam]) safeSet(rb, CaptureRequest.CONTROL_AE_LOCK, Boolean.TRUE);
        }
    }

    /** 单项写入：任何一项失败都只跳过这一项，不影响整帧预览 */
    private static <T> void safeSet(CaptureRequest.Builder rb, CaptureRequest.Key<T> key, T value) {
        try { rb.set(key, value); } catch (Throwable ignore) { }
    }

    private CameraCaptureSession.CaptureCallback capCallback(final int cam) {
        if (capCb[cam] == null) capCb[cam] = new CameraCaptureSession.CaptureCallback() {
            @Override public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult res) {
                capCnt[cam]++;
                Integer iso = res.get(CaptureResult.SENSOR_SENSITIVITY); Long ex = res.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                if (iso != null) lastIso[cam] = iso;
                if (ex != null) lastExpNs[cam] = ex;
            }
            @Override public void onCaptureFailed(CameraCaptureSession s, CaptureRequest r, CaptureFailure f) { capFail[cam]++; }
        };
        return capCb[cam];
    }

    private String diag() {
        return "［诊断］后置帧 " + frameCnt[0] + " / 前置帧 " + frameCnt[1] + "，请求完成 " + capCnt[0] + "/" + capCnt[1]
                + "，请求失败 " + capFail[0] + "/" + capFail[1] + "，会话 " + (ses[0] != null ? "后✓" : "后✗") + (ses[1] != null ? "前✓" : "前✗")
                + "，请求级别 " + reqStage + "，画面 " + effTier + "档@" + effFps + "，" + infoText + "，" + Build.MANUFACTURER + " " + Build.MODEL + " Android " + Build.VERSION.SDK_INT;
    }

    // ---------------------------------------------------------------- 看门狗：画面不动就自动换更稳妥的方式重来，最后才报「不支持」

    private void startWatchdog() {
        if (wdRunning) return;
        wdRunning = true;
        ui.postDelayed(new Runnable() { @Override public void run() { watchdogTick(); } }, 1000);
    }

    private void watchdogTick() {
        if (finished || failedReported) { wdRunning = false; return; }
        if (!camsOpened) { wdRunning = false; return; }        // 摄像头已经关了（暂停 / 重启中）；下次 tryOpen 再起
        long now = System.currentTimeMillis(), dr = drawnCnt;
        if (dr != wdLastDrawn) { wdLastDrawn = dr; wdSince = now; wdTries = 0; }
        boolean configuring = openedCount < needCount();
        boolean zeroCam = zeroCam();   // 要用的摄像头里有一颗从没出过一帧
        long limit = configuring ? (zeroCam ? 5000 : 7000) : 3000;   // 建会话比出帧慢，多给点时间
        if (now - wdSince > limit && !recording && !clipping) {
            wdSince = now;
            wdTries++;
            Log.w("LucidaDual", "画面停了 " + diag());
            if (wdTries == 1) {
                reqStage = 1; say("画面没有刷新，改用最简请求重试…");
                restart();
            } else if (wdTries == 2) {
                reqStage = 2; say("还是没有画面，去掉帧率范围再试…");
                restart();
            } else {
                wdRunning = false;
                if (zeroCam) {          // 降档也没用：这颗摄像头从来没出过画面，是系统 / 硬件不允许前后同时工作
                    unsupported(dual ? "这台手机的前后摄像头不能同时出画面（" + (frameCnt[0] == 0 ? "后置" : "前置") + "一帧都没有）。" + diag() : "摄像头一帧画面都没有。" + diag());
                    return;
                }
                degrade("画面一直不动 " + diag());
                return;
            }
        }
        ui.postDelayed(new Runnable() { @Override public void run() { watchdogTick(); } }, 1000);
    }

    /** 状态变了：在相机线程上重新写入并重发重复请求 */
    private void pushReq(final int cam) {
        if (camH == null) return;
        camH.post(new Runnable() { @Override public void run() {
            try {
                CameraCaptureSession cs = ses[cam];
                CameraDevice d = dev[cam];
                if (cs == null || d == null || gl == null) return;
                // 每次都新建一个请求构建器再写：之前写过的字段不会残留，要「清掉」的字段直接不写，不需要 set(key, null)
                CaptureRequest.Builder rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                rb.addTarget(gl.surface(cam));
                fillReq(cam, rb);
                reqB[cam] = rb;
                cs.setRepeatingRequest(rb.build(), capCallback(cam), camH);
            } catch (Throwable ignore) {}
        } });
    }

    /** 倍数变了（转盘）：写进该摄像头的请求（Android 11+ 用缩放比，可以 <1 切超广角；更老的系统用裁切区域） */
    private void applyZoom(final int cam) { pushReq(cam); }

    /** 手电筒是不是靠「拍摄请求里的闪光模式」点亮的（系统的手电筒接口打不开时的退路，个别机型相机开着时只能这样） */
    private boolean torchByReq;

    /** 开 / 关后置手电筒。先走系统手电筒接口；不行（相机被占用）就把 FLASH_MODE_TORCH 写进后置的取景请求。失败抛异常 */
    private void torchSet(boolean on) throws Exception {
        String id = mainBackId != null ? mainBackId : backId;
        try {
            cm.setTorchMode(id, on);
            if (torchByReq) { torchByReq = false; pushReq(0); }
            return;
        } catch (Throwable first) {
            Boolean fl = null;
            try { fl = cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE); } catch (Throwable ignore) {}
            if (fl == null || !fl) throw new Exception("没有闪光灯：" + first);
            if (!on && !torchByReq) return;               // 本来就没亮
            torchByReq = on; pushReq(0);
            AppLog.info("手电筒", "系统接口打不开（" + first + "），改用拍摄请求点亮");
        }
    }

    private void torch() {
        torchOn = !torchOn;
        if (!dual && !state.backIsMain) { torchOn = false; say("前置没有手电筒"); syncButtons(); return; }
        try { torchSet(torchOn); say(torchOn ? "手电筒：开" : "手电筒：关"); }
        catch (Throwable e) { torchOn = false; say(dual ? "手电筒不可用（双摄时系统常占用闪光灯）" : "这台手机的手电筒暂时打不开"); AppLog.warn("手电筒", "打开失败：" + e); }
        syncButtons();
    }

    // ---------------------------------------------------------------- 渲染（GL 线程）

    private void onFrame(int cam) {
        if (gl == null || finished) return;
        frameCnt[cam]++;
        boolean both = gl.update(cam);
        if (!both || cam != mainCam()) return;        // 以主画面那颗摄像头的帧率出画面 / 编码
        if (lastBackMain != state.backIsMain) { lastBackMain = state.backIsMain; ui.post(new Runnable() { @Override public void run() { syncDial(); } }); }
        // 帧率 > 35 时，没开「满帧画面运行录制」就只每隔一帧刷新一次屏幕（省电 / 少发热）；录制仍然每帧都编码
        if (prefs.fullRate || pipDragging || camFps[cam] <= 35 || (++previewN & 1) == 0) { gl.drawPreview(); drawnCnt++; lastDrawNs = System.nanoTime(); }
        if (recording && recorder != null && gl.hasEncoder()) gl.drawEncoder(System.nanoTime() - recStartNs, recW, recH);
        if (gl.hasLive()) gl.drawLive(recW & ~1, recH & ~1);          // 实况短片（可同时好几路）
    }

    // ---------------------------------------------------------------- 拍照 / 录像

    private File newFile(String ext) {
        return new File(outDir(this), "澄影双摄_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + "_" + (100 + new Random().nextInt(900)) + "." + ext);
    }

    private void shutterTapped() {
        if (!glReady || gl == null || flashBusy) return;
        if (counting) { cancelCountdown(true); return; }
        if (prefs.timerSec > 0 && !recording && !recStarting) { startCountdown(prefs.timerSec); return; }
        fireShutter();
    }

    // ---------------------------------------------------------------- 倒计时 / 水平仪

    private DualLevelView levelView;
    private TextView countView;
    private boolean counting;
    private int countLeft;
    private android.hardware.SensorManager sensors;
    private final android.hardware.SensorEventListener levelListener = new android.hardware.SensorEventListener() {
        @Override public void onSensorChanged(android.hardware.SensorEvent e) {
            float g = 9.80665f;      // Android 传感器给的是「向上的加速度」，和 iOS 的重力方向相反，取反后两端一致
            if (levelView != null) levelView.setGravity(-e.values[0] / g, -e.values[1] / g, -e.values[2] / g);
        }
        @Override public void onAccuracyChanged(android.hardware.Sensor s, int a) {}
    };
    private boolean levelListening;

    /** 水平仪开着才读重力传感器 */
    private void syncLevel() {
        if (levelView == null) return;
        levelView.setVisibility(prefs.level ? View.VISIBLE : View.GONE);
        if (prefs.level && !levelListening) {
            if (sensors == null) sensors = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
            android.hardware.Sensor s = sensors == null ? null : sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY);
            if (s == null && sensors != null) s = sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER);
            if (s == null) { say("这台手机没有重力传感器，水平仪不可用"); return; }
            levelListening = sensors.registerListener(levelListener, s, android.hardware.SensorManager.SENSOR_DELAY_GAME);
        } else if (!prefs.level && levelListening) {
            sensors.unregisterListener(levelListener); levelListening = false;
        }
    }

    private final Runnable countTick = new Runnable() { @Override public void run() {
        if (!counting) return;
        countLeft--;
        if (countLeft > 0) { showCount(); ui.postDelayed(this, 1000); return; }
        counting = false; countView.setVisibility(View.GONE);
        fireShutter();
    } };

    private void showCount() {
        countView.setText(String.valueOf(countLeft)); countView.setVisibility(View.VISIBLE);
        countView.setScaleX(1.35f); countView.setScaleY(1.35f); countView.setAlpha(.3f);
        countView.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start();
        if (prefs.countVibrate) buzz(35);
    }

    /** 短震一下（要 VIBRATE 权限；手机静音/系统关了触感反馈也能震） */
    private void buzz(int ms) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Throwable ignore) {}
    }

    private void startCountdown(int sec) {
        counting = true; countLeft = sec; showCount();
        ui.postDelayed(countTick, 1000);
    }

    private void cancelCountdown(boolean msg) {
        if (!counting) return;
        counting = false; ui.removeCallbacks(countTick); countView.setVisibility(View.GONE);
        if (msg) say("已取消倒计时");
    }

    private void fireShutter() {
        if (!glReady || gl == null || flashBusy) return;
        if (!videoMode) takePhoto();
        else if (recording) stopRecording();
        else if (recStarting) { stopWanted = true; say("录像刚启动，马上停止"); }     // 启动那几百毫秒里点的停止：等启动完立刻停，不再「没反应」
        else if (clipping) say("还有实况在拍 / 合成，等它们完成再录像");
        else startRecording(false, 0);
    }

    // ---------------------------------------------------------------- 闪光灯

    private final int[] lastIso = new int[2];
    private final long[] lastExpNs = new long[2];
    private volatile boolean flashBusy;
    private boolean flashLiveNoted;
    private View screenFlashView;

    /** 现在暗不暗（自动闪光灯用）：自动曝光已经把感光度拉高 / 快门拖慢 */
    private boolean sceneDark(int cam) { return lastIso[cam] >= 800 || lastExpNs[cam] >= 1_000_000_000L / 24; }

    private void screenFlash(boolean on) {
        if (on) {
            if (screenFlashView != null) return;
            WindowManager.LayoutParams a = getWindow().getAttributes(); a.screenBrightness = 1f; getWindow().setAttributes(a);
            screenFlashView = new View(this); screenFlashView.setBackgroundColor(Color.argb(245, 255, 255, 255));
            root.addView(screenFlashView, new FrameLayout.LayoutParams(-1, -1));
        } else {
            if (screenFlashView == null) return;
            root.removeView(screenFlashView); screenFlashView = null;
            WindowManager.LayoutParams a = getWindow().getAttributes(); a.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE; getWindow().setAttributes(a);
        }
    }

    /** 拍照：开了闪光灯（且需要）就先补光，等曝光适应再拍；否则直接拍。后置 = 手电筒补光，前置 = 屏幕补光 */
    private void takePhoto() {
        int mode = prefs.flash;
        if (mode == 0 || torchOn) { doTakePhoto(); return; }
        if (prefs.live) {
            if (!flashLiveNoted) { flashLiveNoted = true; say("拍实况时闪光灯不起作用，要补光请用下面的手电筒按钮"); }
            doTakePhoto(); return;
        }
        final boolean useScreen = !dual && !state.backIsMain;
        if (mode == 1 && !sceneDark(useScreen ? 1 : 0)) { doTakePhoto(); return; }
        flashBusy = true;
        if (!useScreen) {
            try { torchSet(true); }
            catch (Throwable e) { flashBusy = false; say("这台手机没法用手电筒补光"); AppLog.warn("闪光灯", "开手电筒失败：" + e); doTakePhoto(); return; }
        } else screenFlash(true);
        ui.postDelayed(new Runnable() { @Override public void run() {
            doTakePhoto();
            ui.postDelayed(new Runnable() { @Override public void run() {
                if (useScreen) screenFlash(false);
                else { try { torchSet(false); } catch (Throwable ignore) {} }
                flashBusy = false;
            } }, 250);
        } }, useScreen ? 350 : 450);
    }

    private void doTakePhoto() {
        final boolean live = prefs.live;
        final boolean heif = prefs.heif && Build.VERSION.SDK_INT >= 34 && !live;
        final File f = newFile(heif ? "heic" : "jpg");
        final int w = recW, h = recH;
        final LiveTask task;
        if (live) {
            if (activeLive() >= MAX_LIVE) { say("同时拍摄 / 合成的实况已满 " + MAX_LIVE + " 张，请稍等"); return; }
            task = new LiveTask(++liveSeq, liveSec(), f);
            liveTasks.add(task); clipping = true; addLiveRow(task);
        } else task = null;
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
                if (task != null) startLiveClip(task); else store(f, false, null);
            } catch (Throwable t) {
                say("拍照失败：" + t.getMessage());
                if (task != null) ui.post(new Runnable() { @Override public void run() { liveEnded(task, false, "拍照失败"); } });
            }
        } });
    }

    // ---------------------------------------------------------------- 实况（动态照片）连拍

    /** 实况的时长：双摄用「双摄设置」里的，单摄用「拍照与实况」里的 */
    private int liveSec() { if (dual) return prefs.liveDur; int d = new SettingsStore(prefs).sLiveDur(); return d > 0 ? d : 3; }
    private boolean liveTimerOn() { return dual ? prefs.liveTimer : new SettingsStore(prefs).sLiveTimer(); }

    private int activeLive() { int n = 0; for (LiveTask t : liveTasks) if (t.phase < 2) n++; return n; }

    /** GL 线程：照片已经拍好，马上开这一张实况的短片编码（和别的实况同时进行，互不等待） */
    private void startLiveClip(final LiveTask t) {
        try {
            File clip = newFile("mp4"); t.clip = clip;
            DualRecorder r = new DualRecorder(clip, recW & ~1, recH & ~1, prefs.audio && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED, Math.max(24, Math.min(60, camFps[mainCam()])));
            Surface in = r.start();
            t.rec = r;
            t.enc = gl.addLiveEncoder(in, System.nanoTime());
            if (!dual) { int lf = Math.max(15, Math.min(60, new SettingsStore(prefs).sLiveFps())); t.enc.minGapNs = (long) (1_000_000_000L / lf * 0.9); }
            ui.post(new Runnable() { @Override public void run() {
                t.t0Ns = System.nanoTime();
                ui.postDelayed(new Runnable() { @Override public void run() { endLiveClip(t); } }, t.total * 1000L);
            } });
        } catch (Throwable e) {
            // 短片开不起来：照片还是要保存
            say("实况视频失败，只保存照片：" + e.getMessage());
            store(t.photo, false, null);
            ui.post(new Runnable() { @Override public void run() { liveEnded(t, false, "短片无法开始"); } });
        }
    }

    /** 到时间了：收这一张的短片，和照片合成动态照片，存进系统相册 */
    private void endLiveClip(final LiveTask t) {
        t.phase = 1; refreshLiveRows();
        pending++;
        glH.post(new Runnable() { @Override public void run() {
            try { gl.removeLiveEncoder(t.enc); } catch (Throwable ignore) {}
            final boolean ok = t.rec != null && t.rec.stop();
            new Thread(new Runnable() { @Override public void run() {
                final Runnable fine = new Runnable() { @Override public void run() { ui.post(new Runnable() { @Override public void run() { liveEnded(t, true, "实况照片已存入系统相册"); } }); } };
                if (ok && !dual && new SettingsStore(prefs).sLiveStill()) {       // 单摄「实况 + 静态照片」：多存一张同一瞬间的静态照片
                    try { File still = newFile("jpg"); try (java.io.InputStream in = new java.io.FileInputStream(t.photo); FileOutputStream out2 = new FileOutputStream(still)) { byte[] buf = new byte[65536]; int n; while ((n = in.read(buf)) > 0) out2.write(buf, 0, n); } store(still, false, null); } catch (Throwable ignore) {}
                }
                if (ok && !prefs.liveSeparate) {
                    File out = newFile("jpg");
                    if (MotionPhoto.embed(t.photo, t.clip, out, 0)) { t.photo.delete(); t.clip.delete(); store(out, false, fine); }
                    else { say("动态照片合成失败，已分开保存"); store(t.photo, false, null); store(t.clip, true, fine); }
                } else if (ok) { store(t.photo, false, null); store(t.clip, true, fine); }
                else {
                    say("实况视频失败，只保存照片");
                    store(t.photo, false, null);
                    if (t.clip != null) t.clip.delete();
                    ui.post(new Runnable() { @Override public void run() { liveEnded(t, false, "短片失败，已存静态照片"); } });
                }
                ui.post(new Runnable() { @Override public void run() { pending--; if (pendingThrottle && !recording && !clipping) throttleNow(); } });
            } }).start();
        } });
    }

    private void addLiveRow(LiveTask t) {
        TextView row = DualUi.label(this, "", 13, Color.WHITE); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(4), dp(12), dp(4));
        t.row = row;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.bottomMargin = dp(4);
        liveStack.addView(row, lp);
        liveHideToken++;                                       // 又来了新的一张，先别收
        refreshLiveRows();
        if (!liveTicking) {
            liveTicking = true;
            ui.postDelayed(new Runnable() { @Override public void run() {
                refreshLiveRows();
                boolean any = false; for (LiveTask x : liveTasks) if (x.phase == 0) any = true;
                if (any) ui.postDelayed(this, 100); else liveTicking = false;
            } }, 100);
        }
    }

    private void refreshLiveRows() {
        liveStack.setVisibility(liveTimerOn() && !liveTasks.isEmpty() ? View.VISIBLE : View.GONE);
        for (LiveTask t : liveTasks) {
            if (t.row == null) continue;
            int bg; String tx;
            switch (t.phase) {
                case 0: {
                    float e = Math.min(t.total, (System.nanoTime() - t.t0Ns) / 1e9f);
                    tx = String.format(Locale.US, "● 实况 #%d  录制 %.1f / %d 秒", t.n, e, t.total); bg = 0xE6E61A1A; break;
                }
                case 1: tx = "◐ 实况 #" + t.n + "  合成保存中…"; bg = 0xEBD98019; break;
                case 2: tx = "✓ 实况 #" + t.n + "  已存入"; bg = 0xEB1E9E4C; break;
                default: tx = "✕ 实况 #" + t.n + "  失败" + (t.note.isEmpty() ? "" : "：" + t.note); bg = 0xEB808080;
            }
            t.row.setText(tx); t.row.setBackground(DualUi.round(bg, 13, this));
        }
    }

    /** 一张实况收尾（界面线程）。所有实况都完成了，进度条才一起收起来 */
    private void liveEnded(LiveTask t, boolean ok, String msg) {
        if (t.phase >= 2) return;
        t.phase = ok ? 2 : 3; if (!ok) t.note = msg;
        clipping = activeLive() > 0;
        refreshLiveRows();
        if (!liveTimerOn() && ok) say(msg);
        if (activeLive() > 0) return;
        final int tok = ++liveHideToken;
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (tok != liveHideToken || activeLive() > 0) return;
            for (LiveTask x : liveTasks) if (x.row != null) liveStack.removeView(x.row);
            liveTasks.clear(); liveStack.setVisibility(View.GONE);
        } }, 1800);
    }

    private volatile boolean recStarting, stopWanted;

    private void startRecording(final boolean silent, int unused) {
        recStarting = true; stopWanted = false;
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
                recorder = r; recording = true; recStarting = false;
                if (stopWanted) { stopWanted = false; ui.postDelayed(new Runnable() { @Override public void run() { stopRecording(); } }, 600); }
                final boolean audio = r.hasAudio;
                if (!silent) ui.post(new Runnable() { @Override public void run() {
                    paint(); timer.setVisibility(View.VISIBLE); tick();
                    say(audio ? "开始录像（带声音）" : "开始录像（只录画面）");
                } });
            } catch (Throwable t) { recording = false; recStarting = false; stopWanted = false; say("无法开始录像：" + t.getMessage()); }
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
        ui.post(new Runnable() { @Override public void run() { paint(); timer.setVisibility(View.GONE); if (pendingThrottle && !prefs.fullRate) throttleNow(); } });
        pending++;
        glH.post(new Runnable() { @Override public void run() {
            try { gl.setEncoderSurface(null); } catch (Throwable ignore) {}
            boolean ok = r != null && r.stop();
            if (ok) store(f, true, null); else { say("录像失败：" + (r == null ? "" : r.error)); f.delete(); }
            pending--;
        } });
    }

    /** 存进系统相册 + 记入清单 + 登记到双摄相册（后台线程）；done 在存完之后调用（可为 null） */
    private void store(final File f, final boolean video, final Runnable done) {
        pending++;
        new Thread(new Runnable() { @Override public void run() {
            MediaSaver.Result r = MediaSaver.save(DualCameraActivity.this, f, f.getName(), video ? "video/mp4" : f.getName().endsWith(".heic") ? "image/heic" : "image/jpeg");
            if (r.ok) DualLibrary.add(DualCameraActivity.this, r.uri);
            try {
                JSONObject o = new JSONObject();
                o.put("kind", video ? "video" : "photo");
                o.put("name", f.getName());
                o.put("saved", r.ok);
                synchronized (items) { items.put(o); itemFiles.add(f); itemVideo.add(video); }
            } catch (Exception ignore) {}
            if (done == null) say(r.ok ? (video ? "视频已存入系统相册" : "照片已存入系统相册") : "没存进系统相册：" + r.error + "（仍会导入澄影相册）");
            else if (!r.ok) say("没存进系统相册：" + r.error + "（仍会导入澄影相册）");
            refreshThumb();
            if (done != null) done.run();
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
        AppLog.error("相机", "打不开相机(" + code + ")：" + msg);
        final String title = "denied".equals(code) ? "没有相机权限" : "无法打开相机";
        final String body = "denied".equals(code) ? "请到 系统设置 → 应用 → 澄影重构版 → 权限 里打开相机。" : msg;
        ui.post(new Runnable() { @Override public void run() {
            if (isFinishing()) return;
            try { spinnerView.setVisibility(View.GONE); } catch (Throwable ignore) {}
            new android.app.AlertDialog.Builder(DualCameraActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(title).setMessage(body).setPositiveButton("好", null).show();
        } });
    }

    private void finishWith() {
        if (finished) return;
        finished = true;
        try {
            JSONObject o = new JSONObject();
            boolean none; synchronized (items) { none = items.length() == 0; }
            if (failCode != null && none) { o.put("ok", false); o.put("code", failCode); o.put("error", failMsg); }
            else { o.put("ok", true); synchronized (items) { o.put("items", items); } }
            if (torchOn) try { torchSet(false); } catch (Throwable ignore) {}
            Intent i = new Intent();
            i.putExtra("result", o.toString());
            setResult(RESULT_OK, i);
        } catch (Exception ignore) {}
        finish();
    }
}

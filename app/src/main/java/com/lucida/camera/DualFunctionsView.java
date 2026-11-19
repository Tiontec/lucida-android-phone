package com.lucida.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * 多功能面板（相机底部快门旁的按键）：底部抽屉。
 * - 3 列圆形图标按钮；半屏（露出前两行）⇄ 近全屏 两档，手势跟手、松手吸附；点面板外面、或往下拉到底关闭。
 * - 按钮清单和状态都在 PanelRegistry.java 里；灰色（SOON）的点击提示「未加入此功能」。
 * - 右上角齿轮 = 进设置；原来的「更多设置 / 预设顺序 / 关于」入口已经迁到设置里。
 * Activity 这边的挂载方式没变（作为 overlay 加进 root），Host 多了一个 openPalette()。
 */
final class DualFunctionsView extends FrameLayout {
    interface Host {
        boolean isDual();
        String lastFail();
        void setDual(boolean on);
        /** 关掉面板后打开调色盘（Activity 实现：closeOverlay(); setPalette(true);） */
        void openPalette();
        /** 关掉面板后打开规格面板（里面有「画幅」一行） */
        void openRatio();
        /** 拍照闪光灯当前模式：0 关 / 1 自动 / 2 开 */
        int flashMode();
        /** 切到下一档，返回新的模式 */
        int cycleFlash();
    }

    private final Runnable onClose, onSettings;
    private final Host host;

    private final View dim;
    private final DragSheet sheet;
    private final FrameLayout content;
    private final PanelTile[] tiles;
    private final TextView toast;

    private final int side, headerH, minPitch, circle, tileH, gap;
    private int hMin, hMax, bottomMargin, topInset, bottomInset;
    private float curH;                       // 面板当前高度（px）
    private boolean laidOut, closing;
    private ValueAnimator anim;
    private boolean shownDual; private String shownFail = "";
    private int toastGen;

    private final Runnable poll = new Runnable() { @Override public void run() {
        syncDual();
        postDelayed(poll, 500);
    } };

    DualFunctionsView(final Context c, DualPrefs prefs, Runnable onClose, Runnable onSettings, Host host) {
        super(c);
        this.onClose = onClose; this.onSettings = onSettings; this.host = host;
        side = DualUi.dp(c, 12); headerH = DualUi.dp(c, 44); minPitch = DualUi.dp(c, 112); circle = DualUi.dp(c, 68); gap = DualUi.dp(c, 8);
        tileH = circle + DualUi.dp(c, 30);
        setClickable(true);

        dim = new View(c);
        dim.setBackgroundColor(Color.BLACK); dim.setAlpha(0f);
        dim.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { dismiss(null); } });
        addView(dim, new FrameLayout.LayoutParams(-1, -1));

        sheet = new DragSheet(c);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setColor(DualUi.glass ? 0xB8121214 : 0xF2161618);
        bgd.setCornerRadius(DualUi.dp(c, 34));
        if (DualUi.glass) bgd.setStroke(Math.max(1, DualUi.dp(c, 1)), 0x59FFFFFF);
        sheet.setBackground(bgd);
        sheet.setClipToOutline(true);
        sheet.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-1, 0, Gravity.BOTTOM);
        slp.leftMargin = side; slp.rightMargin = side;
        addView(sheet, slp);

        View grabber = new View(c);
        GradientDrawable gd = new GradientDrawable(); gd.setColor(0x59FFFFFF); gd.setCornerRadius(DualUi.dp(c, 3));
        grabber.setBackground(gd);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(DualUi.dp(c, 40), DualUi.dp(c, 5), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        glp.topMargin = DualUi.dp(c, 8);
        sheet.addView(grabber, glp);

        DualIcon gear = new DualIcon(c, DualIcon.GEAR);
        gear.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { dismiss(new Runnable() { @Override public void run() { DualFunctionsView.this.onSettings.run(); } }); } });
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(DualUi.dp(c, 36), DualUi.dp(c, 36), Gravity.TOP | Gravity.RIGHT);
        gp.topMargin = DualUi.dp(c, 4); gp.rightMargin = DualUi.dp(c, 14);
        sheet.addView(gear, gp);

        content = new FrameLayout(c);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.topMargin = headerH;
        sheet.addView(content, clp);

        tiles = new PanelTile[PanelRegistry.ITEMS.length];
        for (int i = 0; i < tiles.length; i++) {
            final PanelRegistry.Item it = PanelRegistry.ITEMS[i];
            final PanelTile t = new PanelTile(c, it);
            t.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { perform(it, t); } });
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(DualUi.dp(c, 100), tileH);
            tp.topMargin = gap + (i / 3) * minPitch;
            content.addView(t, tp);
            tiles[i] = t;
            if (it.action == PanelRegistry.A_TOGGLE_DUAL) t.glyph.setActive(host.isDual());
            if (it.action == PanelRegistry.A_CYCLE_FLASH) t.glyph.setActive(host.flashMode() != 0);
        }
        shownDual = host.isDual();

        toast = DualUi.label(c, "", 14, Color.WHITE);
        toast.setGravity(Gravity.CENTER);
        toast.setPadding(DualUi.dp(c, 16), DualUi.dp(c, 9), DualUi.dp(c, 16), DualUi.dp(c, 9));
        toast.setBackground(DualUi.round(0xEB1E1E20, 20, c));
        toast.setAlpha(0f); toast.setVisibility(View.GONE);
        addView(toast, new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
    }

    // ───────────── 布局与高度 ─────────────

    @Override public WindowInsets onApplyWindowInsets(WindowInsets in) {
        int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
        if (android.os.Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null) top = Math.max(top, in.getDisplayCutout().getSafeInsetTop());
        topInset = Math.max(top, DualUi.dp(getContext(), 24)); bottomInset = bottom;
        if (laidOut) { computeMetrics(); if (!closing) setHeightPx(Math.max(hMin, Math.min(hMax, curH))); }
        return super.onApplyWindowInsets(in);
    }

    private void computeMetrics() {
        Context c = getContext();
        bottomMargin = Math.max(bottomInset, DualUi.dp(c, 4)) + DualUi.dp(c, 8);
        hMax = Math.max(DualUi.dp(c, 320), getHeight() - (topInset + DualUi.dp(c, 52)) - bottomMargin);
        hMin = Math.min(DualUi.dp(c, 300), hMax - DualUi.dp(c, 40));
        int colW = (getWidth() - side * 2) / 3;
        for (int i = 0; i < tiles.length; i++) {
            FrameLayout.LayoutParams tp = (FrameLayout.LayoutParams) tiles[i].getLayoutParams();
            int w = Math.max(1, colW), left = colW * (i % 3);
            if (tp.width != w || tp.leftMargin != left) { tp.width = w; tp.leftMargin = left; tiles[i].setLayoutParams(tp); }
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w <= 0 || h <= 0) return;
        if (!laidOut) {
            laidOut = true;
            post(new Runnable() { @Override public void run() {      // 布局过程里不直接改布局，等一下再开始入场动画
                computeMetrics();
                setHeightPx(0);
                ValueAnimator a = ValueAnimator.ofFloat(0f, hMin);
                a.setDuration(420); a.setInterpolator(new OvershootInterpolator(0.8f));
                a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator va) { setHeightPx((Float) va.getAnimatedValue()); } });
                anim = a; a.start();
            } });
        } else if (!closing) { computeMetrics(); setHeightPx(Math.max(hMin, Math.min(hMax, curH))); }
    }

    /** 把面板高度设成 h（拖动时每一帧都调）。按钮的行距随高度变大：半屏时紧凑，拉开后撑满 */
    private void setHeightPx(float h) {
        curH = Math.max(0f, h);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
        lp.bottomMargin = bottomMargin; lp.height = Math.round(curH);
        sheet.setLayoutParams(lp);
        float span = Math.max(1f, hMax - hMin);
        float t = Math.max(0f, Math.min(1f, (curH - hMin) / span));
        float pMax = Math.max(minPitch, (hMax - headerH - DualUi.dp(getContext(), 20)) / 4f);
        float extra = (pMax - minPitch) * t;
        for (int i = 0; i < tiles.length; i++) tiles[i].setTranslationY((i / 3) * extra);
        dim.setAlpha(0.32f * t);
    }

    // ───────────── 动画 ─────────────

    private void animateTo(float target, int ms, Runnable end) {
        if (anim != null) anim.cancel();
        final Runnable done = end;
        ValueAnimator a = ValueAnimator.ofFloat(curH, target);
        a.setDuration(ms); a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() { @Override public void onAnimationUpdate(ValueAnimator va) { setHeightPx((Float) va.getAnimatedValue()); } });
        if (done != null) a.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;
            @Override public void onAnimationCancel(Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(Animator an) { if (!cancelled) done.run(); }
        });
        anim = a; a.start();
    }

    /** 面板收下去再关掉；then 在关掉之后执行（比如接着打开调色盘 / 设置） */
    private void dismiss(final Runnable then) {
        if (closing) return;
        closing = true;
        animateTo(0f, 260, new Runnable() { @Override public void run() {
            if (then != null) then.run(); else onClose.run();
        } });
    }

    private void settle(float vyPxPerSec) {
        float fast = DualUi.dp(getContext(), 800);
        float target;
        if (vyPxPerSec > fast) {
            if (curH > hMin + DualUi.dp(getContext(), 30)) target = hMin; else { dismiss(null); return; }
        } else if (vyPxPerSec < -fast) {
            target = hMax;
        } else if (curH < hMin * 0.7f) {
            dismiss(null); return;
        } else {
            target = curH < (hMin + hMax) / 2f ? hMin : hMax;
        }
        animateTo(Math.max(hMin, Math.min(hMax, target)), 320, null);
    }

    // ───────────── 点击 ─────────────

    private void perform(PanelRegistry.Item it, PanelTile tile) {
        if (closing) return;
        if (it.state == PanelRegistry.ST_SOON) { showToast(SettingsRegistry.PANEL_SOON_TOAST); return; }
        if (it.action == PanelRegistry.A_OPEN_PALETTE) {
            dismiss(new Runnable() { @Override public void run() { host.openPalette(); } });
        } else if (it.action == PanelRegistry.A_TOGGLE_DUAL) {
            boolean want = !host.isDual();
            host.setDual(want);
            boolean now = host.isDual();
            shownDual = now; shownFail = host.lastFail();
            tile.glyph.setActive(now);
            if (now == want) showToast(want ? "正在开启双摄…（这台手机不支持时会自动退回单摄）" : "已切换到单摄");
            else showToast("现在不能切换（正在录像 / 拍实况）");
        } else if (it.action == PanelRegistry.A_OPEN_RATIO) {
            dismiss(new Runnable() { @Override public void run() { host.openRatio(); } });
        } else if (it.action == PanelRegistry.A_CYCLE_FLASH) {
            int m = host.cycleFlash();
            tile.glyph.setActive(m != 0);
            showToast(new String[]{"闪光灯：关", "闪光灯：自动（暗处才闪）", "闪光灯：开"}[m]);
        } else {
            showToast(SettingsRegistry.PANEL_SOON_TOAST);
        }
    }

    /** 双摄状态在面板开着时被别处改变（比如开启失败自动退回单摄）：同步高亮，并把原因提示出来 */
    private void syncDual() {
        boolean now = host.isDual();
        String fail = host.lastFail();
        if (now != shownDual) {
            shownDual = now;
            for (PanelTile t : tiles) if (t.item.action == PanelRegistry.A_TOGGLE_DUAL) t.glyph.setActive(now);
        }
        if (!fail.equals(shownFail)) {
            shownFail = fail;
            if (fail.length() > 0) showToast("双摄不可用：" + fail);
        }
    }

    // ───────────── 提示条（浮在面板上方） ─────────────

    private void showToast(String s) {
        final int gen = ++toastGen;
        toast.setText(s);
        toast.setVisibility(View.VISIBLE);
        int toastH = DualUi.dp(getContext(), s.length() > 22 ? 62 : 40);
        int y = Math.max(topInset + DualUi.dp(getContext(), 8), getHeight() - bottomMargin - Math.round(curH) - DualUi.dp(getContext(), 12) - toastH);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) toast.getLayoutParams();
        lp.topMargin = y; lp.leftMargin = DualUi.dp(getContext(), 20); lp.rightMargin = DualUi.dp(getContext(), 20);
        toast.setLayoutParams(lp);
        toast.bringToFront();
        toast.animate().alpha(1f).setDuration(160).start();
        postDelayed(new Runnable() { @Override public void run() {
            if (gen == toastGen) toast.animate().alpha(0f).setDuration(250).start();
        } }, 1900);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); postDelayed(poll, 500); }
    @Override protected void onDetachedFromWindow() {
        removeCallbacks(poll);
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    // ───────────── 面板里的小部件 ─────────────

    /** 一个功能按钮：圆形图标 + 文字。SOON 整个变灰；按下略微缩小 */
    private static final class PanelTile extends FrameLayout {
        final PanelRegistry.Item item;
        final PanelGlyph glyph;
        PanelTile(Context c, PanelRegistry.Item item) {
            super(c);
            this.item = item;
            setClickable(true);
            int d = DualUi.dp(c, 68);
            glyph = new PanelGlyph(c, item.glyph);
            addView(glyph, new FrameLayout.LayoutParams(d, d, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            TextView t = DualUi.label(c, item.title, 14, Color.WHITE);
            t.setGravity(Gravity.CENTER); t.setSingleLine(true);
            FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            tp.topMargin = d + DualUi.dp(c, 8);
            addView(t, tp);
            setAlpha(item.state == PanelRegistry.ST_SOON ? 0.38f : 1f);
            setContentDescription(item.title);
        }
        @Override public void setPressed(boolean p) {
            super.setPressed(p);
            animate().scaleX(p ? 0.93f : 1f).scaleY(p ? 0.93f : 1f).setDuration(110).start();
        }
    }

    /** 可上拉 / 下拉的面板：竖向拖动超过触摸阈值就接管（按钮上按下再拖也行），松手时按速度和位置吸附 */
    private final class DragSheet extends FrameLayout {
        private float downY, startH;
        private boolean dragging;
        private VelocityTracker vt;
        private final int slop;

        DragSheet(Context c) { super(c); slop = ViewConfiguration.get(c).getScaledTouchSlop(); }

        private void begin(MotionEvent ev) {
            if (anim != null) anim.cancel();
            downY = ev.getRawY(); startH = curH; dragging = false;
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain(); vt.addMovement(ev);
        }

        @Override public boolean onInterceptTouchEvent(MotionEvent ev) {
            if (closing) return false;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: begin(ev); break;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(ev);
                    if (!dragging && Math.abs(ev.getRawY() - downY) > slop) { dragging = true; startH = curH; downY = ev.getRawY(); return true; }
                    break;
                default: break;
            }
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent ev) {
            if (closing) return true;
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: begin(ev); return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(ev);
                    if (!dragging && Math.abs(ev.getRawY() - downY) > slop) { dragging = true; startH = curH; downY = ev.getRawY(); }
                    if (dragging) {
                        float h = startH - (ev.getRawY() - downY);
                        if (h > hMax) h = hMax + (h - hMax) * 0.25f;      // 上拉过头有阻尼
                        setHeightPx(h);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: {
                    float vy = 0f;
                    if (vt != null) { vt.addMovement(ev); vt.computeCurrentVelocity(1000); vy = vt.getYVelocity(); vt.recycle(); vt = null; }
                    if (dragging) settle(vy);
                    dragging = false;
                    return true;
                }
                default: return true;
            }
        }
    }
}

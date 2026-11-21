package com.lucida.camera;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 「更多设置」整页的容器：页面栈（主页 → 集合页 → 单选列表页），左上角返回，系统返回键先退一页。
 * 页面内容全部来自 SettingsRegistry，画页面的代码在 SettingsPageView.java / SettingsUi.java。
 * 对 Activity 的接口和以前一样：new DualSettingsView(ctx, prefs, host)，作为 overlay 加进 root 即可。
 */
final class DualSettingsView extends FrameLayout {
    interface Host { void onClose(); void onChanged(boolean reconfigure); }

    private final DualPrefs p;
    private final Host host;
    private final SettingsStore store;
    private final List<SettingsPageView> stack = new ArrayList<SettingsPageView>();
    private final FrameLayout pages;
    private final TextView toast;
    private View orderOverlay;
    private int toastGen;
    private boolean animating;

    DualSettingsView(final Context c, final DualPrefs p, final Host host) {
        super(c);
        this.p = p; this.host = host; this.store = new SettingsStore(p);
        setBackgroundColor(PanelTheme.pageBg(c));
        setClickable(true);
        setFocusableInTouchMode(true);

        pages = new FrameLayout(c);
        addView(pages, new FrameLayout.LayoutParams(-1, -1));

        toast = DualUi.label(c, "", 14, PanelTheme.toastText());
        toast.setGravity(Gravity.CENTER);
        toast.setPadding(DualUi.dp(c, 16), DualUi.dp(c, 9), DualUi.dp(c, 16), DualUi.dp(c, 9));
        toast.setBackground(DualUi.surface(PanelTheme.toastBg(), 20, c));
        toast.setAlpha(0f); toast.setVisibility(View.GONE);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        tp.topMargin = DualUi.dp(c, 66);
        addView(toast, tp);

        SettingsPageView home = new SettingsPageView(this, SettingsPageView.HOME, null);
        stack.add(home);
        pages.addView(home, new FrameLayout.LayoutParams(-1, -1));

        // 系统返回键：先退一页（子页 / 预设顺序），在主页才交给 Activity 关闭整个设置
        setOnKeyListener(new OnKeyListener() {
            @Override public boolean onKey(View v, int keyCode, KeyEvent e) {
                if (keyCode != KeyEvent.KEYCODE_BACK) return false;
                if (orderOverlay == null && stack.size() <= 1) return false;
                if (e.getAction() == KeyEvent.ACTION_UP) back();
                return true;
            }
        });
    }

    SettingsStore store() { return store; }

    // ───────────── 给各页用的操作 ─────────────

    /** 设置改了：通知相机界面（reconfigure = 需要重配摄像头） */
    void changed(boolean reconfigure) { host.onChanged(reconfigure); }

    /** 玻璃 / 平面切换后：所有已打开的页面重画 */
    void refreshAll() {
        setBackgroundColor(PanelTheme.pageBg(getContext()));
        post(new Runnable() { @Override public void run() { for (SettingsPageView v : stack) v.rebuild(); } });
    }

    void toast(String s) {
        final int gen = ++toastGen;
        toast.setText(s);
        toast.setVisibility(View.VISIBLE);
        toast.animate().alpha(1f).setDuration(160).start();
        postDelayed(new Runnable() { @Override public void run() {
            if (gen == toastGen) toast.animate().alpha(0f).setDuration(250).start();
        } }, 1600);
    }

    void push(int kind, String arg) {
        if (animating) return;
        final SettingsPageView from = stack.get(stack.size() - 1);
        final SettingsPageView to = new SettingsPageView(this, kind, arg);
        stack.add(to);
        pages.addView(to, new FrameLayout.LayoutParams(-1, -1));
        final float w = Math.max(1, getWidth());
        to.setTranslationX(w);
        animating = true;
        from.animate().translationX(-w / 4f).setDuration(240).start();
        to.animate().translationX(0f).setDuration(240).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { animating = false; from.setTranslationX(0f); to.animate().setListener(null); }
        }).start();
    }

    /** 左上角返回 / 系统返回键：预设顺序页 → 子页 → 关闭设置 */
    void back() {
        if (orderOverlay != null) { closeOrder(); return; }
        if (stack.size() <= 1) { host.onClose(); return; }
        if (animating) return;
        final SettingsPageView top = stack.remove(stack.size() - 1);
        final SettingsPageView under = stack.get(stack.size() - 1);
        under.rebuild();                 // 右侧的当前值要刷新
        final float w = Math.max(1, getWidth());
        under.setTranslationX(-w / 4f);
        animating = true;
        under.animate().translationX(0f).setDuration(220).start();
        top.animate().translationX(w).setDuration(220).setListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { animating = false; pages.removeView(top); }
        }).start();
    }

    void openOrder() {
        if (orderOverlay != null) return;
        final DualOrderView[] ov = new DualOrderView[1];
        ov[0] = new DualOrderView(getContext(), p, "‹ 设置", new Runnable() { @Override public void run() { closeOrder(); } });
        orderOverlay = ov[0];
        addView(ov[0], new FrameLayout.LayoutParams(-1, -1));
    }

    private void closeOrder() {
        if (orderOverlay == null) return;
        removeView(orderOverlay); orderOverlay = null;
        host.onChanged(false);           // 预设顺序可能变了
    }

    // ───────────── 系统栏 / 焦点 ─────────────

    @Override public WindowInsets onApplyWindowInsets(WindowInsets in) {
        int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
        setPadding(0, top, 0, bottom);
        return super.onApplyWindowInsets(in);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestApplyInsets();            // 后加进来的 overlay 要主动要一次系统栏边距
        post(new Runnable() { @Override public void run() { requestFocus(); } });
    }
}

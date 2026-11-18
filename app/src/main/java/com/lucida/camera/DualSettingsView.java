package com.lucida.camera;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 双摄「更多设置」：实况 / 片门全开 / 小窗边框 / 前置镜像 / 录像声音 / 取景网格 / 满帧画面运行录制 / 毛玻璃界面 / 调色盘预设顺序。 */
final class DualSettingsView extends FrameLayout {
    interface Host { void onClose(); void onChanged(boolean reconfigure); }

    DualSettingsView(final Context c, final DualPrefs p, final Host host) {
        super(c);
        setBackgroundColor(0xF2101010);
        setClickable(true);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 56)));
        TextView t = DualUi.label(c, "更多设置", 18, Color.WHITE);
        bar.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView done = DualUi.label(c, "完成", 16, DualUi.YELLOW);
        done.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 10), DualUi.dp(c, 18), DualUi.dp(c, 10));
        done.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { host.onClose(); } });
        bar.addView(done, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        ScrollView sv = new ScrollView(c);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
        int pad = DualUi.dp(c, 16);
        list.setPadding(pad, DualUi.dp(c, 6), pad, DualUi.dp(c, 40));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        this.list = list; this.p = p; this.host = host;
        fill();
    }

    private LinearLayout list;
    private DualPrefs p;
    private Host host;

    private void fill() {
        final Context c = getContext();
        final DualPrefs p = this.p; final Host host = this.host;
        list.removeAllViews();
        final int[] durs = {3, 5, 6, 8};
        int di = 0; for (int i = 0; i < 4; i++) if (durs[i] == p.liveDur) di = i;
        add(list, DualUi.card(c, "实况时长", DualUi.seg(c, new String[]{"3 秒", "5 秒", "6 秒", "8 秒"}, di, new DualUi.IntCb() {
            @Override public void on(int i) { p.liveDur = durs[i]; p.save(); host.onChanged(false); } })));
        add(list, DualUi.card(c, "实况保存方式", DualUi.seg(c, new String[]{"动态照片（单文件）", "照片 + 视频分开"}, p.liveSeparate ? 1 : 0, new DualUi.IntCb() {
            @Override public void on(int i) { p.liveSeparate = i == 1; p.save(); host.onChanged(false); } })));
        add(list, note(c, "安卓没有苹果那种实况照片：动态照片 = 一张 JPEG 里内嵌从按下快门开始的短视频，Google 相册等支持动态照片的相册可播放；没有快门前 1.5 秒，也没有声音。"));
        add(list, DualUi.toggleRow(c, "实况计时显示", "拍实况时在取景框左上角显示已经拍到第几秒、有没有拍完", p.liveTimer, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.liveTimer = v; p.save(); host.onChanged(false); } }));
        add(list, DualUi.toggleRow(c, "片门全开", "前后摄像头都用 4:3 最大规格，成品 3:4；帧率尽量保持所选值", p.fullGate, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.fullGate = v; p.save(); host.onChanged(true); } }));
        add(list, DualUi.card(c, "小窗边框", DualUi.seg(c, new String[]{"无", "白色", "黑色"}, p.pipBorder, new DualUi.IntCb() {
            @Override public void on(int i) { p.pipBorder = i; p.save(); host.onChanged(false); } })));
        add(list, DualUi.toggleRow(c, "前置画面镜像", null, p.mirror, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.mirror = v; p.save(); host.onChanged(false); } }));
        add(list, DualUi.toggleRow(c, "录像时录制声音", null, p.audio, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.audio = v; p.save(); host.onChanged(false); } }));
        add(list, DualUi.toggleRow(c, "取景网格线", null, p.gridLines, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.gridLines = v; p.save(); host.onChanged(false); } }));
        add(list, DualUi.toggleRow(c, "满帧画面运行录制", "开：手机发热也不降帧，按你选的帧率显示和录制（可能更烫、更耗电）；关：过热时自动降到 30 帧保护手机", p.fullRate, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.fullRate = v; p.save(); host.onChanged(false); } }));
        add(list, DualUi.toggleRow(c, "毛玻璃界面", "开：提示条、按钮、面板、分段选择、开关、滑条换成半透明毛玻璃样式，规格面板有果冻弹动；关：全部是普通深色样式（安卓没有苹果系统的液态玻璃，这是模拟出来的）", p.glass, new DualUi.BoolCb() {
            @Override public void on(boolean v) { p.glass = v; DualUi.glass = v; p.save(); host.onChanged(false); setBackgroundColor(v ? 0xCC101010 : 0xF2101010); post(new Runnable() { @Override public void run() { fill(); } }); } }));
        add(list, linkRow(c, "调色盘预设顺序", "调整左右滑动调色盘时预设出现的先后", new Runnable() { @Override public void run() {
            final DualOrderView[] ov = new DualOrderView[1];
            ov[0] = new DualOrderView(getContext(), p, new Runnable() { @Override public void run() { removeView(ov[0]); host.onChanged(false); } });
            addView(ov[0], new FrameLayout.LayoutParams(-1, -1));
        } }));
        add(list, note(c, "说明：多摄同时工作时系统不提供 RAW；动态照片没有声音。"));
    }

    static View linkRow(Context c, String title, String sub, final Runnable go) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(DualUi.surface(0x14FFFFFF, 14, c)); row.setPadding(DualUi.dp(c, 14), DualUi.dp(c, 12), DualUi.dp(c, 14), DualUi.dp(c, 12));
        LinearLayout left = new LinearLayout(c); left.setOrientation(LinearLayout.VERTICAL);
        left.addView(DualUi.label(c, title, 15, Color.WHITE));
        if (sub != null) left.addView(DualUi.label(c, sub, 12, 0x80FFFFFF));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(DualUi.label(c, "›", 22, 0x80FFFFFF));
        row.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { go.run(); } });
        return row;
    }

    private static TextView note(Context c, String s) {
        TextView t = DualUi.label(c, s, 12, 0x73FFFFFF); t.setPadding(DualUi.dp(c, 4), 0, DualUi.dp(c, 4), 0); return t;
    }

    private static void add(LinearLayout l, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = DualUi.dp(l.getContext(), 12);
        l.addView(v, lp);
    }
}

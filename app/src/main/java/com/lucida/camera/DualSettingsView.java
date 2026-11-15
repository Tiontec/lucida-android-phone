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

/** 双摄「更多设置」：实况时长 / 实况保存方式 / 片门全开 / 小窗边框 / 前置镜像 / 录像声音 / 取景网格。 */
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

        final int[] durs = {3, 5, 6, 8};
        int di = 0; for (int i = 0; i < 4; i++) if (durs[i] == p.liveDur) di = i;
        add(list, DualUi.card(c, "实况时长", DualUi.seg(c, new String[]{"3 秒", "5 秒", "6 秒", "8 秒"}, di, new DualUi.IntCb() {
            @Override public void on(int i) { p.liveDur = durs[i]; p.save(); host.onChanged(false); } })));
        add(list, DualUi.card(c, "实况保存方式", DualUi.seg(c, new String[]{"动态照片（单文件）", "照片 + 视频分开"}, p.liveSeparate ? 1 : 0, new DualUi.IntCb() {
            @Override public void on(int i) { p.liveSeparate = i == 1; p.save(); host.onChanged(false); } })));
        add(list, note(c, "安卓没有苹果那种实况照片：动态照片 = 一张 JPEG 里内嵌从按下快门开始的短视频，Google 相册等支持动态照片的相册可播放；没有快门前 1.5 秒，也没有声音。"));
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
        add(list, note(c, "说明：安卓没有苹果的「液态玻璃」系统控件，这一项不提供；多摄同时工作时系统不提供 RAW。"));
    }

    private static TextView note(Context c, String s) {
        TextView t = DualUi.label(c, s, 12, 0x73FFFFFF); t.setPadding(DualUi.dp(c, 4), 0, DualUi.dp(c, 4), 0); return t;
    }

    private static void add(LinearLayout l, View v) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = DualUi.dp(l.getContext(), 12);
        l.addView(v, lp);
    }
}

package com.lucida.camera;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.graphics.ImageFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Range;
import android.widget.Toast;
import android.util.Size;
import android.util.SizeF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 设置里的五个独立页面（设置页的 dest）：故障报错日志 / 摄像头检测 / 版本更新 / 关于澄影 / 赞赏。
 * 和 iOS 的 SettingsPagesM3.swift 一一对应。颜色走 PanelTheme，文案来自 ContentData（由 tools/gen_content.py 生成）。
 */
final class SettingsPagesM3 {
    private SettingsPagesM3() {}

    static boolean isM3(String dest) {
        return "log".equals(dest) || "cams".equals(dest) || "version".equals(dest) || "about".equals(dest) || "donate".equals(dest);
    }

    static String title(String dest) {
        if ("log".equals(dest)) return "故障报错日志";
        if ("cams".equals(dest)) return "摄像头检测";
        if ("version".equals(dest)) return "版本更新";
        if ("about".equals(dest)) return "关于澄影";
        if ("donate".equals(dest)) return "赞赏";
        return "设置";
    }

    static void build(SettingsPageView p, String dest) {
        if ("log".equals(dest)) buildLog(p);
        else if ("cams".equals(dest)) buildCams(p);
        else if ("version".equals(dest)) buildVersion(p);
        else if ("about".equals(dest)) buildAbout(p);
        else if ("donate".equals(dest)) buildDonate(p);
    }

    private static int dp(Context c, float v) { return DualUi.dp(c, v); }

    private static void showToast(Context c, String msg) {
        android.widget.Toast.makeText(c, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ───────────── 故障报错日志 ─────────────

    private static final int LOG_SHOW_MAX = 120;
    private static final SimpleDateFormat LOG_DF = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());

    private static void buildLog(final SettingsPageView p) {
        final Context c = p.ctx();

        // 记录开关
        LinearLayout c1 = SettingsUi.card(c);
        DualSwitch sw = new DualSwitch(c, AppLog.enabled(), new DualSwitch.Cb() {
            @Override public void on(boolean v) { AppLog.setEnabled(v); } });
        FrameLayout swBox = new FrameLayout(c);
        swBox.addView(sw, new FrameLayout.LayoutParams(dp(c, 50), dp(c, 28)));
        SettingsUi.addRow(c1, SettingsUi.row(c, "记录日志", "关闭后不再记录新的日志；已经记下的保留", null, swBox, null));
        p.put(c1, 0);

        // 筛选
        LinearLayout seg = DualUi.seg(c, new String[]{"全部", "警告及以上", "错误"}, p.logFilter, new DualUi.IntCb() {
            @Override public void on(int i) { p.logFilter = i; p.refreshPage(); } });
        LinearLayout c2 = SettingsUi.card(c);
        SettingsUi.addRow(c2, SettingsUi.controlRow(c, "显示", "", null, seg));
        p.put(c2, 8);

        // 列表：最新的在前，最多显示 LOG_SHOW_MAX 条
        List<AppLog.Entry> all = AppLog.entries(AppLog.F_ALL);
        List<AppLog.Entry> filtered = new ArrayList<AppLog.Entry>();
        for (int i = all.size() - 1; i >= 0; i--) {
            AppLog.Entry e = all.get(i);
            if (p.logFilter == 1 && e.level == AppLog.INFO) continue;
            if (p.logFilter == 2 && e.level != AppLog.ERROR) continue;
            filtered.add(e);
        }
        if (filtered.isEmpty()) {
            p.put(SettingsUi.note(c, "还没有记录。出现问题时会自动记在这里。"), 18);
        } else {
            LinearLayout c3 = SettingsUi.card(c);
            int n = Math.min(LOG_SHOW_MAX, filtered.size());
            for (int i = 0; i < n; i++) SettingsUi.addRow(c3, logRow(p, c, filtered.get(i)));
            p.put(c3, 6);
            if (filtered.size() > n) p.put(SettingsUi.note(c, "共 " + filtered.size() + " 条，这里只显示最近 " + n + " 条；导出可以看全部。"), 8);
        }

        // 操作
        LinearLayout c4 = SettingsUi.card(c);
        SettingsUi.addRow(c4, SettingsUi.row(c, "导出全部日志", "导出为文本，可以发给开发者排查问题", null, SettingsUi.chevron(c), new Runnable() {
            @Override public void run() { exportLog(c); } }));
        SettingsUi.addRow(c4, SettingsUi.row(c, "清空日志", "只清空记录，不影响设置", null, null, new Runnable() {
            @Override public void run() { confirmClear(p, c); } }, PanelTheme.danger(c)));
        p.put(c4, 18);
    }

    private static View logRow(final SettingsPageView p, final Context c, final AppLog.Entry e) {
        final String key = e.time + "|" + e.tag + "|" + (e.msg.length() > 40 ? e.msg.substring(0, 40) : e.msg);
        boolean expanded = p.expandedLogKeys.contains(key);
        TextView level = DualUi.label(c, e.levelTitle(), 12.5f, levelColor(c, e.level));
        level.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        String when = LOG_DF.format(new java.util.Date(e.time));
        String title = when + "  " + e.tag + (e.prev ? "  · 上次运行" : "");
        String shown = expanded ? e.msg : collapse(e.msg);
        final String full = e.msg;
        final String fullTitle = title;
        View row = SettingsUi.row(c, title, shown, null, level, new Runnable() {
            @Override public void run() {
                if (p.expandedLogKeys.contains(key)) p.expandedLogKeys.remove(key); else p.expandedLogKeys.add(key);
                p.refreshPage();
            } });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("澄影日志", fullTitle + "\n" + full));
                p.hint("已复制这一条");
                return true;
            } });
        return row;
    }

    private static int levelColor(Context c, int level) {
        if (level == AppLog.ERROR) return PanelTheme.danger(c);
        if (level == AppLog.WARN) return DualUi.YELLOW;      // 警告黄色是应用强调色，不随面板样式变
        return PanelTheme.sub(c);
    }

    /** 折叠显示：最多两行、约 120 字 */
    static String collapse(String s) {
        String[] lines = s.split("\n", -1);
        String head = lines.length > 2 ? lines[0] + "\n" + lines[1] : s;
        if (head.length() > 120 || lines.length > 2) {
            return (head.length() > 120 ? head.substring(0, 120) : head) + "…（点一下展开）";
        }
        return s;
    }

    private static void exportLog(Context c) {
        String text = AppLog.text(AppLog.F_ALL);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "澄影日志");
        i.putExtra(Intent.EXTRA_TEXT, text);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { c.startActivity(Intent.createChooser(i, "导出日志")); }
        catch (Throwable t) { showToast(c, "导出失败：" + t.getMessage()); }
    }

    private static void confirmClear(final SettingsPageView p, Context c) {
        new AlertDialog.Builder(c)
            .setTitle("清空日志？")
            .setMessage("已经记下的日志会全部删除，不能恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空", new android.content.DialogInterface.OnClickListener() {
                @Override public void onClick(android.content.DialogInterface d, int w) {
                    AppLog.clear();
                    p.expandedLogKeys.clear();
                    p.refreshPage();
                } })
            .show();
    }

    // ───────────── 摄像头检测（只查询，不碰相机会话） ─────────────

    /** 一颗摄像头的检测结果：标题 + 多行说明 */
    static final class CamInfo { final String title, sub; CamInfo(String t, String s) { title = t; sub = s; } }

    private static void buildCams(final SettingsPageView p) {
        final Context c = p.ctx();
        List<CamInfo> cams = new ArrayList<CamInfo>();
        String multi = "无法查询";
        String err = null;
        try {
            CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
            if (cm == null) throw new RuntimeException("系统没有提供相机服务");
            for (String id : cm.getCameraIdList()) cams.add(describe(cm, id));
            multi = multiText(cm);
        } catch (CameraAccessException e) {
            err = "相机暂时不能查询：" + e.getReason();
        } catch (Throwable t) {
            err = "查询出错：" + t.getMessage();
        }

        LinearLayout c0 = SettingsUi.card(c);
        SettingsUi.addRow(c0, SettingsUi.row(c, "摄像头数量", null, cams.size() + " 个", null, null));
        SettingsUi.addRow(c0, SettingsUi.row(c, "多摄同时工作", "前后摄像头能否同时开启（双摄功能需要）", multi, null, null));
        p.put(c0, 0);
        if (err != null) p.put(SettingsUi.note(c, err), 10);

        if (!cams.isEmpty()) {
            p.put(SettingsUi.subhead(c, "每颗摄像头"), 18);
            LinearLayout c1 = SettingsUi.card(c);
            for (CamInfo ci : cams) SettingsUi.addRow(c1, SettingsUi.row(c, ci.title, ci.sub, null, null, null));
            p.put(c1, 6);
        }

        LinearLayout c2 = SettingsUi.card(c);
        SettingsUi.addRow(c2, SettingsUi.row(c, "复制检测结果", "纯文本，方便粘贴发给我们排查", null, SettingsUi.chevron(c), new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                sb.append("澄影 ").append(BuildConfig.VERSION_NAME).append("（").append(BuildConfig.VERSION_CODE).append("）\n\n");
                for (CamInfo ci : cams) sb.append("【").append(ci.title).append("】\n").append(ci.sub).append("\n\n");
                ClipboardManager cm = (ClipboardManager) c.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("摄像头检测", sb.toString()));
                p.hint("已复制检测结果");
            } }));
        p.put(c2, 18);
    }

    private static CamInfo describe(CameraManager cm, String id) throws CameraAccessException {
        CameraCharacteristics ch = cm.getCameraCharacteristics(id);
        StringBuilder sb = new StringBuilder();
        Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
        String pos = facing == null ? "未知" : (facing == CameraCharacteristics.LENS_FACING_FRONT ? "前置"
                : (facing == CameraCharacteristics.LENS_FACING_BACK ? "后置" : "外接"));
        sb.append("位置：").append(pos).append(" · 硬件级别：").append(levelName(ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)));
        float[] focal = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focal != null && focal.length > 0) sb.append("\n焦距：").append(String.format(Locale.US, "%.2f", focal[0])).append(" mm");
        SizeF sensor = ch.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
        if (sensor != null) sb.append(String.format(Locale.US, " · 传感器 %.2f×%.2f mm", sensor.getWidth(), sensor.getHeight()));
        StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
            if (sizes != null && sizes.length > 0) {
                Size best = sizes[0];
                for (Size s : sizes) if (s.getWidth() * s.getHeight() > best.getWidth() * best.getHeight()) best = s;
                sb.append("\n最大拍照分辨率：").append(best.getWidth()).append("×").append(best.getHeight());
            }
        }
        Range<Integer>[] fps = ch.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
        if (fps != null && fps.length > 0) {
            int max = 0;
            for (Range<Integer> r : fps) max = Math.max(max, r.getUpper());
            sb.append(" · 最高帧率：").append(max).append(" fps");
        }
        int[] caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean logical = false;
        if (caps != null) for (int cap : caps) if (cap == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) logical = true;
        if (logical && Build.VERSION.SDK_INT >= 28) {
            Set<String> phys = ch.getPhysicalCameraIds();
            sb.append("\n逻辑多摄，由 ").append(phys.size()).append(" 颗镜头组成：").append(join(phys));
        }
        sb.append("\nID：").append(id);
        return new CamInfo("摄像头 " + id, sb.toString());
    }

    private static String levelName(Integer lv) {
        if (lv == null) return "未知";
        switch (lv) {
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY: return "LEGACY";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED: return "LIMITED";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL: return "FULL";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3: return "LEVEL_3";
            case CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL: return "EXTERNAL";
            default: return "其它";
        }
    }

    private static String join(Set<String> s) {
        StringBuilder sb = new StringBuilder();
        for (String x : s) { if (sb.length() > 0) sb.append(" + "); sb.append(x); }
        return sb.toString();
    }

    /** 多摄同时：Android 11 起可以查并发组合；更低版本查不到 */
    private static String multiText(CameraManager cm) throws CameraAccessException {
        if (Build.VERSION.SDK_INT < 30) return "无法查询（系统低于 Android 11）";
        Set<Set<String>> sets = cm.getConcurrentCameraIds();
        int best = 0;
        for (Set<String> s : sets) best = Math.max(best, s.size());
        return best >= 2 ? "支持（最多同开 " + best + " 颗）" : "不支持";
    }

    // ───────────── 版本更新 ─────────────

    private static void buildVersion(SettingsPageView p) {
        Context c = p.ctx();
        LinearLayout c0 = SettingsUi.card(c);
        SettingsUi.addRow(c0, SettingsUi.row(c, "当前版本", null, BuildConfig.VERSION_NAME + "（build " + BuildConfig.VERSION_CODE + "）", null, null));
        View check = SettingsUi.row(c, "检查更新", "暂未开放，需要的话请到发布的地方手动下载", null, null, new Runnable() {
            @Override public void run() { showToast(c, "检查更新暂未开放"); } });
        check.setAlpha(0.45f);
        SettingsUi.addRow(c0, check);
        p.put(c0, 22);

        // 新版本记录：默认展开
        p.put(SettingsUi.subhead(c, "新版本记录"), 8);
        boolean anyNew = false;
        for (ContentData.UpdateEntry e : ContentData.UPDATES) {
            if (!"new".equals(e.group)) continue;
            anyNew = true;
            versionEntry(p, c, e);
        }
        if (!anyNew) p.put(SettingsUi.note(c, "新版本的更新记录正在整理，稍后在这里显示。"), 6);

        // 旧版记录（11.x）：默认折叠，点一下展开
        int oldCount = 0;
        for (ContentData.UpdateEntry e : ContentData.UPDATES) if ("old".equals(e.group)) oldCount++;
        final TextView arrow = SettingsUi.chevron(c);
        arrow.setRotation(p.versionOldExpanded ? 90f : 0f);
        LinearLayout c1 = SettingsUi.card(c);
        SettingsUi.addRow(c1, SettingsUi.row(c, "旧版记录（11.x）",
                "共 " + oldCount + " 条，点一下" + (p.versionOldExpanded ? "收起" : "展开"), null, arrow, new Runnable() {
            @Override public void run() { p.toggleVersionOld(); } }));
        p.put(c1, 18);
        if (p.versionOldExpanded) {
            for (ContentData.UpdateEntry e : ContentData.UPDATES) {
                if ("old".equals(e.group)) versionEntry(p, c, e);
            }
        }
    }

    /** 一个版本：小标题 + 卡片（每条一行） */
    private static void versionEntry(SettingsPageView p, Context c, ContentData.UpdateEntry e) {
        p.put(SettingsUi.subhead(c, "v" + e.version + "　" + e.date), 16);
        LinearLayout card = SettingsUi.card(c);
        for (ContentData.UpdateItem it : e.items) {
            if (it.title.length() == 0) SettingsUi.addRow(card, SettingsUi.row(c, it.body, null, null, null, null));
            else SettingsUi.addRow(card, SettingsUi.row(c, it.title, it.body, null, null, null));
        }
        p.put(card, 6);
    }

    // ───────────── 关于澄影 ─────────────

    private static ImageView rounded(Context c, int resId, int sizeDp, final float radiusDp) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(resId);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setClipToOutline(true);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(v.getContext(), radiusDp));
            } });
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return iv;
    }

    private static void buildAbout(SettingsPageView p) {
        Context c = p.ctx();
        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(LinearLayout.VERTICAL); hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.addView(rounded(c, R.drawable.about_logo, 96, 22));
        TextView title = DualUi.label(c, ContentData.ABOUT_TITLE, 26, PanelTheme.text(c));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2); tp.topMargin = dp(c, 12);
        hero.addView(title, tp);
        TextView ver = DualUi.label(c, ContentData.ABOUT_VER_PREFIX + BuildConfig.VERSION_NAME, 14, PanelTheme.sub(c));
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-2, -2); vp.topMargin = dp(c, 6);
        hero.addView(ver, vp);
        p.put(hero, 22);

        for (ContentData.Section s : ContentData.ABOUT_SECTIONS) {
            TextView h = DualUi.label(c, s.heading, 16, DualUi.YELLOW);
            h.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            h.setPadding(dp(c, 10), 0, dp(c, 10), 0);
            p.put(h, 16);
            p.put(SettingsUi.body(c, s.paragraph), 6);
        }

        LinearLayout devRow = new LinearLayout(c);
        devRow.setOrientation(LinearLayout.HORIZONTAL); devRow.setGravity(Gravity.CENTER_VERTICAL);
        devRow.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        devRow.addView(rounded(c, R.drawable.about_dev, 56, 28));
        LinearLayout names = new LinearLayout(c);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(c, 14), 0, 0, 0);
        names.addView(DualUi.label(c, ContentData.DEV_NAME, 17, PanelTheme.text(c)));
        TextView sub = DualUi.label(c, ContentData.DEV_SUB, 12, PanelTheme.sub(c));
        sub.setPadding(0, dp(c, 3), 0, 0);
        names.addView(sub);
        devRow.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout card = SettingsUi.card(c);
        SettingsUi.addRow(card, devRow);
        p.put(card, 18);
    }

    // ───────────── 赞赏 ─────────────

    private static void buildDonate(final SettingsPageView p) {
        final Context c = p.ctx();
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL); head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.addView(DualUi.label(c, ContentData.DONATE_EN, 11, DualUi.YELLOW));
        TextView hero = DualUi.label(c, ContentData.DONATE_HERO_TITLE, 24, PanelTheme.text(c));
        hero.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); hero.setGravity(Gravity.CENTER);
        head.addView(hero);
        TextView sub = DualUi.label(c, ContentData.DONATE_HERO_SUB, 14, PanelTheme.sub(c)); sub.setGravity(Gravity.CENTER);
        head.addView(sub);
        p.put(head, 20);

        TextView lead = DualUi.label(c, ContentData.DONATE_MODAL_TITLE + "\n" + ContentData.DONATE_LEAD, 15, PanelTheme.text(c));
        lead.setGravity(Gravity.CENTER); lead.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        p.put(lead, 10);
        TextView quote = DualUi.label(c, ContentData.DONATE_QUOTE, 13, PanelTheme.sub(c));
        quote.setGravity(Gravity.CENTER); quote.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        p.put(quote, 18);

        ImageView qr = rounded(c, R.drawable.donate_qr, 260, 18);
        qr.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { saveQr(c); return true; } });
        FrameLayout qrBox = new FrameLayout(c);
        qrBox.addView(qr, new FrameLayout.LayoutParams(dp(c, 260), dp(c, 260), Gravity.CENTER_HORIZONTAL));
        p.put(qrBox, 10);

        TextView scan = DualUi.label(c, ContentData.DONATE_SCAN, 12.5f, PanelTheme.note(c));
        scan.setGravity(Gravity.CENTER);
        p.put(scan, 14);

        LinearLayout chips = new LinearLayout(c);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        for (String ch : ContentData.DONATE_CHIPS) {
            TextView t = DualUi.label(c, ch, 12.5f, PanelTheme.text(c));
            t.setGravity(Gravity.CENTER);
            t.setBackgroundColor(PanelTheme.hover(c));
            t.setPadding(dp(c, 6), dp(c, 9), dp(c, 6), dp(c, 9));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.leftMargin = dp(c, 4); lp.rightMargin = dp(c, 4);
            chips.addView(t, lp);
        }
        LinearLayout chipBox = new LinearLayout(c);
        chipBox.setPadding(dp(c, 16), 0, dp(c, 16), 0);
        chipBox.addView(chips, new LinearLayout.LayoutParams(-1, -2));
        p.put(chipBox, 20);

        LinearLayout card = SettingsUi.card(c);
        SettingsUi.addRow(card, SettingsUi.row(c, ContentData.DONATE_SAVE, "长按上面的二维码也可以保存", null, SettingsUi.chevron(c), new Runnable() {
            @Override public void run() { saveQr(c); } }));
        p.put(card, 18);

        TextView foot = DualUi.label(c, ContentData.DONATE_FOOT, 12, PanelTheme.note(c));
        foot.setGravity(Gravity.CENTER);
        p.put(foot, 10);
    }

    /** 把赞赏码写进系统相册。Android 10 起用 MediaStore（不需要存储权限）；9 及以下需要 WRITE_EXTERNAL_STORAGE（清单里已声明到 28） */
    private static void saveQr(Context c) {
        try {
            Bitmap bm = BitmapFactory.decodeResource(c.getResources(), R.drawable.donate_qr);
            if (bm == null) { showToast(c, "赞赏码找不到"); return; }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "lucida-donate-qr.jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/澄影");
            Uri u = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (u == null) throw new RuntimeException("无法写入相册");
            OutputStream os = c.getContentResolver().openOutputStream(u);
            if (os == null) throw new RuntimeException("无法打开相册文件");
            try { bm.compress(Bitmap.CompressFormat.JPEG, 100, os); } finally { os.close(); }
            showToast(c, "已保存到系统相册");
        } catch (Throwable t) {
            showToast(c, "保存失败：请在系统设置里允许澄影访问存储");
        }
    }
}

package com.lucida.camera;

import android.app.Application;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 原生日志收集器（和 iOS 的 AppLog.swift 同名同接口）。
 * 内核里直接调用：AppLog.info("引擎", "…") / AppLog.warn(…) / AppLog.error(…)。任意线程都能调。
 * - 内存环形缓冲 800 条；最近 200 条持久化到 files/lucida-log.json（App 被杀、闪退后下次启动还能看到，标「上次运行」）。
 * - 要持久化，需在 DualCameraActivity.onCreate 开头调一次 AppLog.init(this)（没调时只记在内存里，之后调了会补上）。
 * - 自动记录：启动信息、系统内存紧张（onTrimMemory）、未捕获异常（崩溃堆栈，写完再交给原处理器）。
 * - 日志页（M3 做）用 entries(filter) / text(filter) / exportFile(ctx, filter) / clear()。
 */
final class AppLog {
    private AppLog() {}

    static final int INFO = 0, WARN = 1, ERROR = 2;
    /** 日志页的筛选：全部 / 错误 / 警告 / 信息 */
    static final int F_ALL = 0, F_ERROR = 1, F_WARN = 2, F_INFO = 3;
    static final int CAPACITY = 800;        // 内存最多保留条数
    static final int PERSIST_COUNT = 200;   // 落盘条数
    static final int MAX_MESSAGE = 3000;    // 单条最长字数

    static final class Entry {
        final long time;      // 毫秒时间戳
        final int level;
        final String tag, msg;
        boolean prev;         // true = 上一次运行留下的
        Entry(long time, int level, String tag, String msg, boolean prev) { this.time = time; this.level = level; this.tag = tag; this.msg = msg; this.prev = prev; }
        String levelLetter() { return level == ERROR ? "E" : (level == WARN ? "W" : "I"); }
        String levelTitle() { return level == ERROR ? "错误" : (level == WARN ? "警告" : "信息"); }
    }

    /** 日志有变化时回调（任意线程；日志页收到后自己切主线程刷新） */
    interface Listener { void onLogChanged(); }

    static String filterTitle(int f) { return f == F_ERROR ? "错误" : (f == F_WARN ? "警告" : (f == F_INFO ? "信息" : "全部")); }
    static boolean accepts(int f, int level) {
        return f == F_ALL || (f == F_ERROR && level == ERROR) || (f == F_WARN && level == WARN) || (f == F_INFO && level == INFO);
    }

    // ───── 对外接口 ─────
    static void info(String tag, String msg) { add(INFO, tag, msg, false); }
    static void warn(String tag, String msg) { add(WARN, tag, msg, false); }
    static void error(String tag, String msg) { add(ERROR, tag, msg, false); }

    /** 在 Activity.onCreate 开头调用一次（可重复调用，只有第一次生效）。读取上次的日志、写启动信息、挂上崩溃钩子 */
    static synchronized void init(Context c) {
        if (appCtx != null || c == null) return;
        appCtx = c.getApplicationContext();
        file = new File(appCtx.getFilesDir(), "lucida-log.json");
        try { on = appCtx.getSharedPreferences("lucida_dual", Context.MODE_PRIVATE).getString("logOn", "1").equals("1"); } catch (Throwable t) { on = true; }
        List<Entry> old = loadFromDisk();
        buf.addAll(0, old);                     // 上次运行的排在前面；init 之前已记在内存里的保留在后面
        trim();
        HandlerThread ht = new HandlerThread("lucida-log"); ht.start();
        io = new Handler(ht.getLooper());
        addLocked(INFO, "App", "—— 新的一次运行 · 澄影重构版 " + BuildConfig.VERSION_NAME + "（" + BuildConfig.VERSION_CODE + "） · "
                + Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE + "（API " + Build.VERSION.SDK_INT + "）", true);
        installCrashHook();
        try {
            ((Application) appCtx).registerComponentCallbacks(new ComponentCallbacks2() {
                @Override public void onTrimMemory(int level) {
                    if (level >= TRIM_MEMORY_RUNNING_LOW) warn("系统", "系统内存紧张（onTrimMemory level=" + level + "），App 可能随后被关闭");
                }
                @Override public void onConfigurationChanged(Configuration newConfig) { }
                @Override public void onLowMemory() { warn("系统", "系统内存严重不足（onLowMemory）"); }
            });
        } catch (Throwable ignore) { }
    }

    /** 记录开关（日志页里的「记录日志」）。关闭后不再记录新日志 */
    static synchronized boolean enabled() { return on; }
    static void setEnabled(boolean v) {
        if (!v) addLocked(INFO, "日志", "日志记录已关闭", true);
        synchronized (AppLog.class) {
            on = v;
            if (appCtx != null) appCtx.getSharedPreferences("lucida_dual", Context.MODE_PRIVATE).edit().putString("logOn", v ? "1" : "0").apply();
        }
        if (v) addLocked(INFO, "日志", "日志记录已开启", true);
    }

    static synchronized List<Entry> entries(int filter) {
        List<Entry> out = new ArrayList<Entry>();
        for (Entry e : buf) if (accepts(filter, e.level)) out.add(e);
        return out;
    }

    static int count(int filter) { return entries(filter).size(); }

    /** 整理成可复制 / 导出的文字 */
    static String text(int filter) {
        List<Entry> list = entries(filter);
        SimpleDateFormat df = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault());
        StringBuilder sb = new StringBuilder();
        sb.append("澄影重构版 ").append(BuildConfig.VERSION_NAME).append("（").append(BuildConfig.VERSION_CODE).append("） · ")
          .append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" · Android ").append(Build.VERSION.RELEASE).append("\n");
        sb.append("筛选：").append(filterTitle(filter)).append(" · 共 ").append(list.size()).append(" 条\n\n");
        Boolean lastPrev = null;
        for (Entry e : list) {
            if (lastPrev == null || lastPrev.booleanValue() != e.prev) { sb.append(e.prev ? "—— 上次运行 ——\n" : "—— 本次运行 ——\n"); lastPrev = Boolean.valueOf(e.prev); }
            sb.append(df.format(new Date(e.time))).append(" ").append(e.levelLetter()).append("/").append(e.tag).append("：").append(e.msg).append("\n");
        }
        return sb.toString();
    }

    /** 写成缓存目录里的 txt 文件（交给分享）；失败返回 null */
    static File exportFile(Context c, int filter) {
        try {
            File dir = new File(c.getCacheDir(), "logs"); dir.mkdirs();
            File f = new File(dir, "lucida-log-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
            FileOutputStream os = new FileOutputStream(f);
            try { os.write(text(filter).getBytes("UTF-8")); } finally { os.close(); }
            return f;
        } catch (Throwable t) { return null; }
    }

    static void clear() {
        synchronized (AppLog.class) { buf.clear(); if (file != null) file.delete(); }
        addLocked(INFO, "日志", "日志已清空", true);
    }

    /** 立刻把最近 200 条写到磁盘 */
    static void flush() { writeNow(); }

    static synchronized void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    static synchronized void removeListener(Listener l) { listeners.remove(l); }

    // ───── 内部 ─────
    private static final List<Entry> buf = new ArrayList<Entry>();
    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static Context appCtx;
    private static File file;
    private static boolean on = true;
    private static Handler io;
    private static boolean notifyPending;
    private static boolean hookInstalled;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable persistTask = new Runnable() { @Override public void run() { writeNow(); } };
    private static final Runnable notifyTask = new Runnable() { @Override public void run() {
        List<Listener> ls;
        synchronized (AppLog.class) { notifyPending = false; ls = new ArrayList<Listener>(listeners); }
        for (Listener l : ls) { try { l.onLogChanged(); } catch (Throwable ignore) { } }
    } };

    private static void add(int level, String tag, String msg, boolean force) { addLocked(level, tag, msg, force); }

    private static void addLocked(int level, String tag, String msg, boolean force) {
        boolean urgent = level == ERROR;
        synchronized (AppLog.class) {
            if (!on && !force) return;
            String m = msg == null ? "" : msg;
            if (m.length() > MAX_MESSAGE) m = m.substring(0, MAX_MESSAGE) + "…";
            buf.add(new Entry(System.currentTimeMillis(), level, tag == null ? "" : tag, m, false));
            trim();
            if (io != null) { io.removeCallbacks(persistTask); io.postDelayed(persistTask, urgent ? 150 : 1000); }
            if (!notifyPending && !listeners.isEmpty()) { notifyPending = true; main.postDelayed(notifyTask, 300); }
        }
    }

    private static void trim() { while (buf.size() > CAPACITY) buf.remove(0); }

    private static void writeNow() {
        List<Entry> slice;
        File f;
        synchronized (AppLog.class) {
            f = file;
            if (f == null) return;
            int from = Math.max(0, buf.size() - PERSIST_COUNT);
            slice = new ArrayList<Entry>(buf.subList(from, buf.size()));
        }
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : slice) {
                JSONObject o = new JSONObject();
                o.put("t", e.time); o.put("l", e.level); o.put("g", e.tag); o.put("m", e.msg);
                arr.put(o);
            }
            File tmp = new File(f.getPath() + ".tmp");
            FileWriter w = new FileWriter(tmp);
            try { w.write(arr.toString()); } finally { w.close(); }
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f); }
        } catch (Throwable ignore) { }
    }

    private static List<Entry> loadFromDisk() {
        List<Entry> out = new ArrayList<Entry>();
        try {
            if (file == null || !file.exists()) return out;
            java.io.FileInputStream is = new java.io.FileInputStream(file);
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            try { byte[] b = new byte[8192]; int n; while ((n = is.read(b)) > 0) bo.write(b, 0, n); } finally { is.close(); }
            JSONArray arr = new JSONArray(new String(bo.toByteArray(), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optLong("t", 0), o.optInt("l", 0), o.optString("g", ""), o.optString("m", ""), true));
            }
        } catch (Throwable ignore) { }
        return out;
    }

    /** 未捕获异常（崩溃）：先把堆栈写进日志并落盘，再交给原来的处理器 */
    private static void installCrashHook() {
        if (hookInstalled) return;
        hookInstalled = true;
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    addLocked(ERROR, "崩溃", "线程 " + t.getName() + " 未捕获的异常：\n" + Log.getStackTraceString(e), true);
                    writeNow();
                } catch (Throwable ignore) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }
}

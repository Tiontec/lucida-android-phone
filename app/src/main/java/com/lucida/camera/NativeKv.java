package com.lucida.camera;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 原生存储（长期计划 A2）：网页里所有 lucida.* 设置的权威存档放在原生（filesDir/lucida-kv.json），网页的 localStorage 只是镜像：
 * 页面启动时网页向原生读取存档，之后每次改动都同步回原生。升级 / 清理网页缓存不丢设置；原生界面（如双摄的预设顺序）和网页读写同一份数据。
 */
final class NativeKv {
    private static NativeKv inst;
    static synchronized NativeKv of(Context c) { if (inst == null) inst = new NativeKv(c.getApplicationContext()); return inst; }

    private final File file;
    private final Map<String, String> items = new HashMap<String, String>();
    private final Map<String, String> nativeChanged = new HashMap<String, String>();
    private boolean seeded;
    private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable writer = new Runnable() { @Override public void run() { writeNow(); } };

    private NativeKv(Context c) {
        file = new File(c.getFilesDir(), "lucida-kv.json");
        try {
            if (file.exists()) {
                FileInputStream in = new FileInputStream(file);
                ByteArrayOutputStream bo = new ByteArrayOutputStream(); byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                in.close();
                JSONObject o = new JSONObject(new String(bo.toByteArray(), "UTF-8"));
                seeded = o.optBoolean("seeded", false);
                JSONObject it = o.optJSONObject("items");
                if (it != null) for (Iterator<String> i = it.keys(); i.hasNext(); ) { String k = i.next(); items.put(k, it.optString(k, "")); }
            }
        } catch (Throwable ignore) {}
    }

    synchronized String get(String k) { return items.get(k); }

    /** 给网页：{"seeded":bool,"items":{...}} */
    synchronized String allJson() {
        try { JSONObject o = new JSONObject(); o.put("seeded", seeded); o.put("items", new JSONObject(items)); return o.toString(); }
        catch (Throwable t) { return "{\"seeded\":false,\"items\":{}}"; }
    }

    synchronized void webSet(String k, String v) { items.put(k, v); schedule(); }
    synchronized void webRemove(String k) { items.remove(k); schedule(); }
    synchronized void webClear() { items.clear(); schedule(); }
    synchronized void webSeed(String json) {
        try { JSONObject it = new JSONObject(json); for (Iterator<String> i = it.keys(); i.hasNext(); ) { String k = i.next(); if (!items.containsKey(k)) items.put(k, it.optString(k, "")); } } catch (Throwable ignore) {}
        seeded = true; schedule();
    }

    /** 原生界面改的：存档，并记下来，回到网页时通知它 */
    synchronized void setNative(String k, String v) { items.put(k, v); nativeChanged.put(k, v); schedule(); }
    synchronized Map<String, String> takeNativeChanges() { Map<String, String> c = new HashMap<String, String>(nativeChanged); nativeChanged.clear(); return c; }

    private void schedule() { h.removeCallbacks(writer); h.postDelayed(writer, 400); }
    synchronized void flush() { h.removeCallbacks(writer); writeNow(); }
    private synchronized void writeNow() {
        try {
            JSONObject o = new JSONObject(); o.put("seeded", seeded); o.put("items", new JSONObject(items));
            File tmp = new File(file.getParentFile(), "lucida-kv.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp); out.write(o.toString().getBytes("UTF-8")); out.getFD().sync(); out.close();
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file); }
        } catch (Throwable ignore) {}
    }
}

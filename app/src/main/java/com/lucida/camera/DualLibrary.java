package com.lucida.camera;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 双摄相册和系统相册的桥（和苹果版 DualLibrary 同一个思路）：
 *  · 双摄拍到的照片 / 动态照片 / 视频存进系统相册（MediaStore）时，把它的 content:// 地址登记下来（重开 App 还在）
 *  · 双摄相册 = 按登记表去系统相册取这些文件。所以在系统相册里删掉的，这里自然不再出现；
 *    在双摄相册里删，是直接删系统相册里的那一份。
 */
final class DualLibrary {
    private DualLibrary() {}

    private static final String PREF = "lucida_dual", KEY = "assets";

    static final class Entry {
        String id;                   // = uri 字符串
        Uri uri;
        boolean video, live;
        long videoLen;               // 动态照片：内嵌视频的字节数（从文件末尾往回数）
    }

    // ------------------------------------------------------------ 登记表（旧 → 新）

    static synchronized ArrayList<String> ids(Context c) {
        ArrayList<String> out = new ArrayList<String>();
        try {
            JSONArray a = new JSONArray(c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < a.length(); i++) out.add(a.getString(i));
        } catch (Exception ignore) {}
        return out;
    }

    private static void save(Context c, List<String> l) {
        JSONArray a = new JSONArray();
        for (String s : l) a.put(s);
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, a.toString()).apply();
    }

    static synchronized void add(Context c, Uri u) {
        if (u == null) return;
        ArrayList<String> l = ids(c);
        String s = u.toString();
        if (!l.contains(s)) { l.add(s); save(c, l); }
    }

    static synchronized void remove(Context c, Collection<String> gone) {
        if (gone.isEmpty()) return;
        ArrayList<String> l = ids(c);
        l.removeAll(new HashSet<String>(gone));
        save(c, l);
    }

    // ------------------------------------------------------------ 读取（后台线程调用）

    /** 登记过、系统相册里还在的项目，新 → 旧；顺带把系统里已经没有的从登记表里清掉。 */
    static ArrayList<Entry> load(Context c) {
        ContentResolver cr = c.getContentResolver();
        ArrayList<String> all = ids(c);
        ArrayList<Entry> out = new ArrayList<Entry>();
        ArrayList<String> gone = new ArrayList<String>();
        for (int i = all.size() - 1; i >= 0; i--) {
            String id = all.get(i);
            Uri u = Uri.parse(id);
            String mime = null; boolean exists = false;
            try (Cursor q = cr.query(u, new String[]{MediaStore.MediaColumns.MIME_TYPE}, null, null, null)) {
                if (q != null && q.moveToFirst()) { exists = true; mime = q.getString(0); }
            } catch (SecurityException e) { continue; }      // 读不了（权限问题）：先不动登记
            catch (Exception e) { continue; }
            if (!exists) { gone.add(id); continue; }
            Entry e = new Entry(); e.id = id; e.uri = u;
            e.video = mime != null && mime.startsWith("video/");
            if (!e.video) { e.videoLen = motionVideoLen(c, u); e.live = e.videoLen > 0; }
            out.add(e);
        }
        remove(c, gone);
        return out;
    }

    private static final Pattern OFFSET = Pattern.compile("MicroVideoOffset=\"(\\d+)\"");

    /** 动态照片（MicroVideo）：读文件头里的 XMP，取出内嵌视频的长度；不是动态照片返回 0 */
    static long motionVideoLen(Context c, Uri u) {
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in == null) return 0;
            byte[] b = new byte[90000]; int n = 0, r;
            while (n < b.length && (r = in.read(b, n, b.length - n)) > 0) n += r;
            Matcher m = OFFSET.matcher(new String(b, 0, n, "ISO-8859-1"));
            return m.find() ? Long.parseLong(m.group(1)) : 0;
        } catch (Throwable t) { return 0; }
    }

    /** 把动态照片里内嵌的视频取出来放到缓存目录，返回文件（后台线程调用） */
    static File extractClip(Context c, Entry e) {
        try (InputStream in = c.getContentResolver().openInputStream(e.uri)) {
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            byte[] all = bo.toByteArray();
            if (e.videoLen <= 0 || e.videoLen >= all.length) return null;
            File f = new File(DualCameraActivity.outDir(c), "live_" + Math.abs(e.id.hashCode()) + ".mp4");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(all, (int) (all.length - e.videoLen), (int) e.videoLen); }
            return f;
        } catch (Throwable t) { return null; }
    }

    // ------------------------------------------------------------ 删除

    /** 直接删系统相册里的这些项；返回「系统要求用户确认才能删」的那几项（不是本 App 创建的） */
    static ArrayList<Uri> delete(Context c, List<Entry> list) {
        ContentResolver cr = c.getContentResolver();
        ArrayList<Uri> need = new ArrayList<Uri>();
        ArrayList<String> done = new ArrayList<String>();
        for (Entry e : list) {
            try { cr.delete(e.uri, null, null); done.add(e.id); }
            catch (SecurityException s) { need.add(e.uri); }
            catch (Exception ex) { /* 删不掉就保留 */ }
        }
        remove(c, done);
        return need;
    }
}

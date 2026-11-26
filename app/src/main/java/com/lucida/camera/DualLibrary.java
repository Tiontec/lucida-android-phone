package com.lucida.camera;

import android.content.ContentResolver;
import android.content.ContentUris;
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
        // 系统相册里的其它照片 / 视频（需要读媒体权限；没授权就只显示澄影拍的）
        if (canReadSystem(c)) appendSystem(c, out, all);
        return out;
    }

    /** 读系统相册需要的权限（安卓 13 以上是 READ_MEDIA_*，以下是 READ_EXTERNAL_STORAGE） */
    static String[] readPerms() {
        if (android.os.Build.VERSION.SDK_INT >= 33)
            return new String[]{android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO};
        return new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    static boolean canReadSystem(Context c) {
        for (String p : readPerms()) {
            if (c.checkSelfPermission(p) != android.content.pm.PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** 按时间倒序把系统相册里不是澄影拍的照片 / 视频追加到 out（最多 2000 条；不做动态照片检测，省时间） */
    private static void appendSystem(Context c, ArrayList<Entry> out, ArrayList<String> known) {
        java.util.HashSet<String> seen = new java.util.HashSet<String>(known);
        for (Entry e : out) seen.add(e.id);
        Uri files = MediaStore.Files.getContentUri("external");
        String[] proj = {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE};
        String sel = MediaStore.Files.FileColumns.MEDIA_TYPE + "=? OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=?";
        String[] args = {String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE), String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)};
        try (Cursor q = c.getContentResolver().query(files, proj, sel, args, MediaStore.Files.FileColumns.DATE_ADDED + " DESC")) {
            if (q == null) return;
            int n = 0;
            while (q.moveToNext() && n < 2000) {
                long rid = q.getLong(0); int mt = q.getInt(1);
                boolean video = mt == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                Uri base = video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                Uri u = ContentUris.withAppendedId(base, rid);
                String id = u.toString();
                if (seen.contains(id)) continue;
                seen.add(id);
                Entry e = new Entry(); e.id = id; e.uri = u; e.video = video;
                out.add(e); n++;
            }
        } catch (Exception ignore) { }
    }

    private static final Pattern OFFSET = Pattern.compile("MicroVideoOffset=\"(\\d+)\"");

    private static final java.util.HashMap<String, Long> LEN_CACHE = new java.util.HashMap<String, Long>();

    /**
     * 动态照片：内嵌视频的长度（从文件末尾往回数）；不是动态照片返回 0。
     * 先读文件头里的 XMP（MicroVideoOffset）；读不到（有的系统会改写 / 抹掉 XMP）就在文件里找 MP4 的 "ftyp" 标记兜底。结果记在内存里，不重复读。
     */
    static long motionVideoLen(Context c, Uri u) {
        String key = u.toString();
        synchronized (LEN_CACHE) { Long hit = LEN_CACHE.get(key); if (hit != null) return hit; }
        long len = 0;
        try (InputStream in = c.getContentResolver().openInputStream(u)) {
            if (in != null) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0 && bo.size() < 80 * 1024 * 1024) bo.write(buf, 0, n);
                byte[] all = bo.toByteArray();
                int head = Math.min(all.length, 90000);
                Matcher m = OFFSET.matcher(new String(all, 0, head, "ISO-8859-1"));
                if (m.find()) len = Long.parseLong(m.group(1));
                if (len <= 0 || len >= all.length) {
                    len = 0;
                    int at = findMp4(all);
                    if (at > 0) len = all.length - at;
                }
            }
        } catch (Throwable t) { AppLog.warn("相册", "检测动态照片失败：" + t); }
        if (len > 0) AppLog.info("相册", "动态照片：内嵌视频 " + len + " 字节");
        synchronized (LEN_CACHE) { LEN_CACHE.put(key, len); }
        return len;
    }

    /** 在 JPEG 之后找内嵌 MP4 的起点（"ftyp" 前面 4 个字节是盒子长度）；找不到返回 -1 */
    private static int findMp4(byte[] a) {
        for (int i = 2000; i + 12 < a.length; i++) {
            if (a[i] == 'f' && a[i + 1] == 't' && a[i + 2] == 'y' && a[i + 3] == 'p') {
                int size = ((a[i - 4] & 0xFF) << 24) | ((a[i - 3] & 0xFF) << 16) | ((a[i - 2] & 0xFF) << 8) | (a[i - 1] & 0xFF);
                boolean brand = a[i + 4] >= 'a' && a[i + 4] <= 'z' && a[i + 5] >= 'a' && a[i + 5] <= 'z';
                if (size >= 16 && size <= 128 && brand) return i - 4;
            }
        }
        return -1;
    }

    /** 删掉这张的检测缓存（删除 / 重新保存时用） */
    static void forget(String id) { synchronized (LEN_CACHE) { LEN_CACHE.remove(id); } }

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

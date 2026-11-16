package com.lucida.camera;

import android.content.Context;

import org.json.JSONArray;

import java.util.ArrayList;

/**
 * 调色盘预设的排列顺序：和主页面是同一个存档键 lucida.order2（JSON 数组，存在原生存储 NativeKv 里，网页和原生互相读得到）。
 * 左右滑动 / 页码圆点按它走；「标准」永远固定在第一个。
 */
final class PresetOrder {
    private PresetOrder() {}
    private static final String KEY = "lucida.order2";

    /** 按存档顺序排好的预设下标（指向 GradeState.PRESETS）；存档不完整 / 不匹配就回到默认顺序 */
    static int[] indices(Context c) {
        int n = GradeState.PRESETS.length;
        ArrayList<Integer> out = new ArrayList<Integer>();
        boolean[] used = new boolean[n];
        String s = NativeKv.of(c).get(KEY);
        if (s != null) {
            try {
                JSONArray a = new JSONArray(s);
                if (a.length() == n) {
                    for (int k = 0; k < n; k++) {
                        String sid = a.optString(k, "");
                        for (int i = 0; i < n; i++) if (!used[i] && GradeState.PRESETS[i].sid.equals(sid)) { used[i] = true; out.add(i); break; }
                    }
                }
            } catch (Throwable ignore) {}
        }
        if (out.size() != n) { out.clear(); used = new boolean[n]; for (int i = 0; i < n; i++) out.add(i); }
        int std = 0; for (int i = 0; i < n; i++) if ("standard".equals(GradeState.PRESETS[i].sid)) std = i;
        out.remove(Integer.valueOf(std)); out.add(0, std);               // 标准固定第一
        int[] r = new int[n]; for (int i = 0; i < n; i++) r[i] = out.get(i);
        return r;
    }

    static void save(Context c, int[] idx) {
        JSONArray a = new JSONArray();
        for (int i = 0; i < idx.length; i++) a.put(GradeState.PRESETS[idx[i]].sid);
        NativeKv.of(c).setNative(KEY, a.toString());
    }

    static void reset(Context c) {
        int[] d = new int[GradeState.PRESETS.length]; for (int i = 0; i < d.length; i++) d[i] = i;
        save(c, d);
    }

    static int posOf(int[] idx, int presetIndex) { for (int i = 0; i < idx.length; i++) if (idx[i] == presetIndex) return i; return 0; }
}

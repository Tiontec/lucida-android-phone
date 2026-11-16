package com.lucida.camera;

import java.util.ArrayList;

/** 调色盘预设的排列顺序（左右滑动 / 页码圆点按它走；「标准」永远固定在第一个）。存成逗号分隔的 sid。 */
final class PresetOrder {
    private PresetOrder() {}

    /** 按设置里存的顺序排好的预设下标（指向 GradeState.PRESETS）；存档不完整 / 不匹配就回到默认顺序 */
    static int[] indices(DualPrefs p) {
        int n = GradeState.PRESETS.length;
        ArrayList<Integer> out = new ArrayList<Integer>();
        boolean[] used = new boolean[n];
        if (p.order != null && p.order.length() > 0) {
            for (String sid : p.order.split(",")) {
                for (int i = 0; i < n; i++) if (!used[i] && GradeState.PRESETS[i].sid.equals(sid)) { used[i] = true; out.add(i); break; }
            }
        }
        for (int i = 0; i < n; i++) if (!used[i]) out.add(i);          // 新增 / 缺失的补在后面
        int std = 0; for (int i = 0; i < n; i++) if ("standard".equals(GradeState.PRESETS[i].sid)) std = i;
        out.remove(Integer.valueOf(std)); out.add(0, std);               // 标准固定第一
        int[] r = new int[n]; for (int i = 0; i < n; i++) r[i] = out.get(i);
        return r;
    }

    static void save(DualPrefs p, int[] idx) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < idx.length; i++) { if (i > 0) sb.append(','); sb.append(GradeState.PRESETS[idx[i]].sid); }
        p.order = sb.toString(); p.save();
    }

    static void reset(DualPrefs p) { p.order = ""; p.save(); }

    static int posOf(int[] idx, int presetIndex) { for (int i = 0; i < idx.length; i++) if (idx[i] == presetIndex) return i; return 0; }
}

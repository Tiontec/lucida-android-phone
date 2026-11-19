package com.lucida.camera;

/**
 * 设置项的统一存取（按注册表里的 id，和 iOS 的 SettingsStore 一致）。值一律是字符串：开关 "1"/"0"，数字就是数字文字，单选就是选项的 v。
 * - 已有的真实设置（res / fps / glass / liveDur …）转到 DualPrefs 的原字段（改完调 save()）；
 * - 新增的键（pipDragFps / sLiveDur / zoomRange …）直接存进同一个 SharedPreferences（lucida_dual），不改动 DualPrefs.java 本身。
 * 暂时不能用（soon）的项也已经定义好键和默认值，以后接线直接用下面的类型化读法即可。
 *
 * 用法：SettingsStore st = new SettingsStore(prefs);  st.pipDragFps();  st.set("zoomRange", "50");
 */
final class SettingsStore {
    private final DualPrefs p;
    SettingsStore(DualPrefs p) { this.p = p; }

    private static String b(boolean v) { return v ? "1" : "0"; }

    /** 默认值（注册表里的 def；注册表没有的额外键看 EXTRA_DEFAULTS） */
    static String defaultValue(String id) {
        SettingsRegistry.Item it = SettingsRegistry.item(id);
        if (it != null) return it.def;
        for (String[] kv : SettingsData.EXTRA_DEFAULTS) if (kv[0].equals(id)) return kv[1];
        return "";
    }

    /** 当前值是否合法（非法时 get 返回默认值） */
    private static boolean valid(String id, String v) {
        SettingsRegistry.Item it = SettingsRegistry.item(id);
        if (it != null) {
            switch (it.type) {
                case SettingsRegistry.T_SWITCH: return v.equals("0") || v.equals("1");
                case SettingsRegistry.T_SEG: case SettingsRegistry.T_RADIO:
                    for (SettingsRegistry.Option o : it.options) if (o.v.equals(v)) return true;
                    return false;
                case SettingsRegistry.T_SLIDER:
                    try { int n = Integer.parseInt(v); return n >= it.min && n <= it.max; } catch (NumberFormatException e) { return false; }
                default: return true;
            }
        }
        return v.equals("0") || v.equals("1");
    }

    String get(String id) {
        // ── 已有的真实设置 ──
        if (id.equals("res")) return String.valueOf(p.res);
        if (id.equals("fps")) return String.valueOf(p.fps);
        if (id.equals("heif")) return b(p.heif);
        if (id.equals("full")) return b(p.fullGate);
        if (id.equals("border")) return String.valueOf(p.pipBorder);
        if (id.equals("mirror")) return b(p.mirror);
        if (id.equals("audio")) return b(p.audio);
        if (id.equals("glass")) return b(p.glass);
        if (id.equals("live")) return b(p.live);
        if (id.equals("liveDur")) return String.valueOf(p.liveDur);
        if (id.equals("liveStill")) return b(p.liveSeparate);
        if (id.equals("liveTimer")) return b(p.liveTimer);
        if (id.equals("fullRate")) return b(p.fullRate);
        if (id.equals("split")) return b(p.style == DualState.STYLE_SPLIT);
        if (id.equals("dualOn")) return b(p.dualOn);
        if (id.equals("singleFront")) return b(p.singleFront);
        // ── 新增的键 ──
        try {
            Object o = p.sp.getAll().get(id);
            if (o != null) { String v = String.valueOf(o); if (valid(id, v)) return v; }
        } catch (Throwable ignore) { }
        return defaultValue(id);
    }

    boolean bool(String id) { return get(id).equals("1"); }

    int intValue(String id) {
        try { return Integer.parseInt(get(id)); } catch (NumberFormatException e) { }
        try { return Integer.parseInt(defaultValue(id)); } catch (NumberFormatException e) { return 0; }
    }

    private static int toInt(String v, int def) { try { return Integer.parseInt(v); } catch (NumberFormatException e) { return def; } }

    void set(String id, String v) {
        boolean on = v.equals("1");
        if (id.equals("res")) { p.res = toInt(v, 1080); p.save(); }
        else if (id.equals("fps")) { p.fps = toInt(v, 30); p.save(); }
        else if (id.equals("heif")) { p.heif = on; p.save(); }
        else if (id.equals("full")) { p.fullGate = on; p.save(); }
        else if (id.equals("border")) { p.pipBorder = Math.max(0, Math.min(2, toInt(v, 0))); p.save(); }
        else if (id.equals("mirror")) { p.mirror = on; p.save(); }
        else if (id.equals("audio")) { p.audio = on; p.save(); }
        else if (id.equals("glass")) { p.glass = on; DualUi.glass = on; p.save(); }
        else if (id.equals("live")) { p.live = on; p.save(); }
        else if (id.equals("liveDur")) { p.liveDur = toInt(v, 3); p.save(); }
        else if (id.equals("liveStill")) { p.liveSeparate = on; p.save(); }
        else if (id.equals("liveTimer")) { p.liveTimer = on; p.save(); }
        else if (id.equals("fullRate")) { p.fullRate = on; p.save(); }
        else if (id.equals("split")) { p.style = on ? DualState.STYLE_SPLIT : DualState.STYLE_PIP; p.save(); }
        else if (id.equals("dualOn")) { p.dualOn = on; p.save(); }
        else if (id.equals("singleFront")) { p.singleFront = on; p.save(); }
        else if (valid(id, v)) { p.sp.edit().putString(id, v).apply(); }
    }

    // ───── 新增设置键的类型化读法（Claude A 在内核里直接用） ─────
    /** 双摄小窗滑动帧率 30 / 60 / 120（默认 60）—— 由 A 接线 */
    int pipDragFps() { return intValue("pipDragFps"); }

    // 单摄的实况（和双摄的 liveDur / liveSeparate / liveTimer 各自独立）—— 由 A 接线
    int sLiveDur() { return intValue("sLiveDur"); }
    boolean sLiveStill() { return bool("sLiveStill"); }
    boolean sLiveTimer() { return bool("sLiveTimer"); }
    int sLiveFps() { return intValue("sLiveFps"); }

    // 变焦与镜头（暂不可用，键已定义）
    int zoomSpeed() { return intValue("zoomSpeed"); }
    String uwScale() { return get("uwScale"); }
    String teleScale() { return get("teleScale"); }
    String zoomFrame() { return get("zoomFrame"); }
    String lensSwitch() { return get("lensSwitch"); }
    int zoomRange() { return intValue("zoomRange"); }

    // 其它（暂不可用，键已定义）
    String panoBlend() { return get("panoBlend"); }
    boolean directRecord() { return bool("direct"); }
    String skin() { return get("skin"); }
    String panelTheme() { return get("panelTheme"); }
    int uiScale() { return intValue("uiScale"); }
    int fontScale() { return intValue("fontScale"); }
    boolean uiReset() { return bool("uiReset"); }
    boolean logOn() { return bool("logOn"); }
}

package com.lucida.camera;

import android.content.SharedPreferences;

/**
 * 调色盘状态 + 着色器参数换算（语义照抄主页面 HTML：PRESETS / gradeParams / texParams）。
 * 纯计算，不依赖 GL；GlComposer 每帧读 params() 的结果。
 */
final class GradeState {
    static final class Preset {
        final String sid, name, grp; final int t, c, s; final float[] tint, cast, tint2; final float ca, fade, con;
        Preset(String sid, String name, String grp, int t, int c, int s, float[] tint, float[] cast, float ca, float fade, float con, float[] tint2) {
            this.sid = sid; this.name = name; this.grp = grp; this.t = t; this.c = c; this.s = s; this.tint = tint; this.cast = cast;
            this.ca = ca; this.fade = fade; this.con = con; this.tint2 = tint2;
        }
        float x() { return c / 100f; }
        float y() { return t / 100f; }
        float str() { return s / 100f; }
    }

    /** 预设和质感参数都来自 SharedData（由 shared/lucida-shared.json 生成，和网页 / iOS 同一份） */
    static final Preset[] PRESETS = SharedData.PRESETS;
    static final String[] TEX_ID = SharedData.TEX_ID;
    static final String[] TEX_NAME = SharedData.TEX_NAME;

    int preset = 0;
    float x = 0, y = 0, str = 1;      // 色彩（横）/ 色调（纵）/ 强度，-1…1 / 0…1
    int tex = 0;
    float tv = 0, gr = 0;             // 质感强度 / 颗粒强度 0…1
    final float[] tvm = new float[4]; // 每种质感各记各的强度

    Preset cur() { return PRESETS[preset]; }

    void pick(int i) {
        preset = ((i % PRESETS.length) + PRESETS.length) % PRESETS.length;
        Preset p = cur(); x = p.x(); y = p.y(); str = p.str();
    }

    boolean active() {
        float[] q = params();
        return Math.abs(q[3]) + Math.abs(q[4]) + Math.abs(q[5]) + Math.abs(q[6]) + q[8] > .004f || q[9] > .5f;
    }

    /**
     * 返回 [0..2]=tnt, 3=amt(色偏量), 4=tone, 5=sat, 6=con, 7=fade… 为方便着色器，顺序见下：
     * 0,1,2 = tnt；3 = amt；4 = tone；5 = sat；6 = con；7 = mono(0)；8 = fade；9 = fa(滤镜层是否启用)；
     * 10..13 = fcon,fsat,fhi,fsh；14 = ftmp；15 = fcl；16 = fsp；17 = fgr；18 = fhal；19..21 = ftint；22..24 = fshd
     */
    float[] params() {
        Preset p = cur();
        float[] o = new float[25];
        o[0] = p.cast[0]; o[1] = p.cast[1]; o[2] = p.cast[2];
        o[3] = p.ca * str; o[4] = y * str * SharedData.TONE_K; o[5] = x * str * SharedData.SAT_K; o[6] = p.con * str; o[7] = 0; o[8] = p.fade * str * SharedData.FADE_K;
        float[] k = new float[9];     // con sat hi sh tmp cl sp gr hal（和 SharedData.TEX_KEYS 同序）
        float[] shd = {0, 0, 0}, tint = {0, 0, 0};
        boolean on = tv > SharedData.TEX_ON;
        if (on) {
            int ti = Math.max(0, Math.min(SharedData.TEX_COEF.length - 1, tex));
            for (int i = 0; i < 9; i++) k[i] = SharedData.TEX_COEF[ti][i] * tv;
            for (int i = 0; i < 3; i++) shd[i] = SharedData.TEX_SHD[ti][i] * tv;
        }
        k[7] += this.gr * SharedData.GRAIN_K;
        boolean fa = on || this.gr >= SharedData.GRAIN_ON;
        if (!fa) k[7] = 0;                     // 和网页一致：没启用滤镜层时整组参数为 0
        float con = k[0], sat = k[1], hi = k[2], sh = k[3], tmp = k[4], cl = k[5], sp = k[6], g = k[7], hal = k[8];
        o[9] = fa ? 1 : 0;
        o[10] = con; o[11] = sat; o[12] = hi; o[13] = sh; o[14] = tmp; o[15] = cl; o[16] = sp; o[17] = g; o[18] = hal;
        o[19] = tint[0]; o[20] = tint[1]; o[21] = tint[2]; o[22] = shd[0]; o[23] = shd[1]; o[24] = shd[2];
        return o;
    }

    void load(SharedPreferences sp) { load(sp, "g_"); }
    void save(SharedPreferences sp) { save(sp, "g_"); }

    /** prefix：后置用 "g_"（沿用旧版存档），前置用 "gf_" */
    void load(SharedPreferences sp, String k) {
        pick(Math.max(0, Math.min(PRESETS.length - 1, sp.getInt(k + "preset", 0))));
        x = clamp(sp.getFloat(k + "x", x), -1, 1); y = clamp(sp.getFloat(k + "y", y), -1, 1); str = clamp(sp.getFloat(k + "str", str), 0, 1);
        tex = Math.max(0, Math.min(3, sp.getInt(k + "tex", 0)));
        tv = clamp(sp.getFloat(k + "tv", 0), 0, 1); gr = clamp(sp.getFloat(k + "gr", 0), 0, 1);
        for (int i = 0; i < 4; i++) tvm[i] = clamp(sp.getFloat(k + "tvm" + i, 0), 0, 1);
    }

    void save(SharedPreferences sp, String k) {
        SharedPreferences.Editor e = sp.edit();
        e.putInt(k + "preset", preset).putFloat(k + "x", x).putFloat(k + "y", y).putFloat(k + "str", str)
                .putInt(k + "tex", tex).putFloat(k + "tv", tv).putFloat(k + "gr", gr);
        for (int i = 0; i < 4; i++) e.putFloat(k + "tvm" + i, tvm[i]);
        e.apply();
    }

    /** 整套复制（「同时」调整时把当前显示的那一套同步给另一颗摄像头） */
    void copyFrom(GradeState o) {
        preset = o.preset; x = o.x; y = o.y; str = o.str; tex = o.tex; tv = o.tv; gr = o.gr;
        System.arraycopy(o.tvm, 0, tvm, 0, 4);
    }

    /** 是否和「标准、无质感、无颗粒」完全一样 */
    boolean isNeutral() { return preset == 0 && x == 0 && y == 0 && str >= .999f && tv < .004f && gr < .004f; }

    /** 各条滑条的「复位」：回到当前预设自带的值 / 0 */
    void resetPad() { Preset p = cur(); x = p.x(); y = p.y(); }
    void resetStr() { str = cur().str(); }
    void resetTv() { tv = 0; tvm[tex] = 0; }
    void resetGr() { gr = 0; }

    void reset() { pick(0); tex = 0; tv = 0; gr = 0; }

    static float clamp(float v, float a, float b) { return Math.max(a, Math.min(b, v)); }
}

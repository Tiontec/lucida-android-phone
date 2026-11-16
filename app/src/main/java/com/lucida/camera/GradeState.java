package com.lucida.camera;

import android.content.SharedPreferences;

/**
 * 调色盘状态 + 着色器参数换算（语义照抄主页面 HTML：PRESETS / gradeParams / texParams）。
 * 纯计算，不依赖 GL；GlComposer 每帧读 params() 的结果。
 */
final class GradeState {
    static final class Preset {
        final String sid, name; final int t, c, s; final float[] tint, cast; final float ca, fade, con;
        Preset(String sid, String name, int t, int c, int s, float[] tint, float[] cast, float ca, float fade, float con) {
            this.sid = sid; this.name = name; this.t = t; this.c = c; this.s = s; this.tint = tint; this.cast = cast; this.ca = ca; this.fade = fade; this.con = con;
        }
        float x() { return c / 100f; }
        float y() { return t / 100f; }
        float str() { return s / 100f; }
    }

    static final Preset[] PRESETS = {
        new Preset("standard", "标准", 0, 0, 100, new float[]{.38f, .46f, .62f}, new float[]{1, 1, 1}, 0, 0, 0),
        new Preset("pearl", "珠光", 18, 0, 100, new float[]{.62f, .2f, .3f}, new float[]{1, .66f, .76f}, .28f, .02f, 0),
        new Preset("coolrose", "冷调玫瑰色", -100, -100, 75, new float[]{.42f, .5f, .72f}, new float[]{.86f, .7f, .92f}, .42f, 0, 0),
        new Preset("amber", "琥珀色", 20, 20, 100, new float[]{.72f, .45f, .2f}, new float[]{1, .7f, .3f}, .55f, 0, 0),
        new Preset("gold", "金色", 20, 10, 85, new float[]{.85f, .58f, .08f}, new float[]{1, .82f, .3f}, .5f, 0, 0),
        new Preset("rosegold", "玫瑰金色", -20, -20, 75, new float[]{.66f, .4f, .3f}, new float[]{1, .72f, .62f}, .45f, 0, 0),
        new Preset("natural", "自然", -70, -15, 100, new float[]{.34f, .4f, .58f}, new float[]{.78f, .84f, .94f}, .12f, 0, 0),
        new Preset("cozy", "温馨", -75, -20, 100, new float[]{.84f, .42f, .12f}, new float[]{1, .72f, .74f}, .38f, 0, 0),
        new Preset("calm", "安静", 50, 45, 100, new float[]{.46f, .6f, .5f}, new float[]{1, .9f, .66f}, .3f, 0, 0),
        new Preset("ethereal", "缥缈", 68, -15, 100, new float[]{.2f, .42f, .78f}, new float[]{.72f, .78f, 1}, .2f, .06f, 0),
        new Preset("contrast", "反差色", -76, 19, 100, new float[]{.1f, .55f, .7f}, new float[]{.55f, .82f, .96f}, .22f, 0, .28f),
        new Preset("radiant", "耀目", 35, 30, 100, new float[]{.3f, .5f, .72f}, new float[]{.88f, .78f, 1}, .22f, 0, 0),
        new Preset("vivid", "鲜明", -30, 33, 100, new float[]{.78f, .2f, .2f}, new float[]{1, 1, 1}, 0, 0, .16f),
    };
    static final String[] TEX_ID = {"std", "soft", "halo", "film"};
    static final String[] TEX_NAME = {"标准", "柔肤", "光晕", "胶片"};

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
        o[3] = p.ca * str; o[4] = y * str * .6f; o[5] = x * str * .8f; o[6] = p.con * str; o[7] = 0; o[8] = p.fade * str;
        float con = 0, sat = 0, hi = 0, sh = 0, tmp = 0, cl = 0, sp = 0, g = 0, hal = 0;
        float[] tint = {0, 0, 0}, shd = {0, 0, 0};
        boolean on = tv > .004f;
        if (on) {
            float v = tv;
            switch (tex) {
                case 0: sp = .9f * v; cl = .55f * v; con = .06f * v; sat = .05f * v; break;
                case 1: sp = -1.0f * v; cl = -.9f * v; con = -.06f * v; sh = .1f * v; sat = -.03f * v; break;
                case 2: hal = 1.25f * v; cl = -.5f * v; hi = -.08f * v; sat = .05f * v; break;
                default: g = .2f * v; con = .14f * v; sat = -.14f * v; hi = -.12f * v; tmp = .05f * v;
                    shd[0] = -.02f * v; shd[1] = .014f * v; shd[2] = .03f * v; break;
            }
        }
        g += this.gr * .3f;
        boolean fa = on || this.gr >= .004f;
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

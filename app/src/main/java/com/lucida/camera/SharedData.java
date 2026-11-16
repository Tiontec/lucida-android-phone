package com.lucida.camera;

/** 由 shared/lucida-shared.json 生成（tools/gen_shared.py）——不要手改；改 json 再重新生成。网页 / iOS / 安卓共用同一份调色数据。 */
final class SharedData {
    private SharedData() {}
    static final float TONE_K = 0.6f, SAT_K = 0.8f, FADE_K = 1f;
    static final float TEX_ON = 0.004f, GRAIN_ON = 0.004f, GRAIN_K = 0.3f;
    /** 质感系数：行 = 标准 / 柔肤 / 光晕 / 胶片，列 = con sat hi sh tmp cl sp gr hal */
    static final String[] TEX_KEYS = {"con", "sat", "hi", "sh", "tmp", "cl", "sp", "gr", "hal"};
    static final String[] TEX_ID = {"std", "soft", "halo", "film"};
    static final String[] TEX_NAME = {"标准", "柔肤", "光晕", "胶片"};
    static final float[][] TEX_COEF = {
        {0.06f, 0.05f, 0f, 0f, 0f, 0.55f, 0.9f, 0f, 0f},
        {-0.06f, -0.03f, 0f, 0.1f, 0f, -0.9f, -1f, 0f, 0f},
        {0f, 0.05f, -0.08f, 0f, 0f, -0.5f, 0f, 0f, 1.25f},
        {0.14f, -0.14f, -0.12f, 0f, 0.05f, 0f, 0f, 0.2f, 0f},
    };
    static final float[][] TEX_SHD = {{0f, 0f, 0f}, {0f, 0f, 0f}, {0f, 0f, 0f}, {-0.02f, 0.014f, 0.03f}};
    static final GradeState.Preset[] PRESETS = {
        new GradeState.Preset("standard", "标准", "底色", 0, 0, 100, new float[]{0.38f, 0.46f, 0.62f}, new float[]{1f, 1f, 1f}, 0f, 0f, 0f, null),
        new GradeState.Preset("pearl", "珠光", "底色", 18, 0, 100, new float[]{0.62f, 0.2f, 0.3f}, new float[]{1f, 0.66f, 0.76f}, 0.28f, 0.02f, 0f, null),
        new GradeState.Preset("coolrose", "冷调玫瑰色", "底色", -100, -100, 75, new float[]{0.42f, 0.5f, 0.72f}, new float[]{0.86f, 0.7f, 0.92f}, 0.42f, 0f, 0f, null),
        new GradeState.Preset("amber", "琥珀色", "底色", 20, 20, 100, new float[]{0.72f, 0.45f, 0.2f}, new float[]{1f, 0.7f, 0.3f}, 0.55f, 0f, 0f, null),
        new GradeState.Preset("gold", "金色", "底色", 20, 10, 85, new float[]{0.85f, 0.58f, 0.08f}, new float[]{1f, 0.82f, 0.3f}, 0.5f, 0f, 0f, null),
        new GradeState.Preset("rosegold", "玫瑰金色", "底色", -20, -20, 75, new float[]{0.66f, 0.4f, 0.3f}, new float[]{1f, 0.72f, 0.62f}, 0.45f, 0f, 0f, null),
        new GradeState.Preset("natural", "自然", "心情", -70, -15, 100, new float[]{0.34f, 0.4f, 0.58f}, new float[]{0.78f, 0.84f, 0.94f}, 0.12f, 0f, 0f, null),
        new GradeState.Preset("cozy", "温馨", "心情", -75, -20, 100, new float[]{0.84f, 0.42f, 0.12f}, new float[]{1f, 0.72f, 0.74f}, 0.38f, 0f, 0f, null),
        new GradeState.Preset("calm", "安静", "心情", 50, 45, 100, new float[]{0.46f, 0.6f, 0.5f}, new float[]{1f, 0.9f, 0.66f}, 0.3f, 0f, 0f, null),
        new GradeState.Preset("ethereal", "缥缈", "心情", 68, -15, 100, new float[]{0.2f, 0.42f, 0.78f}, new float[]{0.72f, 0.78f, 1f}, 0.2f, 0.06f, 0f, null),
        new GradeState.Preset("contrast", "反差色", "心情", -76, 19, 100, new float[]{0.1f, 0.55f, 0.7f}, new float[]{0.55f, 0.82f, 0.96f}, 0.22f, 0f, 0.28f, null),
        new GradeState.Preset("radiant", "耀目", "心情", 35, 30, 100, new float[]{0.3f, 0.5f, 0.72f}, new float[]{0.88f, 0.78f, 1f}, 0.22f, 0f, 0f, null),
        new GradeState.Preset("vivid", "鲜明", "心情", -30, 33, 100, new float[]{0.78f, 0.2f, 0.2f}, new float[]{1f, 1f, 1f}, 0f, 0f, 0.16f, new float[]{0.1f, 0.6f, 0.55f}),
    };
}

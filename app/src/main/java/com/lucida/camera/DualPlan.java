package com.lucida.camera;

/**
 * 双摄「画质规划」：纯计算（不依赖 Android 类，可在电脑上单元测试）。
 * 输入摄像头真实支持的输出尺寸 / 最小帧间隔 / 目标帧率范围，输出该用哪个尺寸、哪个帧率范围、成品画布多大。
 * 原则：用户选的「帧率」优先——该帧率下这颗摄像头能出的最大画面，而不是随便给个 30 帧的大画面。
 */
final class DualPlan {
    private DualPlan() {}

    /** 流尺寸长边上限的阶梯（第 0 档最高）。降档 = 往后挪一格。 */
    static final int[] CAPS = {4096, 2880, 1920, 1280};

    static int startTier(int res) { return res >= 2160 ? 0 : res >= 1080 ? 2 : 3; }
    static int cap(int tier) { return CAPS[Math.max(0, Math.min(CAPS.length - 1, tier))]; }

    static final class SizePick {
        int w, h; double maxFps; boolean fpsOk; boolean aspectOk;
    }

    /**
     * @param sizes     输出尺寸 [i]={宽,高}（横向，宽>=高）
     * @param minDurNs  每个尺寸的最小帧间隔（纳秒，<=0 表示未知，按 30 帧算）
     * @param gate      true = 4:3（片门全开），false = 16:9
     */
    static SizePick pickSize(int[][] sizes, long[] minDurNs, boolean gate, int capLong, int fps) {
        double want = gate ? 4.0 / 3.0 : 16.0 / 9.0;
        SizePick best = null;
        for (int pass = 0; pass < 2 && best == null; pass++) {
            for (int i = 0; i < sizes.length; i++) {
                int w = Math.max(sizes[i][0], sizes[i][1]), h = Math.min(sizes[i][0], sizes[i][1]);
                if (h <= 0) continue;
                double r = (double) w / h;
                boolean asp = Math.abs(r - want) < 0.02;
                if (pass == 0 && !asp) continue;                 // 第一轮只要比例对的
                if (w > capLong) continue;
                double mf = minDurNs != null && i < minDurNs.length && minDurNs[i] > 0 ? 1e9 / minDurNs[i] : 30.0;
                boolean ok = mf >= fps * 0.97;
                SizePick c = new SizePick();
                c.w = w; c.h = h; c.maxFps = mf; c.fpsOk = ok; c.aspectOk = asp;
                if (best == null || better(c, best)) best = c;
            }
        }
        if (best == null) {                                       // 全都超过上限：给最小的
            for (int i = 0; i < sizes.length; i++) {
                int w = Math.max(sizes[i][0], sizes[i][1]), h = Math.min(sizes[i][0], sizes[i][1]);
                if (best == null || (long) w * h < (long) best.w * best.h) {
                    best = new SizePick(); best.w = w; best.h = h; best.maxFps = 30; best.fpsOk = fps <= 30; best.aspectOk = false;
                }
            }
        }
        return best;
    }

    private static boolean better(SizePick a, SizePick b) {
        if (a.fpsOk != b.fpsOk) return a.fpsOk;
        if (!a.fpsOk) { if (Math.abs(a.maxFps - b.maxFps) > 1) return a.maxFps > b.maxFps; }
        return (long) a.w * a.h > (long) b.w * b.h;
    }

    /** 在 AE 目标帧率范围列表里挑一个；返回下标，-1 = 没有可用范围。 */
    static int pickFpsRange(int[][] r, int fps) {
        int best = -1;
        for (int i = 0; i < r.length; i++) {
            if (best < 0 || rangeBetter(r[i], r[best], fps)) best = i;
        }
        return best;
    }

    private static int score(int[] r, int fps) {
        int lo = r[0], hi = r[1];
        int s;
        if (lo == fps && hi == fps) s = 1000;
        else if (hi == fps) s = 900 + lo;                       // [15,30] vs [24,30]：下限越高越稳
        else if (hi < fps) s = 500 + hi * 4 + (lo == hi ? 2 : 0);
        else s = 100 - (hi - fps);                              // 上限超过目标：最后才选
        return s;
    }

    private static boolean rangeBetter(int[] a, int[] b, int fps) { return score(a, fps) > score(b, fps); }

    /** 成品画布：res = 720 / 1080 / 2160；gate = 3:4，否则 9:16；stream = 实际流的长边（不拿小画面硬拉成大成品）。 */
    static int[] canvas(int res, boolean gate, int streamLong) {
        int tierLong = res >= 2160 ? (gate ? 2880 : 3840) : res >= 1080 ? 1920 : 1280;
        int lg = Math.min(tierLong, Math.max(320, streamLong));
        lg &= ~1;
        int sh = gate ? lg * 3 / 4 : lg * 9 / 16;
        sh &= ~1;
        return new int[]{sh, lg};        // {宽, 高}，竖屏
    }

    /** 35mm 等效焦距。 */
    static double equiv(double focalMm, double sensorW, double sensorH) {
        double diag = Math.hypot(sensorW, sensorH);
        return diag <= 0 ? 0 : focalMm * 43.267 / diag;
    }
}

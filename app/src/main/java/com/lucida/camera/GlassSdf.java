package com.lucida.camera;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.graphics.Shader;

/**
 * 液态玻璃（新，来自「液态玻璃技能包」）：圆角矩形的 SDF 折射 + 边缘高光，用 AGSL（RuntimeShader）实现。
 * 只支持 Android 13（API 33）及以上；更低版本 supported() 返回 false，调用方退回固态玻璃（GlassRefract 的 mesh 版）。
 * 输入：和固态玻璃一样，是控件背后那一块取景小图（crop），由 GlassRefract 按时裁好。
 * 公式对应技能包 glsl-shaders.md 的圆角矩形 shader（sdRoundedRect / 梯度 / circleMap / 远离区域直接返回），
 * 修正了原文的问题：梯度的 normalize 在中心点为 NaN（这里用 safeNorm）；smoothstep 边界顺序（这里 edge0 < edge1）。
 */
final class GlassSdf {
    private GlassSdf() {}

    /**
     * 液态玻璃的调参（测后改这里，然后重新编译）：
     *  REFRACT_BAND_DP   折射带宽度（dp）。越大，边缘弯曲的范围越宽、玻璃看起来越厚。默认 12。
     *  REFRACT_AMOUNT_DP 折射位移（dp）。越大，背后的画面扭得越厉害。超过 6 左右边缘容易出条纹。默认 5。
     *  DEPTH             立体项。越大，边缘越像凸透镜（弯曲更饱满）。0 = 只沿边缘折射。默认 0.25。
     *  RIM               边缘高光强度。越大，左上边缘越亮。默认 0.22。
     *  注意：填充混合的权重（暗化 0.15 / 提亮 0.10 / 折射 0.75）写在 SRC 的 shader 里，要改就改那一行。
     */
    static final float REFRACT_BAND_DP = 12f, REFRACT_AMOUNT_DP = 5f, DEPTH = 0.25f, RIM = 0.22f;

    static boolean supported() { return android.os.Build.VERSION.SDK_INT >= 33; }

    static final String SRC =
        "uniform shader content;\n" +
        "uniform float2 size;\n" +
        "uniform float radius;\n" +
        "uniform float refractionHeight;\n" +
        "uniform float refractionAmount;\n" +
        "uniform float depthEffect;\n" +
        "uniform float rimStrength;\n" +
        "float sdRoundedRect(float2 p, float2 hs, float r) {\n" +
        "    float2 c = abs(p) - (hs - float2(r));\n" +
        "    float outside = length(max(c, float2(0.0))) - r;\n" +
        "    float inside = min(max(c.x, c.y), 0.0);\n" +
        "    return outside + inside;\n" +
        "}\n" +
        "float2 safeNorm(float2 v) {\n" +
        "    float l = length(v);\n" +
        "    return l > 1e-5 ? v / l : float2(0.0);\n" +
        "}\n" +
        "float2 gradSd(float2 p, float2 hs, float r) {\n" +
        "    float2 c = abs(p) - (hs - float2(r));\n" +
        "    float2 s = float2(sign(p.x), sign(p.y));\n" +
        "    if (c.x >= 0.0 || c.y >= 0.0) return s * safeNorm(max(c, float2(0.0)));\n" +
        "    float gx = step(c.y, c.x);\n" +
        "    return s * float2(gx, 1.0 - gx);\n" +
        "}\n" +
        "float circleMap(float x) { return 1.0 - sqrt(max(0.0, 1.0 - x * x)); }\n" +
        "half4 main(float2 coord) {\n" +
        "    float2 hs = size * 0.5;\n" +
        "    float2 p = coord - hs;\n" +
        "    float r = min(radius, min(hs.x, hs.y));\n" +
        "    float sd = sdRoundedRect(p, hs, r);\n" +
        "    half4 direct = content.eval(coord);\n" +
        "    if (-sd >= refractionHeight || sd > refractionHeight) return direct;\n" +
        "    sd = min(sd, 0.0);\n" +
        "    float t = clamp(1.0 - (-sd) / refractionHeight, 0.0, 1.0);\n" +
        "    float d = circleMap(t) * refractionAmount;\n" +
        "    float2 grad = safeNorm(gradSd(p, hs, r) + depthEffect * safeNorm(p));\n" +
        "    half4 refr = content.eval(coord + d * grad);\n" +
        "    float3 rf = float3(refr.rgb);\n" +
        "    float3 dr = float3(direct.rgb);\n" +
        "    float3 c = 0.15 * min(rf, dr) + 0.10 * max(rf, dr) + 0.75 * rf;\n" +
        "    float edge = 1.0 - smoothstep(0.0, refractionHeight * 0.15, -sd);\n" +
        "    float lit = max(0.0, dot(grad, normalize(float2(-1.0, -1.0))));\n" +
        "    c += rimStrength * edge * lit;\n" +
        "    return half4(half3(clamp(c, 0.0, 1.0)), half(1.0));\n" +
        "}\n";

    private static RuntimeShader sh;
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Matrix M = new Matrix();
    private static final RectF RF = new RectF();

    /**
     * 在 c 的局部坐标 (0,0)-(w,h) 画一块圆角 r 的液态玻璃，背景取自 crop。
     * 返回 false = 不能画（低版本 / 出错），调用方退回固态玻璃。
     */
    static boolean draw(Canvas c, Bitmap pad, float ml, float mt, float innerW, float innerH, float w, float h, float r, float density) {
        if (!supported() || pad == null || w <= 0 || h <= 0 || innerW <= 0 || innerH <= 0) return false;
        try {
            if (sh == null) sh = new RuntimeShader(SRC);
            sh.setFloatUniform("size", w, h);
            sh.setFloatUniform("radius", r);
            sh.setFloatUniform("refractionHeight", REFRACT_BAND_DP * density);
            sh.setFloatUniform("refractionAmount", REFRACT_AMOUNT_DP * density);
            sh.setFloatUniform("depthEffect", DEPTH);
            sh.setFloatUniform("rimStrength", RIM);
            // pad 比控件大一圈（边距）：采样越过控件边缘时读到真实背景，而不是把边缘像素拉成条纹
            BitmapShader bs = new BitmapShader(pad, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            M.reset();
            M.setTranslate(-ml, -mt);                 // 控件左上角在 pad 里的位置 → 原点
            M.postScale(w / innerW, h / innerH);      // 控件的像素尺寸 → 局部坐标
            bs.setLocalMatrix(M);
            sh.setInputShader("content", bs);
            P.setShader(sh);
            RF.set(0, 0, w, h);
            c.drawRoundRect(RF, r, r, P);
            P.setShader(null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

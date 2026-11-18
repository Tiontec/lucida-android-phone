package com.lucida.camera;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * 把前后摄像头两路画面（GL_TEXTURE_EXTERNAL_OES）合成「画中画 / 上下分屏」，画到：预览窗口、（录像时）编码器输入面、（拍照时）离屏缓冲。
 * 所有方法必须在同一个 GL 线程里调用。
 */
final class GlComposer {
    static final int BACK = 0, FRONT = 1;

    private EGLDisplay dpy = EGL14.EGL_NO_DISPLAY;
    private EGLContext ctx = EGL14.EGL_NO_CONTEXT;
    private EGLConfig cfg;
    private EGLSurface winSurf = EGL14.EGL_NO_SURFACE, encSurf = EGL14.EGL_NO_SURFACE, pbuf = EGL14.EGL_NO_SURFACE;
    private int prog, aLocal, uRect, uUv, uTex, uTexMat, uCorner, uBorder, uAspect, uBorderCol, uAA;
    private int uGradeOn, uTnt, uAmt, uTone, uSat, uCon, uFade, uFa, uFcon, uFsat, uFhi, uFsh, uFtmp, uFcl, uFsp, uFgr, uFhal, uFseed, uFtint, uFshd, uOstep;
    /** 调色盘参数（GradeState.params() 的结果）；gradeOn=false 时不调色。可从任意线程改。 */
    final float[][] gradeCam = {new float[25], new float[25]};   // 0 = 后置，1 = 前置：各用各的调色
    final boolean[] gradeOnCam = new boolean[2];
    /** 小窗边框：0 无 / 1 白 / 2 黑 */
    volatile int borderMode = 0;
    volatile float pipCorner = 0.085f;
    /** 预览取景框在窗口里的位置（像素，y 从上往下）；w<=0 = 铺满窗口 */
    volatile int vfX, vfY, vfW, vfH;
    private final java.util.Random rnd = new java.util.Random();
    private final int[] tex = new int[2];
    final SurfaceTexture[] st = new SurfaceTexture[2];
    final Surface[] surf = new Surface[2];
    final float[][] texMat = {new float[16], new float[16]};
    final int[] bufW = {1280, 1280}, bufH = {720, 720};
    final int[] sensor = {90, 270};
    int dispDeg = 0;
    final DualState state;
    private FloatBuffer quad;
    int winW = 1, winH = 1;
    private int fbo, fboTex, fboW, fboH;
    private final boolean[] hasFrame = new boolean[2];
    /** 单摄：只画主画面这一路（整个取景框），update 只要主画面那颗摄像头有画面就算就绪 */
    volatile boolean single = false;

    GlComposer(DualState s) { state = s; }

    private static final String VS =
            "attribute vec2 aLocal; uniform vec4 uRect; uniform mat4 uUv; uniform mat4 uTexMat;\n" +
            "varying vec2 vTex; varying vec2 vLocal;\n" +
            "void main(){ vLocal=aLocal;\n" +
            " float x=uRect.x+aLocal.x*uRect.z; float y=uRect.y+(1.0-aLocal.y)*uRect.w;\n" +
            " gl_Position=vec4(2.0*x-1.0, 1.0-2.0*y, 0.0, 1.0);\n" +
            " vec4 t=uUv*vec4(aLocal,0.0,1.0); vTex=(uTexMat*t).xy; }\n";
    private static final String FS =
            "#extension GL_OES_EGL_image_external : require\n" +
            "#ifdef GL_FRAGMENT_PRECISION_HIGH\nprecision highp float;\n#else\nprecision mediump float;\n#endif\n" +
            "varying vec2 vTex; varying vec2 vLocal; uniform samplerExternalOES uTex;\n" +
            "uniform float uCorner; uniform float uBorder; uniform float uAspect; uniform vec3 uBorderCol; uniform float uAA;\n" +
            // 调色盘（照抄主页面 Grade 着色器的处理顺序）
            "uniform float uGradeOn; uniform vec3 uTnt; uniform float uAmt, uTone, uSat, uCon, uFade;\n" +
            "uniform float uFa, uFcon, uFsat, uFhi, uFsh, uFtmp, uFcl, uFsp, uFgr, uFhal, uFseed; uniform vec3 uFtint, uFshd; uniform float uOstep;\n" +
            "float lum(vec3 c){ return dot(c, vec3(.2126,.7152,.0722)); }\n" +
            "vec3 grade(vec3 c, vec2 t){\n" +
            " float y=lum(c), mx=max(c.r,max(c.g,c.b)), mn=min(c.r,min(c.g,c.b)), ch=mx-mn, sn=ch/(mx+1e-4), h=0.;\n" +
            " if(ch>1e-4){ if(mx==c.r) h=mod((c.g-c.b)/ch,6.); else if(mx==c.g) h=(c.b-c.r)/ch+2.; else h=(c.r-c.g)/ch+4.; h/=6.; if(h>.9) h-=1.; }\n" +
            " float skin=smoothstep(-.01,.03,h)*(1.-smoothstep(.10,.15,h))*smoothstep(.08,.22,sn)*(1.-smoothstep(.6,.8,sn));\n" +
            " float mid=4.*y*(1.-y);\n" +
            " c+=(uTnt-vec3(lum(uTnt)))*uAmt*.5*(.35+.65*mid)*(1.-.5*skin);\n" +
            " c=clamp(c,0.,1.);\n" +
            " c=pow(c,vec3(exp2(-uTone*.55)));\n" +
            " float cc=uCon+max(-uTone,0.)*.35;\n" +
            " y=lum(c);\n" +
            " if(cc>=0.) c=mix(c,c*c*(3.-2.*c),clamp(cc,0.,1.)); else c=.5+(c-.5)*(1.+cc*.7);\n" +
            " c=uFade+c*(1.-uFade);\n" +
            " y=lum(c);\n" +
            " float m2=max(c.r,max(c.g,c.b)), n2=min(c.r,min(c.g,c.b)), s2=(m2-n2)/(m2+1e-4);\n" +
            " float prot=(1.-.45*skin)*(1.-smoothstep(.82,1.,y))*smoothstep(0.,.12,y);\n" +
            " float sa= uSat>0. ? 1.+uSat*.95*(1.-.55*s2)*prot : 1.+uSat*.95;\n" +
            " c=vec3(y)+(c-vec3(y))*sa;\n" +
            " c=clamp(c,0.,1.);\n" +
            " if(uFa>.5){\n" +
            "  float yy=lum(c), wH=smoothstep(.45,1.,yy), wS=1.-smoothstep(0.,.55,yy);\n" +
            "  c*=1.+uFhi*.5*wH; c*=1.+uFsh*.8*wS;\n" +
            "  c.r*=1.+uFtmp*.5; c.b*=1.-uFtmp*.5; c+=uFtint; c+=uFshd*wS;\n" +
            "  c=.5+(c-.5)*(1.+uFcon*.8);\n" +
            "  if(abs(uFcl)>.001||abs(uFsp)>.001||uFhal>.001){\n" +
            "   vec3 c0=texture2D(uTex,t).rgb; vec2 o1=vec2(uOstep*1.5,0.), o2=vec2(uOstep*6.,0.);\n" +
            "   vec3 b1=(texture2D(uTex,t+o1).rgb+texture2D(uTex,t-o1).rgb+texture2D(uTex,t+o1.yx).rgb+texture2D(uTex,t-o1.yx).rgb)*.25;\n" +
            "   vec3 b2=(texture2D(uTex,t+o2).rgb+texture2D(uTex,t-o2).rgb+texture2D(uTex,t+o2.yx).rgb+texture2D(uTex,t-o2.yx).rgb)*.25;\n" +
            "   c+=(c0-b1)*uFsp+(c0-b2)*uFcl*(.5+2.*yy*(1.-yy));\n" +
            "   c+=vec3(1.,.42,.3)*max(lum(b2)-.6,0.)*2.2*uFhal;\n" +
            "  }\n" +
            "  float y2=lum(c); c=vec3(y2)+(c-vec3(y2))*(1.+uFsat);\n" +
            "  float gn=fract(sin(dot(gl_FragCoord.xy+uFseed,vec2(12.9898,78.233)))*43758.5453)-.5;\n" +
            "  c+=gn*uFgr*(.35+.65*(1.-abs(yy-.5)*2.));\n" +
            "  c=clamp(c,0.,1.);\n" +
            " }\n" +
            " return c;\n" +
            "}\n" +
            "void main(){\n" +
            " float alpha=1.0;\n" +
            " if(uCorner>0.0){ vec2 p=(vLocal-0.5)*vec2(uAspect,1.0); vec2 h=vec2(uAspect,1.0)*0.5-vec2(uCorner);\n" +
            "  vec2 q=abs(p)-h; float d=length(max(q,0.0))+min(max(q.x,q.y),0.0)-uCorner;\n" +
            "  alpha=clamp(0.5-d/uAA,0.0,1.0); if(alpha<=0.0) discard;\n" +
            "  if(uBorder>0.0 && d>-uBorder){ gl_FragColor=vec4(uBorderCol,alpha); return; } }\n" +
            " vec3 c=texture2D(uTex,vTex).rgb;\n" +
            " if(uGradeOn>.5) c=grade(c,vTex);\n" +
            " gl_FragColor=vec4(c,alpha); }\n";

    void init(Surface window) {
        dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] v = new int[2];
        EGL14.eglInitialize(dpy, v, 0, v, 1);
        int[] attr = {EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, 0x3142 /* EGL_RECORDABLE_ANDROID */, 1, EGL14.EGL_NONE};
        EGLConfig[] cs = new EGLConfig[1];
        int[] n = new int[1];
        EGL14.eglChooseConfig(dpy, attr, 0, cs, 0, 1, n, 0);
        if (n[0] == 0) throw new RuntimeException("没有可用的 EGL 配置");
        cfg = cs[0];
        ctx = EGL14.eglCreateContext(dpy, cfg, EGL14.EGL_NO_CONTEXT, new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE}, 0);
        winSurf = EGL14.eglCreateWindowSurface(dpy, cfg, window, new int[]{EGL14.EGL_NONE}, 0);
        pbuf = EGL14.eglCreatePbufferSurface(dpy, cfg, new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
        EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);

        prog = link(VS, FS);
        aLocal = GLES20.glGetAttribLocation(prog, "aLocal");
        uRect = GLES20.glGetUniformLocation(prog, "uRect");
        uUv = GLES20.glGetUniformLocation(prog, "uUv");
        uTex = GLES20.glGetUniformLocation(prog, "uTex");
        uTexMat = GLES20.glGetUniformLocation(prog, "uTexMat");
        uCorner = GLES20.glGetUniformLocation(prog, "uCorner");
        uBorder = GLES20.glGetUniformLocation(prog, "uBorder");
        uAspect = GLES20.glGetUniformLocation(prog, "uAspect");
        uBorderCol = GLES20.glGetUniformLocation(prog, "uBorderCol");
        uAA = GLES20.glGetUniformLocation(prog, "uAA");
        uGradeOn = GLES20.glGetUniformLocation(prog, "uGradeOn");
        uTnt = GLES20.glGetUniformLocation(prog, "uTnt"); uAmt = GLES20.glGetUniformLocation(prog, "uAmt");
        uTone = GLES20.glGetUniformLocation(prog, "uTone"); uSat = GLES20.glGetUniformLocation(prog, "uSat");
        uCon = GLES20.glGetUniformLocation(prog, "uCon"); uFade = GLES20.glGetUniformLocation(prog, "uFade");
        uFa = GLES20.glGetUniformLocation(prog, "uFa"); uFcon = GLES20.glGetUniformLocation(prog, "uFcon");
        uFsat = GLES20.glGetUniformLocation(prog, "uFsat"); uFhi = GLES20.glGetUniformLocation(prog, "uFhi");
        uFsh = GLES20.glGetUniformLocation(prog, "uFsh"); uFtmp = GLES20.glGetUniformLocation(prog, "uFtmp");
        uFcl = GLES20.glGetUniformLocation(prog, "uFcl"); uFsp = GLES20.glGetUniformLocation(prog, "uFsp");
        uFgr = GLES20.glGetUniformLocation(prog, "uFgr"); uFhal = GLES20.glGetUniformLocation(prog, "uFhal");
        uFseed = GLES20.glGetUniformLocation(prog, "uFseed"); uFtint = GLES20.glGetUniformLocation(prog, "uFtint");
        uFshd = GLES20.glGetUniformLocation(prog, "uFshd"); uOstep = GLES20.glGetUniformLocation(prog, "uOstep");
        float[] q = {0, 0, 1, 0, 0, 1, 1, 1};
        quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
        quad.put(q).position(0);

        GLES20.glGenTextures(2, tex, 0);
        for (int i = 0; i < 2; i++) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[i]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            st[i] = new SurfaceTexture(tex[i]);
            Matrix.setIdentityM(texMat[i], 0);
        }
    }

    void setBufferSize(int cam, int w, int h) { bufW[cam] = w; bufH[cam] = h; st[cam].setDefaultBufferSize(w, h); }
    Surface surface(int cam) { if (surf[cam] == null) surf[cam] = new Surface(st[cam]); return surf[cam]; }

    void setEncoderSurface(Surface s) {
        if (encSurf != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(dpy, encSurf); encSurf = EGL14.EGL_NO_SURFACE; }
        if (s != null) encSurf = EGL14.eglCreateWindowSurface(dpy, cfg, s, new int[]{EGL14.EGL_NONE}, 0);
    }
    boolean hasEncoder() { return encSurf != EGL14.EGL_NO_SURFACE; }

    /** 实况短片的编码输出（可以同时有好几路，连拍用）；只能在 GL 线程上增删 / 绘制 */
    static final class Enc { EGLSurface surf = EGL14.EGL_NO_SURFACE; long baseNs; }
    private final java.util.ArrayList<Enc> liveEncs = new java.util.ArrayList<Enc>();

    Enc addLiveEncoder(Surface s, long baseNs) {
        Enc e = new Enc();
        e.surf = EGL14.eglCreateWindowSurface(dpy, cfg, s, new int[]{EGL14.EGL_NONE}, 0);
        e.baseNs = baseNs;
        liveEncs.add(e);
        return e;
    }
    void removeLiveEncoder(Enc e) {
        if (e == null) return;
        liveEncs.remove(e);
        if (e.surf != EGL14.EGL_NO_SURFACE) { EGL14.eglDestroySurface(dpy, e.surf); e.surf = EGL14.EGL_NO_SURFACE; }
    }
    boolean hasLive() { return !liveEncs.isEmpty(); }
    void drawLive(int w, int h) {
        if (liveEncs.isEmpty()) return;
        long now = System.nanoTime();
        for (int i = 0; i < liveEncs.size(); i++) {
            Enc e = liveEncs.get(i);
            EGL14.eglMakeCurrent(dpy, e.surf, e.surf, ctx);
            drawScene(0, 0, w, h);
            EGLExt.eglPresentationTimeANDROID(dpy, e.surf, now - e.baseNs);
            EGL14.eglSwapBuffers(dpy, e.surf);
        }
        EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);
    }

    /** 取走最新一帧；返回两路是否都已经有画面。 */
    boolean update(int cam) {
        try {
            EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);
            st[cam].updateTexImage();
            st[cam].getTransformMatrix(texMat[cam]);
            hasFrame[cam] = true;
        } catch (Exception ignore) {}
        if (single) return hasFrame[state.backIsMain ? BACK : FRONT];
        return hasFrame[0] && hasFrame[1];
    }

    // ---------------------------------------------------------------- 绘制

    void drawPreview() {
        if (winSurf == EGL14.EGL_NO_SURFACE) return;
        EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);
        int[] w = new int[1], h = new int[1];
        EGL14.eglQuerySurface(dpy, winSurf, EGL14.EGL_WIDTH, w, 0);
        EGL14.eglQuerySurface(dpy, winSurf, EGL14.EGL_HEIGHT, h, 0);
        winW = Math.max(1, w[0]); winH = Math.max(1, h[0]);
        // 先把整个窗口刷成黑色（取景框外面是平的黑色底板），再把画面画进取景框
        GLES20.glViewport(0, 0, winW, winH);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        int vw = vfW, vh = vfH, vx = vfX, vy = vfY;
        if (vw <= 0 || vh <= 0) { vx = 0; vy = 0; vw = winW; vh = winH; }
        drawScene(vx, winH - vy - vh, vw, vh);
        EGL14.eglSwapBuffers(dpy, winSurf);
    }

    void drawEncoder(long ptsNs, int w, int h) {
        if (encSurf == EGL14.EGL_NO_SURFACE) return;
        EGL14.eglMakeCurrent(dpy, encSurf, encSurf, ctx);
        drawScene(0, 0, w, h);
        EGLExt.eglPresentationTimeANDROID(dpy, encSurf, ptsNs);
        EGL14.eglSwapBuffers(dpy, encSurf);
        EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);
    }

    /** 离屏画一张 w×h 的成品并读回来（拍照用）。 */
    Bitmap capture(int w, int h) {
        EGL14.eglMakeCurrent(dpy, winSurf, winSurf, ctx);
        if (fbo == 0 || fboW != w || fboH != h) {
            if (fbo != 0) { GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0); GLES20.glDeleteTextures(1, new int[]{fboTex}, 0); }
            int[] t = new int[1], f = new int[1];
            GLES20.glGenTextures(1, t, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glGenFramebuffers(1, f, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, f[0]);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, t[0], 0);
            fbo = f[0]; fboTex = t[0]; fboW = w; fboH = h;
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        drawScene(0, 0, w, h);
        ByteBuffer bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bb.rewind();
        bmp.copyPixelsFromBuffer(bb);
        android.graphics.Matrix flip = new android.graphics.Matrix();
        flip.preScale(1f, -1f);      // glReadPixels 的原点在左下角
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, w, h, flip, false);
        bmp.recycle();
        return out;
    }

    private void drawScene(int ox, int oy, int W, int H) {
        GLES20.glViewport(ox, oy, W, H);
        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(ox, oy, W, H);
        GLES20.glClearColor(0, 0, 0, 1);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        int mainCam = state.backIsMain ? BACK : FRONT, secCam = state.backIsMain ? FRONT : BACK;
        if (single) { drawCam(mainCam, new float[]{0, 0, 1, 1}, W, H, false); return; }
        drawCam(mainCam, state.rect(0, W, H), W, H, false);
        drawCam(secCam, state.rect(1, W, H), W, H, state.style == DualState.STYLE_PIP);
    }

    private void drawCam(int cam, float[] r, int W, int H, boolean rounded) {
        GLES20.glUseProgram(prog);
        GLES20.glUniform4f(uRect, r[0], r[1], r[2], r[3]);
        GLES20.glUniformMatrix4fv(uUv, 1, false, buildUvMatrix(cam, r[2] * W, r[3] * H), 0);
        GLES20.glUniformMatrix4fv(uTexMat, 1, false, texMat[cam], 0);
        float ph = r[3] * H;
        float asp = (r[2] * W) / ph;
        int bm = borderMode;
        GLES20.glUniform1f(uCorner, rounded ? pipCorner : 0f);
        GLES20.glUniform1f(uBorder, rounded && bm != 0 ? Math.max(3f, W * 0.0065f) / ph : 0f);   // 边框宽度（像素 → 以小窗高度为 1）
        GLES20.glUniform3f(uBorderCol, bm == 2 ? 0f : 1f, bm == 2 ? 0f : 1f, bm == 2 ? 0f : 1f);
        GLES20.glUniform1f(uAA, 1.5f / ph);
        GLES20.glUniform1f(uAspect, asp);
        boolean g = gradeOnCam[cam];
        GLES20.glUniform1f(uGradeOn, g ? 1f : 0f);
        if (g) {
            float[] q = gradeCam[cam];
            GLES20.glUniform3f(uTnt, q[0], q[1], q[2]);
            GLES20.glUniform1f(uAmt, q[3]); GLES20.glUniform1f(uTone, q[4]); GLES20.glUniform1f(uSat, q[5]);
            GLES20.glUniform1f(uCon, q[6]); GLES20.glUniform1f(uFade, q[8]);
            GLES20.glUniform1f(uFa, q[9]); GLES20.glUniform1f(uFcon, q[10]); GLES20.glUniform1f(uFsat, q[11]);
            GLES20.glUniform1f(uFhi, q[12]); GLES20.glUniform1f(uFsh, q[13]); GLES20.glUniform1f(uFtmp, q[14]);
            GLES20.glUniform1f(uFcl, q[15]); GLES20.glUniform1f(uFsp, q[16]); GLES20.glUniform1f(uFgr, q[17]);
            GLES20.glUniform1f(uFhal, q[18]);
            GLES20.glUniform3f(uFtint, q[19], q[20], q[21]); GLES20.glUniform3f(uFshd, q[22], q[23], q[24]);
            GLES20.glUniform1f(uFseed, rnd.nextFloat() * 100f);
            GLES20.glUniform1f(uOstep, 1f / Math.max(1, Math.min(bufW[cam], bufH[cam])));
        }
        if (rounded) { GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA); }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex[cam]);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glEnableVertexAttribArray(aLocal);
        GLES20.glVertexAttribPointer(aLocal, 2, GLES20.GL_FLOAT, false, 8, quad);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aLocal);
        if (rounded) GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** 与 PipelineTest 里验证过的 buildUv 一致：先居中裁切、再按需镜像、最后绕中心补足旋转。 */
    float[] buildUvMatrix(int cam, float dw, float dh) {
        int[] tr = DualState.matrixRotation(texMat[cam]);
        int extra = DualState.extraRotation(sensor[cam], dispDeg, cam == FRONT, tr[0], state.rotFix);
        int total = (tr[0] + extra) % 360;
        float uw = (total == 90 || total == 270) ? bufH[cam] : bufW[cam];
        float uh = (total == 90 || total == 270) ? bufW[cam] : bufH[cam];
        float sa = uw / uh, da = dw / dh, fw = 1f, fh = 1f;
        if (sa > da) fw = da / sa; else fh = sa / da;
        boolean mirror = (cam == FRONT && state.mirrorFront) ^ (tr[1] == 1);
        float[] t1 = new float[16], t2 = new float[16], tmp = new float[16], out = new float[16];
        Matrix.setIdentityM(t1, 0);
        Matrix.translateM(t1, 0, 0.5f - fw / 2f, 0.5f - fh / 2f, 0f);
        Matrix.scaleM(t1, 0, fw, fh, 1f);
        if (mirror) {
            Matrix.setIdentityM(t2, 0);
            Matrix.translateM(t2, 0, 0.5f, 0f, 0f);
            Matrix.scaleM(t2, 0, -1f, 1f, 1f);
            Matrix.translateM(t2, 0, -0.5f, 0f, 0f);
            Matrix.multiplyMM(tmp, 0, t2, 0, t1, 0);
            System.arraycopy(tmp, 0, t1, 0, 16);
        }
        Matrix.setIdentityM(t2, 0);
        Matrix.translateM(t2, 0, 0.5f, 0.5f, 0f);
        Matrix.rotateM(t2, 0, DualState.uvRotation(extra, tr[1] == 1), 0f, 0f, 1f);
        Matrix.translateM(t2, 0, -0.5f, -0.5f, 0f);
        Matrix.multiplyMM(out, 0, t2, 0, t1, 0);
        return out;
    }

    void release() {
        try {
            for (int i = 0; i < 2; i++) { if (surf[i] != null) surf[i].release(); if (st[i] != null) st[i].release(); }
            if (dpy != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (encSurf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, encSurf);
                for (Enc e : liveEncs) if (e.surf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, e.surf);
                liveEncs.clear();
                if (winSurf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, winSurf);
                if (pbuf != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(dpy, pbuf);
                EGL14.eglDestroyContext(dpy, ctx);
                EGL14.eglTerminate(dpy);
            }
        } catch (Throwable ignore) {}
        dpy = EGL14.EGL_NO_DISPLAY;
    }

    private static int link(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs), f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v); GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("着色器链接失败：" + GLES20.glGetProgramInfoLog(p));
        return p;
    }
    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new RuntimeException("着色器编译失败：" + GLES20.glGetShaderInfoLog(s));
        return s;
    }
}

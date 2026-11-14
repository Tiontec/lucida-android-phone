package com.lucida.camera;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.view.Surface;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * H.264（Surface 输入）+ AAC（麦克风，可选）→ MP4。视频帧由 GlComposer 画到 inputSurface。
 *
 * 11.1.5 修复「双摄只能录 1 秒」：
 *  旧版录音线程只往 AAC 编码器里塞数据、录完才去取输出 → AAC 输出队列堆满卡死 →
 *  muxer 永远等不到音频轨 → 视频编码器的输出也没人取 → 输入 Surface 的缓冲用完，
 *  GL 线程 eglSwapBuffers 被卡住，画面在约 1 秒后定格。
 *  现在：音频每写入一块数据就立刻把已有的输出取走；视频和音频线程互不等待；
 *  muxer 还没启动时，先把样本暂存在内存里，启动后一次写入；
 *  如果 2.5 秒内一直没有音频轨（麦克风被占用 / 编码器异常），自动降级为只录画面，不会卡死。
 */
final class DualRecorder {
    final int w, h;
    private final File file;
    private final boolean wantAudio;
    private MediaCodec venc, aenc;
    private MediaMuxer mux;
    private Surface input;
    private int vTrack = -1, aTrack = -1;
    private boolean muxStarted, audioDisabled;
    private long vTrackAtMs;
    private final Object lock = new Object();
    private Thread vThread, aThread;
    private AudioRecord rec;
    private volatile boolean stopping, abort;
    private final ArrayList<Pending> queue = new ArrayList<Pending>();
    private static final int MAX_PENDING = 900;
    private static final int RATE = 44100;
    long startNs;
    volatile String error;
    boolean hasAudio;

    private static final class Pending {
        boolean video; byte[] data; MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    }

    DualRecorder(File f, int w, int h, boolean audio) { file = f; this.w = w; this.h = h; wantAudio = audio; }

    Surface start() throws Exception {
        MediaFormat vf = MediaFormat.createVideoFormat("video/avc", w, h);
        vf.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        vf.setInteger(MediaFormat.KEY_BIT_RATE, w * h * 5);
        vf.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        venc = MediaCodec.createEncoderByType("video/avc");
        venc.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        input = venc.createInputSurface();
        venc.start();
        mux = new MediaMuxer(file.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        if (wantAudio) {
            try {
                int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                rec = new AudioRecord(MediaRecorder.AudioSource.CAMCORDER, RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) throw new Exception("麦克风没有初始化");
                MediaFormat af = MediaFormat.createAudioFormat("audio/mp4a-latm", RATE, 1);
                af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                af.setInteger(MediaFormat.KEY_BIT_RATE, 96000);
                af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
                aenc = MediaCodec.createEncoderByType("audio/mp4a-latm");
                aenc.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                aenc.start();
                hasAudio = true;
            } catch (Throwable t) {
                hasAudio = false;
                if (rec != null) { try { rec.release(); } catch (Throwable ignore) {} rec = null; }
                if (aenc != null) { try { aenc.release(); } catch (Throwable ignore) {} aenc = null; }
            }
        }
        startNs = System.nanoTime();
        vThread = new Thread(new Runnable() { @Override public void run() { videoLoop(); } }, "dual-venc");
        vThread.start();
        if (hasAudio) {
            rec.startRecording();
            aThread = new Thread(new Runnable() { @Override public void run() { audioLoop(); } }, "dual-aenc");
            aThread.start();
        }
        return input;
    }

    // ------------------------------------------------------------ 视频：只负责把输出取走

    private void videoLoop() {
        try {
            while (!abort) {
                if (pump(venc, true, 10000)) break;
                checkAudioTimeout();
            }
        } catch (Throwable t) {
            error = String.valueOf(t.getMessage());
        }
    }

    private void checkAudioTimeout() {
        synchronized (lock) {
            if (!muxStarted && vTrack >= 0 && hasAudio && aTrack < 0 && !audioDisabled
                    && System.currentTimeMillis() - vTrackAtMs > 2500) {
                audioDisabled = true;       // 音频轨一直没出来：放弃音频，保住画面
                tryStartMux();
            }
        }
    }

    // ------------------------------------------------------------ 音频：边录边塞边取

    private void audioLoop() {
        try {
            byte[] buf = new byte[4096];
            long samples = 0;
            long base = -1;
            while (!stopping && !abort) {
                int n = rec.read(buf, 0, buf.length);
                if (n < 0) break;
                if (n == 0) continue;
                if (base < 0) {
                    long nowUs = (System.nanoTime() - startNs) / 1000L;
                    base = Math.max(0, nowUs - (n / 2) * 1000000L / RATE);
                }
                int idx = aenc.dequeueInputBuffer(5000);
                int guard = 0;
                while (idx < 0 && !abort && guard++ < 400) {   // 输入满 → 先把输出取走再试
                    pump(aenc, false, 0);
                    idx = aenc.dequeueInputBuffer(5000);
                }
                if (idx < 0) continue;
                ByteBuffer b = aenc.getInputBuffer(idx);
                b.clear(); b.put(buf, 0, n);
                aenc.queueInputBuffer(idx, 0, n, base + samples * 1000000L / RATE, 0);
                samples += n / 2;
                pump(aenc, false, 0);                          // 关键：边录边取走输出
            }
            // 结束标记
            int idx = -1, guard = 0;
            while (idx < 0 && guard++ < 200) {
                idx = aenc.dequeueInputBuffer(10000);
                if (idx < 0) pump(aenc, false, 0);
            }
            if (idx >= 0) aenc.queueInputBuffer(idx, 0, 0, Math.max(0, base) + samples * 1000000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            long until = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < until) {
                if (pump(aenc, false, 20000)) break;
            }
        } catch (Throwable t) {
            // 音频出任何问题都不影响画面
            synchronized (lock) { audioDisabled = true; tryStartMux(); }
        }
    }

    // ------------------------------------------------------------ 取输出（非阻塞等待）

    /** 把编码器当前能给的输出全部处理掉；返回 true = 已收到 EOS。 */
    private boolean pump(MediaCodec c, boolean video, long timeoutUs) {
        MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
        long to = timeoutUs;
        while (true) {
            int i = c.dequeueOutputBuffer(bi, to);
            to = 0;
            if (i == MediaCodec.INFO_TRY_AGAIN_LATER) return false;
            if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized (lock) {
                    int t = mux.addTrack(c.getOutputFormat());
                    if (video) { vTrack = t; vTrackAtMs = System.currentTimeMillis(); } else aTrack = t;
                    tryStartMux();
                }
            } else if (i >= 0) {
                boolean eos = (bi.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                if ((bi.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) bi.size = 0;
                if (bi.size > 0) {
                    ByteBuffer out = c.getOutputBuffer(i);
                    out.position(bi.offset); out.limit(bi.offset + bi.size);
                    writeSample(video, out, bi);
                }
                c.releaseOutputBuffer(i, false);
                if (eos) return true;
            }
        }
    }

    private void tryStartMux() {          // 调用时已持有 lock
        if (muxStarted || vTrack < 0) return;
        if (hasAudio && !audioDisabled && aTrack < 0) return;
        mux.start();
        muxStarted = true;
        for (Pending p : queue) writeNow(p.video, ByteBuffer.wrap(p.data), p.info);
        queue.clear();
    }

    private void writeSample(boolean video, ByteBuffer out, MediaCodec.BufferInfo bi) {
        synchronized (lock) {
            if (!video && audioDisabled) return;
            if (muxStarted) { writeNow(video, out, bi); return; }
            if (queue.size() >= MAX_PENDING) return;     // 保险：最多暂存约 30 秒的样本
            Pending p = new Pending();
            p.video = video; p.data = new byte[bi.size];
            out.get(p.data);
            p.info.set(0, bi.size, bi.presentationTimeUs, bi.flags);
            queue.add(p);
        }
    }

    private void writeNow(boolean video, ByteBuffer out, MediaCodec.BufferInfo bi) {
        int t = video ? vTrack : aTrack;
        if (t < 0) return;
        try { mux.writeSampleData(t, out, bi); }
        catch (Throwable e) { if (error == null) error = String.valueOf(e.getMessage()); }
    }

    /** 画完最后一帧后调用；阻塞到文件写完。返回 true = 文件可用。 */
    boolean stop() {
        stopping = true;
        try { venc.signalEndOfInputStream(); } catch (Throwable t) { if (error == null) error = String.valueOf(t.getMessage()); }
        try { if (vThread != null) vThread.join(5000); } catch (InterruptedException ignore) {}
        try { if (aThread != null) aThread.join(6000); } catch (InterruptedException ignore) {}
        abort = true;
        try { if (rec != null) { rec.stop(); rec.release(); } } catch (Throwable ignore) {}
        boolean ok;
        synchronized (lock) {
            ok = muxStarted && error == null;
            try { if (muxStarted) mux.stop(); } catch (Throwable t) { ok = false; if (error == null) error = String.valueOf(t.getMessage()); }
            try { mux.release(); } catch (Throwable ignore) {}
        }
        try { venc.stop(); venc.release(); } catch (Throwable ignore) {}
        try { if (aenc != null) { aenc.stop(); aenc.release(); } } catch (Throwable ignore) {}
        try { if (input != null) input.release(); } catch (Throwable ignore) {}
        return ok && file.length() > 0;
    }
}

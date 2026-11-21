package com.lucida.camera;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/** 动态照片（Google MicroVideo 格式）：JPEG + 内嵌 MP4，XMP 里记录视频长度。Google 相册等会把它当动态照片播放。 */
final class MotionPhoto {
    private MotionPhoto() {}

    static boolean embed(File jpg, File mp4, File out, long presentationUs) {
        try {
            byte[] j = read(jpg), v = read(mp4);
            if (j.length < 4 || (j[0] & 0xFF) != 0xFF || (j[1] & 0xFF) != 0xD8) return false;
            String xmp = "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                    + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
                    + "<rdf:Description rdf:about=\"\" xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\""
                    + " GCamera:MicroVideo=\"1\" GCamera:MicroVideoVersion=\"1\" GCamera:MicroVideoOffset=\"" + v.length + "\""
                    + " GCamera:MicroVideoPresentationTimestampUs=\"" + presentationUs + "\"/>"
                    + "</rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
            byte[] ns = "http://ns.adobe.com/xap/1.0/\0".getBytes("ISO-8859-1");
            byte[] body = xmp.getBytes("UTF-8");
            int len = 2 + ns.length + body.length;
            if (len > 65535) return false;
            ByteArrayOutputStream o = new ByteArrayOutputStream(j.length + v.length + len + 8);
            o.write(0xFF); o.write(0xD8);
            o.write(0xFF); o.write(0xE1); o.write((len >> 8) & 0xFF); o.write(len & 0xFF);
            o.write(ns); o.write(body);
            o.write(j, 2, j.length - 2);
            o.write(v);
            try (FileOutputStream f = new FileOutputStream(out)) { f.write(o.toByteArray()); }
            return true;
        } catch (Throwable t) { return false; }
    }

    private static byte[] read(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream b = new ByteArrayOutputStream((int) f.length());
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toByteArray();
        }
    }
}

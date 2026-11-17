package com.lucida.camera;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * B1a：「关于 / 版本更新 / 赞赏」三个展示页 + 启动时的「更新了什么」弹窗，全部原生（和苹果版同一套内容）。
 * 内容来自 assets/content/（about.json / updates.json / donate.json + 三张图），由构建脚本从同一份网页源提取。
 * 作为 MainActivity 里的覆盖层显示（不另开 Activity，网页相机不会被暂停）。
 */
final class NativePages {
    private NativePages() {}

    static final int BG = 0xFF0B0B0D, YELLOW = 0xFFFFD60A;

    // ------------------------------------------------------------ 内容读取

    static JSONObject obj(Context c, String name) {
        try { return new JSONObject(text(c, name)); } catch (Throwable t) { return new JSONObject(); }
    }
    static JSONArray arr(Context c, String name) {
        try { return new JSONArray(text(c, name)); } catch (Throwable t) { return new JSONArray(); }
    }
    private static String text(Context c, String name) throws Exception {
        try (InputStream in = c.getAssets().open("content/" + name + ".json")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }
    static Bitmap image(Context c, String name) {
        try (InputStream in = c.getAssets().open("content/" + name + ".jpg")) { return BitmapFactory.decodeStream(in); }
        catch (Throwable t) { return null; }
    }

    /** 文案里只用到 <b> 和 <br>：转成带粗体的文字 */
    static CharSequence rich(String html, int color, int boldColor) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int boldStart = -1, i = 0;
        while (i < html.length()) {
            char ch = html.charAt(i);
            int close = ch == '<' ? html.indexOf('>', i) : -1;
            if (close > 0) {
                String tag = html.substring(i + 1, close).toLowerCase().replace(" ", "");
                if (tag.equals("b")) boldStart = out.length();
                else if (tag.equals("/b") && boldStart >= 0) {
                    out.setSpan(new StyleSpan(Typeface.BOLD), boldStart, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    out.setSpan(new ForegroundColorSpan(boldColor), boldStart, out.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    boldStart = -1;
                } else if (tag.equals("br") || tag.equals("br/")) out.append('\n');
                i = close + 1;
            } else { out.append(ch); i++; }
        }
        return out;
    }

    static LinearLayout bullet(Context c, String html) {
        LinearLayout r = new LinearLayout(c); r.setOrientation(LinearLayout.HORIZONTAL);
        TextView dot = DualUi.label(c, "•", 14, YELLOW);
        TextView t = DualUi.label(c, "", 14, 0xC7FFFFFF);
        t.setText(rich(html, 0xC7FFFFFF, Color.WHITE)); t.setLineSpacing(DualUi.dp(c, 3), 1f);
        r.addView(dot, new LinearLayout.LayoutParams(DualUi.dp(c, 16), -2));
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        r.setPadding(0, DualUi.dp(c, 6), 0, 0);
        return r;
    }

    /** 网页发来 openPage(name)：about / ver / donate = 整页；update = 启动弹窗（每个版本只弹一次） */
    static View create(Activity a, String name, Runnable onClose) {
        if ("update".equals(name)) return UpdateDialog.make(a, onClose);
        if ("about".equals(name) || "ver".equals(name) || "donate".equals(name)) { Page p = new Page(a, name); p.onClose = onClose; return p; }
        return null;
    }

    // ------------------------------------------------------------ 整页

    static final class Page extends FrameLayout {
        private final String kind;
        private LinearLayout col;
        private View modal;
        Runnable onClose;

        Page(final Context c, String kind) {
            super(c);
            this.kind = kind;
            setBackgroundColor(BG); setClickable(true);
            final LinearLayout root = new LinearLayout(c); root.setOrientation(LinearLayout.VERTICAL);
            addView(root, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout bar = new FrameLayout(c);
            root.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 52)));
            DualIcon back = new DualIcon(c, DualIcon.BACK);
            back.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { if (onClose != null) onClose.run(); } });
            bar.addView(back, new FrameLayout.LayoutParams(DualUi.dp(c, 44), DualUi.dp(c, 44), Gravity.CENTER_VERTICAL | Gravity.LEFT));
            ((FrameLayout.LayoutParams) back.getLayoutParams()).leftMargin = DualUi.dp(c, 12);
            bar.addView(DualUi.label(c, kind.equals("about") ? "关于" : kind.equals("ver") ? "版本更新" : "赞赏", 17, Color.WHITE),
                    new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
            ScrollView sv = new ScrollView(c); sv.setVerticalScrollBarEnabled(false);
            root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
            col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(DualUi.dp(c, 20), DualUi.dp(c, 8), DualUi.dp(c, 20), DualUi.dp(c, 48));
            sv.addView(col, new FrameLayout.LayoutParams(-1, -2));
            if (kind.equals("about")) buildAbout(c); else if (kind.equals("ver")) buildVer(c); else buildDonate(c);
        }

        @Override public WindowInsets onApplyWindowInsets(WindowInsets in) {
            int top = in.getSystemWindowInsetTop(), bottom = in.getSystemWindowInsetBottom();
            if (android.os.Build.VERSION.SDK_INT >= 28 && in.getDisplayCutout() != null) top = Math.max(top, in.getDisplayCutout().getSafeInsetTop());
            setPadding(0, Math.max(top, DualUi.dp(getContext(), 24)), 0, bottom);
            return super.onApplyWindowInsets(in);
        }

        boolean closeModalIfOpen() {
            if (modal == null) return false;
            removeView(modal); modal = null; return true;
        }

        private View gap(Context c, int dp) { View v = new View(c); v.setLayoutParams(new LinearLayout.LayoutParams(1, DualUi.dp(c, dp))); return v; }

        private TextView centered(Context c, String t, float sp, int color) {
            TextView v = DualUi.label(c, t, sp, color); v.setGravity(Gravity.CENTER); return v;
        }

        // ---- 关于
        private void buildAbout(Context c) {
            JSONObject a = obj(c, "about");
            ImageView logo = new ImageView(c); logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap lb = image(c, "logo"); if (lb != null) logo.setImageBitmap(lb);
            logo.setBackground(DualUi.round(0xFF222222, 24, c)); logo.setClipToOutline(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(DualUi.dp(c, 96), DualUi.dp(c, 96)); lp.gravity = Gravity.CENTER_HORIZONTAL;
            col.addView(logo, lp); col.addView(gap(c, 14));
            TextView t = centered(c, a.optString("title", "澄影"), 30, Color.WHITE); t.setTypeface(Typeface.DEFAULT_BOLD); t.setLetterSpacing(0.25f);
            col.addView(t); col.addView(gap(c, 6));
            col.addView(centered(c, a.optString("verPrefix", "") + BuildConfig.VERSION_NAME, 13, 0x80FFFFFF));
            JSONArray secs = a.optJSONArray("sections");
            for (int i = 0; secs != null && i < secs.length(); i++) {
                JSONObject s = secs.optJSONObject(i); if (s == null) continue;
                col.addView(gap(c, 22));
                col.addView(DualUi.label(c, s.optString("h"), 13, 0x80FFFFFF)); col.addView(gap(c, 8));
                TextView p = DualUi.label(c, "", 15, 0xE0FFFFFF);
                p.setText(rich(s.optString("p"), 0xE0FFFFFF, Color.WHITE)); p.setLineSpacing(DualUi.dp(c, 4), 1f);
                col.addView(p);
            }
            col.addView(gap(c, 26));
            JSONObject d = a.optJSONObject("dev");
            if (d != null) {
                LinearLayout card = new LinearLayout(c); card.setOrientation(LinearLayout.HORIZONTAL); card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackground(DualUi.round(0x12FFFFFF, 26, c)); card.setPadding(DualUi.dp(c, 16), DualUi.dp(c, 16), DualUi.dp(c, 16), DualUi.dp(c, 16));
                ImageView av = new ImageView(c); av.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap db = image(c, "dev"); if (db != null) av.setImageBitmap(db);
                av.setBackground(DualUi.round(0xFF333333, 32, c)); av.setClipToOutline(true);
                card.addView(av, new LinearLayout.LayoutParams(DualUi.dp(c, 64), DualUi.dp(c, 64)));
                LinearLayout tx = new LinearLayout(c); tx.setOrientation(LinearLayout.VERTICAL);
                TextView n = DualUi.label(c, d.optString("name"), 20, Color.WHITE); n.setTypeface(Typeface.DEFAULT_BOLD); n.setLetterSpacing(0.2f);
                tx.addView(n); tx.addView(DualUi.label(c, d.optString("sub"), 13, 0x80FFFFFF));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, -2, 1f); tp.leftMargin = DualUi.dp(c, 16);
                card.addView(tx, tp);
                col.addView(card);
            }
        }

        // ---- 版本更新
        private void buildVer(Context c) {
            LinearLayout head = new LinearLayout(c); head.setOrientation(LinearLayout.VERTICAL);
            head.setBackground(DualUi.round(0x12FFFFFF, 20, c)); head.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 16), DualUi.dp(c, 18), DualUi.dp(c, 16));
            TextView b = DualUi.label(c, "澄影 " + BuildConfig.VERSION_NAME, 20, Color.WHITE); b.setTypeface(Typeface.DEFAULT_BOLD);
            head.addView(b); head.addView(DualUi.label(c, "当前版本", 13, YELLOW));
            col.addView(head);
            JSONArray ups = arr(c, "updates");
            for (int i = 0; i < ups.length(); i++) {
                JSONObject u = ups.optJSONObject(i); if (u == null) continue;
                col.addView(gap(c, 14));
                LinearLayout card = new LinearLayout(c); card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(DualUi.round(0x0DFFFFFF, 20, c)); card.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 16), DualUi.dp(c, 18), DualUi.dp(c, 16));
                TextView v = DualUi.label(c, u.optString("v"), 17, Color.WHITE); v.setTypeface(Typeface.DEFAULT_BOLD);
                card.addView(v); card.addView(DualUi.label(c, u.optString("d"), 12, 0x73FFFFFF));
                JSONArray its = u.optJSONArray("items");
                for (int k = 0; its != null && k < its.length(); k++) card.addView(bullet(c, its.optString(k)));
                col.addView(card);
            }
        }

        // ---- 赞赏
        private void buildDonate(final Context c) {
            JSONObject d = obj(c, "donate");
            JSONObject hero = d.optJSONObject("hero"); if (hero == null) hero = new JSONObject();
            TextView t = DualUi.label(c, hero.optString("title"), 26, Color.WHITE); t.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(t); col.addView(gap(c, 8));
            col.addView(DualUi.label(c, hero.optString("sub"), 14, 0x99FFFFFF)); col.addView(gap(c, 22));
            TextView btn = DualUi.label(c, "♥  " + hero.optString("button", "赞赏"), 17, Color.BLACK); btn.setGravity(Gravity.CENTER);
            btn.setTypeface(Typeface.DEFAULT_BOLD); btn.setBackground(DualUi.round(YELLOW, 26, c));
            btn.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { openModal(c); } });
            col.addView(btn, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 52)));
            col.addView(gap(c, 26));
            col.addView(centered(c, d.optString("foot"), 13, 0x59FFFFFF));
        }

        private void openModal(final Context c) {
            if (modal != null) return;
            JSONObject m = obj(c, "donate").optJSONObject("modal"); if (m == null) return;
            final FrameLayout dim = new FrameLayout(c);
            dim.setBackgroundColor(0xF004040A); dim.setClickable(true);
            ScrollView sv = new ScrollView(c); sv.setVerticalScrollBarEnabled(false);
            dim.addView(sv, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout cc = new LinearLayout(c); cc.setOrientation(LinearLayout.VERTICAL); cc.setGravity(Gravity.CENTER_HORIZONTAL);
            cc.setPadding(DualUi.dp(c, 28), DualUi.dp(c, 56), DualUi.dp(c, 28), DualUi.dp(c, 32));
            sv.addView(cc, new FrameLayout.LayoutParams(-1, -2));
            TextView heart = centered(c, "♥", 34, 0xFFFF6B8C); cc.addView(heart);
            TextView en = centered(c, m.optString("en"), 11, 0x73FFFFFF); en.setLetterSpacing(0.25f); cc.addView(en); cc.addView(gap(c, 10));
            TextView ti = centered(c, m.optString("title"), 24, Color.WHITE); ti.setTypeface(Typeface.DEFAULT_BOLD); cc.addView(ti); cc.addView(gap(c, 10));
            cc.addView(centered(c, m.optString("lead"), 14, 0xA6FFFFFF)); cc.addView(gap(c, 10));
            cc.addView(centered(c, m.optString("quote"), 14, 0xD9FFFFFF)); cc.addView(gap(c, 16));
            ImageView qr = new ImageView(c); qr.setScaleType(ImageView.ScaleType.CENTER_CROP);
            Bitmap qb = image(c, "donate_qr"); if (qb != null) qr.setImageBitmap(qb);
            qr.setBackground(DualUi.round(Color.WHITE, 18, c)); qr.setClipToOutline(true);
            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(DualUi.dp(c, 200), DualUi.dp(c, 200)); qp.gravity = Gravity.CENTER_HORIZONTAL;
            cc.addView(qr, qp); cc.addView(gap(c, 10));
            cc.addView(centered(c, m.optString("scan"), 12, 0x73FFFFFF)); cc.addView(gap(c, 12));
            LinearLayout chips = new LinearLayout(c); chips.setOrientation(LinearLayout.HORIZONTAL); chips.setGravity(Gravity.CENTER);
            JSONArray cs = m.optJSONArray("chips");
            for (int i = 0; cs != null && i < cs.length(); i++) {
                TextView ch = DualUi.label(c, cs.optString(i), 12, 0xD9FFFFFF);
                ch.setBackground(DualUi.round(0x1AFFFFFF, 13, c)); ch.setPadding(DualUi.dp(c, 10), DualUi.dp(c, 5), DualUi.dp(c, 10), DualUi.dp(c, 5));
                LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2); cl.leftMargin = DualUi.dp(c, 3); cl.rightMargin = DualUi.dp(c, 3);
                chips.addView(ch, cl);
            }
            cc.addView(chips); cc.addView(gap(c, 20));
            LinearLayout btns = new LinearLayout(c); btns.setOrientation(LinearLayout.HORIZONTAL);
            TextView save = DualUi.label(c, m.optString("save"), 16, Color.BLACK); save.setGravity(Gravity.CENTER); save.setTypeface(Typeface.DEFAULT_BOLD);
            save.setBackground(DualUi.round(YELLOW, 22, c));
            TextView later = DualUi.label(c, m.optString("later"), 16, 0xD9FFFFFF); later.setGravity(Gravity.CENTER);
            later.setBackground(DualUi.round(0x1AFFFFFF, 22, c));
            LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, DualUi.dp(c, 44), 1f); b1.rightMargin = DualUi.dp(c, 5);
            LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, DualUi.dp(c, 44), 1f); b2.leftMargin = DualUi.dp(c, 5);
            btns.addView(save, b1); btns.addView(later, b2); cc.addView(btns);
            save.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { saveQr(c); } });
            later.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) {
                closeModalIfOpen(); Toast.makeText(c, "谢谢你，下次再说 ♥", Toast.LENGTH_SHORT).show(); } });
            TextView x = centered(c, "✕", 20, Color.WHITE);
            x.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { closeModalIfOpen(); } });
            FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(DualUi.dp(c, 48), DualUi.dp(c, 48), Gravity.TOP | Gravity.RIGHT);
            xp.topMargin = DualUi.dp(c, 8); xp.rightMargin = DualUi.dp(c, 8);
            dim.addView(x, xp);
            modal = dim; addView(dim, new FrameLayout.LayoutParams(-1, -1));
        }

        /** 保存赞赏码：存进系统相册（图片/澄影） */
        private void saveQr(final Context c) {
            new Thread(new Runnable() { @Override public void run() {
                String msg;
                try {
                    File f = new File(DualCameraActivity.outDir(c), "澄影赞赏码.jpg");
                    try (InputStream in = c.getAssets().open("content/donate_qr.jpg"); FileOutputStream o = new FileOutputStream(f)) { MediaSaver.copy(in, o); }
                    MediaSaver.Result r = MediaSaver.save(c, f, "澄影赞赏码.jpg", "image/jpeg");
                    msg = r.ok ? "赞赏码已保存到相册" : "保存失败，请截图保存（" + r.error + "）";
                } catch (Throwable t) { msg = "保存失败，请截图保存"; }
                final String m = msg;
                post(new Runnable() { @Override public void run() { Toast.makeText(c, m, Toast.LENGTH_SHORT).show(); } });
            } }).start();
        }
    }

    // ------------------------------------------------------------ 启动更新弹窗

    static final class UpdateDialog {
        /** 这个版本还没弹过才返回弹窗；弹过返回 null。看过的版本号记在 lucida.seenVer（和网页共用同一份存档） */
        static View make(final Activity a, final Runnable onClose) {
            String ver = BuildConfig.VERSION_NAME;
            NativeKv kv = NativeKv.of(a);
            if (ver.equals(kv.get("lucida.seenVer"))) return null;
            JSONArray ups = arr(a, "updates");
            JSONObject first = ups.length() > 0 ? ups.optJSONObject(0) : null;
            if (first == null) return null;
            kv.setNative("lucida.seenVer", ver);
            final FrameLayout dim = new FrameLayout(a); dim.setBackgroundColor(0x80000000); dim.setClickable(true);
            LinearLayout card = new LinearLayout(a); card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(DualUi.round(0xF51A1A1C, 26, a)); card.setPadding(DualUi.dp(a, 20), DualUi.dp(a, 22), DualUi.dp(a, 20), DualUi.dp(a, 18));
            TextView t = DualUi.label(a, "澄影 " + ver + " 更新", 20, Color.WHITE); t.setGravity(Gravity.CENTER); t.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(t);
            TextView d = DualUi.label(a, "更新时间：" + first.optString("d"), 12, 0x80FFFFFF); d.setGravity(Gravity.CENTER);
            d.setPadding(0, DualUi.dp(a, 4), 0, DualUi.dp(a, 8));
            card.addView(d);
            int maxH = Math.round(a.getResources().getDisplayMetrics().heightPixels * 0.78f);
            LimitedScroll sv = new LimitedScroll(a, maxH - DualUi.dp(a, 190)); sv.setVerticalScrollBarEnabled(false);
            LinearLayout body = new LinearLayout(a); body.setOrientation(LinearLayout.VERTICAL);
            JSONArray its = first.optJSONArray("items");
            for (int k = 0; its != null && k < its.length(); k++) body.addView(bullet(a, its.optString(k)));
            sv.addView(body);
            card.addView(sv, new LinearLayout.LayoutParams(-1, -2));
            TextView ok = DualUi.label(a, "知道了", 17, Color.BLACK); ok.setGravity(Gravity.CENTER); ok.setTypeface(Typeface.DEFAULT_BOLD);
            ok.setBackground(DualUi.round(YELLOW, 23, a));
            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, DualUi.dp(a, 46)); op.topMargin = DualUi.dp(a, 16);
            card.addView(ok, op);
            int w = Math.min(DualUi.dp(a, 360), Math.round(a.getResources().getDisplayMetrics().widthPixels * 0.88f));
            dim.addView(card, new FrameLayout.LayoutParams(w, -2, Gravity.CENTER));
            ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { onClose.run(); } });
            return dim;
        }
    }

    /** 限制最大高度的滚动区（内容多时才出现滚动，按钮始终看得见） */
    static final class LimitedScroll extends ScrollView {
        private final int max;
        LimitedScroll(Context c, int max) { super(c); this.max = Math.max(DualUi.dp(c, 120), max); }
        @Override protected void onMeasure(int w, int h) { super.onMeasure(w, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST)); }
    }
}

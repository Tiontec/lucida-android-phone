package com.lucida.camera;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置整页的一页：主页 / 集合页 / 单选列表页。内容全部由 SettingsRegistry 描述，这里只负责把注册表画出来：
 *   live 的项：正常显示，读写真实设置（SettingsStore）；soon 的项：整行变灰，点击提示「不可用」。
 * 和 iOS 的 SettingsPages.swift 一一对应。
 */
final class SettingsPageView extends FrameLayout {
    static final int HOME = 0, COLLECTION = 1, RADIO = 2;

    private final DualSettingsView owner;
    private final int kind;
    private final String arg;                 // COLLECTION = 集合 id；RADIO = 设置项 id
    private final SettingsStore st;
    private final LinearLayout list;
    private final DualIcon back;
    private final Runnable soonToast;

    SettingsPageView(final DualSettingsView owner, int kind, String arg) {
        super(owner.getContext());
        this.owner = owner; this.kind = kind; this.arg = arg; this.st = owner.store();
        final Context c = getContext();
        setBackgroundColor(0xFF101012);
        setClickable(true);
        soonToast = new Runnable() { @Override public void run() { owner.toast(SettingsRegistry.SOON_TOAST); } };

        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));

        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 60)));
        back = new DualIcon(c, kind == HOME ? DualIcon.DOWN : DualIcon.BACK);
        back.setContentDescription(kind == HOME ? "关闭" : "返回");
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { owner.back(); } });
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(DualUi.dp(c, 40), DualUi.dp(c, 40), Gravity.CENTER_VERTICAL | Gravity.LEFT);
        bp.leftMargin = DualUi.dp(c, 16);
        bar.addView(back, bp);
        TextView t = DualUi.label(c, title(), 18, Color.WHITE);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bar.addView(t, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = DualUi.dp(c, 16);
        list.setPadding(pad, DualUi.dp(c, 8), pad, DualUi.dp(c, 40));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        rebuild();
    }

    private String title() {
        if (kind == HOME) return "更多设置";
        if (kind == COLLECTION) { SettingsRegistry.Collection c = SettingsRegistry.collection(arg); return c == null ? "设置" : c.title; }
        SettingsRegistry.Item it = SettingsRegistry.item(arg);
        return it == null ? "设置" : it.title;
    }

    // ───────────── 画页面 ─────────────

    /** 按注册表重画整页（玻璃 / 平面切换、从子页返回时调用） */
    void rebuild() {
        back.invalidate();
        list.removeAllViews();
        if (kind == HOME) buildHome();
        else if (kind == COLLECTION) { SettingsRegistry.Collection c = SettingsRegistry.collection(arg); if (c != null) buildCollectionPage(c); }
        else { SettingsRegistry.Item it = SettingsRegistry.item(arg); if (it != null) buildRadioPage(it); }
    }

    private void add(View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = DualUi.dp(getContext(), topDp);
        list.addView(v, lp);
    }

    private void buildHome() {
        final Context c = getContext();
        boolean first = true;
        for (final SettingsRegistry.Collection col : SettingsRegistry.COLLECTIONS) {
            add(SettingsUi.heading(c, col.title), first ? 0 : 22);
            first = false;
            if (col.kind == SettingsRegistry.K_INLINE) {
                addGroups(col, 8);
            } else {
                LinearLayout card = SettingsUi.card(c);
                SettingsUi.addRow(card, SettingsUi.row(c, col.summary, null, null, SettingsUi.chevron(c), new Runnable() {
                    @Override public void run() { owner.push(COLLECTION, col.id); } }));
                add(card, 8);
            }
        }
    }

    private void buildCollectionPage(SettingsRegistry.Collection col) {
        if (col.intro.length() > 0) add(SettingsUi.note(getContext(), SettingsRegistry.resolve(col.intro)), 0);
        addGroups(col, col.intro.length() > 0 ? 6 : 0);
    }

    /** 把集合里的各分组画成：灰色小标题 + 卡片（一行一项）+ 灰色说明 */
    private void addGroups(SettingsRegistry.Collection col, int firstTop) {
        Context c = getContext();
        boolean first = true;
        for (SettingsRegistry.Group g : col.groups) {
            int top = first ? firstTop : 20;
            first = false;
            if (g.head.length() > 0 && !g.head.equals(col.title)) {
                add(SettingsUi.subhead(c, g.head), top); top = 6;
            }
            LinearLayout card = SettingsUi.card(c);
            List<String> notes = new ArrayList<String>();
            for (SettingsRegistry.Item it : g.items) {
                for (View r : rows(it)) SettingsUi.addRow(card, r);
                if (it.type != SettingsRegistry.T_RADIO || it.inline) notes.addAll(it.notes);
            }
            add(card, top);
            List<String> foots = new ArrayList<String>();
            if (g.foot.length() > 0) foots.add(g.foot);
            foots.addAll(notes);
            for (String f : foots) add(SettingsUi.note(c, SettingsRegistry.resolve(f)), 8);
        }
    }

    private void buildRadioPage(SettingsRegistry.Item it) {
        Context c = getContext();
        LinearLayout card = SettingsUi.card(c);
        for (View r : radioRows(it)) SettingsUi.addRow(card, r);
        add(card, 0);
        for (String n : it.notes) add(SettingsUi.note(c, n), 10);
    }

    // ───────────── 一项 → 若干行 ─────────────

    private List<View> rows(SettingsRegistry.Item it) {
        Context c = getContext();
        List<View> out = new ArrayList<View>();
        switch (it.type) {
            case SettingsRegistry.T_SWITCH: out.add(toggleRow(it)); break;
            case SettingsRegistry.T_SEG: out.add(segRow(it)); break;
            case SettingsRegistry.T_LINK: out.add(linkRow(it)); break;
            case SettingsRegistry.T_SLIDER: out.add(sliderRow(it)); break;
            case SettingsRegistry.T_RADIO:
                if (it.inline) out.addAll(radioRows(it)); else out.add(radioEntryRow(it));
                break;
            default: out.add(SettingsUi.row(c, it.title, SettingsRegistry.resolve(SettingsRegistry.subtitle(it)), null, null, null)); break;
        }
        if (!it.isLive()) {
            for (int i = 0; i < out.size(); i++) out.set(i, SettingsUi.soon(c, out.get(i), soonToast));
        }
        return out;
    }

    private View toggleRow(final SettingsRegistry.Item it) {
        Context c = getContext();
        DualSwitch sw = new DualSwitch(c, st.bool(it.id), new DualSwitch.Cb() {
            @Override public void on(boolean v) {
                st.set(it.id, v ? "1" : "0");
                owner.changed(it.reconfigure);
                if (it.id.equals("glass")) owner.refreshAll();     // 玻璃样式变了，所有页面重画
            } });
        FrameLayout box = new FrameLayout(c);
        box.addView(sw, new FrameLayout.LayoutParams(DualUi.dp(c, 50), DualUi.dp(c, 28)));
        return SettingsUi.row(c, it.title, SettingsRegistry.resolve(SettingsRegistry.subtitle(it)), null, box, null);
    }

    private View segRow(final SettingsRegistry.Item it) {
        Context c = getContext();
        String[] labels = new String[it.options.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = it.options.get(i).title;
        final LinearLayout[] holder = new LinearLayout[1];
        holder[0] = DualUi.seg(c, labels, SettingsRegistry.selectedIndex(st, it), new DualUi.IntCb() {
            @Override public void on(int i) {
                if (i < 0 || i >= it.options.size()) return;
                if (it.id.equals("ratio") && st.bool("full")) {         // 片门全开时画幅固定 4:3：不改，选中态退回去
                    DualUi.setSeg(holder[0], SettingsRegistry.selectedIndex(st, it));
                    owner.toast("「片门全开」开着时画幅固定为 4:3，关掉后才能改");
                    return;
                }
                st.set(it.id, it.options.get(i).v);
                owner.changed(it.reconfigure);
            } });
        return SettingsUi.controlRow(c, it.title, SettingsRegistry.resolve(SettingsRegistry.subtitle(it)), null, holder[0]);
    }

    private View sliderRow(final SettingsRegistry.Item it) {
        Context c = getContext();
        DualSlider s = new DualSlider(c, it.min, it.max, st.intValue(it.id), new DualSlider.Cb() {
            @Override public void on(float v, boolean fromUser) { if (fromUser) st.set(it.id, String.valueOf(Math.round(v))); } });
        s.setEnabled(it.isLive());
        return SettingsUi.controlRow(c, it.title, SettingsRegistry.resolve(SettingsRegistry.subtitle(it)), st.get(it.id) + "%", s);
    }

    private View linkRow(final SettingsRegistry.Item it) {
        Context c = getContext();
        return SettingsUi.row(c, it.title, SettingsRegistry.subtitle(it), SettingsRegistry.rowValue(st, it), SettingsUi.chevron(c), new Runnable() {
            @Override public void run() {
                if (it.isLive() && it.dest.equals("order")) owner.openOrder();
                else owner.toast(SettingsRegistry.SOON_TOAST);      // 摄像头检测 / 赞赏 / 日志 / 版本 / 关于：M3 做，先灰色
            } });
    }

    /** 单选列表：点一个选项就选中它（右侧对勾） */
    private List<View> radioRows(final SettingsRegistry.Item it) {
        final Context c = getContext();
        List<View> out = new ArrayList<View>();
        final List<TextView> checks = new ArrayList<TextView>();
        final List<String> values = new ArrayList<String>();
        String current = st.get(it.id);
        for (final SettingsRegistry.Option o : it.options) {
            final TextView check = SettingsUi.check(c, o.v.equals(current) && !o.soon);
            checks.add(check); values.add(o.v);
            View row = SettingsUi.row(c, o.title, o.sub, null, check, new Runnable() {
                @Override public void run() {
                    if (o.soon) { owner.toast(SettingsRegistry.SOON_TOAST); return; }
                    st.set(it.id, o.v);
                    for (int i = 0; i < checks.size(); i++) checks.get(i).setVisibility(values.get(i).equals(o.v) ? View.VISIBLE : View.INVISIBLE);
                    owner.changed(it.reconfigure);
                } });
            out.add(o.soon ? SettingsUi.soon(c, row, soonToast) : row);
        }
        return out;
    }

    /** 非内联的单选：主页 / 集合页上只有一行（右侧显示当前值），点开进选项页 */
    private View radioEntryRow(final SettingsRegistry.Item it) {
        Context c = getContext();
        return SettingsUi.row(c, it.title, null, SettingsRegistry.rowValue(st, it), SettingsUi.chevron(c), new Runnable() {
            @Override public void run() { owner.push(RADIO, it.id); } });
    }
}

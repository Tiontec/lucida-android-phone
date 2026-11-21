package com.lucida.camera;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置注册表：描述「设置主页」里每个集合、每一项（类型 / 选项 / 范围 / 默认值 / 状态）。
 * 内容由 tools/gen_settings.py 生成到 SettingsData.java（iOS 同一份数据生成 SettingsData.swift，两端永远一致）。
 * 以后某项接线完成：只改 gen_settings.py 里它的 status（soon → live）重新生成即可。
 */
final class SettingsRegistry {
    private SettingsRegistry() {}

    // 设置项类型：开关 / 分段 / 链接（进新页面）/ 单选列表 / 滑块 / 纯文字
    static final int T_SWITCH = 0, T_SEG = 1, T_LINK = 2, T_RADIO = 3, T_SLIDER = 4, T_INFO = 5;
    // live = 能用（读写真实设置）；soon = 灰色，点击提示「不可用」
    static final int S_LIVE = 0, S_SOON = 1;
    // 作用范围：双摄 / 单摄 / 两者
    static final int SC_DUAL = 0, SC_SINGLE = 1, SC_BOTH = 2;
    // inline = 直接在设置主页里展开（黄色小标题 + 卡片）；page = 主页上只有一行入口，点开进新页面
    static final int K_INLINE = 0, K_PAGE = 1;

    static final String SOON_TOAST = "不可用";            // 设置里灰色项被点击
    static final String PANEL_SOON_TOAST = "未加入此功能";  // 多功能面板里灰色按钮被点击

    static final class Option {
        final String v, title, sub, shortText;   // 存储值 / 标题 / 说明 / 设置行右侧的简短当前值
        final boolean soon;                      // true = 这个选项灰色，点击提示「不可用」，不会被选中（如「液态玻璃」）
        Option(String v, String title, String sub, String shortText, boolean soon) {
            this.v = v; this.title = title; this.sub = sub; this.shortText = shortText; this.soon = soon;
        }
        Option(String v, String title, String sub, String shortText) { this(v, title, sub, shortText, false); }
    }

    static final class Item {
        final String id, title, sub;
        final int type, scope, status;
        final String def;                 // 默认值（开关 "1"/"0"，其余是选项的 v 或数字）
        final List<Option> options;
        final List<String> notes;         // 选项页底部的说明段落
        final String dest;                // link 的目标：order / cams / donate / log / version / about
        final boolean inline;             // radio：直接在页内展开（带说明、右侧对勾），否则主页只显示一行 + 当前值，点开进选项页
        final boolean reconfigure;        // 改了之后需要重新配置摄像头
        final String icon;                // SF Symbol 名（安卓用同名映射到自绘图标）
        final int min, max;               // slider 范围
        Item(String id, String title, String sub, int type, int scope, int status, String def, List<Option> options, List<String> notes,
             String dest, boolean inline, boolean reconfigure, String icon, int min, int max) {
            this.id = id; this.title = title; this.sub = sub; this.type = type; this.scope = scope; this.status = status; this.def = def;
            this.options = options; this.notes = notes; this.dest = dest; this.inline = inline; this.reconfigure = reconfigure;
            this.icon = icon; this.min = min; this.max = max;
        }
        boolean isLive() { return status == S_LIVE; }
    }

    static final class Group {
        final String head, foot;          // 卡片上方 / 下方的灰色小字
        final List<Item> items;
        Group(String head, String foot, List<Item> items) { this.head = head; this.foot = foot; this.items = items; }
    }

    static final class Collection {
        final String id, title, summary, intro, icon;
        final int kind;
        final List<Group> groups;
        Collection(String id, String title, int kind, String summary, String intro, String icon, List<Group> groups) {
            this.id = id; this.title = title; this.kind = kind; this.summary = summary; this.intro = intro; this.icon = icon; this.groups = groups;
        }
    }

    /** 设置主页的集合顺序 */
    static final List<Collection> COLLECTIONS = SettingsData.buildAll();

    private static Map<String, Item> index;

    private static synchronized Map<String, Item> index() {
        if (index == null) {
            Map<String, Item> m = new HashMap<String, Item>();
            for (Collection c : COLLECTIONS) for (Group g : c.groups) for (Item i : g.items) m.put(i.id, i);
            index = m;
        }
        return index;
    }

    static Item item(String id) { return index().get(id); }

    static Collection collection(String id) {
        for (Collection c : COLLECTIONS) if (c.id.equals(id)) return c;
        return null;
    }

    /** 文案里的占位符：{version} = 当前版本号 */
    static String resolve(String s) { return s.replace("{version}", BuildConfig.VERSION_NAME); }

    /** 选项在列表里的位置（当前值找不到就当第一项） */
    static int selectedIndex(SettingsStore st, Item it) {
        String v = st.get(it.id);
        for (int i = 0; i < it.options.size(); i++) if (it.options.get(i).v.equals(v)) return i;
        return 0;
    }

    /** 设置行右侧显示的当前值（开关、普通链接返回空串） */
    static String rowValue(SettingsStore st, Item it) {
        switch (it.type) {
            case T_SEG: case T_RADIO:
                if (it.options.isEmpty()) return "";
                return it.options.get(selectedIndex(st, it)).shortText;
            case T_SLIDER: return st.get(it.id) + "%";
            case T_LINK: return it.sub.startsWith("{") ? resolve(it.sub) : "";   // 如「版本更新」右侧显示 {version}
            default: return "";
        }
    }

    /** 设置行标题下方的说明文字（占位符型的 sub 是右侧值，不算说明） */
    static String subtitle(Item it) { return it.sub.startsWith("{") ? "" : it.sub; }
}

package com.lucida.camera;

/**
 * 多功能面板的按钮注册表：所有按钮的 id / 标题 / 图标 / 状态 / 点击动作都在这一个数组里。
 * 以后某个功能做好了：把它的 state 改成 ST_ENABLED，并在 DualFunctionsView.perform() 里给它的 action 接上调用即可。
 * （iOS 的 PanelRegistry.swift 与这里一一对应：同样的 id、标题、顺序、状态。）
 */
final class PanelRegistry {
    private PanelRegistry() {}

    static final int ST_ENABLED = 0, ST_SOON = 1;                 // ENABLED = 正常可点；SOON = 灰色，点击提示「未加入此功能」
    static final int A_NONE = 0, A_OPEN_PALETTE = 1, A_TOGGLE_DUAL = 2, A_OPEN_RATIO = 3, A_CYCLE_FLASH = 4, A_CYCLE_TIMER = 5;

    static final class Item {
        final String id, title;
        final int glyph, state, action;
        Item(String id, String title, int glyph, int state, int action) { this.id = id; this.title = title; this.glyph = glyph; this.state = state; this.action = action; }
    }

    /** 面板按钮（3 列，从左到右、从上到下） */
    static final Item[] ITEMS = {
        new Item("filter", "滤镜影调", PanelGlyph.FILTER, ST_SOON, A_NONE),
        new Item("flash", "闪光灯", PanelGlyph.BOLT, ST_ENABLED, A_CYCLE_FLASH),
        new Item("palette", "调色盘", PanelGlyph.PALETTE, ST_ENABLED, A_OPEN_PALETTE),
        new Item("ratio", "比例", PanelGlyph.RATIO, ST_ENABLED, A_OPEN_RATIO),
        new Item("timer", "倒计时", PanelGlyph.TIMER, ST_ENABLED, A_CYCLE_TIMER),
        new Item("pano", "360° 全景", PanelGlyph.GLOBE, ST_SOON, A_NONE),
        new Item("dual", "前后双摄", PanelGlyph.DUAL, ST_ENABLED, A_TOGGLE_DUAL),
        new Item("space", "空间重构", PanelGlyph.CUBE, ST_SOON, A_NONE),
        new Item("skin", "皮肤", PanelGlyph.FISH, ST_SOON, A_NONE),
        new Item("aiext", "AI 扩图", PanelGlyph.AIEXT, ST_SOON, A_NONE),
        new Item("pro", "专业模式", PanelGlyph.PRO, ST_SOON, A_NONE),
        new Item("cine", "电影模式", PanelGlyph.FILM, ST_SOON, A_NONE),
    };
}

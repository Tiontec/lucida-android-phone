package com.lucida.camera;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 调色盘预设顺序：按住右边的「≡」上下拖动换位置；「标准」固定在第一个。
 * 这个顺序决定相机里左右滑动换预设时的先后，以及页码圆点的顺序（和苹果那边一致）。
 */
final class DualOrderView extends FrameLayout {
    private final DualPrefs prefs;
    private final Runnable onBack;
    private final LinearLayout list;
    private final ScrollView sv;
    private final int rowH;

    DualOrderView(Context c, DualPrefs prefs, Runnable onBack) { this(c, prefs, "‹ 设置", onBack); }

    DualOrderView(final Context c, DualPrefs prefs, String backText, Runnable onBack) {
        super(c);
        this.prefs = prefs; this.onBack = onBack;
        rowH = DualUi.dp(c, 52);
        setBackgroundColor(0xFF101010);
        setClickable(true);
        LinearLayout col = new LinearLayout(c); col.setOrientation(LinearLayout.VERTICAL);
        addView(col, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout bar = new FrameLayout(c);
        col.addView(bar, new LinearLayout.LayoutParams(-1, DualUi.dp(c, 56)));
        TextView back = DualUi.label(c, backText, 16, DualUi.YELLOW);
        back.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 10), DualUi.dp(c, 18), DualUi.dp(c, 10));
        back.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { DualOrderView.this.onBack.run(); } });
        bar.addView(back, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.LEFT));
        bar.addView(DualUi.label(c, "调色盘预设顺序", 18, Color.WHITE), new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        TextView reset = DualUi.label(c, "恢复默认", 15, DualUi.YELLOW);
        reset.setPadding(DualUi.dp(c, 18), DualUi.dp(c, 10), DualUi.dp(c, 18), DualUi.dp(c, 10));
        reset.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { PresetOrder.reset(getContext()); fill(); } });
        bar.addView(reset, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.RIGHT));
        TextView note = DualUi.label(c, "按住右边的 ≡ 上下拖动，调整左右滑动调色盘时预设出现的先后；「标准」固定在第一个。", 12, 0x80FFFFFF);
        note.setPadding(DualUi.dp(c, 20), 0, DualUi.dp(c, 20), DualUi.dp(c, 8));
        col.addView(note);
        sv = new ScrollView(c);
        col.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        list = new LinearLayout(c); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(DualUi.dp(c, 16), 0, DualUi.dp(c, 16), DualUi.dp(c, 40));
        sv.addView(list, new FrameLayout.LayoutParams(-1, -2));
        fill();
    }

    private void fill() {
        Context c = getContext();
        list.removeAllViews();
        int[] idx = PresetOrder.indices(getContext());
        for (int k = 0; k < idx.length; k++) list.addView(makeRow(c, idx[k], k == 0), new LinearLayout.LayoutParams(-1, rowH));
    }

    private View makeRow(final Context c, final int presetIndex, boolean fixed) {
        final FrameLayout row = new FrameLayout(c);
        row.setTag(Integer.valueOf(presetIndex));
        row.setBackgroundColor(0xFF161616);
        TextView name = DualUi.label(c, GradeState.PRESETS[presetIndex].name + (fixed ? "（固定第一）" : ""), 16, Color.WHITE);
        name.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(name, new FrameLayout.LayoutParams(-2, -1, Gravity.LEFT));
        ((FrameLayout.LayoutParams) name.getLayoutParams()).leftMargin = DualUi.dp(c, 16);
        View div = new View(c); div.setBackgroundColor(0x1AFFFFFF);
        row.addView(div, new FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM));
        TextView grip = DualUi.label(c, fixed ? "🔒" : "≡", fixed ? 15 : 24, fixed ? 0x80FFFFFF : 0xB3FFFFFF);
        grip.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(DualUi.dp(c, 64), -1, Gravity.RIGHT);
        row.addView(grip, gp);
        if (!fixed) {
            grip.setOnTouchListener(new OnTouchListener() {
                float startRaw; int startIdx, curIdx;
                @Override public boolean onTouch(View v, MotionEvent e) {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            sv.requestDisallowInterceptTouchEvent(true);
                            startRaw = e.getRawY(); startIdx = curIdx = list.indexOfChild(row);
                            row.setBackgroundColor(0xFF2A2A2A); row.setElevation(DualUi.dp(c, 8)); row.bringToFront();
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            float y = startIdx * rowH + (e.getRawY() - startRaw);
                            int target = Math.max(1, Math.min(list.getChildCount() - 1, Math.round(y / rowH)));
                            if (target != curIdx) { list.removeView(row); list.addView(row, target, new LinearLayout.LayoutParams(-1, rowH)); curIdx = target; row.setElevation(DualUi.dp(c, 8)); }
                            row.setTranslationY(y - curIdx * rowH);
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: {
                            row.setTranslationY(0); row.setElevation(0); row.setBackgroundColor(0xFF161616);
                            int[] idx = new int[list.getChildCount()];
                            for (int i = 0; i < idx.length; i++) idx[i] = (Integer) list.getChildAt(i).getTag();
                            PresetOrder.save(c, idx);
                            return true;
                        }
                    }
                    return true;
                }
            });
        }
        return row;
    }
}

package in.sid.launcher;

import android.content.Context;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** A glass list: rows with an icon, a title and a value line. Up/Down move, OK selects, Back closes. */
final class MenuOverlay extends LinearLayout {
    static final class Row {
        final String id, icon;
        String title, value;
        Row(String id, String icon, String title, String value) { this.id = id; this.icon = icon; this.title = title; this.value = value; }
    }

    interface Listener {
        void onSelect(Row row);
        void onBack();
    }

    private final List<Row> rows = new ArrayList<>();
    private final List<View> rowViews = new ArrayList<>();
    private int focus;
    private Listener listener;
    private final boolean big;

    MenuOverlay(Context c, String title, boolean big) {
        super(c);
        this.big = big;
        setOrientation(VERTICAL);
        setBackground(new Glass(big ? 28 : 24).deep(true));
        int pad = U.px(big ? 22 : 10);
        setPadding(pad, pad, pad, pad);
        setClipChildren(false);
        if (title != null) {
            TextView tv = new TextView(c);
            tv.setText(title);
            tv.setTypeface(U.medium);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.f(24));
            tv.setTextColor(0xFFFFFFFF);
            tv.setPadding(U.px(10), 0, U.px(10), U.px(14));
            addView(tv);
        }
    }

    void setListener(Listener l) { listener = l; }

    void setRows(List<Row> list) {
        rows.clear(); rows.addAll(list);
        for (View v : rowViews) removeView(v);
        rowViews.clear();
        for (Row r : rows) {
            View v = rowView(r);
            rowViews.add(v);
            addView(v, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        if (focus >= rows.size()) focus = Math.max(0, rows.size() - 1);
        applyFocus();
    }

    void refreshRow(Row r) {
        int i = rows.indexOf(r);
        if (i < 0) return;
        LinearLayout v = (LinearLayout) rowViews.get(i);
        LinearLayout texts = (LinearLayout) v.getChildAt(1);
        ((TextView) texts.getChildAt(0)).setText(r.title);
        TextView val = (TextView) texts.getChildAt(1);
        val.setText(r.value);
        val.setVisibility(r.value == null || r.value.isEmpty() ? GONE : VISIBLE);
    }

    void setFocus(int i) { focus = Math.max(0, Math.min(rows.size() - 1, i)); applyFocus(); }
    int focus() { return focus; }

    private View rowView(Row r) {
        LinearLayout v = new LinearLayout(getContext());
        v.setOrientation(HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        int ph = U.px(big ? 14 : 16), pv = U.px(big ? 12 : 14);
        v.setPadding(ph, pv, ph, pv);
        IconView icon = new IconView(getContext(), r.icon);
        int is = U.px(big ? 24 : 26);
        LayoutParams ip = new LayoutParams(is, is);
        ip.rightMargin = U.px(14);
        v.addView(icon, ip);
        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(VERTICAL);
        TextView title = new TextView(getContext());
        title.setText(r.title);
        title.setTypeface(U.medium);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.f(big ? 19 : 20));
        title.setSingleLine();
        texts.addView(title);
        TextView value = new TextView(getContext());
        value.setText(r.value);
        value.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, U.f(14));
        value.setSingleLine();
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setVisibility(r.value == null || r.value.isEmpty() ? GONE : VISIBLE);
        texts.addView(value);
        v.addView(texts, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        IconView chev = new IconView(getContext(), "chevron");
        v.addView(chev, new LayoutParams(U.px(20), U.px(20)));
        v.setOnClickListener(x -> { focus = rows.indexOf(r); applyFocus(); if (listener != null) listener.onSelect(r); });
        return v;
    }

    private void applyFocus() {
        for (int i = 0; i < rowViews.size(); i++) {
            LinearLayout v = (LinearLayout) rowViews.get(i);
            boolean on = i == focus;
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(U.f(16));
            bg.setColor(on ? 0xFFFFFFFF : 0);
            v.setBackground(bg);
            int fg = on ? 0xFF0B1220 : 0xFFFFFFFF, dim = on ? U.argb(0.6f, 0x0B1220) : U.white(0.6f);
            ((IconView) v.getChildAt(0)).setColor(fg);
            LinearLayout texts = (LinearLayout) v.getChildAt(1);
            ((TextView) texts.getChildAt(0)).setTextColor(fg);
            ((TextView) texts.getChildAt(1)).setTextColor(dim);
            ((IconView) v.getChildAt(2)).setColor(dim);
        }
    }

    /** @return true when the key was used. */
    boolean onKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_DOWN: setFocus(focus + 1); return true;
            case KeyEvent.KEYCODE_DPAD_UP: setFocus(focus - 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (listener != null && focus < rows.size()) listener.onSelect(rows.get(focus));
                return true;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (listener != null) listener.onBack();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return true;
        }
        return false;
    }
}

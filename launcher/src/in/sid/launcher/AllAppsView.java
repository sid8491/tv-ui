package in.sid.launcher;

import android.content.Context;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** All installed TV apps as a glass grid, six per row. Arrow keys move, OK opens, Back closes. */
final class AllAppsView extends LinearLayout {
    interface Listener {
        void onSelect(Apps.Entry e);
        void onBack();
    }

    private final DockView grid;
    private final TextView label;
    private int focus;
    private Listener listener;

    AllAppsView(Context c, List<Apps.Entry> apps) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(new Glass(28).deep(true));
        setPadding(U.px(10), U.px(12), U.px(10), U.px(14));
        setClipChildren(false);
        TextView title = new TextView(c);
        title.setText("All apps");
        title.setTypeface(U.medium);
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, U.f(24));
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(U.px(16), 0, U.px(16), U.px(4));
        addView(title);
        grid = new DockView(c, 6, 12, 16, 18, 10);
        grid.setLift(1.08f, -4);
        grid.setApps(apps);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        label = new TextView(c);
        label.setTypeface(U.medium);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, U.f(20));
        label.setTextColor(0xFFFFFFFF);
        label.setPadding(U.px(16), U.px(6), U.px(16), 0);
        addView(label);
        setFocus(0);
    }

    void setListener(Listener l) { listener = l; }

    private void setFocus(int i) {
        focus = Math.max(0, Math.min(grid.count() - 1, i));
        grid.setFocused(focus);
        Apps.Entry e = grid.at(focus);
        label.setText(e == null ? "" : e.label);
    }

    boolean onKey(int key) {
        int cols = grid.cols();
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: setFocus(focus + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: setFocus(focus - 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (focus + cols < grid.count()) setFocus(focus + cols); return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (focus - cols >= 0) setFocus(focus - cols); else if (listener != null) listener.onBack(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                if (listener != null && grid.at(focus) != null) listener.onSelect(grid.at(focus));
                return true;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (listener != null) listener.onBack();
                return true;
        }
        return false;
    }
}

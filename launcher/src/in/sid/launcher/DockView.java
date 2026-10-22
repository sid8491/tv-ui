package in.sid.launcher;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

/**
 * One glass bar holding the apps' real banners. The focused tile lifts out of the dock (scale + translation,
 * raised Z so neighbours never cover it). Also used for the dock editor's "available" strip and All apps (rows).
 */
final class DockView extends ViewGroup {
    private final List<Apps.Entry> apps = new ArrayList<>();
    private int focused = -1;
    private boolean editing;
    private final int columns;       // 0 = one row with as many columns as apps
    private final float padV, padH, gap, radius;
    private float lift = 1.42f, liftY = -14f;
    private float maxTile = 0; // 0 = no cap; otherwise tiles never grow past this width and the bar hugs them

    void setMaxTileWidth(float units) { maxTile = U.f(units); }

    DockView(Context c, int columns, float padVUnits, float padHUnits, float gapUnits, float tileRadiusUnits) {
        super(c);
        this.columns = columns;
        padV = U.f(padVUnits); padH = U.f(padHUnits); gap = U.f(gapUnits); radius = U.f(tileRadiusUnits);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setLift(float scale, float dyUnits) { lift = scale; liftY = dyUnits; }

    void setApps(List<Apps.Entry> list) {
        apps.clear();
        apps.addAll(list);
        removeAllViews();
        for (Apps.Entry e : apps) addView(tile(e));
        if (focused >= apps.size()) focused = apps.size() - 1;
        applyFocus(false);
        requestLayout();
    }

    List<Apps.Entry> apps() { return apps; }
    int count() { return apps.size(); }
    Apps.Entry at(int i) { return i >= 0 && i < apps.size() ? apps.get(i) : null; }
    int cols() { return columns > 0 ? columns : Math.max(1, apps.size()); }

    private View tile(Apps.Entry e) {
        ImageView iv = new ImageView(getContext());
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setImageDrawable(e.drawable(getContext()));
        iv.setBackgroundColor(0xFF000000);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); }
        });
        iv.setClipToOutline(true);
        GradientDrawable ringOff = new GradientDrawable();
        ringOff.setColor(0);
        ringOff.setCornerRadius(radius);
        ringOff.setStroke(U.px(1), U.white(0.18f));
        iv.setForeground(ringOff);
        iv.setAlpha(0.92f);
        return iv;
    }

    void setFocused(int idx) { focused = idx; applyFocus(true); }
    int focused() { return focused; }
    void setEditing(boolean e) { editing = e; applyFocus(false); }

    private void applyFocus(boolean animate) {
        for (int i = 0; i < getChildCount(); i++) {
            ImageView v = (ImageView) getChildAt(i);
            boolean on = i == focused;
            GradientDrawable ring = (GradientDrawable) v.getForeground();
            ring.setStroke(on ? U.px(2.5f) : U.px(1), on ? (editing ? 0xFFF59E0B : 0xF2FFFFFF) : U.white(0.18f));
            float s = on ? lift : 1f, ty = on ? U.f(liftY) : 0f;
            v.setTranslationZ(on ? U.f(20) : 0f);
            v.setElevation(on ? U.f(12) : 0f);
            if (animate) {
                v.animate().scaleX(s).scaleY(s).translationY(ty).alpha(on ? 1f : 0.92f).setDuration(200).start();
            } else {
                v.animate().cancel();
                v.setScaleX(s); v.setScaleY(s); v.setTranslationY(ty); v.setAlpha(on ? 1f : 0.92f);
            }
        }
    }

    /** Editor operations. */
    void move(int from, int to) {
        if (from < 0 || to < 0 || from >= apps.size() || to >= apps.size()) return;
        Apps.Entry e = apps.remove(from);
        apps.add(to, e);
        View v = getChildAt(from);
        removeViewAt(from);
        addView(v, to);
        requestLayout();
    }

    void removeAt(int i) {
        if (i < 0 || i >= apps.size()) return;
        apps.remove(i);
        removeViewAt(i);
        if (focused >= apps.size()) focused = apps.size() - 1;
        requestLayout();
        applyFocus(false);
    }

    void add(Apps.Entry e) {
        apps.add(e);
        addView(tile(e));
        requestLayout();
        applyFocus(false);
    }

    /** Centre x of tile i in this view's coordinates (for the name label beneath). */
    float tileCenterX(int i) {
        View v = getChildAt(i);
        return v == null ? 0 : (v.getLeft() + v.getRight()) / 2f;
    }

    int tileWidth() {
        int n = cols();
        int fit = (int) ((getMeasuredWidth() - 2 * padH - (n - 1) * gap) / n);
        return maxTile > 0 ? (int) Math.min(fit, maxTile) : fit;
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        if (apps.isEmpty()) { setMeasuredDimension(w, (int) (2 * padV + U.f(72))); return; }
        int n = cols();
        int tw = (int) ((w - 2 * padH - (n - 1) * gap) / n);
        if (maxTile > 0 && tw > maxTile) { tw = (int) maxTile; w = (int) (2 * padH + n * tw + (n - 1) * gap); }
        int th = tw * 9 / 16;
        int rows = Math.max(1, (apps.size() + n - 1) / n);
        int h = (int) (2 * padV + rows * th + (rows - 1) * gap);
        int cw = MeasureSpec.makeMeasureSpec(tw, MeasureSpec.EXACTLY), ch = MeasureSpec.makeMeasureSpec(th, MeasureSpec.EXACTLY);
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).measure(cw, ch);
        setMeasuredDimension(w, h);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int n = cols();
        int tw = tileWidth(), th = tw * 9 / 16;
        for (int i = 0; i < getChildCount(); i++) {
            int col = i % n, row = i / n;
            int x = (int) (padH + col * (tw + gap)), y = (int) (padV + row * (th + gap));
            getChildAt(i).layout(x, y, x + tw, y + th);
        }
    }
}

package in.sid.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

/** "Continue watching": up to four glass cards in a 2x2 grid, each with the still, the app's banner badge, a progress bar and two lines. */
final class ContinueView extends ViewGroup {
    static final int COLS = 2;
    private final List<WatchNext.Item> items = new ArrayList<>();
    private final Apps apps;
    private int focused = -1;
    private final float gap = U.f(16);

    ContinueView(Context c, Apps apps) {
        super(c);
        this.apps = apps;
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setItems(List<WatchNext.Item> list) {
        items.clear();
        items.addAll(list);
        removeAllViews();
        for (WatchNext.Item it : items) addView(new Card(getContext(), it, apps.get(it.pkg)));
        requestLayout();
        applyFocus(false);
    }

    int count() { return items.size(); }
    WatchNext.Item at(int i) { return i >= 0 && i < items.size() ? items.get(i) : null; }

    void setFocused(int idx) { focused = idx; applyFocus(true); }

    private void applyFocus(boolean animate) {
        for (int i = 0; i < getChildCount(); i++) {
            Card v = (Card) getChildAt(i);
            boolean on = i == focused;
            v.setRing(on);
            v.setTranslationZ(on ? U.f(20) : 0f);
            float s = on ? 1.06f : 1f, ty = on ? U.f(-6) : 0f;
            if (animate) v.animate().scaleX(s).scaleY(s).translationY(ty).setDuration(200).start();
            else { v.setScaleX(s); v.setScaleY(s); v.setTranslationY(ty); }
        }
    }

    static int cardHeight(int cardW) {
        int still = (cardW - U.px(16)) * 9 / 16;
        return still + U.px(16) + U.px(50);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int cw = (int) ((w - gap) / COLS), ch = cardHeight(cw);
        int rows = Math.max(1, (getChildCount() + COLS - 1) / COLS);
        for (int i = 0; i < getChildCount(); i++)
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        setMeasuredDimension(w, (int) (rows * ch + (rows - 1) * gap));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int cw = (int) ((getWidth() - gap) / COLS), ch = cardHeight(cw);
        for (int i = 0; i < getChildCount(); i++) {
            int col = i % COLS, row = i / COLS;
            int x = (int) (col * (cw + gap)), y = (int) (row * (ch + gap));
            getChildAt(i).layout(x, y, x + cw, y + ch);
        }
    }

    /** One card, fully drawn. */
    static final class Card extends View {
        private final WatchNext.Item item;
        private final android.graphics.Bitmap badge;
        private final Glass glass = new Glass(16);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final RectF rect = new RectF();
        private boolean ring;

        Card(Context c, WatchNext.Item item, Apps.Entry app) {
            super(c);
            this.item = item;
            this.badge = app != null ? app.banner : null;
            setWillNotDraw(false);
        }

        void setRing(boolean r) { ring = r; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            glass.setBounds(0, 0, w, h);
            glass.draw(c);
            if (ring) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(U.f(2.5f)); p.setColor(0xF2FFFFFF); p.setShader(null);
                rect.set(U.f(1.25f), U.f(1.25f), w - U.f(1.25f), h - U.f(1.25f));
                c.drawRoundRect(rect, U.f(16), U.f(16), p);
            }
            float pad = U.f(8);
            float sw = w - 2 * pad, sh = sw * 9 / 16f;
            rect.set(pad, pad, pad + sw, pad + sh);
            clip.reset();
            clip.addRoundRect(rect, U.f(10), U.f(10), Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            p.setStyle(Paint.Style.FILL);
            if (item.poster != null) {
                float s = Math.max(rect.width() / item.poster.getWidth(), rect.height() / item.poster.getHeight());
                float bw = item.poster.getWidth() * s, bh = item.poster.getHeight() * s;
                float bx = rect.centerX() - bw / 2f, by = rect.centerY() - bh / 2f;
                p.setShader(null); p.setFilterBitmap(true);
                c.drawBitmap(item.poster, null, new RectF(bx, by, bx + bw, by + bh), p);
            } else {
                p.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF2A3A6A, 0xFF0B1220, Shader.TileMode.CLAMP));
                c.drawRect(rect, p);
                p.setShader(null);
                text.setTypeface(U.bold); text.setTextSize(U.f(40)); text.setColor(U.white(0.14f));
                c.drawText(item.title, rect.left + U.f(14), rect.bottom - U.f(14), text);
            }
            // progress bar
            if (item.progress > 0f) {
                float bx = rect.left + U.f(12), bw2 = rect.width() - U.f(24), by = rect.bottom - U.f(14), bh2 = U.f(4);
                p.setColor(U.white(0.25f));
                c.drawRoundRect(new RectF(bx, by, bx + bw2, by + bh2), bh2 / 2, bh2 / 2, p);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(new RectF(bx, by, bx + bw2 * item.progress, by + bh2), bh2 / 2, bh2 / 2, p);
            }
            c.restore();
            // app badge
            if (badge != null) {
                float bh = U.f(28), bw = bh * 16 / 9f;
                float bx = rect.left + U.f(10), by = rect.top + U.f(10);
                clip.reset();
                clip.addRoundRect(new RectF(bx, by, bx + bw, by + bh), U.f(5), U.f(5), Path.Direction.CW);
                c.save(); c.clipPath(clip);
                p.setFilterBitmap(true);
                c.drawBitmap(badge, null, new RectF(bx, by, bx + bw, by + bh), p);
                c.restore();
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(U.f(1)); p.setColor(U.white(0.3f));
                c.drawRoundRect(new RectF(bx, by, bx + bw, by + bh), U.f(5), U.f(5), p);
                p.setStyle(Paint.Style.FILL);
            }
            // title + subtitle
            text.setTypeface(U.medium); text.setTextSize(U.f(17)); text.setColor(0xFFFFFFFF);
            float tx = pad + U.f(6), ty = rect.bottom + U.f(8) + U.f(17);
            c.drawText(ellipsize(item.title, text, w - 2 * tx), tx, ty, text);
            text.setTypeface(U.regular); text.setTextSize(U.f(14)); text.setColor(U.white(0.65f));
            c.drawText(ellipsize(item.subtitle == null ? "" : item.subtitle, text, w - 2 * tx), tx, ty + U.f(19), text);
        }

        static String ellipsize(String s, Paint p, float maxW) {
            if (p.measureText(s) <= maxW) return s;
            String e = "…";
            int n = s.length();
            while (n > 0 && p.measureText(s.substring(0, n) + e) > maxW) n--;
            return s.substring(0, n) + e;
        }
    }
}

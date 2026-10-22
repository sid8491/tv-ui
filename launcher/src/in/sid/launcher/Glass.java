package in.sid.launcher;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The glass panel: translucent white gradient, hairline border, lighter top edge.
 * Android 10 has no real-time blur for app views, so this is the "frosted" look done with fills.
 * deep = darker, more opaque variant for menus; solid = the white "focused" state.
 */
final class Glass extends Drawable {
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint top = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private boolean deep, solid;
    private int lastH = -1;

    Glass(float radiusUnits) {
        radius = U.f(radiusUnits);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(U.f(1.5f));
        top.setStyle(Paint.Style.STROKE);
        top.setStrokeWidth(U.f(1.5f));
    }

    Glass deep(boolean d) { deep = d; invalidateSelf(); return this; }

    void setSolid(boolean s) {
        if (solid != s) { solid = s; lastH = -1; invalidateSelf(); }
    }

    boolean isSolid() { return solid; }

    @Override public void draw(Canvas c) {
        r.set(getBounds());
        float inset = stroke.getStrokeWidth() / 2f;
        r.inset(inset, inset);
        int h = getBounds().height();
        if (h != lastH) {
            lastH = h;
            if (solid) {
                fill.setShader(null);
                fill.setColor(0xFFFFFFFF);
            } else if (deep) {
                fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, U.argb(0.93f, 0x141A30), U.argb(0.96f, 0x0A0E1E), Shader.TileMode.CLAMP));
            } else {
                fill.setShader(new LinearGradient(0, r.top, 0, r.bottom, U.white(0.16f), U.white(0.07f), Shader.TileMode.CLAMP));
            }
        }
        c.drawRoundRect(r, radius, radius, fill);
        stroke.setColor(solid ? 0xFFFFFFFF : U.white(deep ? 0.18f : 0.22f));
        c.drawRoundRect(r, radius, radius, stroke);
        if (!solid) {
            // lighter top edge: a short arc-free line inside the top of the rounded rect
            top.setColor(U.white(deep ? 0.22f : 0.32f));
            float y = r.top + U.f(1.5f);
            c.drawLine(r.left + radius, y, r.right - radius, y, top);
        }
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}

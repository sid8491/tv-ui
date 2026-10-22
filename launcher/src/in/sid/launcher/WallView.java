package in.sid.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** "Aanya's wall": five glass-framed prints, loosely scattered, showing photos from a folder on the TV. */
final class WallView extends View {
    /** Frame geometry in design units: width, height, x, y, rotation degrees. */
    private static final float[][] FRAMES = {
        {264, 176, 0, 40, -5}, {208, 250, 280, 0, 4}, {280, 176, 509, 51, -3}, {204, 196, 104, 236, 3}, {264, 172, 345, 264, -2},
    };
    static final int W = 790, H = 452;

    private final List<Bitmap> photos = new ArrayList<>();
    private float focus; // 0..1 animated
    private final Glass glass = new Glass(14);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private ValueAnimator anim;

    WallView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(U.f(2f));
        ring.setColor(0xFFFFFFFF);
        setWillNotDraw(false);
    }

    void setPhotos(List<Bitmap> list) {
        photos.clear();
        if (list != null) photos.addAll(list);
        invalidate();
    }

    void setFocused(boolean f) {
        float target = f ? 1f : 0f;
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(focus, target);
        anim.setDuration(240);
        anim.addUpdateListener(a -> { focus = (float) a.getAnimatedValue(); invalidate(); });
        anim.start();
    }

    @Override protected void onDraw(Canvas c) {
        float pad = U.f(10);
        for (int i = 0; i < FRAMES.length; i++) {
            float[] f = FRAMES[i];
            float w = U.f(f[0]), h = U.f(f[1]), x = U.f(f[2]), y = U.f(f[3]);
            float cx = x + w / 2f, cy = y + h * 0.6f;
            c.save();
            c.translate(0, -U.f(10) * focus);
            c.scale(1f + 0.04f * focus, 1f + 0.04f * focus, cx, cy);
            c.rotate(f[4] * (1f + 0.6f * focus), cx, cy);
            // glass frame
            glass.setBounds((int) x, (int) y, (int) (x + w), (int) (y + h));
            glass.draw(c);
            if (focus > 0f) {
                ring.setAlpha((int) (230 * focus));
                rect.set(x, y, x + w, y + h);
                c.drawRoundRect(rect, U.f(14), U.f(14), ring);
            }
            // photo area
            rect.set(x + pad, y + pad, x + w - pad, y + h - pad);
            clip.reset();
            clip.addRoundRect(rect, U.f(8), U.f(8), Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            Bitmap bmp = i < photos.size() ? photos.get(i) : null;
            if (bmp != null) {
                // centre-crop
                float s = Math.max(rect.width() / bmp.getWidth(), rect.height() / bmp.getHeight());
                float bw = bmp.getWidth() * s, bh = bmp.getHeight() * s;
                float bx = rect.centerX() - bw / 2f, by = rect.centerY() - bh / 2f;
                paint.setShader(null);
                paint.setFilterBitmap(true);
                c.drawBitmap(bmp, null, new RectF(bx, by, bx + bw, by + bh), paint);
            } else {
                int hue = (i * 47 + 20) % 360;
                int c1 = android.graphics.Color.HSVToColor(140, new float[]{hue, 0.45f, 0.55f});
                int c2 = android.graphics.Color.HSVToColor(140, new float[]{(hue + 40) % 360, 0.5f, 0.3f});
                paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, c1, c2, Shader.TileMode.CLAMP));
                c.drawRect(rect, paint);
                paint.setShader(null);
            }
            c.restore();
            c.restore();
        }
    }
}

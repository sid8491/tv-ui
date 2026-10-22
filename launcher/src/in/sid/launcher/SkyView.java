package in.sid.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

/**
 * The wallpaper: a time-of-day sky with stars and dark hills, rendered once into a bitmap per mode.
 * Modes: dawn, day, golden, night, diwali (see Prefs.SKY_MODES; "auto" is resolved by the caller).
 */
final class SkyView extends View {
    private String mode = "night";
    private Bitmap cache;
    private String cachedMode;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    SkyView(Context c) { super(c); }

    void setMode(String m) {
        if (!m.equals(mode)) { mode = m; invalidate(); }
    }

    String mode() { return mode; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        cache = null;
    }

    @Override protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        if (cache == null || !mode.equals(cachedMode)) {
            if (cache == null) cache = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            render(new Canvas(cache), w, h);
            cachedMode = mode;
        }
        canvas.drawBitmap(cache, 0, 0, null);
    }

    private void render(Canvas c, int w, int h) {
        int[] colors; float[] stops; int glow; float glowA; float starA;
        switch (mode) {
            case "dawn":
                colors = new int[]{0xFF1A1235, 0xFF3A2A5C, 0xFF7A4F78, 0xFFD08A7A, 0xFFF0B48A};
                stops = new float[]{0f, 0.35f, 0.62f, 0.85f, 1f};
                glow = 0xFFAA6E; glowA = 0.75f; starA = 0.6f; break;
            case "day":
                colors = new int[]{0xFF1B4F8F, 0xFF2F73B8, 0xFF5C9FD6, 0xFFA9D0EA};
                stops = new float[]{0f, 0.4f, 0.7f, 1f};
                glow = 0xFFFFFF; glowA = 0.45f; starA = 0f; break;
            case "golden":
                colors = new int[]{0xFF27345E, 0xFF5E4A74, 0xFFB9637A, 0xFFEE9A5E, 0xFFFFC98A};
                stops = new float[]{0f, 0.35f, 0.6f, 0.85f, 1f};
                glow = 0xFF8C3C; glowA = 0.85f; starA = 0.25f; break;
            case "diwali":
                colors = new int[]{0xFF120A2A, 0xFF2A1250, 0xFF5A2460, 0xFF8A3A50};
                stops = new float[]{0f, 0.4f, 0.7f, 1f};
                glow = 0xFFBE46; glowA = 0.75f; starA = 0.7f; break;
            default: // night
                colors = new int[]{0xFF050A16, 0xFF0A1A2E, 0xFF123047, 0xFF2A4457, 0xFF4A4B4B};
                stops = new float[]{0f, 0.35f, 0.62f, 0.8f, 1f};
                glow = 0xD69660; glowA = 0.55f; starA = 1f; break;
        }
        p.setShader(new LinearGradient(0, 0, 0, h, colors, stops, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        // horizon glow
        p.setShader(new RadialGradient(w / 2f, h * 1.08f, w * 0.6f, U.argb(glowA, glow), U.argb(0f, glow), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);
        // stars (fixed seed so the sky looks the same every day)
        if (starA > 0f) {
            Random rnd = new Random(7);
            for (int i = 0; i < 170; i++) {
                float x = rnd.nextFloat() * w, y = rnd.nextFloat() * h * 0.62f;
                float o = 0.25f + rnd.nextFloat() * 0.75f;
                float k = (rnd.nextFloat() * 1.6f + 0.6f) * U.f(1.1f);
                p.setColor(U.white(o * starA));
                c.drawCircle(x, y, k, p);
            }
        }
        // hills
        float hillTop = h * 0.62f;
        p.setShader(new RadialGradient(w * 0.3f, h, w * 0.6f, 0xFF2B2219, U.argb(0f, 0x2B2219), Shader.TileMode.CLAMP));
        c.drawRect(0, hillTop, w, h, p);
        p.setShader(new RadialGradient(w * 0.72f, h, w * 0.7f, 0xFF1F1A14, U.argb(0f, 0x1F1A14), Shader.TileMode.CLAMP));
        c.drawRect(0, hillTop, w, h, p);
        p.setShader(new LinearGradient(0, hillTop, 0, h, U.argb(0f, 0x14100C), 0xFF15110D, Shader.TileMode.CLAMP));
        c.drawRect(0, hillTop, w, h, p);
        p.setShader(null);
    }
}

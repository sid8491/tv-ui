package in.sid.launcher;

import android.content.Context;
import android.graphics.Typeface;
import android.util.DisplayMetrics;

/** Design units: the mock-up was drawn on a 1920-wide canvas; 1 unit = screenWidth / 1920 px. */
final class U {
    private U() {}

    static float u = 1f;
    static int screenW = 1920, screenH = 1080;
    static Typeface regular, medium, bold;

    static void init(Context c) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        screenW = m.widthPixels;
        screenH = m.heightPixels;
        u = screenW / 1920f;
        regular = Typeface.create("sans-serif", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        bold = Typeface.create("sans-serif", Typeface.BOLD);
    }

    static int px(float units) {
        return Math.round(units * u);
    }

    static float f(float units) {
        return units * u;
    }

    static int white(float alpha) {
        return (Math.round(alpha * 255) << 24) | 0xFFFFFF;
    }

    static int argb(float alpha, int rgb) {
        return (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
    }
}

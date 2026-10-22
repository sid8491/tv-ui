package in.sid.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Installed TV apps: label, Leanback banner (or an icon on a dark field when the app has none), launch intent. */
final class Apps {
    static final class Entry {
        final String pkg;
        final String label;
        final Bitmap banner;   // always 320x180, rasterised once so every view draws it the same way
        final Intent intent;

        Entry(String pkg, String label, Bitmap banner, Intent intent) {
            this.pkg = pkg; this.label = label; this.banner = banner; this.intent = intent;
        }

        /** A fresh drawable per view (drawables must not be shared between views). */
        Drawable drawable(Context c) { return new BitmapDrawable(c.getResources(), banner); }
    }

    private final Map<String, Entry> byPkg = new LinkedHashMap<>();

    Apps(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(q, 0);
        String category = Intent.CATEGORY_LEANBACK_LAUNCHER;
        if (list.size() < 4) { // not a TV (emulator testing): fall back to ordinary launcher apps
            category = Intent.CATEGORY_LAUNCHER;
            list = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        }
        android.util.Log.d("home", "apps: " + list.size() + " via " + category);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || byPkg.containsKey(pkg)) continue;
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(category)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Bitmap bmp = banner != null && banner.getIntrinsicWidth() > 0 ? rasterise(banner) : iconBanner(ri.loadIcon(pm));
            byPkg.put(pkg, new Entry(pkg, String.valueOf(ri.loadLabel(pm)), bmp, launch));
        }
    }

    Entry get(String pkg) { return byPkg.get(pkg); }

    /** All apps, alphabetical by label. */
    List<Entry> all() {
        List<Entry> out = new ArrayList<>(byPkg.values());
        Collections.sort(out, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /** Apps that are installed, in the order given; unknown packages are skipped. */
    List<Entry> pick(List<String> pkgs) {
        List<Entry> out = new ArrayList<>();
        for (String p : pkgs) { Entry e = byPkg.get(p); if (e != null) out.add(e); }
        return out;
    }

    private static final int BW = 320, BH = 180;

    /** The app's banner drawn into a small 16:9 bitmap (centre-cropped). */
    private static Bitmap rasterise(Drawable d) {
        Bitmap bmp = Bitmap.createBitmap(BW, BH, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.BLACK);
        float iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
        float s = Math.max(BW / iw, BH / ih);
        int w = Math.round(iw * s), h = Math.round(ih * s);
        int left = (BW - w) / 2, top = (BH - h) / 2;
        d.setBounds(left, top, left + w, top + h);
        d.draw(cv);
        return bmp;
    }

    /** 16:9 banner made from a launcher icon, for apps that ship none. */
    private static Bitmap iconBanner(Drawable icon) {
        Bitmap bmp = Bitmap.createBitmap(BW, BH, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.rgb(0x1C, 0x1C, 0x1E));
        if (icon != null) {
            int s = (int) (BH * 0.62f);
            int left = (BW - s) / 2, top = (BH - s) / 2;
            icon.setBounds(left, top, left + s, top + s);
            icon.draw(cv);
        }
        return bmp;
    }
}

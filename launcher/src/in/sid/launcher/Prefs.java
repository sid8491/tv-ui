package in.sid.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Launcher settings, kept in SharedPreferences "home". */
final class Prefs {
    private final SharedPreferences sp;

    static final String[] DEFAULT_DOCK = {
        "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "com.apple.atve.androidtv.appletv", "in.startv.hotstar",
        "com.google.android.youtube.tv", "com.sonyliv",
    };

    /** Apps whose Watch Next rows are ignored (YouTube publishes recommendations there, not things being watched). */
    static final String[] CW_EXCLUDE = {"com.google.android.youtube.tv", "com.google.android.youtube.tvmusic"};
    static final String[] SKY_MODES = {"auto", "dawn", "day", "golden", "night", "diwali"};

    Prefs(Context c) { sp = c.getSharedPreferences("home", Context.MODE_PRIVATE); }

    String name() { return sp.getString("name", "Aanya"); }
    void setName(String n) { sp.edit().putString("name", n).apply(); }

    String sky() { return sp.getString("sky", "auto"); }
    void setSky(String s) { sp.edit().putString("sky", s).apply(); }

    int shuffleDays() { return sp.getInt("shuffle", 1); }
    void setShuffleDays(int d) { sp.edit().putInt("shuffle", d).apply(); }

    boolean continueWatching() { return sp.getBoolean("cw", true); }
    void setContinueWatching(boolean b) { sp.edit().putBoolean("cw", b).apply(); }

    List<String> dock() {
        String s = sp.getString("dock", null);
        List<String> out = new ArrayList<>();
        if (s == null) out.addAll(Arrays.asList(DEFAULT_DOCK));
        else for (String p : s.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    void setDock(List<String> pkgs) {
        StringBuilder sb = new StringBuilder();
        for (String p : pkgs) { if (sb.length() > 0) sb.append(','); sb.append(p); }
        sp.edit().putString("dock", sb.toString()).apply();
    }

    /** Photo folder on the TV's shared storage (relative to /sdcard). */
    String photoFolder() { return sp.getString("photos", "Pictures/Aanya"); }

    /** "photo" = Aanya's photos with the app badge (needs photos in <folder>/icons), "original" = the apps' banners. */
    String dockStyle() { return sp.getString("dockstyle", "photo"); }
    void setDockStyle(String s) { sp.edit().putString("dockstyle", s).apply(); }

    /** Face crop found for a photo, as fractions {x, y, w, h}; null when not computed yet. */
    float[] faceCrop(String key) {
        String s = sp.getString("face3:" + key, null);
        if (s == null) return null;
        try {
            String[] p = s.split(",");
            return new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])};
        } catch (Exception e) { return null; }
    }
    void setFaceCrop(String key, float[] c) { sp.edit().putString("face3:" + key, c[0] + "," + c[1] + "," + c[2] + "," + c[3]).apply(); }
}

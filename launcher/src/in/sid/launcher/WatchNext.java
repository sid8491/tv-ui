package in.sid.launcher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * "Continue watching": rows other apps publish to Android TV's Watch Next channel
 * (content://android.media.tv/watch_next_program; needs READ_TV_LISTINGS). Posters are fetched here too.
 */
final class WatchNext {
    static final class Item {
        String pkg, title, subtitle, intentUri, posterUri;
        float progress;
        long engaged;
        Bitmap poster;
    }

    interface Callback { void onLoaded(List<Item> items); }

    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");

    static void load(Context c, int max, Callback cb) {
        final Context app = c.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            List<Item> items = query(app);
            List<Item> top = pickVaried(items, max);
            for (Item it : top) it.poster = fetch(app, it.posterUri, 420);
            main.post(() -> cb.onLoaded(top));
        }, "watchnext").start();
    }

    /** Most recent rows, spread across apps: one per app first, then a second per app if slots remain. */
    static List<Item> pickVaried(List<Item> sorted, int max) {
        List<String> exclude = java.util.Arrays.asList(Prefs.CW_EXCLUDE);
        List<Item> out = new ArrayList<>();
        for (int perApp = 1; perApp <= 2 && out.size() < max; perApp++) {
            java.util.Map<String, Integer> used = new java.util.HashMap<>();
            for (Item it : out) used.put(it.pkg, used.containsKey(it.pkg) ? used.get(it.pkg) + 1 : 1);
            for (Item it : sorted) {
                if (out.size() >= max) break;
                if (it.pkg == null || exclude.contains(it.pkg) || out.contains(it)) continue;
                int n = used.containsKey(it.pkg) ? used.get(it.pkg) : 0;
                if (n >= perApp) continue;
                out.add(it);
                used.put(it.pkg, n + 1);
            }
        }
        return out;
    }

    private static List<Item> query(Context c) {
        List<Item> out = new ArrayList<>();
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(URI, null, null, null, null);
            if (cur == null) return out;
            int iPkg = cur.getColumnIndex("package_name"), iTitle = cur.getColumnIndex("title"),
                iEp = cur.getColumnIndex("episode_title"), iSeason = cur.getColumnIndex("season_display_number"),
                iEpNo = cur.getColumnIndex("episode_display_number"), iPos = cur.getColumnIndex("last_playback_position_millis"),
                iDur = cur.getColumnIndex("duration_millis"), iPoster = cur.getColumnIndex("poster_art_uri"),
                iThumb = cur.getColumnIndex("thumbnail_uri"), iIntent = cur.getColumnIndex("intent_uri"),
                iEng = cur.getColumnIndex("last_engagement_time_utc_millis"), iBrowsable = cur.getColumnIndex("browsable"),
                iType = cur.getColumnIndex("watch_next_type");
            while (cur.moveToNext()) {
                if (iBrowsable >= 0 && !cur.isNull(iBrowsable) && cur.getInt(iBrowsable) == 0) continue;
                Item it = new Item();
                it.pkg = str(cur, iPkg);
                it.title = str(cur, iTitle);
                if (it.title == null || it.title.isEmpty()) continue;
                String season = str(cur, iSeason), ep = str(cur, iEpNo), epTitle = str(cur, iEp);
                StringBuilder sb = new StringBuilder();
                if (season != null && !season.isEmpty()) sb.append('S').append(season);
                if (ep != null && !ep.isEmpty()) { if (sb.length() > 0) sb.append(' '); sb.append('E').append(ep); }
                if (epTitle != null && !epTitle.isEmpty()) { if (sb.length() > 0) sb.append(" · "); sb.append(epTitle); }
                long pos = iPos >= 0 && !cur.isNull(iPos) ? cur.getLong(iPos) : 0, dur = iDur >= 0 && !cur.isNull(iDur) ? cur.getLong(iDur) : 0;
                if (dur > 0) {
                    it.progress = Math.max(0f, Math.min(1f, pos / (float) dur));
                    long left = Math.max(0, (dur - pos) / 60000);
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append(left).append(" min left");
                } else if (iType >= 0 && !cur.isNull(iType) && cur.getInt(iType) == 1) { // WATCH_NEXT_TYPE_NEXT
                    if (sb.length() > 0) sb.append(" · ");
                    sb.append("Up next");
                }
                it.subtitle = sb.toString();
                it.posterUri = str(cur, iThumb);
                if (it.posterUri == null || it.posterUri.isEmpty()) it.posterUri = str(cur, iPoster);
                it.intentUri = str(cur, iIntent);
                it.engaged = iEng >= 0 && !cur.isNull(iEng) ? cur.getLong(iEng) : 0;
                out.add(it);
            }
        } catch (Exception ignored) {
            // no permission or no provider: the home screen shows the info card instead
        } finally {
            if (cur != null) cur.close();
        }
        java.util.Collections.sort(out, (a, b) -> Long.compare(b.engaged, a.engaged));
        return out;
    }

    private static String str(Cursor c, int i) {
        return i >= 0 && !c.isNull(i) ? c.getString(i) : null;
    }

    /** Downsampled bitmap from http(s) or content:// URI; null on any failure. */
    static Bitmap fetch(Context c, String uri, int targetW) {
        if (uri == null || uri.isEmpty()) return null;
        try {
            byte[] data;
            if (uri.startsWith("http")) {
                HttpURLConnection conn = (HttpURLConnection) new URL(uri).openConnection();
                conn.setConnectTimeout(6000); conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                try (InputStream in = conn.getInputStream()) { data = readAll(in); } finally { conn.disconnect(); }
            } else {
                ContentResolver cr = c.getContentResolver();
                try (InputStream in = cr.openInputStream(Uri.parse(uri))) { data = readAll(in); }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}

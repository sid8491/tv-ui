package in.sid.launcher;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.FaceDetector;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * "Photo" dock tiles: a photo of Aanya fills the tile and the app's own banner sits in a small badge at the
 * bottom-right. Photos come from the icons/ subfolder of the wall folder; each dock app gets its own photo
 * (they only repeat when there are more apps than photos). The crop is centred on the largest face the
 * built-in detector finds, and that crop is remembered in prefs so detection runs once per photo.
 */
final class PhotoTiles {
    static final int W = Apps.BW, H = Apps.BH;
    static final String SUBFOLDER = "icons";
    private static final int DECODE_W = 480;

    private PhotoTiles() { }

    /** The icon photos in this cycle's order: sorted, then shuffled with the same daily seed as the wall. */
    static List<File> files(File wallDir, int shuffleDays) {
        File dir = new File(wallDir, SUBFOLDER);
        File[] files = dir.listFiles((d, n) -> {
            String l = n.toLowerCase(Locale.ROOT);
            return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp");
        });
        if (files == null || files.length == 0) return Collections.emptyList();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        Collections.sort(list);
        long day = System.currentTimeMillis() / 86400000L / Math.max(1, shuffleDays);
        Collections.shuffle(list, new Random(day * 31 + 7));
        return list;
    }

    /**
     * One tile per package. Photos are handed out in alphabetical package order so reordering the dock
     * never changes which photo an app has. Runs on a background thread.
     */
    static Map<String, Bitmap> build(Prefs prefs, Apps apps, List<String> pkgs, List<File> photos) {
        Map<String, Bitmap> out = new HashMap<>();
        if (photos.isEmpty()) return out;
        Map<String, float[]> manual = readCrops(new File(photos.get(0).getParentFile(), "crops.txt"));
        List<String> order = new ArrayList<>(pkgs);
        Collections.sort(order);
        Map<File, Bitmap> cropped = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            Apps.Entry e = apps.get(order.get(i));
            if (e == null) continue;
            File f = photos.get(i % photos.size());
            Bitmap photo = cropped.get(f);
            if (photo == null) {
                photo = croppedPhoto(prefs, f, manual.get(f.getName()));
                if (photo == null) continue;
                cropped.put(f, photo);
            }
            out.put(e.pkg, compose(photo, e.banner));
        }
        return out;
    }

    /**
     * Optional hand-picked crops next to the photos: one "name.jpg x y w h" per line, fractions of the photo,
     * for photos where the detector misses (she is small in the frame) or picks the wrong thing.
     */
    static Map<String, float[]> readCrops(File f) {
        Map<String, float[]> out = new HashMap<>();
        if (!f.isFile()) return out;
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.trim().split(" +");
                if (p.length < 5 || p[0].startsWith("#")) continue;
                out.put(p[0], new float[]{Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4])});
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** The photo decoded small and cropped to 16:9 around the face (or the hand-picked crop when there is one). */
    private static Bitmap croppedPhoto(Prefs prefs, File f, float[] manual) {
        Bitmap b = HomeView.decode(f, DECODE_W);
        if (b == null) return null;
        if ((b.getWidth() & 1) == 1) b = Bitmap.createBitmap(b, 0, 0, b.getWidth() - 1, b.getHeight()); // the detector needs an even width
        String key = f.getName() + ":" + f.length();
        float[] crop = manual != null ? manual : prefs.faceCrop(key);
        if (crop == null) {
            crop = findCrop(b, 0f, f.getName());
            if (crop == null) { // no face at this size: she may be small in the frame, look again at full size
                Bitmap big = HomeView.decode(f, DECODE_W * 2);
                if (big != null) {
                    if ((big.getWidth() & 1) == 1) big = Bitmap.createBitmap(big, 0, 0, big.getWidth() - 1, big.getHeight());
                    crop = findCrop(big, 0.55f, f.getName() + "@2x");
                    big.recycle();
                }
            }
            if (crop == null) crop = defaultCrop(b.getWidth(), b.getHeight());
            prefs.setFaceCrop(key, crop);
        }
        int w = b.getWidth(), h = b.getHeight();
        Rect src = new Rect(Math.round(crop[0] * w), Math.round(crop[1] * h), Math.round((crop[0] + crop[2]) * w), Math.round((crop[1] + crop[3]) * h));
        src.intersect(0, 0, w, h);
        Bitmap tile = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        new Canvas(tile).drawBitmap(b, src, new Rect(0, 0, W, H), p);
        return tile;
    }

    /** No face found: the middle of the photo, a bit tighter than full width (a small child in a big scene is usually mid-frame). */
    static float[] defaultCrop(int w, int h) {
        return fit(w, h, w / 2f, h * 0.5f, w * 0.62f);
    }

    /** A 16:9 crop as fractions {x, y, w, h} around the largest face, or null when none is found. */
    static float[] findCrop(Bitmap b, float minConfidence, String tag) {
        int w = b.getWidth(), h = b.getHeight();
        float cx, cy, cw;
        try {
            FaceDetector.Face[] faces = new FaceDetector.Face[4];
            int n = new FaceDetector(w, h, faces.length).findFaces(b, faces);
            FaceDetector.Face best = null;
            for (int i = 0; i < n; i++) {
                android.util.Log.d("home", "face " + tag + " " + w + "x" + h + " conf=" + faces[i].confidence() + " eyes=" + faces[i].eyesDistance());
                if (faces[i].confidence() < Math.max(minConfidence, FaceDetector.Face.CONFIDENCE_THRESHOLD)) continue;
                if (faces[i].eyesDistance() < 8f) continue; // too small to be a real face at this size
                if (best == null || faces[i].eyesDistance() > best.eyesDistance()) best = faces[i];
            }
            if (best == null) return null;
            PointF mid = new PointF();
            best.getMidPoint(mid);
            cx = mid.x; cy = mid.y;
            cw = Math.max(w * 0.45f, Math.min(w, best.eyesDistance() * 9f));
        } catch (Exception e) { return null; }
        return fit(w, h, cx, cy, cw);
    }

    private static float[] fit(int w, int h, float cx, float cy, float cw) {
        float ch = cw * 9f / 16f;
        if (ch > h) { ch = h; cw = ch * 16f / 9f; }
        if (cw > w) { cw = w; ch = cw * 9f / 16f; }
        float x = Math.max(0, Math.min(w - cw, cx - cw / 2f));
        float y = Math.max(0, Math.min(h - ch, cy - ch * 0.42f)); // face a little above centre
        return new float[]{x / w, y / h, cw / w, ch / h};
    }

    /** Photo, a soft dark shade over its lower part, then the banner in a rounded badge with a white hairline. */
    private static Bitmap compose(Bitmap photo, Bitmap banner) {
        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas cv = new Canvas(out);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        cv.drawBitmap(photo, 0, 0, p);
        Paint shade = new Paint();
        shade.setShader(new LinearGradient(0, H * 0.4f, 0, H, 0x00000000, 0x96000000, Shader.TileMode.CLAMP));
        cv.drawRect(0, H * 0.4f, W, H, shade);
        float bw = W * 0.42f, bh = bw * 9f / 16f, margin = 11f, r = 18f;
        RectF badge = new RectF(W - bw - margin, H - bh - margin, W - margin, H - margin);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        ring.setColor(0xE6FFFFFF);
        RectF rr = new RectF(badge);
        rr.inset(-2f, -2f);
        cv.drawRoundRect(rr, r + 2f, r + 2f, ring);
        Path clip = new Path();
        clip.addRoundRect(badge, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        cv.drawBitmap(banner, null, badge, p);
        cv.restore();
        return out;
    }
}

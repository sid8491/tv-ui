package in.sid.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small line icons drawn with Paint, so the app ships no icon assets. kinds: image, input, grid, gear, tv, home, play, calendar, heart. */
final class IconView extends View {
    private String kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int color = 0xE6FFFFFF;

    IconView(Context c, String kind) {
        super(c);
        this.kind = kind;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    void setColor(int c) { color = c; invalidate(); }
    void setKind(String k) { kind = k; invalidate(); }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
        c.translate(ox, oy);
        float k = s / 24f; // the icons are drawn on a 24-unit grid
        p.setColor(color);
        p.setStrokeWidth(1.9f * k);
        p.setStyle(Paint.Style.STROKE);
        switch (kind) {
            case "image":
                c.drawRoundRect(new RectF(3 * k, 4 * k, 21 * k, 20 * k), 2.5f * k, 2.5f * k, p);
                c.drawCircle(8.5f * k, 9.5f * k, 1.6f * k, p);
                c.drawLine(21 * k, 15.5f * k, 16 * k, 10.5f * k, p);
                c.drawLine(16 * k, 10.5f * k, 8 * k, 19 * k, p);
                break;
            case "input":
                c.drawRoundRect(new RectF(3 * k, 6 * k, 21 * k, 18 * k), 2.5f * k, 2.5f * k, p);
                c.drawLine(7 * k, 10.5f * k, 9 * k, 10.5f * k, p);
                c.drawLine(11 * k, 10.5f * k, 13 * k, 10.5f * k, p);
                c.drawLine(15 * k, 10.5f * k, 17 * k, 10.5f * k, p);
                c.drawLine(8 * k, 14 * k, 16 * k, 14 * k, p);
                break;
            case "grid":
                c.drawRoundRect(new RectF(3.5f * k, 3.5f * k, 10.5f * k, 10.5f * k), 1.8f * k, 1.8f * k, p);
                c.drawRoundRect(new RectF(13.5f * k, 3.5f * k, 20.5f * k, 10.5f * k), 1.8f * k, 1.8f * k, p);
                c.drawRoundRect(new RectF(3.5f * k, 13.5f * k, 10.5f * k, 20.5f * k), 1.8f * k, 1.8f * k, p);
                c.drawRoundRect(new RectF(13.5f * k, 13.5f * k, 20.5f * k, 20.5f * k), 1.8f * k, 1.8f * k, p);
                break;
            case "gear":
                c.drawCircle(12 * k, 12 * k, 3.2f * k, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (12 * k + Math.cos(a) * 6.2f * k), (float) (12 * k + Math.sin(a) * 6.2f * k),
                        (float) (12 * k + Math.cos(a) * 9.2f * k), (float) (12 * k + Math.sin(a) * 9.2f * k), p);
                }
                c.drawCircle(12 * k, 12 * k, 6.2f * k, p);
                break;
            case "tv":
                c.drawRoundRect(new RectF(2.5f * k, 4.5f * k, 21.5f * k, 17.5f * k), 2.5f * k, 2.5f * k, p);
                c.drawLine(8 * k, 21 * k, 16 * k, 21 * k, p);
                break;
            case "home": {
                Path path = new Path();
                path.moveTo(3 * k, 11.5f * k); path.lineTo(12 * k, 4 * k); path.lineTo(21 * k, 11.5f * k);
                c.drawPath(path, p);
                path.reset();
                path.moveTo(5.5f * k, 10 * k); path.lineTo(5.5f * k, 20 * k); path.lineTo(18.5f * k, 20 * k); path.lineTo(18.5f * k, 10 * k);
                c.drawPath(path, p);
                break;
            }
            case "play": {
                p.setStyle(Paint.Style.FILL);
                Path path = new Path();
                path.moveTo(8 * k, 5.5f * k); path.lineTo(8 * k, 18.5f * k); path.lineTo(19 * k, 12 * k); path.close();
                c.drawPath(path, p);
                break;
            }
            case "calendar":
                c.drawRoundRect(new RectF(3 * k, 5 * k, 21 * k, 21 * k), 2.5f * k, 2.5f * k, p);
                c.drawLine(3 * k, 10 * k, 21 * k, 10 * k, p);
                c.drawLine(8 * k, 3 * k, 8 * k, 7 * k, p);
                c.drawLine(16 * k, 3 * k, 16 * k, 7 * k, p);
                break;
            case "heart": {
                p.setStyle(Paint.Style.FILL);
                Path path = new Path();
                path.moveTo(12 * k, 21 * k);
                path.cubicTo(4.5f * k, 16.4f * k, 1.2f * k, 8.4f * k, 6.6f * k, 5 * k);
                path.cubicTo(8.5f * k, 5 * k, 10 * k, 6 * k, 12 * k, 7.4f * k);
                path.cubicTo(14 * k, 6 * k, 15.5f * k, 5 * k, 17.4f * k, 5 * k);
                path.cubicTo(22.8f * k, 8.4f * k, 19.5f * k, 16.4f * k, 12 * k, 21 * k);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case "chevron":
                c.drawLine(9 * k, 6 * k, 15 * k, 12 * k, p);
                c.drawLine(15 * k, 12 * k, 9 * k, 18 * k, p);
                break;
            case "sun":
                c.drawCircle(12 * k, 12 * k, 4 * k, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    c.drawLine((float) (12 * k + Math.cos(a) * 7 * k), (float) (12 * k + Math.sin(a) * 7 * k),
                        (float) (12 * k + Math.cos(a) * 9.5f * k), (float) (12 * k + Math.sin(a) * 9.5f * k), p);
                }
                break;
            case "camera":
                c.drawRoundRect(new RectF(2.5f * k, 8.5f * k, 21.5f * k, 19.5f * k), 1.5f * k, 1.5f * k, p);
                c.drawLine(7 * k, 8.5f * k, 8.5f * k, 6 * k, p);
                c.drawLine(8.5f * k, 6 * k, 15.5f * k, 6 * k, p);
                c.drawLine(15.5f * k, 6 * k, 17 * k, 8.5f * k, p);
                c.drawCircle(12 * k, 13.5f * k, 3.2f * k, p);
                break;
        }
    }
}

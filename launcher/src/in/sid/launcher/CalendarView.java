package in.sid.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** The calendar page: big clock, date, sunrise/sunset, wallpaper, week number, coming up, and the month grid. */
final class CalendarView extends View {
    private final Glass glass = new Glass(34).deep(true);
    private final Glass tile = new Glass(16);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SimpleDateFormat time = new SimpleDateFormat("h:mm", Locale.ENGLISH);
    private final SimpleDateFormat longDate = new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private final SimpleDateFormat monthTitle = new SimpleDateFormat("MMMM yyyy", Locale.ENGLISH);
    private final SimpleDateFormat shortDate = new SimpleDateFormat("d MMM", Locale.ENGLISH);
    String skyLabel = "";
    String sunrise = "6:24", sunset = "5:52";

    CalendarView(Context c) { super(c); setWillNotDraw(false); }

    static int widthPx() { return U.px(1440); }
    static int heightPx() { return U.px(640); }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        glass.setBounds(0, 0, w, h);
        glass.draw(c);
        Calendar now = Calendar.getInstance();
        float pad = U.f(40);
        float lx = pad, ly = pad;
        // left column
        t.setTypeface(U.medium); t.setTextSize(U.f(96)); t.setColor(0xFFFFFFFF); t.setTextAlign(Paint.Align.LEFT);
        c.drawText(time.format(now.getTime()), lx, ly + U.f(84), t);
        t.setTextSize(U.f(26));
        c.drawText(longDate.format(now.getTime()), lx, ly + U.f(128), t);
        int week = now.get(Calendar.WEEK_OF_YEAR);
        String[][] tiles = {{"Sunrise", sunrise}, {"Sunset", sunset}, {"Wallpaper now", skyLabel}, {"Week", week + " of 52"}};
        float tw = (U.f(520) - U.f(14)) / 2f, th = U.f(70);
        for (int i = 0; i < 4; i++) {
            float tx = lx + (i % 2) * (tw + U.f(14)), ty = ly + U.f(156) + (i / 2) * (th + U.f(14));
            tile.setBounds((int) tx, (int) ty, (int) (tx + tw), (int) (ty + th));
            tile.draw(c);
            t.setTypeface(U.regular); t.setTextSize(U.f(13)); t.setColor(U.white(0.6f));
            c.drawText(tiles[i][0], tx + U.f(16), ty + U.f(26), t);
            t.setTypeface(U.medium); t.setTextSize(U.f(22)); t.setColor(0xFFFFFFFF);
            c.drawText(tiles[i][1], tx + U.f(16), ty + U.f(54), t);
        }
        float uy = ly + U.f(156) + 2 * (th + U.f(14)) + U.f(20);
        t.setTypeface(U.medium); t.setTextSize(U.f(13)); t.setColor(U.white(0.6f));
        c.drawText("COMING UP", lx, uy, t);
        List<Object[]> up = Special.upcoming(now, 4);
        float ry = uy + U.f(30);
        for (Object[] o : up) {
            Calendar when = (Calendar) o[0];
            Special.Day d = (Special.Day) o[1];
            p.setColor(0xFFFFB547);
            c.drawCircle(lx + U.f(4), ry - U.f(6), U.f(4), p);
            t.setTypeface(U.regular); t.setTextSize(U.f(18)); t.setColor(0xFFFFFFFF); t.setTextAlign(Paint.Align.LEFT);
            c.drawText(d.label, lx + U.f(18), ry, t);
            t.setColor(U.white(0.6f)); t.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Special.sameDay(when, now) ? "Today" : shortDate.format(when.getTime()), lx + U.f(520), ry, t);
            ry += U.f(32);
        }
        // month grid
        float gx = lx + U.f(560), gy = ly;
        t.setTypeface(U.medium); t.setTextSize(U.f(28)); t.setColor(0xFFFFFFFF); t.setTextAlign(Paint.Align.LEFT);
        c.drawText(monthTitle.format(now.getTime()), gx, gy + U.f(28), t);
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        float cellW = (w - gx - pad - 6 * U.f(8)) / 7f, cellH = U.f(60), hy = gy + U.f(60);
        t.setTypeface(U.regular); t.setTextSize(U.f(14)); t.setColor(U.white(0.6f)); t.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < 7; i++) c.drawText(names[i], gx + i * (cellW + U.f(8)) + cellW / 2, hy, t);
        Calendar first = (Calendar) now.clone();
        first.set(Calendar.DAY_OF_MONTH, 1);
        int lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
        float rowY = hy + U.f(14);
        for (int d = 1; d <= days; d++) {
            int cell = lead + d - 1, col = cell % 7, row = cell / 7;
            float cx = gx + col * (cellW + U.f(8)), cy = rowY + row * (cellH + U.f(8));
            boolean today = d == now.get(Calendar.DAY_OF_MONTH);
            RectF r = new RectF(cx, cy, cx + cellW, cy + cellH);
            if (today) { p.setStyle(Paint.Style.FILL); p.setColor(0xFFFFFFFF); c.drawRoundRect(r, U.f(12), U.f(12), p); }
            else { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(U.f(1)); p.setColor(U.white(0.08f)); c.drawRoundRect(r, U.f(12), U.f(12), p); p.setStyle(Paint.Style.FILL); }
            t.setTypeface(today ? U.bold : U.regular); t.setTextSize(U.f(20)); t.setColor(today ? 0xFF0B1220 : 0xFFFFFFFF);
            c.drawText(String.valueOf(d), r.centerX(), r.centerY() + U.f(7), t);
            Calendar dc = (Calendar) first.clone();
            dc.set(Calendar.DAY_OF_MONTH, d);
            if (Special.today(dc) != null) {
                p.setColor(today ? 0xFFD97706 : 0xFFFFB547);
                c.drawCircle(r.centerX(), r.bottom - U.f(8), U.f(3), p);
            }
        }
        t.setTypeface(U.regular); t.setTextSize(U.f(13)); t.setColor(U.white(0.6f)); t.setTextAlign(Paint.Align.LEFT);
        c.drawText("Dots mark festivals and family dates · Back closes", gx, h - pad + U.f(4), t);
    }
}

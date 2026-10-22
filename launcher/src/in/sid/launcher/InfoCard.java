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

/** Shown on the right when Continue watching is off or empty: clock, date, this week, next special day. OK opens the calendar. */
final class InfoCard extends View {
    private final Glass glass = new Glass(26);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean focused;
    private final SimpleDateFormat time = new SimpleDateFormat("h:mm", Locale.ENGLISH);
    private final SimpleDateFormat date = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);
    private final SimpleDateFormat dow = new SimpleDateFormat("EEE", Locale.ENGLISH);
    private final SimpleDateFormat shortDate = new SimpleDateFormat("EEE, d MMM", Locale.ENGLISH);

    InfoCard(Context c) { super(c); setWillNotDraw(false); }

    void setFocused(boolean f) {
        focused = f;
        animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f).translationY(f ? U.f(-6) : 0).setDuration(200).start();
        invalidate();
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        glass.setBounds(0, 0, w, h);
        glass.draw(c);
        if (focused) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(U.f(2.5f)); p.setColor(0xF2FFFFFF);
            c.drawRoundRect(new RectF(U.f(1.25f), U.f(1.25f), w - U.f(1.25f), h - U.f(1.25f)), U.f(26), U.f(26), p);
        }
        p.setStyle(Paint.Style.FILL);
        Calendar now = Calendar.getInstance();
        float x = U.f(30), y = U.f(28);
        t.setTypeface(U.medium); t.setTextSize(U.f(72)); t.setColor(0xFFFFFFFF);
        c.drawText(time.format(now.getTime()), x, y + U.f(64), t);
        t.setTextSize(U.f(20));
        c.drawText(date.format(now.getTime()), x, y + U.f(100), t);
        // week strip
        Calendar d = (Calendar) now.clone();
        int dowIdx = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        d.add(Calendar.DAY_OF_YEAR, -dowIdx);
        float cellW = (w - 2 * x - 6 * U.f(8)) / 7f, cellH = U.f(56), cy = y + U.f(122);
        for (int i = 0; i < 7; i++) {
            float cx = x + i * (cellW + U.f(8));
            boolean today = i == dowIdx;
            RectF r = new RectF(cx, cy, cx + cellW, cy + cellH);
            p.setColor(today ? 0xFFFFFFFF : U.white(0.08f));
            c.drawRoundRect(r, U.f(12), U.f(12), p);
            t.setTypeface(U.regular); t.setTextSize(U.f(12)); t.setColor(today ? 0xFF0B1220 : U.white(0.6f));
            t.setTextAlign(Paint.Align.CENTER);
            c.drawText(dow.format(d.getTime()), r.centerX(), cy + U.f(18), t);
            t.setTypeface(U.medium); t.setTextSize(U.f(18)); t.setColor(today ? 0xFF0B1220 : 0xFFFFFFFF);
            c.drawText(String.valueOf(d.get(Calendar.DAY_OF_MONTH)), r.centerX(), cy + U.f(42), t);
            t.setTextAlign(Paint.Align.LEFT);
            d.add(Calendar.DAY_OF_YEAR, 1);
        }
        // next special day
        List<Object[]> up = Special.upcoming(now, 1);
        if (!up.isEmpty()) {
            Calendar when = (Calendar) up.get(0)[0];
            Special.Day day = (Special.Day) up.get(0)[1];
            float ly = cy + cellH + U.f(34);
            p.setColor(0xFFFFB547);
            c.drawCircle(x + U.f(4), ly - U.f(6), U.f(4), p);
            t.setTypeface(U.regular); t.setTextSize(U.f(16)); t.setColor(0xFFFFFFFF);
            c.drawText(day.label, x + U.f(18), ly, t);
            t.setColor(U.white(0.6f)); t.setTextAlign(Paint.Align.RIGHT);
            c.drawText(Special.sameDay(when, now) ? "Today" : shortDate.format(when.getTime()), w - x, ly, t);
            t.setTextAlign(Paint.Align.LEFT);
        }
    }

    static int heightPx() { return U.px(28 + 122 + 56 + 34 + 30); }
}

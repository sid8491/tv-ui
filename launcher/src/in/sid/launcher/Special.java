package in.sid.launcher;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/** Special days: Indian festivals and family dates. Lunar festival dates move each year, so this table is kept by hand. */
final class Special {
    static final class Day {
        final int year, month, day; // month 1-12; year 0 = every year
        final String label;
        final String sky; // wallpaper to use on the day, or null

        Day(int year, int month, int day, String label, String sky) {
            this.year = year; this.month = month; this.day = day; this.label = label; this.sky = sky;
        }

        Calendar next(Calendar from) {
            Calendar c = (Calendar) from.clone();
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
            int y = year != 0 ? year : from.get(Calendar.YEAR);
            c.set(y, month - 1, day);
            if (year == 0 && c.before(from) && !sameDay(c, from)) c.set(Calendar.YEAR, y + 1);
            return c;
        }
    }

    static final List<Day> DAYS = new ArrayList<>();
    static {
        DAYS.add(new Day(0, 1, 26, "Republic Day", null));
        DAYS.add(new Day(2026, 3, 4, "Holi", "golden"));
        DAYS.add(new Day(0, 8, 15, "Independence Day", null));
        DAYS.add(new Day(2026, 10, 20, "Dussehra", null));
        DAYS.add(new Day(2026, 11, 8, "Diwali", "diwali"));
        DAYS.add(new Day(0, 11, 14, "Children's Day", null));
        DAYS.add(new Day(0, 12, 25, "Christmas", null));
        DAYS.add(new Day(0, 1, 1, "New Year", null));
    }

    static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    /** Today's special day, or null. */
    static Day today(Calendar now) {
        for (Day d : DAYS) if (sameDay(d.next(now), now)) return d;
        return null;
    }

    /** Upcoming special days (including today), soonest first. */
    static List<Object[]> upcoming(Calendar now, int max) {
        List<Object[]> out = new ArrayList<>();
        for (Day d : DAYS) {
            Calendar c = d.next(now);
            if (d.year != 0 && c.before(now) && !sameDay(c, now)) continue;
            out.add(new Object[]{c, d});
        }
        Collections.sort(out, (a, b) -> ((Calendar) a[0]).compareTo((Calendar) b[0]));
        return out.size() > max ? out.subList(0, max) : out;
    }
}

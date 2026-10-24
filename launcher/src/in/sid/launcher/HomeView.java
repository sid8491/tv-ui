package in.sid.launcher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The home screen. One focus index walks: dock tiles (0..n-1), the clock block, the four top buttons, the photo wall,
 * then the continue-watching cards (or the info card). Overlays (menus, calendar, all apps, sources) take the keys while open.
 */
final class HomeView extends FrameLayout {
    private final Context ctx;
    private final Prefs prefs;
    private final Apps apps;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // children
    private final SkyView sky;
    private final TextView clock, greeting, dateText, dockLabel, statusLabel, wallTitle, cwTitle;
    private final FrameLayout clockBlock;
    private final Glass clockGlass = new Glass(22);
    private final List<FrameLayout> topButtons = new ArrayList<>();
    private final List<Glass> topGlass = new ArrayList<>();
    private final List<IconView> topIcons = new ArrayList<>();
    private final WallView wall;
    private final ContinueView cw;
    private final InfoCard info;
    private final DockView dock;
    private final LegendView legend;
    private final FrameLayout overlayHost;
    private DockView availStrip; private TextView editHint; private FrameLayout editBar;

    // state
    private int focus = 0, lastDock = 0;
    private boolean editing; private boolean editAvail; private int editIdx;
    private boolean moving;    // editor: ◀ ▶ move the lifted tile instead of selecting another
    private boolean quickMove; // the move began with a long press on the home screen: dropping the tile leaves the editor
    private View overlay; private String overlayKind;
    private List<WatchNext.Item> cwItems = new ArrayList<>();
    private String skyLabel = "";

    private static final int DOCK_TOP = 776; // design units; the band above (206..DOCK_TOP) holds the wall and the cards
    private static final String[] TOP = {"screensaver", "sources", "allapps", "settings"};
    private static final String[] TOP_ICON = {"image", "input", "grid", "gear"};
    private static final String[] TOP_LABEL = {"Screensaver", "Sources", "All apps", "Settings"};
    private static final String[] TOP_DETAIL = {"Starts Backdrop now", "HDMI and AV inputs", "Every installed app", "TV settings or Home settings"};

    HomeView(Context c, Prefs prefs, Apps apps) {
        super(c);
        ctx = c; this.prefs = prefs; this.apps = apps;
        setClipChildren(false);
        // the home screen keeps its own focus model; Android's view focus (and its default highlight) stays out of it
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        setDefaultFocusHighlightEnabled(false);

        sky = new SkyView(c);
        addView(sky, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        // clock block (focusable: opens the calendar)
        clockBlock = new FrameLayout(c);
        clockBlock.setPadding(U.px(18), U.px(14), U.px(18), U.px(14));
        clock = text(c, 52, U.medium, 0xFFFFFFFF);
        greeting = text(c, 22, U.medium, 0xFFFFFFFF);
        dateText = text(c, 17, U.regular, U.white(0.7f));
        clockBlock.addView(clock, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 0, 0));
        TextView avatar = text(c, 18, U.medium, 0xFFFFFFFF);
        avatar.setText(prefs.name().isEmpty() ? "" : prefs.name().substring(0, 1));
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(new Glass(20));
        clockBlock.addView(avatar, lp(U.px(40), U.px(40), 0, U.px(70)));
        clockBlock.addView(greeting, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(52), U.px(66)));
        clockBlock.addView(dateText, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(52), U.px(94)));
        addView(clockBlock, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(96 - 18), U.px(56 - 14)));

        // top buttons
        for (int i = 0; i < TOP.length; i++) {
            FrameLayout b = new FrameLayout(c);
            Glass g = new Glass(32);
            b.setBackground(g);
            IconView ic = new IconView(c, TOP_ICON[i]);
            b.addView(ic, lp(U.px(28), U.px(28), U.px(18), U.px(18)));
            int x = U.px(1920 - 96) - (TOP.length - i) * U.px(64) - (TOP.length - 1 - i) * U.px(18);
            addView(b, lp(U.px(64), U.px(64), x, U.px(56)));
            topButtons.add(b); topGlass.add(g); topIcons.add(ic);
            final int idx = i;
            b.setOnClickListener(v -> { setFocus(TOP0() + idx); activate(); });
        }

        // wall
        wall = new WallView(c);
        addView(wall, lp(U.px(WallView.W), U.px(WallView.H), U.px(96), U.px(206 + (600 - WallView.H) / 2)));
        wallTitle = text(c, 18, U.medium, U.white(0.85f));
        wallTitle.setText("♥ " + prefs.name() + "'s wall");
        addView(wallTitle, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(96), U.px(206 + (600 - WallView.H) / 2 + WallView.H + 16)));

        // continue watching / info card
        cwTitle = text(c, 18, U.medium, U.white(0.85f));
        cwTitle.setText("▶ Continue watching");
        addView(cwTitle, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(1224), 0));
        cw = new ContinueView(c, apps);
        addView(cw, lp(U.px(600), LayoutParams.WRAP_CONTENT, U.px(1224), 0));
        info = new InfoCard(c);
        addView(info, lp(U.px(560), InfoCard.heightPx(), U.px(1920 - 96 - 560), 0));

        // dock + label + legend
        dock = new DockView(c, 0, 22, 26, 16, 12);
        dock.setMaxTileWidth(232);
        dock.setBackground(new Glass(34));
        addView(dock, lp(U.px(1920 - 192), LayoutParams.WRAP_CONTENT, U.px(96), U.px(DOCK_TOP)));
        dockLabel = text(c, 24, U.medium, 0xFFFFFFFF);
        dockLabel.setGravity(Gravity.CENTER);
        dockLabel.setShadowLayer(U.f(10), 0, U.f(2), 0x80000000);
        addView(dockLabel, lp(U.px(400), U.px(44), 0, 0));
        statusLabel = text(c, 20, U.regular, 0xFFFFFFFF);
        statusLabel.setGravity(Gravity.END);
        statusLabel.setShadowLayer(U.f(10), 0, U.f(2), 0x80000000);
        addView(statusLabel, lp(U.px(1920 - 192), U.px(44), U.px(96), 0));
        legend = new LegendView(c);
        addView(legend, lp(U.px(1920 - 192), U.px(22), U.px(96), 0));

        overlayHost = new FrameLayout(c);
        overlayHost.setClipChildren(false);
        addView(overlayHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        reloadDock();
        applySky();
        tick();
        setFocus(0);
        loadPhotos();
        loadWatchNext();
    }

    /* ------------------------------------------------------------------ layout helpers */

    private static LayoutParams lp(int w, int h, int x, int y) {
        LayoutParams p = new LayoutParams(w, h);
        p.leftMargin = x; p.topMargin = y;
        return p;
    }

    private static TextView text(Context c, float sizeUnits, android.graphics.Typeface tf, int color) {
        TextView tv = new TextView(c);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, U.f(sizeUnits));
        tv.setTypeface(tf);
        tv.setTextColor(color);
        tv.setSingleLine();
        return tv;
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // things placed relative to the dock's measured height
        int dockTop = U.px(DOCK_TOP), dockBottom = dockTop + dock.getMeasuredHeight();
        place(dock, (U.screenW - dock.getMeasuredWidth()) / 2, dockTop);   // the bar hugs its tiles, centred
        place(dockLabel, dockLabel.getLeft(), dockBottom + U.px(14));
        place(statusLabel, U.px(96), dockBottom + U.px(14));
        place(legend, U.px(96), dockBottom + U.px(14) + U.px(40));
        // wall and continue block centred in the middle band (206..DOCK_TOP)
        int band = U.px(DOCK_TOP - 206);
        place(wall, U.px(96), U.px(206) + (band - wall.getMeasuredHeight()) / 2);
        place(wallTitle, U.px(96), wall.getBottom() + U.px(16));
        int cwH = cw.getMeasuredHeight() + U.px(28);
        int cwTop = U.px(206) + (band - cwH) / 2;
        place(cwTitle, U.px(1224), cwTop);
        place(cw, U.px(1224), cwTop + U.px(28));
        place(info, U.px(1920 - 96 - 560), U.px(206) + (band - info.getMeasuredHeight()) / 2);
        positionDockLabel();
    }

    private static void place(View v, int x, int y) {
        v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
    }

    private void positionDockLabel() {
        int i = editing ? (editAvail ? -1 : editIdx) : (focus < dock.count() ? focus : -1);
        if (i < 0) return;
        int cx = dock.getLeft() + (int) dock.tileCenterX(i);
        int x = Math.max(0, Math.min(U.px(1920) - dockLabel.getMeasuredWidth(), cx - dockLabel.getMeasuredWidth() / 2));
        place(dockLabel, x, dockLabel.getTop());
    }

    /* ------------------------------------------------------------------ focus model */

    private int GRID() { return dock.count(); }
    private int CLOCK() { return GRID(); }
    private int TOP0() { return GRID() + 1; }
    private int WALL() { return TOP0() + TOP.length; }
    private int CW0() { return WALL() + 1; }
    private boolean cwVisible() { return prefs.continueWatching() && !cwItems.isEmpty(); }
    private int cwCount() { return cwVisible() ? cw.count() : 1; }
    private int CWEND() { return CW0() + cwCount(); }

    private boolean inDock() { return focus < GRID(); }
    private boolean inTop() { return focus >= TOP0() && focus < WALL(); }
    private boolean inCW() { return focus >= CW0() && focus < CWEND(); }

    void setFocus(int f) {
        focus = Math.max(0, Math.min(CWEND() - 1, f));
        if (inDock()) lastDock = focus;
        dock.setFocused(inDock() ? focus : -1);
        clockBlock.setBackground(focus == CLOCK() ? clockGlass : null);
        clockGlass.setSolid(false);
        for (int i = 0; i < topButtons.size(); i++) {
            boolean on = focus == TOP0() + i;
            topGlass.get(i).setSolid(on);
            topIcons.get(i).setColor(on ? 0xFF0B1220 : 0xE6FFFFFF);
            topButtons.get(i).animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).setDuration(160).start();
        }
        wall.setFocused(focus == WALL());
        if (cwVisible()) cw.setFocused(inCW() ? focus - CW0() : -1); else info.setFocused(inCW());
        updateLabels();
    }

    private void updateLabels() {
        if (editing) {
            dockLabel.setVisibility(VISIBLE); statusLabel.setVisibility(GONE);
            Apps.Entry e = editAvail ? availStrip.at(editIdx) : dock.at(editIdx);
            dockLabel.setText(e == null ? "" : e.label + (editAvail ? " · not in the dock" : (moving ? " · moving, " : " · ") + "position " + (editIdx + 1) + " of " + dock.count()));
            dockLabel.measure(MeasureSpec.makeMeasureSpec(U.px(1920), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(U.px(44), MeasureSpec.EXACTLY));
            place(dockLabel, U.px(96) + (U.px(1920 - 192) - dockLabel.getMeasuredWidth()) / 2, dockLabel.getTop());
            return;
        }
        if (inDock()) {
            dockLabel.setVisibility(VISIBLE); statusLabel.setVisibility(GONE);
            Apps.Entry e = dock.at(focus);
            dockLabel.setText(e == null ? "" : e.label);
            dockLabel.measure(MeasureSpec.makeMeasureSpec(U.px(400), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(U.px(44), MeasureSpec.EXACTLY));
            positionDockLabel();
        } else {
            dockLabel.setVisibility(GONE); statusLabel.setVisibility(VISIBLE);
            String title, detail;
            if (focus == CLOCK()) { title = "Today"; detail = "Press OK for the calendar"; }
            else if (inTop()) { title = TOP_LABEL[focus - TOP0()]; detail = TOP_DETAIL[focus - TOP0()]; }
            else if (focus == WALL()) { title = prefs.name() + "'s wall"; detail = "Photos from " + prefs.photoFolder(); }
            else if (cwVisible()) { WatchNext.Item it = cw.at(focus - CW0()); Apps.Entry a = it == null ? null : apps.get(it.pkg); title = it == null ? "" : it.title; detail = (it == null ? "" : it.subtitle) + (a != null ? " · " + a.label : ""); }
            else { title = "Today"; detail = "Press OK for the calendar"; }
            statusLabel.setText(title + "  ·  " + detail);
        }
    }

    /* ------------------------------------------------------------------ keys */

    boolean onKey(int key) {
        android.util.Log.d("home", "key " + key + " focus=" + focus + " grid=" + GRID() + " overlay=" + overlayKind + " editing=" + editing);
        if (key == KeyEvent.KEYCODE_FUNCTION) { openCalendar(); return true; } // the remote's "Text" button (not in the legend on purpose)
        if (overlay != null) return overlayKey(key);
        if (editing) return editKey(key);
        int next = focus;
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (inDock()) next = Math.min(focus + 1, GRID() - 1);
                else if (focus == CLOCK()) next = TOP0();
                else if (inTop()) next = Math.min(focus + 1, WALL() - 1);
                else if (focus == WALL()) next = CW0();
                else if (inCW() && cwVisible() && (focus - CW0()) % 2 == 0 && focus + 1 < CWEND()) next = focus + 1;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (inDock()) next = Math.max(focus - 1, 0);
                else if (inTop()) next = focus == TOP0() ? CLOCK() : focus - 1;
                else if (inCW()) next = cwVisible() && (focus - CW0()) % 2 == 1 ? focus - 1 : WALL();
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (inDock()) next = lastDock >= (GRID() + 1) / 2 ? (cwVisible() && cw.count() > 2 ? CW0() + 2 : CW0()) : WALL();
                else if (focus == WALL()) next = CLOCK();
                else if (inCW()) next = cwVisible() && focus - CW0() >= 2 ? focus - 2 : WALL() - 1;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (focus == CLOCK()) next = WALL();
                else if (inTop()) next = focus - TOP0() < 2 ? WALL() : CW0();
                else if (focus == WALL()) next = lastDock;
                else if (inCW()) next = cwVisible() && focus - CW0() + 2 < cw.count() ? focus + 2 : lastDock;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                activate();
                return true;
            case KeyEvent.KEYCODE_BACK:
                return true; // home screen: Back does nothing
            default:
                return false;
        }
        setFocus(next);
        return true;
    }

    /** OK held down. On a dock tile this picks the tile up so ◀ ▶ move it; in the editor it does the same for the selected tile. */
    boolean onLongPress() {
        if (overlay != null) return false;
        if (editing) {
            if (!editAvail && !moving) startMoving(false);
            return true;
        }
        if (inDock()) {
            startEditing();
            editIdx = focus;
            dock.setFocused(editIdx);
            startMoving(true);
            return true;
        }
        return false;
    }

    private void startMoving(boolean quick) {
        moving = true; quickMove = quick;
        dock.setMoving(true);
        editHint.setText(hint());
        updateLabels();
        post(this::positionDockLabel);
    }

    /** Drop the lifted tile; a quick move (from the home screen) is done at that point. */
    private void stopMoving() {
        moving = false;
        dock.setMoving(false);
        if (quickMove) { quickMove = false; stopEditing(); }
    }

    private void activate() {
        if (inDock()) { Apps.Entry e = dock.at(focus); if (e != null) launch(e.intent); }
        else if (focus == CLOCK()) showCalendar();
        else if (inTop()) topAction(TOP[focus - TOP0()]);
        else if (focus == WALL()) Toast.makeText(ctx, "Photo viewer: coming next", Toast.LENGTH_SHORT).show();
        else if (inCW()) {
            if (cwVisible()) { WatchNext.Item it = cw.at(focus - CW0()); if (it != null) resume(it); }
            else showCalendar();
        }
    }

    private void launch(Intent i) {
        try { ctx.startActivity(i); } catch (Exception e) { Toast.makeText(ctx, "Could not open that app", Toast.LENGTH_SHORT).show(); }
    }

    private void resume(WatchNext.Item it) {
        try {
            if (it.intentUri != null) {
                Intent i = Intent.parseUri(it.intentUri, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(i);
                return;
            }
        } catch (Exception ignored) { }
        Apps.Entry a = apps.get(it.pkg);
        if (a != null) launch(a.intent);
    }

    private void topAction(String id) {
        switch (id) {
            case "screensaver":
                try {
                    ctx.startActivity(new Intent().setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) { Toast.makeText(ctx, "Screensaver could not be started", Toast.LENGTH_SHORT).show(); }
                break;
            case "sources": showSources(); break;
            case "allapps": showAllApps(); break;
            case "settings": showSettingsMenu(); break;
        }
    }

    /* ------------------------------------------------------------------ overlays */

    private void showOverlay(View v, String kind, int w, int h, int x, int y) {
        closeOverlay();
        overlay = v; overlayKind = kind;
        LayoutParams p = new LayoutParams(w, h);
        p.leftMargin = x; p.topMargin = y;
        overlayHost.addView(v, p);
        android.util.Log.d("home", "overlay " + kind + " at " + x + "," + y + " size " + w + "x" + h);
        v.setAlpha(0f); v.setScaleX(0.96f); v.setScaleY(0.96f);
        v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
    }

    void closeOverlay() {
        if (overlay != null) { overlayHost.removeView(overlay); overlay = null; overlayKind = null; }
    }

    private boolean overlayKey(int key) {
        if (overlay instanceof MenuOverlay) return ((MenuOverlay) overlay).onKey(key);
        if (overlay instanceof AllAppsView) return ((AllAppsView) overlay).onKey(key);
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER) { closeOverlay(); return true; }
        return true;
    }

    boolean calendarShowing() { return "calendar".equals(overlayKind); }

    /** Calendar from anywhere (Text key, or the remap service when another app is in front): toggles it, leaving any menu or editor. */
    void openCalendar() {
        if ("calendar".equals(overlayKind)) { closeOverlay(); return; }
        if (editing) stopEditing();
        closeOverlay();
        showCalendar();
    }

    private void showCalendar() {
        CalendarView cal = new CalendarView(ctx);
        cal.skyLabel = skyLabel;
        showOverlay(cal, "calendar", CalendarView.widthPx(), CalendarView.heightPx(), (U.screenW - CalendarView.widthPx()) / 2, (U.screenH - CalendarView.heightPx()) / 2);
    }

    private void showSettingsMenu() {
        MenuOverlay m = new MenuOverlay(ctx, null, false);
        List<MenuOverlay.Row> rows = new ArrayList<>();
        rows.add(new MenuOverlay.Row("tv", "tv", "TV settings", "Network, remote, display, sound"));
        rows.add(new MenuOverlay.Row("home", "home", "Home settings", "Wallpaper, photos, dock, remote"));
        m.setRows(rows);
        m.setListener(new MenuOverlay.Listener() {
            @Override public void onSelect(MenuOverlay.Row r) {
                if (r.id.equals("tv")) { closeOverlay(); launch(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
                else showHomeSettings();
            }
            @Override public void onBack() { closeOverlay(); }
        });
        showOverlay(m, "menu", U.px(420), LayoutParams.WRAP_CONTENT, U.px(1920 - 96 - 420), U.px(136));
    }

    private void showHomeSettings() {
        MenuOverlay m = new MenuOverlay(ctx, "Home settings", true);
        List<MenuOverlay.Row> rows = new ArrayList<>();
        MenuOverlay.Row sky = new MenuOverlay.Row("sky", "sun", "Wallpaper", skyValue());
        MenuOverlay.Row folder = new MenuOverlay.Row("folder", "camera", "Photo folder", prefs.photoFolder() + " · " + photoCount + " photos");
        MenuOverlay.Row shuffle = new MenuOverlay.Row("shuffle", "image", "Shuffle the wall", shuffleValue());
        MenuOverlay.Row dockRow = new MenuOverlay.Row("dock", "grid", "Dock apps", dock.count() + " apps · reorder, remove, add (or hold OK on a tile)");
        MenuOverlay.Row style = new MenuOverlay.Row("style", "image", "Dock icons", dockStyleValue());
        MenuOverlay.Row cwRow = new MenuOverlay.Row("cw", "play", "Continue watching", prefs.continueWatching() ? "On" : "Off");
        MenuOverlay.Row name = new MenuOverlay.Row("name", "heart", "Greeting name", prefs.name());
        MenuOverlay.Row remote = new MenuOverlay.Row("remote", "tv", "Remote buttons", "Red Apple TV · Green Prime · Yellow Stremio · Blue Where to Watch · Play JioHotstar");
        rows.addAll(Arrays.asList(sky, folder, shuffle, dockRow, style, cwRow, name, remote));
        m.setRows(rows);
        m.setListener(new MenuOverlay.Listener() {
            @Override public void onSelect(MenuOverlay.Row r) {
                switch (r.id) {
                    case "sky": {
                        List<String> modes = Arrays.asList(Prefs.SKY_MODES);
                        prefs.setSky(modes.get((modes.indexOf(prefs.sky()) + 1) % modes.size()));
                        applySky(); r.value = skyValue(); m.refreshRow(r); break;
                    }
                    case "shuffle": {
                        int d = prefs.shuffleDays(); prefs.setShuffleDays(d == 1 ? 3 : d == 3 ? 7 : 1);
                        r.value = shuffleValue(); m.refreshRow(r); loadPhotos(); break;
                    }
                    case "cw": {
                        prefs.setContinueWatching(!prefs.continueWatching());
                        r.value = prefs.continueWatching() ? "On" : "Off"; m.refreshRow(r); applyCwVisibility(); break;
                    }
                    case "dock": closeOverlay(); startEditing(); break;
                    case "style": {
                        prefs.setDockStyle("photo".equals(prefs.dockStyle()) ? "original" : "photo");
                        r.value = dockStyleValue(); m.refreshRow(r); loadPhotos(); break;
                    }
                    case "name": {
                        String[] names = {"Aanya", "Sid", "Family"};
                        int i = Arrays.asList(names).indexOf(prefs.name());
                        prefs.setName(names[(i + 1) % names.length]);
                        r.value = prefs.name(); m.refreshRow(r);
                        wallTitle.setText("♥ " + prefs.name() + "'s wall"); tick(); break;
                    }
                    default: break;
                }
            }
            @Override public void onBack() { showSettingsMenu(); }
        });
        showOverlay(m, "home", U.px(640), LayoutParams.WRAP_CONTENT, U.px(1920 - 96 - 640), U.px(230));
    }

    private String dockStyleValue() {
        if (!"photo".equals(prefs.dockStyle())) return "Original banners";
        return iconCount > 0 ? prefs.name() + "'s photos · " + iconCount + " in " + prefs.photoFolder() + "/" + PhotoTiles.SUBFOLDER
                             : "Photos · none found in " + prefs.photoFolder() + "/" + PhotoTiles.SUBFOLDER + ", showing banners";
    }

    private String skyValue() {
        String s = prefs.sky();
        return s.equals("auto") ? "Auto (follows the sun) · " + skyLabel + " now" : skyLabel;
    }

    private String shuffleValue() {
        int d = prefs.shuffleDays();
        return d == 1 ? "Every day" : "Every " + d + " days";
    }

    private void showSources() {
        MenuOverlay m = new MenuOverlay(ctx, "Sources", true);
        List<MenuOverlay.Row> rows = new ArrayList<>();
        final List<String> ids = new ArrayList<>();
        try {
            TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo in : tim.getTvInputList()) {
                if (!in.isPassthroughInput()) continue;
                int state = tim.getInputState(in.getId());
                String s = state == TvInputManager.INPUT_STATE_CONNECTED ? "Connected" : state == TvInputManager.INPUT_STATE_CONNECTED_STANDBY ? "Standby" : "Nothing connected";
                CharSequence label = in.loadLabel(ctx);
                rows.add(new MenuOverlay.Row(in.getId(), "input", label == null ? in.getId() : label.toString(), s));
                ids.add(in.getId());
            }
        } catch (Exception ignored) { }
        if (rows.isEmpty()) rows.add(new MenuOverlay.Row("none", "input", "No inputs found", "The TV reported no HDMI or AV inputs"));
        m.setRows(rows);
        m.setListener(new MenuOverlay.Listener() {
            @Override public void onSelect(MenuOverlay.Row r) {
                closeOverlay();
                if (!ids.contains(r.id)) return;
                Uri u = TvContract.buildChannelUriForPassthroughInput(r.id);
                launch(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            @Override public void onBack() { closeOverlay(); }
        });
        showOverlay(m, "sources", U.px(560), LayoutParams.WRAP_CONTENT, U.px(1920 - 96 - 560), U.px(136));
    }

    private void showAllApps() {
        AllAppsView v = new AllAppsView(ctx, apps.all());
        v.setListener(new AllAppsView.Listener() {
            @Override public void onSelect(Apps.Entry e) { closeOverlay(); launch(e.intent); }
            @Override public void onBack() { closeOverlay(); }
        });
        showOverlay(v, "apps", U.px(1920 - 192), LayoutParams.WRAP_CONTENT, U.px(96), U.px(150));
    }

    /* ------------------------------------------------------------------ dock editor */

    private void startEditing() {
        editing = true; editAvail = false; editIdx = 0;
        dock.setEditing(true);
        dock.setFocused(0);
        clockBlock.setBackground(null);
        wall.setFocused(false);
        wallTitle.setVisibility(INVISIBLE);
        editBar = new FrameLayout(ctx);
        editBar.setBackground(new Glass(20).deep(true));
        editHint = text(ctx, 14, U.regular, U.white(0.75f));
        TextView title = text(ctx, 17, U.medium, 0xFFFFFFFF);
        title.setText("Editing dock");
        editBar.addView(title, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(18), U.px(14)));
        editBar.addView(editHint, lp(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, U.px(150), U.px(17)));
        availStrip = new DockView(ctx, 0, 6, 10, 10, 8);
        availStrip.setLift(1.15f, -4);
        List<Apps.Entry> avail = available();
        availStrip.setApps(avail);
        int stripW = U.px(avail.isEmpty() ? 0 : Math.min(760, avail.size() * 102 + 20));
        editBar.addView(availStrip, lp(stripW, LayoutParams.WRAP_CONTENT, U.px(1920 - 192) - stripW - U.px(18), U.px(4)));
        addView(editBar, lp(U.px(1920 - 192), U.px(66), U.px(96), U.px(806 - 66 - 16)));
        editHint.setText(hint());
        updateLabels();
    }

    private List<Apps.Entry> available() {
        List<Apps.Entry> out = new ArrayList<>();
        for (Apps.Entry e : apps.all()) if (!dock.apps().contains(e)) out.add(e);
        return out;
    }

    private String hint() {
        if (moving) return "◀ ▶ move · OK or Back to drop";
        return editAvail ? "◀ ▶ choose · OK add to dock · ▼ back to dock · Back done" : "◀ ▶ select · hold OK to move · OK remove · ▲ available apps · Back done";
    }

    private boolean editKey(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_ESCAPE:
                if (moving) { stopMoving(); if (!editing) return true; break; }
                stopEditing(); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (editAvail) { editIdx = Math.max(0, editIdx - 1); availStrip.setFocused(editIdx); }
                else if (moving) { if (editIdx > 0) { dock.move(editIdx, editIdx - 1); editIdx--; dock.setFocused(editIdx); } }
                else { editIdx = Math.max(0, editIdx - 1); dock.setFocused(editIdx); }
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (editAvail) { editIdx = Math.min(availStrip.count() - 1, editIdx + 1); availStrip.setFocused(editIdx); }
                else if (moving) { if (editIdx < dock.count() - 1) { dock.move(editIdx, editIdx + 1); editIdx++; dock.setFocused(editIdx); } }
                else { editIdx = Math.min(dock.count() - 1, editIdx + 1); dock.setFocused(editIdx); }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (moving) break;
                if (!editAvail && availStrip.count() > 0) { editAvail = true; editIdx = 0; dock.setFocused(-1); availStrip.setFocused(0); }
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (moving) break;
                if (editAvail) { editAvail = false; editIdx = 0; availStrip.setFocused(-1); dock.setFocused(0); }
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                if (moving) { stopMoving(); if (!editing) return true; break; }
                if (editAvail) {
                    Apps.Entry e = availStrip.at(editIdx);
                    if (e != null) { dock.add(e); availStrip.removeAt(editIdx); editIdx = Math.min(editIdx, availStrip.count() - 1);
                        if (availStrip.count() == 0) { editAvail = false; editIdx = dock.count() - 1; dock.setFocused(editIdx); } else availStrip.setFocused(editIdx); }
                } else if (dock.count() > 1) {
                    Apps.Entry e = dock.at(editIdx);
                    dock.removeAt(editIdx);
                    if (e != null) availStrip.add(e);
                    editIdx = Math.min(editIdx, dock.count() - 1);
                    dock.setFocused(editIdx);
                }
                break;
            default:
                return false;
        }
        editHint.setText(hint());
        updateLabels();
        post(this::positionDockLabel);
        return true;
    }

    private void stopEditing() {
        editing = false; moving = false; quickMove = false;
        List<String> pkgs = new ArrayList<>();
        for (Apps.Entry e : dock.apps()) pkgs.add(e.pkg);
        prefs.setDock(pkgs);
        loadPhotos(); // new apps need their photo tiles
        dock.setEditing(false);
        wallTitle.setVisibility(VISIBLE);
        removeView(editBar);
        editBar = null; availStrip = null;
        setFocus(Math.min(editIdx, dock.count() - 1));
    }

    private void reloadDock() {
        List<Apps.Entry> d = new ArrayList<>(apps.pick(prefs.dock()));
        if (d.size() < 4) { // few of the preferred apps exist here (emulator): fill up from what is installed
            for (Apps.Entry e : apps.all()) { if (d.size() >= 12) break; if (!d.contains(e)) d.add(e); }
        }
        dock.setApps(d);
    }

    /* ------------------------------------------------------------------ data */

    private int photoCount, iconCount;

    void loadPhotos() {
        final File dir = new File(Environment.getExternalStorageDirectory(), prefs.photoFolder());
        final int shuffle = prefs.shuffleDays();
        final boolean photoDock = "photo".equals(prefs.dockStyle());
        final List<String> dockPkgs = new ArrayList<>();
        for (Apps.Entry e : dock.apps()) dockPkgs.add(e.pkg);
        if (!photoDock) dock.setPhotoTiles(null);
        new Thread(() -> {
            File[] files = dir.listFiles((d, n) -> { String l = n.toLowerCase(Locale.ROOT); return l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp"); });
            final List<Bitmap> out = new ArrayList<>();
            int count = files == null ? 0 : files.length;
            if (files != null && files.length > 0) {
                List<File> list = new ArrayList<>(Arrays.asList(files));
                Collections.sort(list);
                long day = System.currentTimeMillis() / 86400000L / Math.max(1, shuffle);
                Collections.shuffle(list, new Random(day));
                for (int i = 0; i < Math.min(5, list.size()); i++) {
                    Bitmap b = decode(list.get(i), 360);
                    if (b != null) out.add(b);
                }
            }
            final int c = count;
            handler.post(() -> { photoCount = c; wall.setPhotos(out); });
            // dock tiles: each app gets its own photo with the app's banner as a badge
            List<File> icons = PhotoTiles.files(dir, shuffle);
            final int ic = icons.size();
            final Map<String, Bitmap> tiles = photoDock ? PhotoTiles.build(prefs, apps, dockPkgs, icons) : null;
            handler.post(() -> { iconCount = ic; if (photoDock) dock.setPhotoTiles(tiles.isEmpty() ? null : tiles); });
        }, "photos").start();
    }

    static Bitmap decode(File f, int targetW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / (sample * 2) >= targetW) sample *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = sample; o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
            return b == null ? null : rotateByExif(f, b);
        } catch (Exception e) { return null; }
    }

    private static Bitmap rotateByExif(File f, Bitmap b) {
        try {
            android.media.ExifInterface ex = new android.media.ExifInterface(f.getPath());
            int o = ex.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
            if (deg == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(deg);
            return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
        } catch (Exception e) { return b; }
    }

    void loadWatchNext() {
        if (!prefs.continueWatching()) { applyCwVisibility(); return; }
        WatchNext.load(ctx, 4, items -> { cwItems = items; cw.setItems(items); applyCwVisibility(); });
    }

    private void applyCwVisibility() {
        boolean v = cwVisible();
        cw.setVisibility(v ? VISIBLE : GONE);
        cwTitle.setVisibility(v ? VISIBLE : GONE);
        info.setVisibility(v ? GONE : VISIBLE);
        if (focus >= CWEND()) focus = CWEND() - 1;
        requestLayout();
        setFocus(focus);
    }

    /* ------------------------------------------------------------------ clock + sky */

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm", Locale.ENGLISH);
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE, d MMMM", Locale.ENGLISH);

    void tick() {
        handler.removeCallbacks(tickRunnable);
        Calendar now = Calendar.getInstance();
        clock.setText(timeFmt.format(now.getTime()));
        dateText.setText(dateFmt.format(now.getTime()));
        int h = now.get(Calendar.HOUR_OF_DAY);
        greeting.setText((h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening") + ", " + prefs.name());
        applySky();
        info.invalidate();
        long msToNextMinute = 60000 - (System.currentTimeMillis() % 60000);
        handler.postDelayed(tickRunnable, msToNextMinute + 50);
    }

    private final Runnable tickRunnable = this::tick;

    void stop() { handler.removeCallbacks(tickRunnable); }

    private void applySky() {
        String mode = prefs.sky();
        Calendar now = Calendar.getInstance();
        if (mode.equals("auto")) {
            Special.Day sp = Special.today(now);
            if (sp != null && sp.sky != null) mode = sp.sky;
            else {
                int h = now.get(Calendar.HOUR_OF_DAY);
                mode = h >= 5 && h < 7 ? "dawn" : h >= 7 && h < 17 ? "day" : h >= 17 && h < 19 ? "golden" : "night";
            }
        }
        sky.setMode(mode);
        skyLabel = mode.equals("golden") ? "Golden hour" : Character.toUpperCase(mode.charAt(0)) + mode.substring(1);
    }

    void onResume() { tick(); loadWatchNext(); if (overlay != null) closeOverlay(); if (editing) stopEditing(); }

    /** The colour-key legend under the dock. */
    static final class LegendView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        private static final int[] COLORS = {0xFFE53935, 0xFF43A047, 0xFFFDD835, 0xFF1E88E5, 0xFFFFFFFF};
        private static final String[] LABELS = {"Apple TV", "Prime Video", "Stremio", "Where to Watch", "JioHotstar"};

        LegendView(Context c) { super(c); setWillNotDraw(false); }

        @Override protected void onDraw(Canvas c) {
            t.setTypeface(U.regular); t.setTextSize(U.f(13)); t.setShadowLayer(U.f(6), 0, U.f(1), 0x80000000);
            float x = getWidth();
            float sq = U.f(14);
            for (int i = LABELS.length - 1; i >= 0; i--) {
                boolean dim = LABELS[i].equals("free");
                t.setColor(U.white(dim ? 0.4f : 0.6f));
                float tw = t.measureText(LABELS[i]);
                x -= tw;
                c.drawText(LABELS[i], x, U.f(15), t);
                x -= sq + U.f(6);
                p.setColor(dim ? U.argb(0.6f, COLORS[i]) : COLORS[i]);
                c.drawRoundRect(new RectF(x, U.f(3), x + sq, U.f(3) + sq), U.f(4), U.f(4), p);
                if (i == LABELS.length - 1) { // play key: dark triangle on the white cap
                    p.setColor(0xFF0B1220);
                    android.graphics.Path tri = new android.graphics.Path();
                    tri.moveTo(x + sq * 0.35f, U.f(3) + sq * 0.25f); tri.lineTo(x + sq * 0.35f, U.f(3) + sq * 0.75f); tri.lineTo(x + sq * 0.78f, U.f(3) + sq * 0.5f); tri.close();
                    c.drawPath(tri, p);
                }
                x -= U.f(18);
            }
        }
    }
}

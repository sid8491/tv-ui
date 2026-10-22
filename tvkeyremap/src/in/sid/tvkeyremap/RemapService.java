package in.sid.tvkeyremap;

import android.accessibilityservice.AccessibilityService;
import android.content.ComponentName;
import android.content.Intent;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remaps remote buttons on the Skyworth/Flipkart TV:
 *   Google Play button (KEYCODE_BUTTON_2 = 189)   -> JioHotstar
 *   Red button         (KEYCODE_PROG_RED = 183)   -> Apple TV
 *   Green button       (KEYCODE_PROG_GREEN = 184) -> Prime Video
 *   Blue button        (KEYCODE_PROG_BLUE = 186)  -> Where to Watch
 *   Yellow button      (KEYCODE_PROG_YELLOW = 185)-> Stremio
 */
public class RemapService extends AccessibilityService {
    private static final String TAG = "TVKeyRemap";

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_BUTTON_2 && code != KeyEvent.KEYCODE_PROG_RED && code != KeyEvent.KEYCODE_PROG_GREEN
                && code != KeyEvent.KEYCODE_PROG_BLUE && code != KeyEvent.KEYCODE_PROG_YELLOW) return false;
        if (event.getAction() == KeyEvent.ACTION_UP) {
            switch (code) {
                case KeyEvent.KEYCODE_BUTTON_2: launchPackage("in.startv.hotstar", "Google Play button"); break;
                case KeyEvent.KEYCODE_PROG_RED: launchPackage("com.apple.atve.androidtv.appletv", "Red button"); break;
                case KeyEvent.KEYCODE_PROG_GREEN: launchPackage("com.amazon.amazonvideo.livingroom", "Green button"); break;
                case KeyEvent.KEYCODE_PROG_YELLOW: launchPackage("com.stremio.one", "Yellow button"); break;
                default: launchComponent("in.sid.wheretowatch", "in.sid.wheretowatch.MainActivity", "Blue button");
            }
        }
        return true; // swallow DOWN and UP so the OEM handler never sees the key
    }

    private void launchPackage(String pkg, String what) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        start(i, what, pkg);
    }

    private void launchComponent(String pkg, String cls, String what) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName(pkg, cls));
        start(i, what, pkg);
    }

    private void start(Intent i, String what, String pkg) {
        if (i == null) { Log.w(TAG, what + ": target not installed: " + pkg); return; }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            Log.i(TAG, what + " -> launched " + pkg);
        } catch (Exception e) {
            Log.w(TAG, what + ": failed to launch " + pkg + ": " + e);
        }
    }
}

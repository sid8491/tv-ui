package in.sid.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private HomeView home;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        U.init(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        home = new HomeView(this, new Prefs(this), new Apps(this));
        setContentView(home);
        askPermissions();
    }

    private void askPermissions() {
        List<String> need = new ArrayList<>();
        for (String p : new String[]{"android.permission.READ_EXTERNAL_STORAGE", "android.permission.READ_TV_LISTINGS"})
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) need.add(p);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (home != null) { home.loadPhotos(); home.loadWatchNext(); }
    }

    @Override protected void onResume() {
        super.onResume();
        if (home != null) home.onResume();
    }

    @Override protected void onDestroy() {
        if (home != null) home.stop();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (home != null) home.closeOverlay();
    }

    private boolean swallowUp;

    /** All remote keys are handled here, before any child view can claim them (the home screen has one focus model of its own). */
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean oneShot = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_BACK;
            if (event.getRepeatCount() > 0 && oneShot) { swallowUp = true; return true; }
            if (home != null && home.onKey(code)) { swallowUp = true; return true; }
            swallowUp = false;
        } else if (event.getAction() == KeyEvent.ACTION_UP && swallowUp) {
            swallowUp = false;
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        // the home screen stays put
    }
}

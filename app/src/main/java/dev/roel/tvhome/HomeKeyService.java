package dev.roel.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * This TV's firmware ignores the chosen default Home app, so we catch the Home key ourselves
 * and also jump back whenever the stock launcher's window shows up.
 */
public class HomeKeyService extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private List<String> stockHomes = new ArrayList<>();

    /** Every other app that calls itself a Home screen (the stock launcher, TV Launcher...). */
    static List<String> otherHomes(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<String> out = new ArrayList<>();
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, 0)) {
            String p = r.activityInfo.packageName;
            if (!p.equals(c.getPackageName()) && !out.contains(p)) out.add(p);
        }
        return out;
    }

    static void stopInBackground(Context c, List<String> pkgs) {
        ActivityManager am = (ActivityManager) c.getSystemService(ACTIVITY_SERVICE);
        for (String p : pkgs) am.killBackgroundProcesses(p);
    }

    @Override
    protected void onServiceConnected() {
        stockHomes = otherHomes(this);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_UP) goHome();
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        if (stockHomes.contains(e.getPackageName().toString())) goHome();
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        // Give the stock launcher time to go to the background before stopping it.
        handler.postDelayed(() -> stopInBackground(this, stockHomes), 2000);
    }

    @Override
    public void onInterrupt() {}
}

package dev.roel.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;

/**
 * This TV's firmware ignores the chosen default Home app and always opens its own launcher on Home.
 * It also makes Android hold back our launch ("Activity start request ... stopped") until the stock
 * launcher has appeared, about 2.5 s. So on Home we cover the screen at once with a picture of Zero Home,
 * start Zero Home (it comes up when allowed), and lift the cover when it is really on screen.
 */
public class HomeKeyService extends AccessibilityService {
    /** Last picture of the Zero Home screen, taken by MainActivity when it goes to the background. */
    static Bitmap snapshot;
    private static HomeKeyService running;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hideCover;
    private List<String> stockHomes = new ArrayList<>();
    private View cover;

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

    /** Home screen activities by class name: a package like Settings can own a hidden Home screen too. */
    static List<String> homeClasses(Context c) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<String> out = new ArrayList<>();
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(home, 0)) {
            if (!r.activityInfo.packageName.equals(c.getPackageName())) out.add(r.activityInfo.name);
        }
        return out;
    }

    static void stopInBackground(Context c, List<String> pkgs) {
        ActivityManager am = (ActivityManager) c.getSystemService(ACTIVITY_SERVICE);
        for (String p : pkgs) am.killBackgroundProcesses(p);
    }

    /** Called by MainActivity when it gains or loses the screen. */
    static void homeFocus(boolean focused) {
        homeFocused = focused;
        if (running != null && focused && !running.holdCover) running.liftCoverSoon();
    }

    private static boolean homeFocused;
    // True from the Home press until the firmware's own launcher has come and gone (or clearly won't come).
    private boolean holdCover;
    private final Runnable releaseHold = () -> {
        holdCover = false;
        if (homeFocused) liftCoverSoon();
    };

    private void liftCoverSoon() {
        handler.removeCallbacks(hide);
        handler.postDelayed(hide, 150);
    }

    @Override
    protected void onServiceConnected() {
        running = this;
        stockHomes = homeClasses(this);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        hideCover();
        running = null;
        return super.onUnbind(intent);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_HOME) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            holdCover = true;
            handler.removeCallbacks(releaseHold);
            showCover();
            goHome();
        } else if (e.getAction() == KeyEvent.ACTION_UP) {
            // The firmware opens its launcher right after the release. If none shows by then, stop waiting.
            handler.postDelayed(releaseHold, 1500);
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        // Match the screen, not the package: Settings is a package that also has a hidden Home screen.
        if (e.getClassName() != null && stockHomes.contains(e.getClassName().toString())) {
            // Firmware launcher is up (under the cover). Ours returns when Android allows it; lift then.
            handler.removeCallbacks(releaseHold);
            holdCover = false;
            goHome();
        }
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }

    private void showCover() {
        if (cover != null) return;
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.FIT_XY);
        if (snapshot != null) v.setImageBitmap(snapshot);
        else v.setBackgroundColor(0xFF14151A);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN,
                PixelFormat.OPAQUE);
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).addView(v, lp);
            cover = v;
            // Never leave the screen covered if Zero Home fails to come up.
            handler.removeCallbacks(hide);
            handler.postDelayed(hide, 9000); // Android's hold on our launch can last about 5 s after the release
        } catch (RuntimeException ignored) {
            // No cover is fine; Home still works, just with the stock screen showing first.
        }
    }

    private void hideCover() {
        if (cover == null) return;
        try {
            ((WindowManager) getSystemService(WINDOW_SERVICE)).removeView(cover);
        } catch (RuntimeException ignored) {
        }
        cover = null;
    }

    @Override
    public void onInterrupt() {}
}

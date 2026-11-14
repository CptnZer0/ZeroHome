package dev.roel.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = 0xFF14151A, PANEL = 0xFF1E2028, TILE = 0xFF262933,
            TEXT = 0xFFF2F2F2, MUTED = 0xFF9AA0A6, FOCUS_BG = 0xFF3A3D48;

    // This TV's own screens (CultraView / HiSilicon firmware). Missing ones just show a message.
    private static final ComponentName TV_INPUT = new ComponentName("com.hisilicon.tvui", "com.hisilicon.tvui.play.MainActivity");
    private static final ComponentName ACCOUNT = new ComponentName("com.cultraview.hictvsetting", "com.cultraview.hictvsetting.activity.GoogleAccountManagerActivity");

    private static final String KEEP_RUNNING = "com.spotify.tv.android";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ResolveInfo> apps = new HashMap<>();
    private List<String> order = new ArrayList<>();
    private SharedPreferences prefs;
    private Typeface regular, medium;
    private LinearLayout row;
    private ImageView heroImage;
    private TextView heroName, ramText;
    private String focusedPkg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        regular = getResources().getFont(R.font.poppins_regular);
        medium = getResources().getFont(R.font.poppins_medium);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        updateRam();
        handler.postDelayed(() -> {
            if (prefs.getBoolean("autoClean", true)) clearCached();
            else HomeKeyService.stopInBackground(this, HomeKeyService.otherHomes(this));
            updateRam();
        }, 4000); // the app you just left needs a few seconds to count as "background"
    }

    @Override
    public void onBackPressed() {} // Home has nowhere to go back to.

    // ---------- layout ----------

    private View buildScreen() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        page.setPadding(dp(48), dp(28), dp(48), dp(32));

        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(round(PANEL, 20));
        LinearLayout heroInner = new LinearLayout(this);
        heroInner.setGravity(Gravity.CENTER_VERTICAL);
        heroInner.setPadding(dp(40), dp(24), dp(40), dp(24));
        heroImage = new ImageView(this);
        heroImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heroInner.addView(heroImage, new LinearLayout.LayoutParams(dp(320), dp(180)));
        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setPadding(dp(40), 0, 0, 0);
        heroName = text("", 40, TEXT, medium);
        heroName.setMaxLines(2);
        heroText.addView(heroName);
        heroText.addView(text("OK to open. Hold OK to move or hide.", 16, MUTED, regular));
        heroInner.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(heroInner, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, 0, 1);
        heroLp.setMargins(0, dp(20), 0, dp(24));
        page.addView(hero, heroLp);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(dp(8), dp(12), dp(8), dp(12));
        row = new LinearLayout(this);
        row.setClipChildren(false);
        scroll.addView(row);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        page.setClipChildren(false);
        return page;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        style(time, 34, TEXT, medium);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 15, MUTED, regular);
        clock.addView(time);
        clock.addView(date);
        bar.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));

        View input = topButton(R.drawable.ic_input, "Input", v -> open(new Intent().setComponent(TV_INPUT)), null);
        bar.addView(input);
        bar.addView(topButton(R.drawable.ic_wifi, "Network", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)), null));
        bar.addView(topButton(R.drawable.ic_settings, "Settings", v -> open(new Intent(Settings.ACTION_SETTINGS)), v -> {
            openAdvanced();
            return true;
        }));
        bar.addView(topButton(R.drawable.ic_account, "Account", v -> open(new Intent().setComponent(ACCOUNT)), null));
        View ram = topButton(R.drawable.ic_memory, "RAM", v -> cleanRam(), v -> {
            boolean on = !prefs.getBoolean("autoClean", true);
            prefs.edit().putBoolean("autoClean", on).apply();
            Toast.makeText(this, on ? "Auto-clean on Home: ON" : "Auto-clean on Home: OFF", Toast.LENGTH_SHORT).show();
            return true;
        });
        ramText = (TextView) ((ViewGroup) ram).getChildAt(1);
        bar.addView(ram);
        // Stop focus at the ends of the bar instead of jumping into the app row.
        input.setId(View.generateViewId());
        ram.setId(View.generateViewId());
        input.setNextFocusLeftId(input.getId());
        ram.setNextFocusRightId(ram.getId());
        return bar;
    }

    private View topButton(int icon, String label, View.OnClickListener click, View.OnLongClickListener hold) {
        LinearLayout b = new LinearLayout(this);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(dp(16), dp(10), dp(18), dp(10));
        b.setFocusable(true);
        b.setBackground(round(Color.TRANSPARENT, 24));
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        b.addView(i, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView t = text(label, 15, TEXT, regular);
        t.setPadding(dp(8), 0, 0, 0);
        b.addView(t);
        b.setContentDescription(label);
        b.setOnClickListener(click);
        if (hold != null) b.setOnLongClickListener(hold);
        b.setOnFocusChangeListener((v, f) -> {
            v.setBackground(round(f ? FOCUS_BG : Color.TRANSPARENT, 24));
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------- apps ----------

    private void loadApps() {
        PackageManager pm = getPackageManager();
        List<String> skip = HomeKeyService.otherHomes(this);
        skip.add(getPackageName());
        apps.clear();
        // Leanback (TV) entries first, so TV apps use their TV banner.
        for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo r : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String p = r.activityInfo.packageName;
                if (!skip.contains(p) && !apps.containsKey(p)) apps.put(p, r);
            }
        }
        List<String> installed = new ArrayList<>(apps.keySet());
        Collections.sort(installed, (a, z) -> label(a).compareToIgnoreCase(label(z)));
        order = AppOrder.arrange(list("order"), installed, list("hidden"));
        drawRow();
    }

    private void drawRow() {
        row.removeAllViews();
        for (String p : order) row.addView(appTile(p));
        row.addView(plusTile());
        String want = focusedPkg != null && order.contains(focusedPkg) ? focusedPkg : (order.isEmpty() ? null : order.get(0));
        if (want != null) row.getChildAt(order.indexOf(want)).requestFocus();
    }

    private View appTile(String pkg) {
        FrameLayout tile = baseTile();
        ResolveInfo r = apps.get(pkg);
        Drawable banner = r.activityInfo.loadBanner(getPackageManager());
        ImageView img = new ImageView(this);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            tile.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            img.setImageDrawable(r.loadIcon(getPackageManager()));
            tile.addView(img, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        }
        tile.setContentDescription(label(pkg));
        tile.setOnClickListener(v -> openApp(pkg));
        tile.setOnLongClickListener(v -> {
            appMenu(pkg);
            return true;
        });
        tile.setOnFocusChangeListener((v, f) -> {
            focus(v, f);
            if (f) showHero(pkg);
        });
        return tile;
    }

    private View plusTile() {
        FrameLayout tile = baseTile();
        ImageView i = new ImageView(this);
        i.setImageResource(R.drawable.ic_plus);
        tile.addView(i, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        tile.setContentDescription("Hidden apps");
        tile.setOnClickListener(v -> hiddenMenu());
        tile.setOnFocusChangeListener((v, f) -> {
            focus(v, f);
            if (f) {
                focusedPkg = null;
                heroImage.setImageResource(R.drawable.ic_plus);
                heroName.setText("Hidden apps");
            }
        });
        return tile;
    }

    private FrameLayout baseTile() {
        FrameLayout tile = new FrameLayout(this);
        tile.setFocusable(true);
        tile.setClipToOutline(true);
        tile.setBackground(round(TILE, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(176), dp(99));
        lp.setMargins(dp(10), 0, dp(10), 0);
        tile.setLayoutParams(lp);
        return tile;
    }

    private void focus(View v, boolean f) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), Color.WHITE);
        ((FrameLayout) v).setForeground(f ? ring : null);
        v.animate().scaleX(f ? 1.12f : 1f).scaleY(f ? 1.12f : 1f).setDuration(140).start();
        v.setElevation(f ? dp(8) : 0);
    }

    private void showHero(String pkg) {
        focusedPkg = pkg;
        ResolveInfo r = apps.get(pkg);
        Drawable banner = r.activityInfo.loadBanner(getPackageManager());
        heroImage.setImageDrawable(banner != null ? banner : r.loadIcon(getPackageManager()));
        heroName.setText(label(pkg));
    }

    private void openApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) open(i);
    }

    private void appMenu(String pkg) {
        String[] items = {"Move left", "Move right", "Hide"};
        new AlertDialog.Builder(this).setTitle(label(pkg)).setItems(items, (d, w) -> {
            if (w == 2) {
                List<String> hidden = list("hidden");
                hidden.add(pkg);
                save("hidden", hidden);
                focusedPkg = null;
            } else {
                order = AppOrder.move(order, pkg, w == 0 ? -1 : 1);
                save("order", order);
            }
            loadApps();
        }).show();
    }

    private void hiddenMenu() {
        List<String> hidden = list("hidden");
        hidden.retainAll(apps.keySet());
        if (hidden.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[hidden.size()];
        for (int i = 0; i < names.length; i++) names[i] = label(hidden.get(i));
        new AlertDialog.Builder(this).setTitle("Show again").setItems(names, (d, w) -> {
            focusedPkg = hidden.remove(w);
            save("hidden", hidden);
            loadApps();
        }).show();
    }

    // ---------- top bar actions ----------

    private void openAdvanced() {
        // tvfactory is the only way into ADB settings on this TV; Activity Launcher reaches it.
        Intent i = getPackageManager().getLaunchIntentForPackage("com.tvfactory");
        if (i == null) i = getPackageManager().getLaunchIntentForPackage("de.szalkowski.activitylauncher");
        if (i != null) open(i);
        else Toast.makeText(this, "No advanced settings app found", Toast.LENGTH_SHORT).show();
    }

    /** Stops apps waiting in the background (never Spotify). Playing music is protected by Android anyway. */
    private void clearCached() {
        List<String> pkgs = new ArrayList<>(apps.keySet());
        pkgs.remove(KEEP_RUNNING);
        pkgs.addAll(HomeKeyService.otherHomes(this));
        HomeKeyService.stopInBackground(this, pkgs);
    }

    private void cleanRam() {
        long before = freeMb();
        clearCached();
        handler.postDelayed(() -> {
            long after = freeMb();
            updateRam();
            Toast.makeText(this, "Freed " + Math.max(0, after - before) + " MB. " + after + " MB free.", Toast.LENGTH_SHORT).show();
        }, 800);
    }

    private void updateRam() {
        ramText.setText(freeMb() + " MB");
    }

    private long freeMb() {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(m);
        return m.availMem / (1024 * 1024);
    }

    private void open(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) { // ActivityNotFound or SecurityException on firmware screens
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- small helpers ----------

    private String label(String pkg) {
        ResolveInfo r = apps.get(pkg);
        return r == null ? pkg : r.loadLabel(getPackageManager()).toString();
    }

    private List<String> list(String key) {
        String s = prefs.getString(key, "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    private void save(String key, List<String> value) {
        prefs.edit().putString(key, TextUtils.join(",", value)).apply();
    }

    private TextView text(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        style(t, sp, color, tf);
        return t;
    }

    private void style(TextView t, int sp, int color, Typeface tf) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

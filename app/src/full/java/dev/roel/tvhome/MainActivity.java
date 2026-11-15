package dev.roel.tvhome;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Zero Home Full: the focused app's banner fills the screen, with rows for Favourites, Recent, All apps and TV inputs. */
public class MainActivity extends Activity {
    private static final int BG = 0xFF14151A, TILE = 0xFF262933,
            TEXT = 0xFFF2F2F2, MUTED = 0xFF9AA0A6, SOFT = 0xD9FFFFFF, // white at 85%: reads on any glow colour
            GLASS = 0x26FFFFFF, GLASS_FOCUS = 0x59FFFFFF;

    // CultraView / HiSilicon firmware screens. Missing ones fall through to the next option.
    private static final ComponentName TV_INPUT = new ComponentName("com.hisilicon.tvui", "com.hisilicon.tvui.play.MainActivity");
    private static final ComponentName ACCOUNT = new ComponentName("com.cultraview.hictvsetting", "com.cultraview.hictvsetting.activity.GoogleAccountManagerActivity");
    private static final String VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

    private static final String KEEP_RUNNING = "com.spotify.tv.android";
    private static final long IDLE_MS = 5 * 60 * 1000;
    private static final int RECENT_MAX = 6;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ResolveInfo> apps = new HashMap<>();
    private final Random random = new Random();
    private List<String> order = new ArrayList<>(), favs = new ArrayList<>(), recent = new ArrayList<>();
    private SharedPreferences prefs;
    private Typeface regular, medium, semibold;
    private LinearLayout rows;
    private final Map<String, Integer> glowCache = new HashMap<>();
    private GradientDrawable base, glowTop, glowBottom;
    private ValueAnimator glowAnim;
    private int glowNow = Glow.FALLBACK, glowWant = Glow.FALLBACK;
    private String glowKey = "";
    private Drawable glowSource;
    private TextView heroName, heroHint, ramText, idleClock;
    private FrameLayout idle;
    private String focusKey; // "row:pkg" of the tile to focus again after a redraw
    private View firstTile;

    private final Runnable applyGlow = this::fadeGlow;
    private final Runnable goIdle = this::showIdle;
    private final Runnable moveIdle = new Runnable() {
        @Override
        public void run() {
            // Shift the clock a little every minute so it never burns into the panel.
            int w = idle.getWidth() - idleClock.getWidth(), h = idle.getHeight() - idleClock.getHeight();
            if (w > 0 && h > 0) {
                idleClock.setTranslationX(random.nextInt(w));
                idleClock.setTranslationY(random.nextInt(h));
            }
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        regular = getResources().getFont(R.font.poppins_regular);
        medium = getResources().getFont(R.font.poppins_medium);
        semibold = getResources().getFont(R.font.poppins_semibold);
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadApps();
        updateRam();
        poke();
        handler.postDelayed(() -> {
            if (prefs.getBoolean("autoClean", true)) clearCached();
            updateRam();
        }, 4000); // the app you just left needs a few seconds to count as "background"
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(goIdle);
        hideIdle();
        // Picture of this screen, shown as a cover on the next Home press (see HomeKeyService).
        View root = getWindow().getDecorView();
        if (root.getWidth() > 0) {
            Bitmap b = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.RGB_565);
            root.draw(new Canvas(b));
            HomeKeyService.snapshot = b;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        HomeKeyService.homeFocus(hasFocus);
    }

    @Override
    public void onBackPressed() {} // Home has nowhere to go back to.

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        poke();
        if (idle.getVisibility() == View.VISIBLE) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) hideIdle();
            return true; // the key that wakes the screen does nothing else
        }
        if (e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (e.getAction() == KeyEvent.ACTION_UP && f != null) f.performLongClick();
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ---------- layout ----------

    private View buildScreen() {
        FrameLayout root = new FrameLayout(this);
        // Background: a dark base tinted by the focused app's colour, a bright glow top right, a deeper one bottom left.
        int w = getResources().getDisplayMetrics().widthPixels;
        base = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {BG, BG});
        glowTop = radial(0.9f, 0f, w * 0.75f);
        glowBottom = radial(0f, 1f, w * 0.6f);
        root.setBackground(new LayerDrawable(new Drawable[] {base, glowTop, glowBottom}));
        paintGlow(glowNow);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(40), dp(24), dp(40), dp(16));
        page.addView(buildTopBar(), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout title = new LinearLayout(this);
        title.setOrientation(LinearLayout.VERTICAL);
        heroName = text("", 40, TEXT, semibold);
        heroName.setSingleLine(true);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        heroHint = text("", 15, SOFT, regular);
        title.addView(heroName);
        title.addView(heroHint);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(dp(8), dp(20), 0, dp(8));
        page.addView(title, titleLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(24));
        scroll.setClipToPadding(false);
        rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setClipChildren(false);
        scroll.addView(rows);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        idle = new FrameLayout(this);
        idle.setBackgroundColor(Color.BLACK);
        idle.setVisibility(View.GONE);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        style(clock, 96, alpha(MUTED, 0x99), medium);
        idleClock = clock;
        idle.addView(clock, new FrameLayout.LayoutParams(-2, -2));
        root.addView(idle, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }

    private GradientDrawable radial(float x, float y, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        g.setGradientCenter(x, y);
        g.setGradientRadius(radius);
        return g;
    }

    private View buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm a");
        time.setFormat24Hour("HH:mm");
        style(time, 30, TEXT, medium);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 15, SOFT, regular);
        clock.addView(time);
        clock.addView(date);
        bar.addView(clock, new LinearLayout.LayoutParams(0, -2, 1));

        View input = topButton(R.drawable.ic_input, "Input", v -> open(new Intent().setComponent(TV_INPUT), new Intent(VIEW_INPUTS), new Intent(Settings.ACTION_SETTINGS)), null);
        bar.addView(input);
        bar.addView(topButton(R.drawable.ic_wifi, "Network", v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)), null));
        bar.addView(topButton(R.drawable.ic_settings, "Settings", v -> open(new Intent(Settings.ACTION_SETTINGS)), v -> {
            openAdvanced();
            return true;
        }));
        // Only CultraView firmware has a screen to manage the signed-in account; elsewhere it lives inside Settings.
        if (getPackageManager().resolveActivity(new Intent().setComponent(ACCOUNT), 0) != null)
            bar.addView(topButton(R.drawable.ic_account, "Account", v -> open(new Intent().setComponent(ACCOUNT)), null));
        View ram = topButton(R.drawable.ic_memory, "RAM", v -> cleanRam(), v -> {
            boolean on = !prefs.getBoolean("autoClean", true);
            prefs.edit().putBoolean("autoClean", on).apply();
            Toast.makeText(this, on ? "Auto-clean on Home: ON" : "Auto-clean on Home: OFF", Toast.LENGTH_SHORT).show();
            return true;
        });
        ramText = (TextView) ((ViewGroup) ram).getChildAt(1);
        bar.addView(ram);
        // Stop focus at the ends of the bar instead of jumping into the rows.
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
            v.setBackground(round(f ? GLASS_FOCUS : GLASS, 24));
            v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(120).start();
        });
        b.setBackground(round(GLASS, 24));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(6), 0, 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    // ---------- apps and rows ----------

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
        List<String> hidden = list("hidden");
        order = AppOrder.arrange(list("order"), installed, hidden);
        favs = shown(list("favs"), hidden);
        recent = shown(list("recent"), hidden);
        drawRows();
    }

    /** Drops apps that were uninstalled or hidden. */
    private List<String> shown(List<String> pkgs, List<String> hidden) {
        List<String> out = new ArrayList<>();
        for (String p : pkgs) if (apps.containsKey(p) && !hidden.contains(p)) out.add(p);
        return out;
    }

    private void drawRows() {
        rows.removeAllViews();
        firstTile = null;
        if (!favs.isEmpty()) appRow("Favourites", "fav", favs, false);
        if (!recent.isEmpty()) appRow("Recent", "recent", recent, false);
        appRow("All apps", "all", order, true);
        inputRow();
        View want = focusKey == null ? null : rows.findViewWithTag(focusKey);
        if (want == null) want = firstTile;
        if (want != null) want.requestFocus();
    }

    private LinearLayout newRow(String title) {
        TextView t = text(title, 16, SOFT, medium);
        t.setPadding(dp(8), dp(6), 0, 0);
        rows.addView(t);
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(dp(8), dp(10), dp(8), dp(12)); // room for the focus zoom
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        scroll.addView(row);
        rows.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void appRow(String title, String rowId, List<String> pkgs, boolean withPlus) {
        LinearLayout row = newRow(title);
        for (String p : pkgs) row.addView(appTile(rowId, p));
        if (withPlus) row.addView(plusTile());
    }

    private View appTile(String rowId, String pkg) {
        FrameLayout tile = baseTile(rowId + ":" + pkg);
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
            appMenu(rowId, pkg);
            return true;
        });
        tile.setOnFocusChangeListener((v, f) -> {
            focus(v, f);
            if (!f) return;
            showTitle(label(pkg), "OK to open. Hold OK for menu.");
            setGlow(pkg, r);
        });
        return tile;
    }

    private View plusTile() {
        FrameLayout tile = baseTile("all:+");
        ImageView i = new ImageView(this);
        i.setImageResource(R.drawable.ic_plus);
        tile.addView(i, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        tile.setContentDescription("Hidden apps");
        tile.setOnClickListener(v -> hiddenMenu());
        tile.setOnFocusChangeListener((v, f) -> {
            focus(v, f);
            if (!f) return;
            showTitle("Hidden apps", "OK to show a hidden app again.");
            setGlow("", null);
        });
        return tile;
    }

    /** HDMI and AV tiles that switch straight to that input. */
    private void inputRow() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim == null) return; // not a TV with the Android TV input framework
        List<TvInputInfo> inputs = new ArrayList<>();
        for (TvInputInfo i : tim.getTvInputList()) {
            // Skip tuners, hidden inputs and CEC devices hanging off an HDMI port.
            if (i.isPassthroughInput() && !i.isHidden(this) && i.getParentId() == null) inputs.add(i);
        }
        if (inputs.isEmpty()) return;
        Collections.sort(inputs, (a, z) -> inputLabel(a).compareToIgnoreCase(inputLabel(z)));
        LinearLayout row = newRow("Inputs");
        for (TvInputInfo i : inputs) {
            String name = inputLabel(i);
            // A small pill: white with dark text when focused.
            TextView chip = text(name, 15, TEXT, medium);
            chip.setTag("input:" + i.getId());
            chip.setFocusable(true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(22), 0, dp(22), 0);
            chip.setBackground(round(GLASS, 20));
            chip.setOnClickListener(v -> open(
                    new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(i.getId())),
                    new Intent(VIEW_INPUTS), new Intent(Settings.ACTION_SETTINGS)));
            chip.setOnFocusChangeListener((v, f) -> {
                chip.setBackground(round(f ? Color.WHITE : GLASS, 20));
                chip.setTextColor(f ? BG : TEXT);
                chip.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f).setDuration(150).start();
                if (!f) return;
                focusKey = (String) v.getTag();
                showTitle(name, "OK to switch to this input.");
                setGlow("", null);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(40));
            lp.setMargins(dp(8), 0, dp(4), 0);
            row.addView(chip, lp);
        }
    }

    private String inputLabel(TvInputInfo i) {
        CharSequence custom = i.loadCustomLabel(this);
        return (TextUtils.isEmpty(custom) ? i.loadLabel(this) : custom).toString();
    }

    private FrameLayout baseTile(String key) {
        FrameLayout tile = new FrameLayout(this);
        tile.setTag(key);
        tile.setFocusable(true);
        tile.setClipToOutline(true);
        tile.setBackground(round(TILE, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(160), dp(90));
        lp.setMargins(dp(8), 0, dp(8), 0);
        tile.setLayoutParams(lp);
        tile.setOnFocusChangeListener((v, f) -> focus(v, f));
        if (firstTile == null) firstTile = tile;
        return tile;
    }

    private void focus(View v, boolean f) {
        if (f) focusKey = (String) v.getTag();
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(12));
        ring.setStroke(dp(3), Color.WHITE);
        ((FrameLayout) v).setForeground(f ? ring : null);
        v.animate().scaleX(f ? 1.10f : 1f).scaleY(f ? 1.10f : 1f).setDuration(150).start();
        v.setElevation(f ? dp(8) : 0);
    }

    private void showTitle(String name, String hint) {
        heroHint.setText(hint);
        if (name.contentEquals(heroName.getText())) return;
        heroName.setText(name);
        heroName.setAlpha(0f);
        heroName.setTranslationY(dp(8));
        heroName.animate().alpha(1f).translationY(0).setDuration(160).start();
    }

    /** Fades the background to this app's colour once focus rests for 120 ms, so fast scrolling stays smooth. */
    private void setGlow(String key, ResolveInfo r) {
        if (key.equals(glowKey)) return; // same app after a redraw: leave any pending fade running
        handler.removeCallbacks(applyGlow);
        glowKey = key;
        glowSource = null;
        if (r != null) {
            // A fresh drawable: the tile's own one must keep its bounds.
            glowSource = r.activityInfo.loadBanner(getPackageManager());
            if (glowSource == null) glowSource = r.loadIcon(getPackageManager());
        }
        handler.postDelayed(applyGlow, 120);
    }

    private void fadeGlow() {
        Integer c = glowCache.get(glowKey);
        if (c == null) {
            c = Glow.FALLBACK;
            if (glowSource != null) {
                Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
                glowSource.setBounds(0, 0, 32, 18);
                glowSource.draw(new Canvas(b));
                int[] px = new int[32 * 18];
                b.getPixels(px, 0, 32, 0, 0, 32, 18);
                b.recycle();
                c = Glow.pick(px);
            }
            glowCache.put(glowKey, c);
        }
        if (c == glowWant) return;
        glowWant = c;
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = ValueAnimator.ofArgb(glowNow, c);
        glowAnim.setDuration(400);
        glowAnim.addUpdateListener(a -> paintGlow((int) a.getAnimatedValue()));
        glowAnim.start();
    }

    private void paintGlow(int c) {
        glowNow = c;
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.max(hsv[1], 0.55f);
        hsv[2] = 0.85f;
        int top = Color.HSVToColor(hsv);
        hsv[0] = (hsv[0] + 40) % 360; // a neighbour colour, so the screen has two tones
        hsv[2] = 0.5f;
        int bottom = Color.HSVToColor(hsv);
        base.setColors(new int[] {mix(BG, top, 0.22f), BG});
        glowTop.setColors(new int[] {alpha(top, 0xB0), alpha(top, 0)});
        glowBottom.setColors(new int[] {alpha(bottom, 0x90), alpha(bottom, 0)});
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t);
        int g = Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t);
        int bl = Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t);
        return Color.rgb(r, g, bl);
    }

    private void openApp(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        recent = AppOrder.pushRecent(recent, pkg, RECENT_MAX);
        save("recent", recent);
        open(i);
    }

    private void appMenu(String rowId, String pkg) {
        List<String> items = new ArrayList<>();
        items.add(favs.contains(pkg) ? "Remove from Favourites" : "Add to Favourites");
        if (!rowId.equals("recent")) {
            items.add("Move left");
            items.add("Move right");
        }
        items.add("Hide");
        items.add("App info");
        new AlertDialog.Builder(this).setTitle(label(pkg)).setItems(items.toArray(new String[0]), (d, w) -> {
            String pick = items.get(w);
            if (pick.equals("App info")) {
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
                return;
            }
            if (pick.startsWith("Add")) {
                favs.add(pkg);
                save("favs", favs);
            } else if (pick.startsWith("Remove")) {
                favs.remove(pkg);
                save("favs", favs);
                if (rowId.equals("fav")) focusKey = "all:" + pkg;
            } else if (pick.equals("Hide")) {
                List<String> hidden = list("hidden");
                hidden.add(pkg);
                save("hidden", hidden);
                focusKey = null;
            } else if (rowId.equals("fav")) {
                favs = AppOrder.move(favs, pkg, pick.equals("Move left") ? -1 : 1);
                save("favs", favs);
            } else {
                order = AppOrder.move(order, pkg, pick.equals("Move left") ? -1 : 1);
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
            focusKey = "all:" + hidden.remove(w);
            save("hidden", hidden);
            loadApps();
        }).show();
    }

    // ---------- idle clock ----------

    private void poke() {
        handler.removeCallbacks(goIdle);
        handler.postDelayed(goIdle, IDLE_MS);
    }

    private void showIdle() {
        idle.setAlpha(0f);
        idle.setVisibility(View.VISIBLE);
        idle.animate().alpha(1f).setDuration(600).start();
        handler.post(moveIdle);
    }

    private void hideIdle() {
        handler.removeCallbacks(moveIdle);
        idle.animate().cancel();
        idle.setVisibility(View.GONE);
    }

    // ---------- top bar actions ----------

    private void openAdvanced() {
        // tvfactory is the only way into ADB settings on CultraView TVs; Activity Launcher reaches it.
        Intent i = getPackageManager().getLaunchIntentForPackage("com.tvfactory");
        if (i == null) i = getPackageManager().getLaunchIntentForPackage("de.szalkowski.activitylauncher");
        if (i != null) open(i);
        else Toast.makeText(this, "No advanced settings app found", Toast.LENGTH_SHORT).show();
    }

    /** Stops apps waiting in the background (never Spotify). The stock launcher is not in `apps`, so it stays loaded. */
    private void clearCached() {
        List<String> pkgs = new ArrayList<>(apps.keySet());
        pkgs.remove(KEEP_RUNNING);
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

    /** Tries each intent in turn: the first one this TV can open wins (screens differ per brand). */
    private void open(Intent... options) {
        for (Intent i : options) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception e) { // ActivityNotFound or SecurityException on firmware screens
            }
        }
        Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
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

    private static int alpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

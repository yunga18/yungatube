package com.yunga.yungatube;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;
import org.json.JSONObject;

final class Ui {
    /** Page background. */
    static final int BG = Color.WHITE;
    /** Light surfaces: chips, search box, description. */
    static final int PANEL = Color.rgb(242,242,242);
    static final int SOFT = Color.rgb(229,229,229);
    /** Primary text (historical name). */
    static final int WHITE = Color.rgb(15,15,15);
    static final int MUTED = Color.rgb(96,96,96);
    /** YungaTube green accent (historical name). */
    static final int RED = Color.rgb(22,163,74);
    static final int LIVE = Color.rgb(204,0,0);
    static final int DIVIDER = Color.rgb(230,230,230);

    private Ui() {}

    static int dp(Context c, float n) {
        return (int)(n * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static LinearLayout vertical(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    static GradientDrawable background(int color, int radius, Context c) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(c, radius));
        return bg;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        return bg;
    }

    static TextView text(Context c, String message, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(message);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    /** Rounded chip: dark when selected, light grey otherwise. */
    static TextView pill(Context c, String message, boolean active) {
        TextView t = text(c, message, 14, active ? Color.WHITE : WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c,14), dp(c,8), dp(c,14), dp(c,8));
        t.setBackground(background(active ? WHITE : PANEL, 8, c));
        return t;
    }

    /** Round action button used under the player. */
    static TextView action(Context c, String message) {
        TextView t = text(c, message, 13, WHITE, true);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c,14), 0, dp(c,14), 0);
        t.setBackground(background(PANEL, 18, c));
        return t;
    }

    /** Flat text icon for app bars. */
    static TextView icon(Context c, String glyph, String description) {
        TextView t = text(c, glyph, 22, WHITE, false);
        t.setGravity(Gravity.CENTER);
        t.setContentDescription(description);
        return t;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        return v;
    }

    static LinearLayout.LayoutParams lp(Context c, int w, int h) {
        return new LinearLayout.LayoutParams(
            w < 0 ? w : dp(c,w),
            h < 0 ? h : dp(c,h)
        );
    }

    static void insets(Activity a, View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(),
                insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(),
                insets.getSystemWindowInsetBottom());
            return insets;
        });
        root.requestApplyInsets();
    }

    static void lightBars(Activity a) {
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        a.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    /** Installed versionName, so screens never show a stale hard-coded number. */
    static String version(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName; }
        catch (Exception e) { return ""; }
    }

    static String id(JSONObject json) {
        String id = json.optString("videoId", "");
        return id.matches("[A-Za-z0-9_-]{11}") ? id : "";
    }

    static String duration(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d",h,m,s)
            : String.format(Locale.US, "%d:%02d",m,s);
    }

    /** Spanish compact number: 950, 1,2 mil, 35 mil, 1,2 M, 3 mil M. */
    static String count(long n) {
        if (n < 1000) return Long.toString(Math.max(0,n));
        if (n < 1_000_000) return decimal(n / 1000.0) + " mil";
        if (n < 1_000_000_000) return decimal(n / 1_000_000.0) + " M";
        return decimal(n / 1_000_000_000.0) + " mil M";
    }

    private static String decimal(double value) {
        if (value >= 10) return Long.toString((long) value);
        long tenths = (long) (value * 10);
        return tenths % 10 == 0 ? Long.toString(tenths / 10)
            : (tenths / 10) + "," + (tenths % 10);
    }

    static String views(long n) {
        if (n < 0) return "";
        if (n == 1) return "1 vista";
        return count(n) + (n >= 1_000_000 ? " de vistas" : " vistas");
    }

    static boolean live(JSONObject video) {
        return video.optBoolean("liveNow", false);
    }

    /** "Canal · 1,2 M de vistas · hace 2 días" */
    static String meta(JSONObject video, boolean withChannel) {
        StringBuilder sb = new StringBuilder();
        if (withChannel) sb.append(video.optString("author", "Canal"));
        String v = video.has("viewCount") ? views(video.optLong("viewCount", -1)) : "";
        if (!v.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(live(video) ? count(video.optLong("viewCount")) + " espectadores" : v);
        }
        String when = video.optString("publishedText", "");
        if (!when.isEmpty() && !live(video)) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(when);
        }
        return sb.toString();
    }

    static String compact(JSONObject video) {
        return meta(video, true);
    }

    static String initial(String source) {
        if (source == null || source.isEmpty()) return "Y";
        return source.substring(0,1).toUpperCase(Locale.ROOT);
    }
}

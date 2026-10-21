package com.yunga.yungatube;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

final class Ui {
    static final int BG = Color.rgb(16,17,22);
    static final int PANEL = Color.rgb(28,30,38);
    static final int SOFT = Color.rgb(38,40,49);
    static final int WHITE = Color.rgb(247,247,249);
    static final int MUTED = Color.rgb(169,172,185);
    static final int RED = Color.rgb(250,59,83);

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

    static TextView text(Context c, String message, int size, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(message);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setFontFeatureSettings("kern");
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    static TextView pill(Context c, String message, boolean active) {
        TextView t = text(c, message, 13, active ? WHITE : MUTED, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c,16), dp(c,10), dp(c,16), dp(c,10));
        t.setBackground(background(active ? RED : SOFT, 50, c));
        return t;
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

    static String id(JSONObject json) {
        String id = json.optString("videoId", "");
        return id.matches("[A-Za-z0-9_-]{11}") ? id : "";
    }

    static String duration(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d",h,m,s)
            : String.format(java.util.Locale.US, "%d:%02d",m,s);
    }

    static String compact(JSONObject video) {
        String channel = video.optString("author", "Canal desconocido");
        int seconds = video.optInt("lengthSeconds",0);
        String time = seconds > 0 ? "  ·  " + duration(seconds) : "";
        return channel + time;
    }
}

package com.divinedoll.launcher.utils;

import android.graphics.Color;

import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Colors the user can change in Appearance. The play button and the lines have their own classes. */
public class ThemeColors {
    public static final String PREF_BACKGROUND = "bg_color";
    public static final String PREF_SIDEBAR = "sidebar_color";
    public static final String PREF_TEXT = "text_color";
    public static final int DEFAULT_TEXT = 0xFFFFFFFF;
    public static final int DEFAULT_BACKGROUND = 0xFF1E1E1E;
    public static final int DEFAULT_SIDEBAR = 0xFF262626;

    private static int get(String key, int def) {
        try {
            return LauncherPreferences.DEFAULT_PREF.getInt(key, def);
        } catch (Exception e) {
            return def;
        }
    }

    public static int background() {
        return get(PREF_BACKGROUND, DEFAULT_BACKGROUND);
    }

    public static int text() {
        return get(PREF_TEXT, DEFAULT_TEXT) | 0xFF000000;
    }

    public static int sidebar() {
        return get(PREF_SIDEBAR, DEFAULT_SIDEBAR);
    }

    /** Panels and cards are a bit lighter than the background. */
    public static int panel() {
        return lighten(background(), 0.06f);
    }

    private static int lighten(int color, float amount) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        return Color.argb(255, Math.min(255, Math.round(r + (255 - r) * amount)),
                Math.min(255, Math.round(g + (255 - g) * amount)), Math.min(255, Math.round(b + (255 - b) * amount)));
    }

    public static void set(String key, int color) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt(key, color).apply();
    }

    public static void resetAll() {
        LauncherPreferences.DEFAULT_PREF.edit()
                .remove(PREF_BACKGROUND).remove(PREF_SIDEBAR).remove(PREF_TEXT)
                .remove(AccentColor.PREF).remove(PlayColor.PREF).apply();
    }
}

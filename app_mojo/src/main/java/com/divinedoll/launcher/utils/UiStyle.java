package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Look of buttons and cards: pixel blocks (default) or flat rounded shapes. */
public final class UiStyle {
    private UiStyle() {}

    public static final String PREF = "ui_style_rounded";

    public static boolean rounded() {
        return LauncherPreferences.DEFAULT_PREF != null && LauncherPreferences.DEFAULT_PREF.getBoolean(PREF, false);
    }

    public static void setRounded(boolean value) {
        LauncherPreferences.DEFAULT_PREF.edit().putBoolean(PREF, value).apply();
    }

    private static int shade(int color, float f) {
        return Color.argb(255, Math.min(255, Math.round(Color.red(color) * f)),
                Math.min(255, Math.round(Color.green(color) * f)), Math.min(255, Math.round(Color.blue(color) * f)));
    }

    private static GradientDrawable shape(Context c, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(ForkUi.dp(c, 10));
        g.setStroke(ForkUi.dp(c, 1), stroke);
        return g;
    }

    /** Flat rounded button: normal, pressed and disabled states. */
    public static Drawable button(Context c, int base) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{-android.R.attr.state_enabled}, shape(c, 0xFF2A2A2A, 0xFF3A3A3A));
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, shade(base, 0.75f), shade(base, 1.2f)));
        s.addState(new int[]{}, shape(c, base, shade(base, 1.3f)));
        return s;
    }
}

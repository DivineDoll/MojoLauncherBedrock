package com.divinedoll.launcher.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.divinedoll.launcher.LauncherActivity;
import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/**
 * One organised screen for everything about the look of the launcher: ready-made looks, colors,
 * button style, launcher name, banner, interface options and theme files.
 */
public final class AppearancePanel {
    private AppearancePanel() {}

    public interface Hooks {
        void pickBanner();
        void refreshBanner();
        void refreshPlay();
        void pickThemeFile();
        void recreate();
    }

    public static final String PREF_NAME = "launcher_name";

    public static String launcherName(Context c) {
        String name = LauncherPreferences.DEFAULT_PREF.getString(PREF_NAME, "");
        return name == null || name.trim().isEmpty() ? c.getString(R.string.fork_banner_title) : name;
    }

    // accent, background, sidebar, play button
    private static final int[][] LOOKS = {
            {0xFF8B4FD6, 0xFF1A1526, 0xFF211B30, 0xFF8B4FD6},
            {0xFF2E6FD1, 0xFF111C2B, 0xFF16263A, 0xFF2E6FD1},
            {0xFF52A535, ThemeColors.DEFAULT_BACKGROUND, ThemeColors.DEFAULT_SIDEBAR, 0xFF3C8527},
            {0xFFD14B2E, 0xFF291618, 0xFF331D1F, 0xFFD14B2E},
            {0xFFE0A526, 0xFF241F11, 0xFF2D2716, 0xFFE0A526},
            {0xFF2AA8B5, 0xFF10252A, 0xFF16303A, 0xFF2AA8B5}};
    private static final int[] LOOK_NAMES = {R.string.fork_look_violet, R.string.fork_look_ocean, R.string.fork_look_emerald,
            R.string.fork_look_ruby, R.string.fork_look_amber, R.string.fork_look_teal};

    private static TextView section(Context c, int title) {
        TextView t = ForkUi.text(c, c.getString(title), 12, true);
        t.setTextColor(AccentColor.light());
        t.setAllCaps(true);
        t.setPadding(0, ForkUi.dp(c, 14), 0, ForkUi.dp(c, 6));
        return t;
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(2, 0x88FFFFFF);
        return g;
    }

    /** A tappable row: optional color dot, label, optional value on the right. */
    private static LinearLayout row(Context c, CharSequence label, CharSequence value, int dot, Runnable action) {
        LinearLayout r = ForkUi.row(c);
        int pad = ForkUi.dp(c, 10);
        r.setPadding(pad, pad, pad, pad);
        r.setBackground(ForkUi.box(c, 0x33000000, 0x44FFFFFF, 1));
        if(dot != 0) {
            View d = new View(c);
            d.setBackground(circle(dot));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ForkUi.dp(c, 20), ForkUi.dp(c, 20));
            lp.rightMargin = ForkUi.dp(c, 10);
            r.addView(d, lp);
        }
        r.addView(ForkUi.text(c, label, 14, false), ForkUi.weight(1f));
        if(value != null) {
            TextView v = ForkUi.text(c, value, 13, false);
            v.setTextColor(AccentColor.light());
            r.addView(v);
        }
        r.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = ForkUi.dp(c, 5);
        r.setLayoutParams(lp);
        return r;
    }

    private static void applyLook(int[] look, Hooks hooks) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putInt(AccentColor.PREF, look[0])
                .putInt(PlayColor.PREF, look[3])
                .apply();
        ThemeColors.set(ThemeColors.PREF_BACKGROUND, look[1]);
        ThemeColors.set(ThemeColors.PREF_SIDEBAR, look[2]);
        hooks.recreate();
    }

    private static void renameDialog(Context c, Hooks hooks) {
        EditText input = new EditText(c);
        input.setSingleLine(true);
        input.setText(launcherName(c));
        input.setHint(R.string.fork_ap_name_hint);
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_ap_name)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_NAME, input.getText().toString().trim()).apply();
                    hooks.recreate();
                })
                .show();
    }

    public static void show(Activity act, Hooks hooks) {
        Context c = act;
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = ForkUi.dp(c, 14);
        box.setPadding(pad, 0, pad, pad);

        // ---- quick looks
        box.addView(section(c, R.string.fork_ap_looks));
        LinearLayout looks = ForkUi.row(c);
        for(int i = 0; i < LOOKS.length; i++) {
            final int[] look = LOOKS[i];
            LinearLayout chip = new LinearLayout(c);
            chip.setOrientation(LinearLayout.VERTICAL);
            chip.setGravity(Gravity.CENTER_HORIZONTAL);
            chip.setPadding(ForkUi.dp(c, 6), ForkUi.dp(c, 4), ForkUi.dp(c, 6), ForkUi.dp(c, 4));
            View swatch = new View(c);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{look[1], look[0]});
            g.setCornerRadius(ForkUi.dp(c, 10));
            g.setStroke(ForkUi.dp(c, 1), 0x88FFFFFF);
            swatch.setBackground(g);
            chip.addView(swatch, new LinearLayout.LayoutParams(ForkUi.dp(c, 52), ForkUi.dp(c, 52)));
            TextView name = ForkUi.text(c, c.getString(LOOK_NAMES[i]), 10, false);
            name.setGravity(Gravity.CENTER);
            chip.addView(name);
            chip.setOnClickListener(v -> applyLook(look, hooks));
            looks.addView(chip);
        }
        HorizontalScrollView looksScroll = new HorizontalScrollView(c);
        looksScroll.setHorizontalScrollBarEnabled(false);
        looksScroll.addView(looks);
        box.addView(looksScroll);

        // ---- colors
        box.addView(section(c, R.string.fork_ap_colors));
        box.addView(row(c, c.getString(R.string.fork_accent_title), null, AccentColor.get(), () -> AccentColor.choose(c, hooks::recreate)));
        box.addView(row(c, c.getString(R.string.fork_play_color_title), null, PlayColor.get(), () -> PlayColor.choose(c, hooks::refreshPlay)));
        box.addView(row(c, c.getString(R.string.fork_bg_color_title), null, ThemeColors.background(), () ->
                ColorPicker.show(c, R.string.fork_bg_color_title, ThemeColors.background(), ThemeColors.DEFAULT_BACKGROUND,
                        color -> { ThemeColors.set(ThemeColors.PREF_BACKGROUND, color); hooks.recreate(); })));
        box.addView(row(c, c.getString(R.string.fork_sidebar_color_title), null, ThemeColors.sidebar(), () ->
                ColorPicker.show(c, R.string.fork_sidebar_color_title, ThemeColors.sidebar(), ThemeColors.DEFAULT_SIDEBAR,
                        color -> { ThemeColors.set(ThemeColors.PREF_SIDEBAR, color); hooks.recreate(); })));
        box.addView(row(c, c.getString(R.string.fork_text_color_title), null, ThemeColors.text(), () ->
                ColorPicker.show(c, R.string.fork_text_color_title, ThemeColors.text(), ThemeColors.DEFAULT_TEXT,
                        color -> { ThemeColors.set(ThemeColors.PREF_TEXT, color); hooks.recreate(); })));

        // ---- style
        box.addView(section(c, R.string.fork_ap_style));
        box.addView(row(c, c.getString(R.string.fork_ap_style_row),
                c.getString(UiStyle.rounded() ? R.string.fork_ap_style_rounded : R.string.fork_ap_style_blocks), 0, () -> {
                    UiStyle.setRounded(!UiStyle.rounded());
                    hooks.recreate();
                }));

        // ---- launcher
        box.addView(section(c, R.string.fork_ap_launcher));
        box.addView(row(c, c.getString(R.string.fork_ap_name), launcherName(c), 0, () -> renameDialog(c, hooks)));
        box.addView(row(c, c.getString(R.string.fork_banner_pick_title), null, 0, hooks::pickBanner));
        box.addView(row(c, c.getString(R.string.fork_pixel_menu), null, 0, () -> PixelArtEditor.show(c)));

        // ---- interface
        box.addView(section(c, R.string.fork_ap_interface));
        box.addView(row(c, c.getString(R.string.fork_fullscreen),
                c.getString(LauncherActivity.fullscreenUi() ? R.string.fork_on : R.string.fork_off), 0, () -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean(LauncherActivity.PREF_FULLSCREEN_UI, !LauncherActivity.fullscreenUi()).apply();
                    hooks.recreate();
                }));
        box.addView(row(c, c.getString(R.string.fork_lang_title), null, 0, () -> LanguagePicker.show(c, hooks::recreate)));
        box.addView(row(c, c.getString(R.string.fork_pad_title), null, 0, () -> GamepadLayout.show(c)));

        // ---- themes and backup
        box.addView(section(c, R.string.fork_ap_more));
        box.addView(row(c, c.getString(R.string.fork_theme_menu), null, 0, () -> ThemeManager.show(c, hooks::recreate, hooks::pickThemeFile)));
        box.addView(row(c, c.getString(R.string.fork_extra_menu), null, 0, () -> ExtraOptions.show(c, hooks::refreshBanner)));
        box.addView(row(c, c.getString(R.string.fork_colors_reset_all), null, 0, () -> {
            ThemeColors.resetAll();
            hooks.recreate();
        }));

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_appearance_title)
                .setView(scroll)
                .setPositiveButton(R.string.fork_ap_close, null)
                .show();
    }
}

package com.divinedoll.launcher.utils;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Lets the user choose the launcher language (the system one by default). */
public final class LanguagePicker {
    private LanguagePicker() {}

    private static final String[] TAGS = {"", "en", "es", "pt", "pt-BR", "fr", "de", "it", "ru", "uk", "zh-CN", "zh-TW", "ja", "ko", "ar", "tr",
            "pl", "nl", "vi", "id", "hi", "th", "cs", "sv", "ro", "hu", "el", "fi", "da", "no", "he"};

    private static String labelOf(Context c, String tag) {
        if(tag.isEmpty()) return c.getString(R.string.fork_lang_system);
        Locale l = Locale.forLanguageTag(tag);
        String name = l.getDisplayName(l);
        if(name.isEmpty()) return tag;
        return name.substring(0, 1).toUpperCase(l) + name.substring(1);
    }

    public static void show(Context c, Runnable recreate) {
        String current = LauncherPreferences.DEFAULT_PREF.getString(LocaleUtils.PREF_APP_LANGUAGE, "");
        String[] labels = new String[TAGS.length];
        int checked = 0;
        for(int i = 0; i < TAGS.length; i++) {
            labels[i] = labelOf(c, TAGS[i]);
            if(TAGS[i].equals(current)) checked = i;
        }
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_lang_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString(LocaleUtils.PREF_APP_LANGUAGE, TAGS[which])
                            .putBoolean("force_english", false)
                            .apply();
                    LauncherPreferences.PREF_FORCE_ENGLISH = false;
                    d.dismiss();
                    recreate.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

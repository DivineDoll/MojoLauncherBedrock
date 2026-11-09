package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/**
 * Saved looks of the launcher: accent, Play button, background and sidebar colors.
 * There are a few built in themes, and the user can save the current one, export it as a
 * small .json file and import one made by someone else.
 */
public class ThemeManager {
    private static final String[] KEYS = {AccentColor.PREF, PlayColor.PREF, ThemeColors.PREF_BACKGROUND, ThemeColors.PREF_SIDEBAR, ThemeColors.PREF_TEXT};

    private static final int[][] PRESETS = {
            {0xFF52A535, 0xFF3C8527, 0xFF1E1E1E, 0xFF262626, ThemeColors.DEFAULT_TEXT},
            {0xFFE04646, 0xFFD14B2E, 0xFF2A1313, 0xFF331A1A, ThemeColors.DEFAULT_TEXT},
            {0xFF9B59E8, 0xFF8B4FD6, 0xFF16121F, 0xFF1E1830, ThemeColors.DEFAULT_TEXT},
            {0xFF29B6C5, 0xFF2E6FD1, 0xFF0F1B26, 0xFF14222F, ThemeColors.DEFAULT_TEXT},
            {0xFFE8B02E, 0xFFE0A526, 0xFF241E10, 0xFF2E2716, ThemeColors.DEFAULT_TEXT}
    };
    private static final int[] PRESET_NAMES = {R.string.fork_theme_classic, R.string.fork_theme_nether,
            R.string.fork_theme_end, R.string.fork_theme_ocean, R.string.fork_theme_desert};

    public static File directory(Context c) {
        return dir(c);
    }

    public static String currentAsJson() throws Exception {
        return toJson(current()).toString(2);
    }

    public static void applyJson(String text) throws Exception {
        apply(fromJson(text));
    }

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "themes");
        if(!d.isDirectory()) d.mkdirs();
        return d;
    }

    private static int[] current() {
        int[] values = new int[KEYS.length];
        int[] defaults = {AccentColor.DEFAULT, PlayColor.get(), ThemeColors.DEFAULT_BACKGROUND, ThemeColors.DEFAULT_SIDEBAR, ThemeColors.DEFAULT_TEXT};
        for(int i = 0; i < KEYS.length; i++) values[i] = LauncherPreferences.DEFAULT_PREF.getInt(KEYS[i], defaults[i]);
        return values;
    }

    private static void apply(int[] values) {
        android.content.SharedPreferences.Editor edit = LauncherPreferences.DEFAULT_PREF.edit();
        for(int i = 0; i < KEYS.length && i < values.length; i++) edit.putInt(KEYS[i], values[i] | 0xFF000000);
        edit.apply();
    }

    private static String cleanName(String raw) {
        String n = raw.trim().replaceAll("[^A-Za-z0-9 _-]", "_");
        if(n.length() > 30) n = n.substring(0, 30);
        return n.trim().isEmpty() ? "theme" : n.trim();
    }

    private static JSONObject toJson(int[] v) throws Exception {
        JSONObject o = new JSONObject();
        for(int i = 0; i < KEYS.length; i++) o.put(KEYS[i], String.format("#%06X", v[i] & 0xFFFFFF));
        return o;
    }

    private static int[] fromJson(String text) throws Exception {
        JSONObject o = new JSONObject(text);
        int[] v = current();
        boolean any = false;
        for(int i = 0; i < KEYS.length; i++) {
            if(!o.has(KEYS[i])) continue;
            v[i] = Color.parseColor(o.getString(KEYS[i]));
            any = true;
        }
        if(!any) throw new IllegalArgumentException("No theme colors");
        return v;
    }

    private static File[] saved(Context c) {
        File[] files = dir(c).listFiles((d, n) -> n.endsWith(".json"));
        if(files == null) return new File[0];
        java.util.Arrays.sort(files);
        return files;
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while((n = in.read(buf)) > 0 && out.size() < 65536) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** Reads a theme file or a full backup picked by the user and applies it. */
    public static boolean importFrom(Context c, Uri uri) {
        try(InputStream raw = c.getContentResolver().openInputStream(uri)) {
            if(raw == null) return false;
            java.io.BufferedInputStream in = new java.io.BufferedInputStream(raw);
            in.mark(4);
            boolean zip = in.read() == 'P' && in.read() == 'K';
            in.reset();
            if(zip) {
                BackupManager.restore(c, in);
                return true;
            }
            int[] values = fromJson(read(in));
            String name = "imported";
            try(android.database.Cursor cur = c.getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if(cur != null && cur.moveToFirst() && cur.getString(0) != null) name = cur.getString(0).replaceAll("\\.json$", "");
            }
            saveAs(c, cleanName(name), values);
            apply(values);
            return true;
        } catch(Exception e) {
            return false;
        }
    }

    private static void saveAs(Context c, String name, int[] values) throws Exception {
        try(FileOutputStream out = new FileOutputStream(new File(dir(c), name + ".json"))) {
            out.write(toJson(values).toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Shows the list of themes. recreate runs after a theme is applied, pickFile opens the
     * file chooser of the caller so a theme file can be imported.
     */
    public static void show(Context c, Runnable recreate, Runnable pickFile) {
        File[] files = saved(c);
        List<String> names = new ArrayList<>();
        for(int id : PRESET_NAMES) names.add(c.getString(id));
        for(File f : files) names.add(f.getName().replace(".json", ""));
        names.add(c.getString(R.string.fork_backup_export));
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_theme_menu)
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if(which < PRESETS.length) {
                        apply(PRESETS[which]);
                        recreate.run();
                    } else if(which == names.size() - 1) {
                        exportBackup(c);
                    } else {
                        savedMenu(c, files[which - PRESETS.length], recreate);
                    }
                })
                .setPositiveButton(R.string.fork_theme_save, (d, w) -> askName(c))
                .setNeutralButton(R.string.fork_theme_import, (d, w) -> pickFile.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void exportBackup(Context c) {
        com.divinedoll.launcher.MojoApplication.sExecutorService.execute(() -> {
            String result;
            try {
                result = c.getString(R.string.fork_backup_done, BackupManager.export(c));
            } catch(Exception e) {
                result = c.getString(R.string.fork_theme_error);
            }
            final String text = result;
            com.divinedoll.launcher.Tools.runOnUiThread(() -> Toast.makeText(c, text, Toast.LENGTH_LONG).show());
        });
    }

    private static void askName(Context c) {
        EditText input = new EditText(c);
        input.setHint(R.string.fork_pixel_name_hint);
        input.setSingleLine();
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_theme_save)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try {
                        String name = cleanName(input.getText().toString());
                        saveAs(c, name, current());
                        Toast.makeText(c, c.getString(R.string.fork_theme_saved, name), Toast.LENGTH_SHORT).show();
                    } catch(Exception e) {
                        Toast.makeText(c, R.string.fork_theme_error, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private static void savedMenu(Context c, File file, Runnable recreate) {
        String[] items = {c.getString(R.string.fork_theme_apply), c.getString(R.string.fork_theme_export),
                c.getString(R.string.fork_theme_delete)};
        new AlertDialog.Builder(c).setTitle(file.getName().replace(".json", "")).setItems(items, (d, which) -> {
            try {
                if(which == 0) {
                    try(InputStream in = new java.io.FileInputStream(file)) {
                        apply(fromJson(read(in)));
                    }
                    recreate.run();
                } else if(which == 1) {
                    String name = OnlineSkins.exportToDownloads(c, file, "application/json");
                    Toast.makeText(c, c.getString(R.string.fork_theme_exported, name), Toast.LENGTH_LONG).show();
                } else {
                    file.delete();
                }
            } catch(Exception e) {
                Toast.makeText(c, R.string.fork_theme_error, Toast.LENGTH_LONG).show();
            }
        }).show();
    }
}

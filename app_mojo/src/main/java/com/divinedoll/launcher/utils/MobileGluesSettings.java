package com.divinedoll.launcher.utils;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.game.renderer.extra.MobileGluesSupport;

/**
 * Edits MobileGlues' config.json inside the launcher (same keys as the plugin app). Keys that are
 * not shown here are kept untouched.
 */
public final class MobileGluesSettings {
    private MobileGluesSettings() {}

    private static final Gson GSON = new Gson();

    private static JsonObject read(File file) {
        if(file.isFile()) {
            try(FileReader reader = new FileReader(file)) {
                JsonObject o = GSON.fromJson(reader, JsonObject.class);
                if(o != null) return o;
            } catch (Exception ignored) {}
        }
        return new JsonObject();
    }

    private static int intOf(JsonObject o, String key, int fallback) {
        try {
            return o.has(key) ? o.get(key).getAsInt() : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Spinner spinner(Context c, LinearLayout parent, int title, int[] options, int selected) {
        TextView label = ForkUi.text(c, c.getString(title), 13, false);
        label.setPadding(0, ForkUi.dp(c, 10), 0, 0);
        parent.addView(label);
        String[] names = new String[options.length];
        for(int i = 0; i < names.length; i++) names[i] = c.getString(options[i]);
        Spinner spinner = new Spinner(c);
        spinner.setAdapter(new ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setSelection(Math.max(0, Math.min(selected, names.length - 1)));
        parent.addView(spinner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return spinner;
    }

    private static SwitchCompat toggle(Context c, LinearLayout parent, int title, boolean on) {
        SwitchCompat s = new SwitchCompat(c);
        s.setText(title);
        s.setChecked(on);
        s.setPadding(0, ForkUi.dp(c, 8), 0, 0);
        parent.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    public static void show(Context c) {
        File dir = MobileGluesSupport.configDir();
        File file = new File(dir, "config.json");
        JsonObject config = read(file);

        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = ForkUi.dp(c, 16);
        box.setPadding(pad, pad / 2, pad, 0);
        TextView where = ForkUi.text(c, c.getString(R.string.fork_mg_dir, dir.getAbsolutePath()), 11, false);
        where.setTextColor(0xFF999999);
        box.addView(where);

        Spinner angle = spinner(c, box, R.string.fork_mg_angle,
                new int[]{R.string.fork_mg_angle_0, R.string.fork_mg_angle_1, R.string.fork_mg_angle_2, R.string.fork_mg_angle_3}, intOf(config, "enableANGLE", 0));
        Spinner noError = spinner(c, box, R.string.fork_mg_noerror,
                new int[]{R.string.fork_mg_noerror_0, R.string.fork_mg_noerror_1, R.string.fork_mg_noerror_2, R.string.fork_mg_noerror_3}, intOf(config, "enableNoError", 0));
        SwitchCompat compute = toggle(c, box, R.string.fork_mg_compute, intOf(config, "enableExtComputeShader", 0) > 0);
        SwitchCompat timer = toggle(c, box, R.string.fork_mg_timer, intOf(config, "enableExtTimerQuery", 0) > 0);
        SwitchCompat dsa = toggle(c, box, R.string.fork_mg_dsa, intOf(config, "enableExtDirectStateAccess", 0) > 0);
        SwitchCompat depth = toggle(c, box, R.string.fork_mg_depthfix, intOf(config, "angleDepthClearFixMode", 0) > 0);

        ScrollView scroll = new ScrollView(c);
        scroll.addView(box);
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_mg_settings)
                .setView(scroll)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.fork_mg_reset, (d, w) -> {
                    for(String key : new String[]{"enableANGLE", "enableNoError", "enableExtComputeShader", "enableExtTimerQuery",
                            "enableExtDirectStateAccess", "angleDepthClearFixMode"}) config.remove(key);
                    write(c, file, config);
                })
                .setPositiveButton(R.string.fork_mg_save, (d, w) -> {
                    config.addProperty("enableANGLE", angle.getSelectedItemPosition());
                    config.addProperty("enableNoError", noError.getSelectedItemPosition());
                    config.addProperty("enableExtComputeShader", compute.isChecked() ? 1 : 0);
                    config.addProperty("enableExtTimerQuery", timer.isChecked() ? 1 : 0);
                    config.addProperty("enableExtDirectStateAccess", dsa.isChecked() ? 1 : 0);
                    config.addProperty("angleDepthClearFixMode", depth.isChecked() ? 1 : 0);
                    write(c, file, config);
                })
                .show();
    }

    private static void write(Context c, File file, JsonObject config) {
        try {
            File parent = file.getParentFile();
            if(parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create " + parent);
            try(FileWriter writer = new FileWriter(file)) {
                GSON.toJson(config, writer);
            }
            Toast.makeText(c, c.getString(R.string.fork_mg_saved, file.getAbsolutePath()), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(c, c.getString(R.string.fork_op_error, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
        }
    }
}

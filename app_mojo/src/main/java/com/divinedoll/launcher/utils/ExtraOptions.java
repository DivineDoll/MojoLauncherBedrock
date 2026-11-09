package com.divinedoll.launcher.utils;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Small switches that do not fit in the color dialogs, and the performance profiles. */
public class ExtraOptions {
    /** Resolution scale in percent, sustained performance and vsync for each profile. */
    public static final String GRID_PREF = "controls_grid";
    private static final int[] SCALE = {60, 80, 100};
    private static final boolean[] SUSTAINED = {true, true, false};
    private static final boolean[] VSYNC = {false, true, true};

    /** Size of the control grid in pixels while editing, or 0 when the grid is off. */
    public static float gridStepPx() {
        try {
            return LauncherPreferences.DEFAULT_PREF.getBoolean(GRID_PREF, false) ? com.divinedoll.launcher.Tools.dpToPx(8) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void show(Context c, Runnable onChanged) {
        String[] items = {c.getString(R.string.fork_extra_switches), c.getString(R.string.fork_perf_title), c.getString(R.string.fork_diag_title),
                c.getString(R.string.fork_autobackup_restore)};
        new AlertDialog.Builder(c).setTitle(R.string.fork_extra_menu).setItems(items, (d, which) -> {
            if(which == 0) switches(c, onChanged);
            else if(which == 1) performance(c);
            else if(which == 2) Diagnostics.share(c);
            else restoreAutoBackup(c);
        }).show();
    }

    private static void switches(Context c, Runnable onChanged) {
        String[] items = {c.getString(R.string.fork_extra_banner_auto), c.getString(R.string.fork_extra_click_sound),
                c.getString(R.string.fork_extra_fps), c.getString(R.string.fork_extra_memory),
                c.getString(R.string.fork_extra_battery), c.getString(R.string.fork_extra_grid)};
        boolean[] checked = {
                LauncherPreferences.DEFAULT_PREF.getBoolean(BannerManager.PREF_AUTO, false),
                LauncherPreferences.DEFAULT_PREF.getBoolean(ClickSound.PREF, true),
                LauncherPreferences.DEFAULT_PREF.getBoolean(FpsOverlay.PREF, false),
                LauncherPreferences.DEFAULT_PREF.getBoolean(FpsOverlay.PREF_MEMORY, false),
                LauncherPreferences.DEFAULT_PREF.getBoolean(FpsOverlay.PREF_BATTERY, false),
                LauncherPreferences.DEFAULT_PREF.getBoolean(GRID_PREF, false)
        };
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_extra_switches)
                .setMultiChoiceItems(items, checked, (d, which, on) -> checked[which] = on)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putBoolean(BannerManager.PREF_AUTO, checked[0])
                            .putBoolean(ClickSound.PREF, checked[1])
                            .putBoolean(FpsOverlay.PREF, checked[2])
                            .putBoolean(FpsOverlay.PREF_MEMORY, checked[3])
                            .putBoolean(FpsOverlay.PREF_BATTERY, checked[4])
                            .putBoolean(GRID_PREF, checked[5]).apply();
                    if(onChanged != null) onChanged.run();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void restoreAutoBackup(Context c) {
        java.io.File file = BackupManager.autoBackupFile(c);
        if(!file.isFile()) {
            Toast.makeText(c, R.string.fork_autobackup_none, Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(c).setMessage(R.string.fork_autobackup_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    try(java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                        BackupManager.restore(c, in);
                        Toast.makeText(c, R.string.fork_autobackup_done, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(c, String.valueOf(e.getMessage()), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Changes only the resolution scale, sustained performance and vsync. The renderer and the RAM stay as they are. */
    private static void performance(Context c) {
        String[] names = {c.getString(R.string.fork_perf_fast), c.getString(R.string.fork_perf_balanced), c.getString(R.string.fork_perf_quality)};
        new AlertDialog.Builder(c).setTitle(R.string.fork_perf_title).setItems(names, (d, which) -> {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putInt("resolutionRatio", SCALE[which])
                    .putBoolean("sustainedPerformance", SUSTAINED[which])
                    .putBoolean("force_vsync", VSYNC[which]).apply();
            LauncherPreferences.loadPreferences(c.getApplicationContext());
            Toast.makeText(c, c.getString(R.string.fork_perf_applied, names[which]), Toast.LENGTH_LONG).show();
        }).show();
    }
}

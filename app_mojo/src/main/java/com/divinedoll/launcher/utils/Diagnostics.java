package com.divinedoll.launcher.utils;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayDeque;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Collects the device, the main settings and the end of the game log in one text file that can be shared. */
public class Diagnostics {
    private static final int LOG_LINES = 300;

    public static void share(Context c) {
        try {
            File dir = new File(c.getCacheDir(), "bedrock");
            if(!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("No cache folder");
            File file = new File(dir, "diagnostico.txt");
            try(FileWriter out = new FileWriter(file)) {
                out.write(build(c));
            }
            Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".bedrockfiles", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(intent, c.getString(R.string.fork_diag_title)));
        } catch(Exception e) {
            Toast.makeText(c, c.getString(R.string.fork_diag_error, e.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private static String build(Context c) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Hardware: ").append(Build.HARDWARE).append('\n');
        sb.append("ABI: ").append(Build.SUPPORTED_ABIS[0]).append('\n');
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        sb.append("RAM total: ").append(memory.totalMem / (1024 * 1024)).append(" MB\n");
        sb.append("App: ").append(c.getPackageName()).append(' ')
                .append(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName).append('\n');
        sb.append("Renderer: ").append(LauncherPreferences.PREF_RENDERER).append('\n');
        sb.append("RAM for the game: ").append(LauncherPreferences.PREF_RAM_ALLOCATION).append(" MB\n");
        sb.append("Resolution scale: ").append(LauncherPreferences.PREF_SCALE_FACTOR).append('\n');
        sb.append("Alternate surface: ").append(LauncherPreferences.PREF_USE_ALTERNATE_SURFACE).append("\n\n");
        sb.append("--- End of latestlog.txt ---\n");
        File log = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
        if(log.isFile()) {
            ArrayDeque<String> lines = new ArrayDeque<>();
            try(BufferedReader reader = new BufferedReader(new FileReader(log))) {
                String line;
                while((line = reader.readLine()) != null) {
                    lines.add(line);
                    if(lines.size() > LOG_LINES) lines.removeFirst();
                }
            }
            for(String line : lines) sb.append(line).append('\n');
        } else {
            sb.append("(no log yet)\n");
        }
        return sb.toString();
    }
}

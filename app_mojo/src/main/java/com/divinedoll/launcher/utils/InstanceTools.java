package com.divinedoll.launcher.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.instances.Instance;
import com.divinedoll.launcher.instances.Instances;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Duplicate / export / import of instances, tags and play time. Only user content is copied, never the game install. */
public final class InstanceTools {
    private InstanceTools() {}

    /** Folders of user content that travel with an instance. */
    public static final String[] CONTENT = {"mods", "config", "resourcepacks", "shaderpacks", "saves", "screenshots", "scripts"};
    public static final String[] LOOSE = {"options.txt", "optionsof.txt", "servers.dat"};

    public static final int[] TAG_LABELS = {R.string.fork_tag_0, R.string.fork_tag_1, R.string.fork_tag_2, R.string.fork_tag_3, R.string.fork_tag_4, R.string.fork_tag_5};
    public static final int[] TAG_COLORS = {0xFF888888, 0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFF9C27B0, 0xFFF44336};

    private static final String META = "mojo_export.json";

    static final class Meta {
        @SerializedName("name") String name;
        @SerializedName("versionId") String versionId;
        @SerializedName("renderer") String renderer;
        @SerializedName("jvmArgs") String jvmArgs;
        @SerializedName("argsMode") int argsMode;
    }

    public static Instance find(String name) throws IOException {
        for(Instance i : Instances.loadAllInstances()) {
            if(i.name != null && i.name.equals(name)) return i;
        }
        throw new IOException("Instance not found: " + name);
    }

    public static Instance duplicate(Instance src, String suffix) throws IOException {
        Instance copy = Instances.createInstance(i -> {
            i.name = src.name + suffix;
            i.versionId = src.versionId;
            i.renderer = src.renderer;
            i.jvmArgs = src.jvmArgs;
            i.argsMode = src.argsMode;
            i.selectedRuntime = src.selectedRuntime;
            i.controlLayout = src.controlLayout;
            i.sharedData = src.sharedData;
            i.installer = src.installer;
            i.icon = src.icon;
        }, null);
        if(!src.sharedData) {
            File from = src.getGameDirectory();
            File to = copy.getGameDirectory();
            for(String d : CONTENT) {
                File s = new File(from, d);
                if(s.isDirectory()) org.apache.commons.io.FileUtils.copyDirectory(s, new File(to, d));
            }
            for(String f : LOOSE) {
                File s = new File(from, f);
                if(s.isFile()) org.apache.commons.io.FileUtils.copyFile(s, new File(to, f));
            }
        }
        return copy;
    }

    public static void zipTree(ZipOutputStream z, File f, String name) throws IOException {
        if(f.isDirectory()) {
            File[] kids = f.listFiles();
            if(kids != null) for(File k : kids) zipTree(z, k, name + "/" + k.getName());
        } else {
            z.putNextEntry(new ZipEntry(name));
            try(FileInputStream in = new FileInputStream(f)) {
                byte[] buf = new byte[16384];
                int n;
                while((n = in.read(buf)) > 0) z.write(buf, 0, n);
            }
            z.closeEntry();
        }
    }

    public static void export(Context c, Instance inst, Uri dest) throws IOException {
        OutputStream os = c.getContentResolver().openOutputStream(dest);
        if(os == null) throw new IOException("Cannot write the destination");
        try(ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(os))) {
            Meta m = new Meta();
            m.name = inst.name;
            m.versionId = inst.versionId;
            m.renderer = inst.renderer;
            m.jvmArgs = inst.jvmArgs;
            m.argsMode = inst.argsMode;
            zip.putNextEntry(new ZipEntry(META));
            zip.write(new Gson().toJson(m).getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            File root = inst.getGameDirectory();
            for(String d : CONTENT) {
                File s = new File(root, d);
                if(s.isDirectory()) zipTree(zip, s, d);
            }
            for(String f : LOOSE) {
                File s = new File(root, f);
                if(s.isFile()) zipTree(zip, s, f);
            }
        }
    }

    private static boolean allowedTop(String top) {
        for(String d : CONTENT) if(d.equals(top)) return true;
        for(String f : LOOSE) if(f.equals(top)) return true;
        return false;
    }

    public static Instance importZip(Context c, Uri src, String suffix) throws IOException {
        InputStream raw = c.getContentResolver().openInputStream(src);
        if(raw == null) throw new IOException("Cannot read the file");
        Instance inst = Instances.createInstance(i -> {
            i.name = "Imported";
            i.versionId = Instance.VERSION_LATEST_RELEASE;
        }, null);
        File root = inst.getGameDirectory();
        String rootPath = root.getCanonicalPath();
        Meta meta = null;
        try(ZipInputStream z = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry e;
            while((e = z.getNextEntry()) != null) {
                if(e.isDirectory()) continue;
                String name = e.getName().replace('\\', '/');
                if(name.equals(META)) {
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while((n = z.read(buf)) > 0) bos.write(buf, 0, n);
                    meta = new Gson().fromJson(new String(bos.toByteArray(), StandardCharsets.UTF_8), Meta.class);
                    continue;
                }
                if(!allowedTop(name.split("/")[0])) continue;
                File out = new File(root, name);
                // zip-slip: the file must stay inside the instance folder
                if(!out.getCanonicalPath().startsWith(rootPath + File.separator)) continue;
                File parent = out.getParentFile();
                if(parent != null) parent.mkdirs();
                try(FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while((n = z.read(buf)) > 0) fos.write(buf, 0, n);
                }
            }
        }
        if(meta != null) {
            if(meta.name != null) inst.name = meta.name + suffix;
            if(meta.versionId != null) inst.versionId = meta.versionId;
            inst.renderer = meta.renderer;
            inst.jvmArgs = meta.jvmArgs;
            inst.argsMode = meta.argsMode;
        }
        inst.write();
        return inst;
    }

    // ---- tags ----
    public static int getTag(String name) {
        int t = LauncherPreferences.DEFAULT_PREF.getInt("inst_tag_" + name, 0);
        return t >= 0 && t < TAG_LABELS.length ? t : 0;
    }

    public static void setTag(String name, int tag) {
        LauncherPreferences.DEFAULT_PREF.edit().putInt("inst_tag_" + name, tag).apply();
    }

    // ---- play time ----
    public static void addPlaytime(String name, long ms) {
        if(name == null || ms <= 0) return;
        SharedPreferences p = LauncherPreferences.DEFAULT_PREF;
        p.edit().putLong("inst_play_" + name, p.getLong("inst_play_" + name, 0) + ms)
                .putLong("inst_last_" + name, System.currentTimeMillis()).apply();
    }

    public static String stats(Context c, String name) {
        SharedPreferences p = LauncherPreferences.DEFAULT_PREF;
        long ms = p.getLong("inst_play_" + name, 0);
        if(ms < 60000) return c.getString(R.string.fork_stats_never);
        long min = ms / 60000;
        String time = (min >= 60 ? (min / 60) + "h " : "") + (min % 60) + "m";
        String text = c.getString(R.string.fork_stats_played, time);
        long last = p.getLong("inst_last_" + name, 0);
        if(last > 0) text += " · " + c.getString(R.string.fork_stats_last, java.text.DateFormat.getDateInstance().format(new Date(last)));
        return text;
    }
}

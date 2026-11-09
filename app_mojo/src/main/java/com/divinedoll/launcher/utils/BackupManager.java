package com.divinedoll.launcher.utils;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** One .zip with the theme, saved themes, pixel arts, palettes, skins and control layouts, to keep or move to another phone. */
public class BackupManager {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _.-]{1,60}");
    private static final int MAX_ENTRY = 25 * 1024 * 1024;

    /** Builds the backup and copies it to Downloads. Returns the file name. Blocking. */
    public static String export(Context c) throws Exception {
        File zip = new File(c.getCacheDir(), "mojo-backup.zip");
        writeZip(c, zip);
        String name = OnlineSkins.exportToDownloads(c, zip, "application/zip");
        zip.delete();
        return name;
    }

    private static void writeZip(Context c, File zip) throws Exception {
        try(ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            put(out, "theme.json", ThemeManager.currentAsJson().getBytes(StandardCharsets.UTF_8));
            addFolder(out, ThemeManager.directory(c), "themes/", ".json");
            addFolder(out, PixelArtEditor.directory(c), "pixelart/", ".png");
            addFolder(out, new File(c.getFilesDir(), "palettes"), "palettes/", ".json");
            addFolder(out, new File(c.getFilesDir(), "skin_library"), "skins/library/", ".png");
            addFolder(out, new File(c.getFilesDir(), "offline_skins"), "skins/offline/", ".png");
            if(com.divinedoll.launcher.Tools.CTRLMAP_PATH != null) addFolder(out, new File(com.divinedoll.launcher.Tools.CTRLMAP_PATH), "controls/", ".json");
        }
    }

    public static File autoBackupFile(Context c) {
        return new File(c.getFilesDir(), "auto_backup.zip");
    }

    /** Keeps one copy in the app folder, made before the launcher presets are rewritten after an update. */
    public static void autoBackup(Context c) {
        try {
            writeZip(c, autoBackupFile(c));
        } catch (Exception ignored) {}
    }

    private static void addFolder(ZipOutputStream out, File dir, String prefix, String ext) throws IOException {
        File[] files = dir.listFiles((d, n) -> n.endsWith(ext));
        if(files == null) return;
        for(File f : files) {
            byte[] data = new byte[(int) f.length()];
            try(FileInputStream in = new FileInputStream(f)) {
                int read = 0;
                while(read < data.length) {
                    int n = in.read(data, read, data.length - read);
                    if(n < 0) break;
                    read += n;
                }
            }
            put(out, prefix + f.getName(), data);
        }
    }

    private static void put(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    /** Restores a backup. Only the known folders and plain file names are accepted. Blocking. */
    public static void restore(Context c, InputStream raw) throws Exception {
        String theme = null;
        try(ZipInputStream zip = new ZipInputStream(raw)) {
            for(ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String name = e.getName();
                byte[] data = readLimited(zip);
                if(name.equals("theme.json")) {
                    theme = new String(data, StandardCharsets.UTF_8);
                } else if(name.startsWith("themes/") && name.endsWith(".json")) {
                    write(ThemeManager.directory(c), name.substring(7), data);
                } else if(name.startsWith("pixelart/") && name.endsWith(".png")) {
                    write(PixelArtEditor.directory(c), name.substring(9), data);
                } else if(name.startsWith("palettes/") && name.endsWith(".json")) {
                    writeSafe(new File(c.getFilesDir(), "palettes"), name.substring(9), data);
                } else if(name.startsWith("skins/library/") && name.endsWith(".png")) {
                    writeSafe(new File(c.getFilesDir(), "skin_library"), name.substring(14), data);
                } else if(name.startsWith("skins/offline/") && name.endsWith(".png")) {
                    writeSafe(new File(c.getFilesDir(), "offline_skins"), name.substring(14), data);
                } else if(com.divinedoll.launcher.Tools.CTRLMAP_PATH != null && name.startsWith("controls/") && name.endsWith(".json")) {
                    writeSafe(new File(com.divinedoll.launcher.Tools.CTRLMAP_PATH), name.substring(9), data);
                }
            }
        }
        if(theme != null) ThemeManager.applyJson(theme);
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if(out.size() > MAX_ENTRY) throw new IOException("Entry too large");
        }
        return out.toByteArray();
    }

    private static void write(File dir, String fileName, byte[] data) throws IOException {
        if(!NAME.matcher(fileName).matches() || fileName.contains("..")) return;
        try(FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
            out.write(data);
        }
    }

    /** Names made by the launcher itself, such as skins and control layouts, may have more characters. */
    private static void writeSafe(File dir, String fileName, byte[] data) throws IOException {
        if(fileName.isEmpty() || fileName.length() > 120 || fileName.startsWith(".") || fileName.contains("..")
                || fileName.indexOf('/') >= 0 || fileName.indexOf('\\') >= 0 || fileName.indexOf('\0') >= 0) return;
        if(!dir.isDirectory() && !dir.mkdirs()) return;
        try(FileOutputStream out = new FileOutputStream(new File(dir, fileName))) {
            out.write(data);
        }
    }
}

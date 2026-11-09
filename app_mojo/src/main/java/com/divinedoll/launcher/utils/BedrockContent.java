package com.divinedoll.launcher.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/**
 * Downloads for Minecraft Bedrock: add-ons and texture packs from CurseForge, and skin packs
 * built from a skin PNG. The files are handed to the Bedrock app, which imports them itself.
 */
public class BedrockContent {
    public static final String BEDROCK_PACKAGE = "com.mojang.minecraftpe";
    public static final String PREF_KEY = "curseforge_bedrock_key";
    private static final int GAME_ID = 78022;

    public static class Item {
        public long id;
        public String name = "", summary = "", author = "";
        public long downloads;
        public long fileId;
        public String fileName = "", downloadUrl;
    }

    /** The key set by the user, or the one built into the launcher when it has one. */
    public static String apiKey(Context c) {
        String own = LauncherPreferences.DEFAULT_PREF.getString(PREF_KEY, "");
        if(own != null && !own.trim().isEmpty()) return own.trim();
        String built = c.getString(R.string.curseforge_api_key);
        return "DUMMY".equals(built) ? "" : built;
    }

    private static String httpGet(String address, String key) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(20000);
        con.setRequestProperty("Accept", "application/json");
        if(key != null) con.setRequestProperty("x-api-key", key);
        try {
            int code = con.getResponseCode();
            if(code >= 400) throw new IOException("HTTP " + code);
            try(InputStream in = con.getInputStream()) {
                return new String(readAll(in), StandardCharsets.UTF_8);
            }
        } finally {
            con.disconnect();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** Id of the Bedrock category whose name has the word, e.g. "add-on" or "texture". */
    private static int classId(String key, String word) throws IOException {
        JsonObject root = JsonParser.parseString(httpGet(
                "https://api.curseforge.com/v1/categories?gameId=" + GAME_ID + "&classesOnly=true", key)).getAsJsonObject();
        for(JsonElement e : root.getAsJsonArray("data")) {
            JsonObject o = e.getAsJsonObject();
            String name = str(o, "name");
            if(name != null && name.toLowerCase().replace("-", "").replace(" ", "").contains(word)) return o.get("id").getAsInt();
        }
        throw new IOException("No category " + word);
    }

    /** kind is "addon" or "texture". Blocking. */
    public static List<Item> search(Context c, String kind, String query) throws IOException {
        String key = apiKey(c);
        if(key.isEmpty()) throw new IOException("NO_KEY");
        int cls = classId(key, kind);
        String url = "https://api.curseforge.com/v1/mods/search?gameId=" + GAME_ID + "&classId=" + cls
                + "&sortField=2&sortOrder=desc&pageSize=25&searchFilter=" + java.net.URLEncoder.encode(query, "UTF-8");
        JsonArray data = JsonParser.parseString(httpGet(url, key)).getAsJsonObject().getAsJsonArray("data");
        List<Item> result = new ArrayList<>();
        for(JsonElement e : data) {
            JsonObject o = e.getAsJsonObject();
            Item item = new Item();
            item.id = o.get("id").getAsLong();
            item.name = str(o, "name") == null ? "" : str(o, "name");
            item.summary = str(o, "summary") == null ? "" : str(o, "summary");
            if(o.has("downloadCount")) item.downloads = o.get("downloadCount").getAsLong();
            JsonArray authors = o.getAsJsonArray("authors");
            if(authors != null && authors.size() > 0) item.author = str(authors.get(0).getAsJsonObject(), "name");
            JsonArray files = o.getAsJsonArray("latestFiles");
            if(files == null || files.size() == 0) continue;
            JsonObject file = files.get(files.size() - 1).getAsJsonObject();
            item.fileId = file.get("id").getAsLong();
            item.fileName = str(file, "fileName") == null ? "pack" : str(file, "fileName");
            item.downloadUrl = str(file, "downloadUrl");
            result.add(item);
        }
        return result;
    }

    private static File cacheDir(Context c) {
        File d = new File(c.getCacheDir(), "bedrock");
        if(!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Downloads the file of an item into the cache. Blocking. */
    public static File download(Context c, Item item) throws IOException {
        String url = item.downloadUrl;
        if(url == null) {
            JsonObject o = JsonParser.parseString(httpGet("https://api.curseforge.com/v1/mods/" + item.id
                    + "/files/" + item.fileId + "/download-url", apiKey(c))).getAsJsonObject();
            url = str(o, "data");
            if(url == null) throw new IOException("The author disabled third party downloads");
        }
        File out = new File(cacheDir(c), item.fileName.replaceAll("[^A-Za-z0-9._ -]", "_"));
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        try {
            if(con.getResponseCode() >= 400) throw new IOException("HTTP " + con.getResponseCode());
            try(InputStream in = con.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
        } finally {
            con.disconnect();
        }
        return out;
    }

    /** Builds a .mcpack skin pack with one skin. Blocking. */
    public static File buildSkinPack(Context c, String name, Bitmap skin, boolean slim) throws IOException {
        String id = name.replaceAll("[^A-Za-z0-9_]", "_");
        if(id.isEmpty()) id = "skin";
        Bitmap full = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        new android.graphics.Canvas(full).drawBitmap(skin, 0, 0, null);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        full.compress(Bitmap.CompressFormat.PNG, 100, png);

        UUID headerId = UUID.nameUUIDFromBytes(("fork.skinpack.header." + id).getBytes(StandardCharsets.UTF_8));
        UUID moduleId = UUID.nameUUIDFromBytes(("fork.skinpack.module." + id).getBytes(StandardCharsets.UTF_8));
        String manifest = "{\"format_version\":1,\"header\":{\"name\":\"" + id + "\",\"uuid\":\"" + headerId
                + "\",\"version\":[1,0,0]},\"modules\":[{\"type\":\"skin_pack\",\"uuid\":\"" + moduleId
                + "\",\"version\":[1,0,0]}]}";
        String skins = "{\"skins\":[{\"localization_name\":\"skin1\",\"geometry\":\""
                + (slim ? "geometry.humanoid.customSlim" : "geometry.humanoid.custom")
                + "\",\"texture\":\"skin.png\",\"type\":\"free\"}],\"serialize_name\":\"" + id
                + "\",\"localization_name\":\"" + id + "\"}";
        String lang = "skinpack." + id + "=" + name + "\nskin." + id + ".skin1=" + name + "\n";

        File out = new File(cacheDir(c), id + ".mcpack");
        try(ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(out))) {
            put(zip, "manifest.json", manifest.getBytes(StandardCharsets.UTF_8));
            put(zip, "skins.json", skins.getBytes(StandardCharsets.UTF_8));
            put(zip, "skin.png", png.toByteArray());
            put(zip, "texts/languages.json", "[\"en_US\"]".getBytes(StandardCharsets.UTF_8));
            put(zip, "texts/en_US.lang", lang.getBytes(StandardCharsets.UTF_8));
        }
        return out;
    }

    private static void put(ZipOutputStream zip, String name, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(data);
        zip.closeEntry();
    }

    /** Copies a picked pack into the cache so the file provider can share it. Blocking. */
    public static File copyToCache(Context c, Uri uri) throws IOException {
        String name = "pack.mcpack";
        try(android.database.Cursor cur = c.getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if(cur != null && cur.moveToFirst() && cur.getString(0) != null) name = cur.getString(0).replaceAll("[^A-Za-z0-9._ -]", "_");
        }
        File out = new File(cacheDir(c), name);
        try(InputStream in = c.getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
            if(in == null) throw new IOException("Cannot open the file");
            byte[] buf = new byte[16384];
            int n;
            while((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }
        return out;
    }

    public static boolean isInstalled(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage(BEDROCK_PACKAGE) != null;
    }

    /** Opens a pack file with Bedrock, which imports it. Returns false when it could not be opened. */
    public static boolean openWithBedrock(Context c, File file) {
        Uri uri = FileProvider.getUriForFile(c, c.getPackageName() + ".bedrockfiles", file);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/octet-stream");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            intent.setPackage(BEDROCK_PACKAGE);
            c.startActivity(intent);
            return true;
        } catch(Exception first) {
            try {
                intent.setPackage(null);
                c.startActivity(Intent.createChooser(intent, file.getName()));
                return true;
            } catch(Exception second) {
                return false;
            }
        }
    }
}

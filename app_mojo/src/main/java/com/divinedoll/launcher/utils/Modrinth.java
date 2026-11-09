package com.divinedoll.launcher.utils;

import android.app.Activity;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.divinedoll.launcher.R;

/** Search, install (with required dependencies) and update mods from Modrinth into the mods folder of an instance. */
public final class Modrinth {
    private Modrinth() {}

    private static final String API = "https://api.modrinth.com/v2";
    private static final Gson GSON = new Gson();

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String request(String method, String url, String body) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "DivineDoll/DivineLauncher");
        if(body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try(OutputStream o = c.getOutputStream()) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int code = c.getResponseCode();
        if(code >= 400) throw new IOException("HTTP " + code);
        try(InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void download(String url, File out) throws IOException {
        File tmp = new File(out.getParentFile(), out.getName() + ".part");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "DivineDoll/DivineLauncher");
        if(c.getResponseCode() >= 400) throw new IOException("HTTP " + c.getResponseCode());
        try(InputStream in = c.getInputStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
            byte[] buf = new byte[16384];
            int n;
            while((n = in.read(buf)) > 0) fos.write(buf, 0, n);
        }
        if(out.exists()) out.delete();
        if(!tmp.renameTo(out)) throw new IOException("Cannot save " + out.getName());
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** Minecraft version inside a version id such as "1.20.1-forge-47.2.0" or "fabric-loader-0.15.11-1.20.1". */
    public static String gameVersionOf(String versionId) {
        if(versionId == null) return null;
        Matcher m = Pattern.compile("(?<![\\d.])1\\.\\d{1,2}(?:\\.\\d{1,2})?(?![\\d.])").matcher(versionId);
        return m.find() ? m.group() : null;
    }

    public static String loaderOf(String versionId) {
        if(versionId == null) return null;
        String v = versionId.toLowerCase(java.util.Locale.ROOT);
        if(v.contains("neoforge")) return "neoforge";
        if(v.contains("fabric")) return "fabric";
        if(v.contains("quilt")) return "quilt";
        if(v.contains("forge")) return "forge";
        return null;
    }

    private static JsonObject pickVersion(JsonArray arr) {
        JsonObject first = null;
        for(JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            if(first == null) first = o;
            if("release".equals(str(o, "version_type"))) return o;
        }
        return first;
    }

    private static JsonObject primaryFile(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if(files == null || files.size() == 0) return null;
        for(JsonElement e : files) {
            JsonObject o = e.getAsJsonObject();
            if(o.has("primary") && o.get("primary").getAsBoolean()) return o;
        }
        return files.get(0).getAsJsonObject();
    }

    private static void install(String projectId, String gv, String loader, File folder, int depth, Set<String> seen, List<String> done) throws IOException {
        if(depth > 3 || !seen.add(projectId)) return;
        StringBuilder q = new StringBuilder(API + "/project/" + enc(projectId) + "/version?");
        if(gv != null) q.append("game_versions=").append(enc("[\"" + gv + "\"]")).append('&');
        if(loader != null) q.append("loaders=").append(enc("[\"" + loader + "\"]"));
        JsonArray arr = GSON.fromJson(request("GET", q.toString(), null), JsonArray.class);
        JsonObject v = arr == null ? null : pickVersion(arr);
        if(v == null) {
            if(depth == 0) throw new IOException("No compatible version for this instance");
            return;
        }
        JsonObject file = primaryFile(v);
        if(file != null) {
            String name = new File(str(file, "filename")).getName();
            File out = new File(folder, name);
            if(!out.exists()) {
                folder.mkdirs();
                download(str(file, "url"), out);
                done.add(name);
            }
        }
        JsonArray deps = v.getAsJsonArray("dependencies");
        if(deps != null) {
            for(JsonElement e : deps) {
                JsonObject d = e.getAsJsonObject();
                if("required".equals(str(d, "dependency_type"))) {
                    String pid = str(d, "project_id");
                    if(pid != null) install(pid, gv, loader, folder, depth + 1, seen, done);
                }
            }
        }
    }

    private static AlertDialog busy(Activity act) {
        return new AlertDialog.Builder(act).setMessage(R.string.fork_mr_working).setCancelable(false).show();
    }

    private static void message(Activity act, String text) {
        new AlertDialog.Builder(act).setMessage(text).setPositiveButton(android.R.string.ok, null).show();
    }

    public static void search(Activity act, File gameDir, String versionId, String projectType, String folderName, Runnable onDone) {
        EditText input = new EditText(act);
        input.setSingleLine(true);
        input.setHint(R.string.fork_mr_hint);
        new AlertDialog.Builder(act)
                .setTitle(R.string.fork_mr_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.fork_mr_search, (d, w) -> runSearch(act, gameDir, versionId, projectType, folderName, input.getText().toString().trim(), onDone))
                .show();
    }

    private static void runSearch(Activity act, File gameDir, String versionId, String projectType, String folderName, String query, Runnable onDone) {
        String gv = gameVersionOf(versionId);
        String loader = "mod".equals(projectType) ? loaderOf(versionId) : null;
        AlertDialog wait = busy(act);
        new Thread(() -> {
            try {
                StringBuilder facets = new StringBuilder("[[\"project_type:" + projectType + "\"]");
                if(gv != null) facets.append(",[\"versions:").append(gv).append("\"]");
                if(loader != null) facets.append(",[\"categories:").append(loader).append("\"]");
                facets.append("]");
                String url = API + "/search?limit=20&query=" + enc(query) + "&facets=" + enc(facets.toString());
                JsonObject res = GSON.fromJson(request("GET", url, null), JsonObject.class);
                JsonArray hits = res.getAsJsonArray("hits");
                String[] labels = new String[hits.size()];
                String[] ids = new String[hits.size()];
                for(int i = 0; i < labels.length; i++) {
                    JsonObject h = hits.get(i).getAsJsonObject();
                    ids[i] = str(h, "project_id");
                    labels[i] = str(h, "title") + " — " + str(h, "description");
                }
                act.runOnUiThread(() -> {
                    wait.dismiss();
                    if(labels.length == 0) { message(act, act.getString(R.string.fork_mr_none)); return; }
                    new AlertDialog.Builder(act).setTitle(R.string.fork_mr_title)
                            .setItems(labels, (d, which) -> runInstall(act, gameDir, ids[which], gv, loader, folderName, onDone))
                            .setNegativeButton(android.R.string.cancel, null).show();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> { wait.dismiss(); message(act, act.getString(R.string.fork_op_error, String.valueOf(e.getMessage()))); });
            }
        }).start();
    }

    private static void runInstall(Activity act, File gameDir, String projectId, String gv, String loader, String folderName, Runnable onDone) {
        AlertDialog wait = busy(act);
        new Thread(() -> {
            try {
                List<String> done = new ArrayList<>();
                install(projectId, gv, loader, new File(gameDir, folderName), 0, new HashSet<>(), done);
                act.runOnUiThread(() -> {
                    wait.dismiss();
                    message(act, act.getString(R.string.fork_mr_installed, done.isEmpty() ? "—" : android.text.TextUtils.join(", ", done)));
                    if(onDone != null) onDone.run();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> { wait.dismiss(); message(act, act.getString(R.string.fork_op_error, String.valueOf(e.getMessage()))); });
            }
        }).start();
    }

    private static String sha1(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        try(FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for(byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void updateAll(Activity act, File gameDir, String versionId, Runnable onDone) {
        String gv = gameVersionOf(versionId);
        String loader = loaderOf(versionId);
        AlertDialog wait = busy(act);
        new Thread(() -> {
            try {
                File mods = new File(gameDir, "mods");
                File[] jars = mods.listFiles(f -> f.isFile() && f.getName().endsWith(".jar"));
                Map<String, File> byHash = new HashMap<>();
                JsonArray hashes = new JsonArray();
                if(jars != null) for(File j : jars) { String h = sha1(j); byHash.put(h, j); hashes.add(h); }
                int updated = 0;
                if(hashes.size() > 0) {
                    JsonObject body = new JsonObject();
                    body.add("hashes", hashes);
                    body.addProperty("algorithm", "sha1");
                    if(loader != null) { JsonArray l = new JsonArray(); l.add(loader); body.add("loaders", l); }
                    if(gv != null) { JsonArray g = new JsonArray(); g.add(gv); body.add("game_versions", g); }
                    JsonObject res = GSON.fromJson(request("POST", API + "/version_files/update", body.toString()), JsonObject.class);
                    for(Map.Entry<String, JsonElement> en : res.entrySet()) {
                        JsonObject file = primaryFile(en.getValue().getAsJsonObject());
                        File old = byHash.get(en.getKey());
                        if(file == null || old == null) continue;
                        String newSha = file.getAsJsonObject("hashes").get("sha1").getAsString();
                        if(newSha.equalsIgnoreCase(en.getKey())) continue;
                        File out = new File(mods, new File(str(file, "filename")).getName());
                        download(str(file, "url"), out);
                        if(!out.equals(old)) old.delete();
                        updated++;
                    }
                }
                final int count = updated;
                act.runOnUiThread(() -> {
                    wait.dismiss();
                    message(act, act.getString(R.string.fork_mr_updated, count));
                    if(onDone != null) onDone.run();
                });
            } catch (Exception e) {
                act.runOnUiThread(() -> { wait.dismiss(); message(act, act.getString(R.string.fork_op_error, String.valueOf(e.getMessage()))); });
            }
        }).start();
    }
}

package git.artdeell.mojo.authenticator.accounts;

import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * A tiny Yggdrasil-compatible skin server that listens on the loopback interface only.
 * authlib-injector points the game to it, so the vanilla client downloads the skin of an
 * offline account the same way it would download a real one. It does not authenticate anyone.
 */
public class LocalSkinServer {
    private static final String TAG = "LocalSkinServer";
    private static LocalSkinServer sInstance;

    private final ServerSocket mSocket;
    private volatile File mSkinFile;
    private volatile String mUsername;
    private volatile String mUuid; // without dashes, lowercase
    private volatile boolean mSlim;

    private LocalSkinServer() throws IOException {
        mSocket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        Thread acceptor = new Thread(this::acceptLoop, "LocalSkinServer");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** Starts (or reuses) the server and returns its base URL, to be given to authlib-injector. */
    public static synchronized String start(File skinFile, String username, String uuid, boolean slim) throws IOException {
        if(sInstance == null) sInstance = new LocalSkinServer();
        sInstance.mSkinFile = skinFile;
        sInstance.mUsername = username;
        sInstance.mUuid = uuid.replace("-", "").toLowerCase(Locale.ROOT);
        sInstance.mSlim = slim;
        return "http://127.0.0.1:" + sInstance.mSocket.getLocalPort();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + mSocket.getLocalPort();
    }

    private void acceptLoop() {
        while(!mSocket.isClosed()) {
            try {
                Socket client = mSocket.accept();
                Thread t = new Thread(() -> handle(client), "LocalSkinServer-client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if(!mSocket.isClosed()) Log.w(TAG, "accept failed", e);
            }
        }
    }

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        for(int c; (c = in.read()) != -1; ) {
            if(c == '\n') break;
            if(c != '\r') sb.append((char) c);
            if(sb.length() > 8192) throw new IOException("Line too long");
        }
        return sb.toString();
    }

    private void handle(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(10000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            String requestLine = readLine(in);
            String[] parts = requestLine.split(" ");
            if(parts.length < 2) return;
            String method = parts[0];
            String path = parts[1];
            int query = path.indexOf('?');
            if(query >= 0) path = path.substring(0, query);

            int contentLength = 0;
            for(String line = readLine(in); !line.isEmpty(); line = readLine(in)) {
                if(line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    try { contentLength = Integer.parseInt(line.substring(15).trim()); } catch (NumberFormatException ignored) {}
                }
            }
            byte[] body = new byte[Math.min(Math.max(contentLength, 0), 65536)];
            for(int read = 0; read < body.length; ) {
                int n = in.read(body, read, body.length - read);
                if(n < 0) break;
                read += n;
            }
            route(method, path, new String(body, StandardCharsets.UTF_8), out);
        } catch (Exception e) {
            Log.d(TAG, "request failed", e);
        }
    }

    private void route(String method, String path, String body, OutputStream out) throws IOException, JSONException {
        if(path.equals("/") || path.isEmpty()) {
            JSONObject meta = new JSONObject()
                    .put("serverName", "MojoLauncher local skins")
                    .put("implementationName", "mojo-local-skin-server")
                    .put("implementationVersion", "1.0");
            JSONObject root = new JSONObject().put("meta", meta)
                    .put("skinDomains", new JSONArray().put("127.0.0.1"));
            send(out, 200, "application/json", root.toString().getBytes(StandardCharsets.UTF_8));
        } else if(path.startsWith("/sessionserver/session/minecraft/profile/")) {
            String id = path.substring(path.lastIndexOf('/') + 1).replace("-", "").toLowerCase(Locale.ROOT);
            if(!id.equals(mUuid)) { send(out, 204, null, null); return; }
            send(out, 200, "application/json", profile().toString().getBytes(StandardCharsets.UTF_8));
        } else if(method.equals("POST") && path.equals("/api/profiles/minecraft")) {
            JSONArray result = new JSONArray();
            if(body.contains("\"" + mUsername + "\"")) {
                result.put(new JSONObject().put("id", mUuid).put("name", mUsername));
            }
            send(out, 200, "application/json", result.toString().getBytes(StandardCharsets.UTF_8));
        } else if(path.equals("/textures/skin.png")) {
            File file = mSkinFile;
            if(file == null || !file.isFile()) { send(out, 404, null, null); return; }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[4096];
                for(int n; (n = fis.read(buf)) != -1; ) bytes.write(buf, 0, n);
            }
            send(out, 200, "image/png", bytes.toByteArray());
        } else {
            send(out, 404, "application/json", "{\"error\":\"NotFound\"}".getBytes(StandardCharsets.UTF_8));
        }
    }

    private JSONObject profile() throws JSONException {
        JSONObject skin = new JSONObject().put("url", baseUrl() + "/textures/skin.png");
        if(mSlim) skin.put("metadata", new JSONObject().put("model", "slim"));
        JSONObject textures = new JSONObject()
                .put("timestamp", System.currentTimeMillis())
                .put("profileId", mUuid)
                .put("profileName", mUsername)
                .put("textures", new JSONObject().put("SKIN", skin));
        String value = Base64.encodeToString(textures.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return new JSONObject().put("id", mUuid).put("name", mUsername)
                .put("properties", new JSONArray().put(new JSONObject().put("name", "textures").put("value", value)));
    }

    private static void send(OutputStream out, int code, String type, byte[] body) throws IOException {
        String status = code == 200 ? "OK" : code == 204 ? "No Content" : "Not Found";
        int length = body == null ? 0 : body.length;
        StringBuilder head = new StringBuilder("HTTP/1.1 " + code + " " + status + "\r\n");
        if(type != null) head.append("Content-Type: ").append(type).append("\r\n");
        head.append("Content-Length: ").append(length).append("\r\nConnection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        if(body != null) out.write(body);
        out.flush();
    }
}

package git.artdeell.mojo.lan;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import net.burningtnt.terracotta.TerracottaAndroidAPI;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;

/**
 * Wraps the Terracotta (EasyTier based) multiplayer core. It lives in the launcher process, next
 * to {@link LanVpnService}, because the VPN request has to be answered from that same process.
 */
public class TerracottaManager {
    public static final String TAG = "TerracottaManager";

    /** Immutable snapshot of what Terracotta reported. */
    public static class State {
        public final String name;      // waiting, host-scanning, host-starting, host-ok, guest-connecting, guest-starting, guest-ok, exception
        public final int index;
        public final String room;      // host-ok only
        public final String url;       // guest-ok only
        public final int exceptionType;
        public final int players;

        State(JSONObject json) {
            name = json.optString("state", "waiting");
            index = json.optInt("index", -1);
            room = json.isNull("room") ? null : json.optString("room", null);
            url = json.isNull("url") ? null : json.optString("url", null);
            exceptionType = json.optInt("type", -1);
            JSONArray profiles = json.optJSONArray("profiles");
            players = profiles == null ? 0 : profiles.length();
        }

        public boolean isWaiting() { return name.equals("waiting"); }
        public boolean isHost() { return name.startsWith("host"); }
        public boolean isGuest() { return name.startsWith("guest"); }
    }

    public interface Listener {
        void onStateChanged(State state);
    }

    /** Asked to show the system VPN permission dialog (needs an Activity). */
    public interface VpnPermissionHandler {
        /** @return true if the request is being handled, false to reject it */
        boolean requestVpnPermission(android.content.Intent prepareIntent);
    }

    private static TerracottaManager sInstance;

    public static synchronized TerracottaManager get() {
        if(sInstance == null) sInstance = new TerracottaManager();
        return sInstance;
    }

    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> mListeners = new CopyOnWriteArrayList<>();
    private volatile VpnPermissionHandler mVpnHandler;
    private volatile State mState;
    private volatile boolean mInitialized;
    private volatile String mVersionInfo = "";
    private Context mAppContext;

    public static boolean isSupported() {
        return android.os.Build.VERSION.SDK_INT >= 26;
    }

    public State getState() { return mState; }
    public String getVersionInfo() { return mVersionInfo; }
    public void addListener(Listener l) { mListeners.add(l); }
    public void removeListener(Listener l) { mListeners.remove(l); }
    public void setVpnPermissionHandler(VpnPermissionHandler h) { mVpnHandler = h; }

    /** @throws Exception if the native library is missing or fails to start */
    public synchronized void ensureInitialized(Context context) throws Exception {
        if(mInitialized) return;
        if(!isSupported()) throw new IllegalStateException("Android 8.0 or newer is required");
        mAppContext = context.getApplicationContext();
        File root = new File(mAppContext.getFilesDir(), "terracotta");
        root.mkdirs();
        File log = new File(root, "terracotta.log");
        TerracottaAndroidAPI.Metadata metadata;
        try {
            metadata = TerracottaAndroidAPI.initialize(root.getAbsolutePath(), log.getAbsolutePath(),
                    () -> mMain.post(this::onVpnRequested));
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            throw new IllegalStateException("libterracotta.so is missing in this build", e);
        }
        mVersionInfo = "Terracotta " + metadata.getTerracottaVersion() + " / EasyTier " + metadata.getEasyTierVersion();
        mInitialized = true;
        Thread poller = new Thread(this::pollLoop, "TerracottaPoller");
        poller.setDaemon(true);
        poller.start();
    }

    private void pollLoop() {
        int lastIndex = Integer.MIN_VALUE;
        while(true) {
            try {
                State state = new State(new JSONObject(TerracottaAndroidAPI.getState()));
                if(state.index != lastIndex) {
                    lastIndex = state.index;
                    mState = state;
                    for(Listener l : mListeners) mMain.post(() -> l.onStateChanged(state));
                    LanVpnService.refreshNotification();
                }
                Thread.sleep(400);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                Log.w(TAG, "Could not read the Terracotta state", e);
                try { Thread.sleep(1500); } catch (InterruptedException ie) { return; }
            }
        }
    }

    private void onVpnRequested() {
        try {
            android.content.Intent prepare = android.net.VpnService.prepare(mAppContext);
            if(prepare == null) {
                startVpnService();
                return;
            }
            VpnPermissionHandler handler = mVpnHandler;
            if(handler == null || !handler.requestVpnPermission(prepare)) rejectVpn();
        } catch (Exception e) {
            Log.w(TAG, "VPN request failed", e);
            rejectVpn();
        }
    }

    /** Called after the user granted the VPN permission. */
    public void startVpnService() {
        android.content.Intent intent = new android.content.Intent(mAppContext, LanVpnService.class)
                .setAction(LanVpnService.ACTION_START);
        mAppContext.startForegroundService(intent);
    }

    /** Called when the user denied the VPN permission. */
    public void rejectVpn() {
        try {
            TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
        } catch (IllegalStateException ignored) {
        }
        MojoApplication.sExecutorService.execute(() -> {
            try { TerracottaAndroidAPI.setWaiting(); } catch (Exception ignored) {}
        });
    }

    public void stop() {
        if(!mInitialized) return;
        MojoApplication.sExecutorService.execute(() -> {
            try { TerracottaAndroidAPI.setWaiting(); } catch (Exception e) { Log.w(TAG, "setWaiting failed", e); }
        });
        if(mAppContext != null) mAppContext.stopService(new android.content.Intent(mAppContext, LanVpnService.class));
    }

    /** Starts hosting: the world has to be opened to LAN from inside the game afterwards. */
    public void startHost(String player) throws Exception {
        List<String> nodes = fetchNodes();
        TerracottaAndroidAPI.setScanning(null, player, nodes);
    }

    /** @return false when the room code is not valid */
    public boolean startGuest(String room, String player) throws Exception {
        if(TerracottaAndroidAPI.parseRoomCode(room) == null) return false;
        List<String> nodes = fetchNodes();
        return TerracottaAndroidAPI.setGuesting(room, player, nodes);
    }

    private List<String> fetchNodes() {
        List<String> result = new ArrayList<>();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("https://terracotta.glavo.site/nodes").openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            try (InputStream in = c.getInputStream()) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                for(int n; (n = in.read(buf)) != -1; ) out.write(buf, 0, n);
                JSONArray array = new JSONArray(out.toString("UTF-8"));
                boolean china = Locale.getDefault().getCountry().equalsIgnoreCase("CN");
                for(int i = 0; i < array.length(); i++) {
                    JSONObject node = array.optJSONObject(i);
                    if(node == null) continue;
                    String url = node.optString("url", "");
                    String region = node.optString("region", "");
                    if(url.isEmpty()) continue;
                    // Nodes tagged CN are only meant for mainland China
                    if(!region.isEmpty() && !(china && region.equalsIgnoreCase("CN"))) continue;
                    result.add(url);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not fetch the node list, using Terracotta defaults", e);
        } finally {
            if(c != null) c.disconnect();
        }
        return result;
    }

    /** Short Spanish description of a state, for the screen and the notification. */
    public static String describe(Context context, State state) {
        if(state == null || state.isWaiting()) return context.getString(R.string.fork_lan_state_waiting);
        switch (state.name) {
            case "host-scanning": return context.getString(R.string.fork_lan_state_host_scanning);
            case "host-starting": return context.getString(R.string.fork_lan_state_host_starting);
            case "host-ok": return context.getString(R.string.fork_lan_state_host_ok, state.room == null ? "" : state.room, state.players);
            case "guest-connecting":
            case "guest-starting": return context.getString(R.string.fork_lan_state_guest_connecting);
            case "guest-ok": return context.getString(R.string.fork_lan_state_guest_ok, state.url == null ? "" : state.url);
            case "exception": return context.getString(R.string.fork_lan_state_error);
            default: return state.name;
        }
    }
}

package git.artdeell.mojo.lan;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.burningtnt.terracotta.TerracottaAndroidAPI;

import java.io.IOException;

import git.artdeell.mojo.R;

/**
 * VPN used by Terracotta/EasyTier. The notification shows the current room code or the address
 * to connect to, so it can be read without leaving the game.
 * Adapted from the VpnService of Zalith Launcher 2 (GPL-3.0), which is based on FoldCraftLauncher.
 */
@SuppressLint("VpnServicePolicy")
public class LanVpnService extends VpnService {
    private static final String TAG = "LanVpnService";
    private static final String CHANNEL_ID = "mojo_lan_vpn";
    private static final int NOTIFICATION_ID = 7421;
    public static final String ACTION_START = "git.artdeell.mojo.lan.START";

    private static volatile LanVpnService sInstance;
    private ParcelFileDescriptor mInterface;

    /** Called from any thread when the multiplayer state changes. */
    public static void refreshNotification() {
        LanVpnService service = sInstance;
        if(service == null) return;
        NotificationManager manager = (NotificationManager) service.getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, service.buildNotification());
    }

    private void ensureChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if(manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID,
                    getString(R.string.fork_lan_notification_channel), NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification buildNotification() {
        String text = TerracottaManager.describe(this, TerracottaManager.get().getState());
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.fork_lan_notification_title))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        sInstance = this;
        ensureChannel();
        try {
            Notification notification = buildNotification();
            if(Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.w(TAG, "Cannot start the VPN foreground service", e);
            rejectPending();
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        if(intent == null || !ACTION_START.equals(intent.getAction())) {
            // The system recreated the service: there is no pending request to answer
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        Builder builder = new Builder().setSession("MojoLauncher LAN");
        try {
            // The game and the launcher must not be routed through the tunnel they create
            builder.addDisallowedApplication(getPackageName());
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        TerracottaAndroidAPI.VpnServiceRequest request;
        try {
            request = TerracottaAndroidAPI.getPendingVpnServiceRequest();
        } catch (IllegalStateException e) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        try {
            mInterface = request.startVpnService(builder);
        } catch (RuntimeException e) {
            Log.w(TAG, "Could not establish the VPN", e);
            request.reject();
            stopSelf();
            return Service.START_NOT_STICKY;
        }
        return Service.START_STICKY;
    }

    private void rejectPending() {
        try {
            TerracottaAndroidAPI.getPendingVpnServiceRequest().reject();
        } catch (IllegalStateException ignored) {
        }
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN revoked by the system or the user");
        TerracottaManager.get().stop();
        cleanup();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        sInstance = null;
        stopForeground(true);
        if(mInterface != null) {
            try { mInterface.close(); } catch (IOException ignored) {}
            mInterface = null;
        }
    }
}

package com.divinedoll.launcher.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;

/** Records the screen (video only) with MediaProjection while the game runs. */
public class RecorderService extends Service {
    public static final String ACTION_START = "com.divinedoll.launcher.REC_START";
    public static final String ACTION_STOP = "com.divinedoll.launcher.REC_STOP";
    public static final String EXTRA_CODE = "code";
    public static final String EXTRA_DATA = "data";
    private static final int NOTIFICATION_ID = 40;

    public static volatile boolean sRecording;

    private MediaProjection mProjection;
    private MediaRecorder mRecorder;
    private VirtualDisplay mDisplay;
    private File mFile;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent != null && ACTION_STOP.equals(intent.getAction())) {
            finishRecording();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(intent == null || sRecording) return START_NOT_STICKY;

        Tools.buildNotificationChannel(getApplicationContext());
        Intent stop = new Intent(this, RecorderService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 41, stop, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, getString(R.string.notif_channel_id))
                .setContentTitle(getString(R.string.fork_rec_notif))
                .setContentText(getString(R.string.fork_rec_tap_stop))
                .setContentIntent(stopIntent)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.fork_rec_stop), stopIntent)
                .setSmallIcon(R.drawable.notif_icon)
                .setOngoing(true)
                .build();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        try {
            begin(intent.getIntExtra(EXTRA_CODE, 0), intent.getParcelableExtra(EXTRA_DATA));
            sRecording = true;
            Toast.makeText(this, R.string.fork_rec_started, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.fork_rec_error, String.valueOf(e.getMessage())), Toast.LENGTH_LONG).show();
            release();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @SuppressWarnings("deprecation")
    private void begin(int code, Intent data) throws Exception {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mProjection = manager.getMediaProjection(code, data);
        if(mProjection == null) throw new IllegalStateException("No permission");
        // Android 14 requires a callback before the virtual display is created
        mProjection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                finishRecording();
            }
        }, null);

        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        float scale = Math.min(1f, 1280f / Math.max(dm.widthPixels, dm.heightPixels));
        int width = ((int) (dm.widthPixels * scale)) & ~1;
        int height = ((int) (dm.heightPixels * scale)) & ~1;

        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "DivineLauncher");
        if(!dir.isDirectory() && !dir.mkdirs()) throw new IllegalStateException("Cannot create " + dir);
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        mFile = new File(dir, "rec-" + stamp + ".mp4");

        mRecorder = new MediaRecorder();
        mRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mRecorder.setVideoSize(width, height);
        mRecorder.setVideoFrameRate(30);
        mRecorder.setVideoEncodingBitRate(8_000_000);
        mRecorder.setOutputFile(mFile.getAbsolutePath());
        mRecorder.prepare();
        mDisplay = mProjection.createVirtualDisplay("MojoRecorder", width, height, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mRecorder.getSurface(), null, null);
        mRecorder.start();
    }

    private void release() {
        sRecording = false;
        if(mRecorder != null) {
            try { mRecorder.release(); } catch (Exception ignored) {}
            mRecorder = null;
        }
        if(mDisplay != null) {
            mDisplay.release();
            mDisplay = null;
        }
        if(mProjection != null) {
            try { mProjection.stop(); } catch (Exception ignored) {}
            mProjection = null;
        }
    }

    private synchronized void finishRecording() {
        if(!sRecording) return;
        boolean ok = true;
        try {
            mRecorder.stop();
        } catch (Exception e) {
            ok = false; // stopped right after starting: no frames
        }
        release();
        File file = mFile;
        mFile = null;
        if(file == null) return;
        if(!ok || file.length() == 0) {
            file.delete();
            return;
        }
        String shown = file.getAbsolutePath();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DISPLAY_NAME, file.getName());
                values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
                values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DivineLauncher");
                android.net.Uri uri = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
                if(uri != null) {
                    try(OutputStream out = getContentResolver().openOutputStream(uri); FileInputStream in = new FileInputStream(file)) {
                        byte[] buf = new byte[64 * 1024];
                        int n;
                        while(out != null && (n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                    file.delete();
                    shown = "Movies/DivineLauncher/" + file.getName();
                }
            } catch (Exception ignored) {
                // the copy in the app folder is kept
            }
        }
        final String message = getString(R.string.fork_rec_saved, shown);
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show());
    }

    @Override
    public void onDestroy() {
        finishRecording();
        release();
        super.onDestroy();
    }
}

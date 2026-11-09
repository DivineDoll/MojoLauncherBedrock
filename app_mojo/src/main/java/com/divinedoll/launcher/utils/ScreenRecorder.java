package com.divinedoll.launcher.utils;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;

import androidx.core.content.ContextCompat;

import com.divinedoll.launcher.services.RecorderService;

/** Small helper around {@link RecorderService}. */
public final class ScreenRecorder {
    private ScreenRecorder() {}

    public static boolean isRecording() {
        return RecorderService.sRecording;
    }

    public static Intent permissionIntent(Context c) {
        return ((MediaProjectionManager) c.getSystemService(Context.MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent();
    }

    public static void start(Context c, int resultCode, Intent data) {
        Intent i = new Intent(c, RecorderService.class).setAction(RecorderService.ACTION_START)
                .putExtra(RecorderService.EXTRA_CODE, resultCode)
                .putExtra(RecorderService.EXTRA_DATA, data);
        ContextCompat.startForegroundService(c, i);
    }

    public static void stop(Context c) {
        c.startService(new Intent(c, RecorderService.class).setAction(RecorderService.ACTION_STOP));
    }
}

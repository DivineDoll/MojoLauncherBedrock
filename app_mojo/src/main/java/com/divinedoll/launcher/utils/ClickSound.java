package com.divinedoll.launcher.utils;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.widget.Button;

import com.divinedoll.launcher.prefs.LauncherPreferences;

/** A short click made by the launcher itself, so no sound file is needed. It can be turned off in More options. */
public class ClickSound {
    public static final String PREF = "click_sound";
    private static final int RATE = 22050;
    private static AudioTrack track;

    public static boolean enabled() {
        try {
            return LauncherPreferences.DEFAULT_PREF.getBoolean(PREF, true);
        } catch (Exception e) {
            return true;
        }
    }

    public static synchronized void play() {
        if(!enabled()) return;
        try {
            if(track == null) track = build();
            track.stop();
            track.reloadStaticData();
            track.play();
        } catch (Exception e) {
            track = null;
        }
    }

    @SuppressWarnings("deprecation")
    private static AudioTrack build() {
        int n = RATE * 35 / 1000;
        short[] data = new short[n];
        for(int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double envelope = 1.0 - i / (double) n;
            double tone = Math.sin(2 * Math.PI * 660 * t) + 0.5 * Math.sin(2 * Math.PI * 990 * t);
            data[i] = (short) (tone * envelope * envelope * 5500);
        }
        AudioTrack audio = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, n * 2, AudioTrack.MODE_STATIC);
        audio.write(data, 0, n);
        return audio;
    }

    /** Button of the fork screens that plays the click. */
    public static class SoundButton extends Button {
        public SoundButton(android.content.Context context) {
            super(context);
        }

        @Override
        public boolean performClick() {
            play();
            return super.performClick();
        }
    }
}

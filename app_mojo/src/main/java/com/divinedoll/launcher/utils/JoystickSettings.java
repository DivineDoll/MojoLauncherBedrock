package com.divinedoll.launcher.utils;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.input.ControlLayout;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Dead zone of the on-screen joystick (the movement stick must be pushed this far before it reacts). */
public final class JoystickSettings {
    private JoystickSettings() {}

    public static final String PREF_DEADZONE = "joystick_deadzone";
    public static final int DEFAULT_DEADZONE = 35;

    public static int deadzone() {
        return LauncherPreferences.DEFAULT_PREF.getInt(PREF_DEADZONE, DEFAULT_DEADZONE);
    }

    public static void show(Context c, ControlLayout layout) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = ForkUi.dp(c, 16);
        box.setPadding(pad, pad, pad, 0);
        TextView value = ForkUi.text(c, c.getString(R.string.fork_joy_value, deadzone()), 14, false);
        box.addView(value);
        SeekBar bar = new SeekBar(c);
        bar.setMax(70);
        bar.setProgress(deadzone());
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                value.setText(c.getString(R.string.fork_joy_value, progress));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_DEADZONE, seekBar.getProgress()).apply();
                if(layout != null) layout.applyJoystickDeadzone();
            }
        });
        box.addView(bar);
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_joy_title)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.fork_mg_reset, (d, w) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putInt(PREF_DEADZONE, DEFAULT_DEADZONE).apply();
                    if(layout != null) layout.applyJoystickDeadzone();
                })
                .show();
    }
}

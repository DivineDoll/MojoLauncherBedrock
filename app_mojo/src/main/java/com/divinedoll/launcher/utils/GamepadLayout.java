package com.divinedoll.launcher.utils;

import android.content.Context;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.Locale;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.prefs.LauncherPreferences;

/** Detects which family a connected controller belongs to (Xbox, PlayStation, Nintendo...) and can swap A/B and X/Y. */
public final class GamepadLayout {
    private GamepadLayout() {}

    public enum Family {
        XBOX(R.string.fork_pad_xbox), PLAYSTATION(R.string.fork_pad_ps), NINTENDO(R.string.fork_pad_nintendo),
        STEAM(R.string.fork_pad_steam), STADIA(R.string.fork_pad_stadia), GENERIC(R.string.fork_pad_generic);

        public final int label;

        Family(int label) {
            this.label = label;
        }
    }

    private static final String PREF_SWAP = "gamepad_swap";
    private static final String PREF_LAST = "gamepad_last";

    public static Family detect(InputDevice device) {
        if(device == null) return Family.GENERIC;
        String name = device.getName() == null ? "" : device.getName().toLowerCase(Locale.ROOT);
        switch (device.getVendorId()) {
            case 0x045e: return Family.XBOX;
            case 0x054c: return Family.PLAYSTATION;
            case 0x057e: return Family.NINTENDO;
            case 0x28de: return Family.STEAM;
            case 0x18d1: if(name.contains("stadia")) return Family.STADIA; break;
            default: break;
        }
        if(name.contains("xbox") || name.contains("x-box") || name.contains("xinput")) return Family.XBOX;
        if(name.contains("dualsense") || name.contains("dualshock") || name.contains("playstation")
                || name.contains("ps4") || name.contains("ps5") || name.contains("wireless controller")) return Family.PLAYSTATION;
        if(name.contains("nintendo") || name.contains("pro controller") || name.contains("joy-con") || name.contains("switch")) return Family.NINTENDO;
        if(name.contains("steam")) return Family.STEAM;
        if(name.contains("stadia")) return Family.STADIA;
        return Family.GENERIC;
    }

    private static boolean shouldSwap(Family family) {
        String mode = LauncherPreferences.DEFAULT_PREF.getString(PREF_SWAP, "off");
        return "on".equals(mode) || ("auto".equals(mode) && family == Family.NINTENDO);
    }

    private static int swapped(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BUTTON_A: return KeyEvent.KEYCODE_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_B: return KeyEvent.KEYCODE_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_X: return KeyEvent.KEYCODE_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_Y: return KeyEvent.KEYCODE_BUTTON_X;
            default: return code;
        }
    }

    /** Returns the event to send: the same one, or a copy with A/B and X/Y exchanged. */
    public static KeyEvent adapt(KeyEvent event) {
        int code = event.getKeyCode();
        int other = swapped(code);
        if(other == code || !shouldSwap(detect(event.getDevice()))) return event;
        return new KeyEvent(event.getDownTime(), event.getEventTime(), event.getAction(), other, event.getRepeatCount(),
                event.getMetaState(), event.getDeviceId(), event.getScanCode(), event.getFlags(), event.getSource());
    }

    /** Remembers the controller and tells the user which layout was detected. */
    public static void announce(Context c, InputDevice device) {
        if(device == null) return;
        Family family = detect(device);
        String name = String.valueOf(device.getName());
        LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_LAST, name + "|" + c.getString(family.label)).apply();
        Toast.makeText(c, c.getString(R.string.fork_pad_detected, name, c.getString(family.label)), Toast.LENGTH_LONG).show();
    }

    public static void show(Context c) {
        String last = LauncherPreferences.DEFAULT_PREF.getString(PREF_LAST, "");
        String info;
        if(last.isEmpty()) {
            info = c.getString(R.string.fork_pad_none);
        } else {
            String[] parts = last.split("\\|", 2);
            info = c.getString(R.string.fork_pad_detected, parts[0], parts.length > 1 ? parts[1] : "");
        }
        String[] modes = {"off", "on", "auto"};
        String[] labels = {c.getString(R.string.fork_pad_swap_off), c.getString(R.string.fork_pad_swap_on), c.getString(R.string.fork_pad_swap_auto)};
        String current = LauncherPreferences.DEFAULT_PREF.getString(PREF_SWAP, "off");
        int checked = 0;
        for(int i = 0; i < modes.length; i++) if(modes[i].equals(current)) checked = i;
        new AlertDialog.Builder(c)
                .setTitle(R.string.fork_pad_title)
                .setMessage(info)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    LauncherPreferences.DEFAULT_PREF.edit().putString(PREF_SWAP, modes[which]).apply();
                    d.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}

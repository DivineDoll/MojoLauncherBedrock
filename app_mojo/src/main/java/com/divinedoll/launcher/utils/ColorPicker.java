package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.divinedoll.launcher.R;

/** Color picker with a color wheel, a brightness slider, a hex code field and quick colors. */
public class ColorPicker {
    public interface Callback {
        void picked(int color);
    }

    private static final int[] QUICK = {0xFF52A535, 0xFF2E8BE6, 0xFFE04646, 0xFF9B59E8, 0xFFE8B02E, 0xFF29B6C5,
            0xFFFFFFFF, 0xFF1E1E1E};

    public static void show(Context c, int titleRes, int current, int defaultColor, Callback callback) {
        final float[] hsv = new float[3];
        Color.colorToHSV(current, hsv);
        final boolean[] busy = {false};

        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        int pad = ForkUi.dp(c, 16);
        root.setPadding(pad, ForkUi.dp(c, 8), pad, 0);

        ColorWheelView wheel = new ColorWheelView(c);
        wheel.set(hsv[0], hsv[1], hsv[2]);
        root.addView(wheel, new LinearLayout.LayoutParams(ForkUi.dp(c, 190), ForkUi.dp(c, 190)));

        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sideLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sideLp.leftMargin = ForkUi.dp(c, 12);
        root.addView(side, sideLp);

        View preview = new View(c);
        side.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ForkUi.dp(c, 34)));

        TextView brightnessLabel = new TextView(c);
        brightnessLabel.setText(R.string.fork_picker_brightness);
        brightnessLabel.setPadding(0, ForkUi.dp(c, 8), 0, 0);
        side.addView(brightnessLabel);
        SeekBar brightness = new SeekBar(c);
        brightness.setMax(100);
        brightness.setProgress(Math.round(hsv[2] * 100f));
        side.addView(brightness);

        EditText hex = new EditText(c);
        hex.setHint(R.string.fork_picker_hex_hint);
        hex.setSingleLine(true);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        side.addView(hex);

        LinearLayout quick = new LinearLayout(c);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickLp.topMargin = ForkUi.dp(c, 8);
        side.addView(quick, quickLp);

        Runnable refresh = () -> {
            busy[0] = true;
            int color = Color.HSVToColor(hsv);
            preview.setBackground(ForkUi.box(c, color, 0xFFAAAAAA, 1));
            wheel.set(hsv[0], hsv[1], hsv[2]);
            brightness.setProgress(Math.round(hsv[2] * 100f));
            String text = String.format("#%06X", color & 0xFFFFFF);
            if(!text.equalsIgnoreCase(hex.getText().toString())) hex.setText(text);
            busy[0] = false;
        };

        wheel.setListener((hue, sat) -> {
            hsv[0] = hue;
            hsv[1] = sat;
            refresh.run();
        });
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if(!fromUser || busy[0]) return;
                hsv[2] = progress / 100f;
                refresh.run();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int co, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int be, int co) {}
            @Override public void afterTextChanged(Editable e) {
                if(busy[0]) return;
                String text = e.toString().trim();
                if(!text.startsWith("#")) text = "#" + text;
                if(text.length() != 7) return;
                try {
                    Color.colorToHSV(Color.parseColor(text), hsv);
                    busy[0] = true;
                    preview.setBackground(ForkUi.box(c, Color.HSVToColor(hsv), 0xFFAAAAAA, 1));
                    wheel.set(hsv[0], hsv[1], hsv[2]);
                    brightness.setProgress(Math.round(hsv[2] * 100f));
                    busy[0] = false;
                } catch (IllegalArgumentException ignored) {}
            }
        });

        for(int color : QUICK) {
            View swatch = new View(c);
            swatch.setBackground(ForkUi.box(c, color, 0xFFAAAAAA, 1));
            swatch.setOnClickListener(v -> {
                Color.colorToHSV(color, hsv);
                refresh.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ForkUi.dp(c, 24), 1f);
            lp.rightMargin = ForkUi.dp(c, 3);
            quick.addView(swatch, lp);
        }
        refresh.run();

        new AlertDialog.Builder(c)
                .setTitle(titleRes)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.fork_picker_reset, (d, w) -> callback.picked(defaultColor))
                .setPositiveButton(android.R.string.ok, (d, w) -> callback.picked(Color.HSVToColor(hsv) | 0xFF000000))
                .show();
    }
}

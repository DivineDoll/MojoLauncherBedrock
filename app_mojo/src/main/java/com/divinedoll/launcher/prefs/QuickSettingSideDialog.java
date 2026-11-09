package com.divinedoll.launcher.prefs;

import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_BUTTON_TRANSPARENCY;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_DISABLE_GESTURES;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_ENABLE_GYRO;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_GYRO_INVERT_X;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_GYRO_INVERT_Y;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_GYRO_SENSITIVITY;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_LONGPRESS_TRIGGER;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_MOUSESPEED;
import static com.divinedoll.launcher.prefs.LauncherPreferences.PREF_SCALE_FACTOR;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.kdt.CustomSeekbar;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.utils.FpsOverlay;
import com.divinedoll.launcher.utils.interfaces.SimpleSeekBarListener;

/**
 * Side dialog for quick settings that you can change in game
 * The implementation has to take action on some preference changes
 */
public abstract class QuickSettingSideDialog extends com.kdt.SideDialogView {

    private SharedPreferences.Editor mEditor;
    private final ViewGroup mControlsParent;

    private SwitchCompat mGyroSwitch, mGyroXSwitch, mGyroYSwitch, mGestureSwitch;
    private SwitchCompat mFpsSwitch, mMemorySwitch, mBatterySwitch;
    private boolean mOriginalFps, mOriginalMemory, mOriginalBattery;
    private CustomSeekbar mGyroSensitivityBar, mMouseSpeedBar, mGestureDelayBar, mResolutionBar, mTransparencyBar;
    private TextView mGyroSensitivityText, mGyroSensitivityDisplayText, mMouseSpeedText, mGestureDelayText, mGestureDelayDisplayText, mResolutionText, mTransparencyText;

    private boolean mOriginalGyroEnabled, mOriginalGyroXEnabled, mOriginalGyroYEnabled, mOriginalGestureDisabled;
    private float mOriginalGyroSensitivity, mOriginalMouseSpeed, mOriginalResolution;
    private int mOriginalGestureDelay;
    private short mOriginalTransparency;

    public QuickSettingSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_quick_setting);
        mControlsParent = parent;
        setTitle(R.string.quick_setting_title);
        setupCancelButton();
    }

    @Override
    protected void onInflate() {
        bindLayout();
        com.divinedoll.launcher.utils.McStyle.applyFont(mDialogContent);
        Tools.runOnUiThread(() -> {
            this.setupListeners();
            this.updateGyroCompatibility();
        });
    }

    @Override
    protected void onDestroy() {
        removeListeners();
    }

    private void bindLayout() {
        // Bind layout elements
        mGyroSwitch = mDialogContent.findViewById(R.id.checkboxGyro);
        mGyroXSwitch = mDialogContent.findViewById(R.id.checkboxGyroX);
        mGyroYSwitch = mDialogContent.findViewById(R.id.checkboxGyroY);
        mGestureSwitch = mDialogContent.findViewById(R.id.checkboxGesture);
        mFpsSwitch = mDialogContent.findViewById(R.id.checkboxQuickFps);
        mMemorySwitch = mDialogContent.findViewById(R.id.checkboxQuickMemory);
        mBatterySwitch = mDialogContent.findViewById(R.id.checkboxQuickBattery);
        android.view.View record = mDialogContent.findViewById(R.id.buttonQuickRecord);
        if(record != null) {
            ((android.widget.Button) record).setText(com.divinedoll.launcher.utils.ScreenRecorder.isRecording() ? R.string.fork_rec_stop : R.string.fork_rec_start);
            record.setOnClickListener(v -> {
                onRecordClicked();
                cancel();
            });
        }
        android.view.View joystick = mDialogContent.findViewById(R.id.buttonQuickJoystick);
        if(joystick != null) joystick.setOnClickListener(v -> com.divinedoll.launcher.utils.JoystickSettings.show(mControlsParent.getContext(),
                mControlsParent instanceof com.divinedoll.launcher.input.ControlLayout ? (com.divinedoll.launcher.input.ControlLayout) mControlsParent : null));
        android.view.View pad = mDialogContent.findViewById(R.id.buttonQuickGamepad);
        if(pad != null) pad.setOnClickListener(v -> com.divinedoll.launcher.utils.GamepadLayout.show(mControlsParent.getContext()));
        android.view.View shot = mDialogContent.findViewById(R.id.buttonQuickShot);
        if(shot != null) shot.setOnClickListener(v -> {
            com.divinedoll.launcher.game.platform.Platform.PLATFORM.sendKeyEvent(291, true, com.divinedoll.launcher.CallbackBridge.getCurrentMods());
            com.divinedoll.launcher.game.platform.Platform.PLATFORM.sendKeyEvent(291, false, com.divinedoll.launcher.CallbackBridge.getCurrentMods());
            cancel();
        });
        android.view.View mirror = mDialogContent.findViewById(R.id.buttonQuickMirror);
        if(mirror != null) mirror.setOnClickListener(v -> {
            if(mControlsParent instanceof com.divinedoll.launcher.input.ControlLayout) {
                ((com.divinedoll.launcher.input.ControlLayout) mControlsParent).toggleMirror();
            }
        });
        android.view.View hide = mDialogContent.findViewById(R.id.buttonQuickHideControls);
        if(hide != null) hide.setOnClickListener(v -> {
            if(mControlsParent instanceof com.divinedoll.launcher.input.ControlLayout) {
                ((com.divinedoll.launcher.input.ControlLayout) mControlsParent).toggleHud();
            }
            cancel();
        });

        mGyroSensitivityBar = mDialogContent.findViewById(R.id.editGyro_seekbar);
        mMouseSpeedBar = mDialogContent.findViewById(R.id.editMouseSpeed_seekbar);
        mGestureDelayBar = mDialogContent.findViewById(R.id.editGestureDelay_seekbar);
        mResolutionBar = mDialogContent.findViewById(R.id.editResolution_seekbar);
        mTransparencyBar = mDialogContent.findViewById(R.id.buttonTransparency_seekBar);

        mGyroSensitivityText = mDialogContent.findViewById(R.id.editGyro_textView_percent);
        mGyroSensitivityDisplayText = mDialogContent.findViewById(R.id.editGyro_textView);
        mMouseSpeedText = mDialogContent.findViewById(R.id.editMouseSpeed_textView_percent);
        mGestureDelayText = mDialogContent.findViewById(R.id.editGestureDelay_textView_percent);
        mGestureDelayDisplayText = mDialogContent.findViewById(R.id.editGestureDelay_textView);
        mResolutionText = mDialogContent.findViewById(R.id.editResolution_textView_percent);
        mTransparencyText = mDialogContent.findViewById(R.id.buttonTransparency_textView_percent);

        styleSections();
    }

    /** Cards with an accent border, accent titles and themed tool buttons. */
    private void styleSections() {
        android.content.Context ctx = mDialogContent.getContext();
        int border = (com.divinedoll.launcher.utils.AccentColor.get() & 0x00FFFFFF) | 0x99000000;
        int[] cards = {R.id.quickCard1, R.id.quickCard2, R.id.quickCard3, R.id.quickCard4, R.id.quickCard5};
        int[] titles = {R.id.quickTitle1, R.id.quickTitle2, R.id.quickTitle3, R.id.quickTitle4, R.id.quickTitle5};
        for(int id : cards) {
            android.view.View card = mDialogContent.findViewById(id);
            if(card != null) card.setBackground(com.divinedoll.launcher.utils.ForkUi.box(ctx, 0x40000000, border, 1));
        }
        for(int id : titles) {
            android.widget.TextView title = mDialogContent.findViewById(id);
            if(title != null) title.setTextColor(com.divinedoll.launcher.utils.AccentColor.light());
        }
        com.divinedoll.launcher.utils.McStyle.applyFont(mDialogContent);
        int[] tools = {R.id.buttonQuickHideControls, R.id.buttonQuickMirror, R.id.buttonQuickRecord,
                R.id.buttonQuickJoystick, R.id.buttonQuickGamepad, R.id.buttonQuickShot};
        for(int id : tools) {
            android.view.View b = mDialogContent.findViewById(id);
            if(b instanceof android.widget.Button) com.divinedoll.launcher.utils.McStyle.button((android.widget.Button) b, false);
        }
    }

    private void setupListeners() {
        mEditor = LauncherPreferences.DEFAULT_PREF.edit();

        mOriginalGyroEnabled = PREF_ENABLE_GYRO;
        mOriginalGyroXEnabled = PREF_GYRO_INVERT_X;
        mOriginalGyroYEnabled = PREF_GYRO_INVERT_Y;
        mOriginalGestureDisabled = PREF_DISABLE_GESTURES;

        mOriginalGyroSensitivity = PREF_GYRO_SENSITIVITY;
        mOriginalMouseSpeed = PREF_MOUSESPEED;
        mOriginalGestureDelay = PREF_LONGPRESS_TRIGGER;
        mOriginalResolution = PREF_SCALE_FACTOR;
        mOriginalTransparency = PREF_BUTTON_TRANSPARENCY;

        mOriginalFps = FpsOverlay.fpsEnabled();
        mOriginalMemory = FpsOverlay.memoryEnabled();
        mOriginalBattery = FpsOverlay.batteryEnabled();
        mFpsSwitch.setChecked(mOriginalFps);
        mMemorySwitch.setChecked(mOriginalMemory);
        mBatterySwitch.setChecked(mOriginalBattery);
        mFpsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEditor.putBoolean(FpsOverlay.PREF, isChecked);
            applyReadout();
            if(isChecked && !FpsOverlay.counting()) {
                android.widget.Toast.makeText(buttonView.getContext(), R.string.fork_fps_restart, android.widget.Toast.LENGTH_LONG).show();
            }
        });
        mMemorySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEditor.putBoolean(FpsOverlay.PREF_MEMORY, isChecked);
            applyReadout();
        });
        mBatterySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mEditor.putBoolean(FpsOverlay.PREF_BATTERY, isChecked);
            applyReadout();
        });

        mGyroSwitch.setChecked(mOriginalGyroEnabled);
        mGyroXSwitch.setChecked(mOriginalGyroXEnabled);
        mGyroYSwitch.setChecked(mOriginalGyroYEnabled);
        mGestureSwitch.setChecked(mOriginalGestureDisabled);

        mGyroSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_ENABLE_GYRO = isChecked;
            onGyroStateChanged();
            updateGyroVisibility(isChecked);
            mEditor.putBoolean("enableGyro", isChecked);
        });

        mGyroXSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_X = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertX", isChecked);
        });

        mGyroYSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_GYRO_INVERT_Y = isChecked;
            onGyroStateChanged();
            mEditor.putBoolean("gyroInvertY", isChecked);
        });

        mGestureSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            PREF_DISABLE_GESTURES = isChecked;
            updateGestureVisibility(isChecked);
            mEditor.putBoolean("disableGestures", isChecked);
        });

        mGyroSensitivityBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_GYRO_SENSITIVITY = progress / 100f;
            mEditor.putInt("gyroSensitivity", progress);
            setSeekTextPercent(mGyroSensitivityText, progress);
        });
        mGyroSensitivityBar.setProgress((int) (mOriginalGyroSensitivity * 100f));
        setSeekTextPercent(mGyroSensitivityText, mGyroSensitivityBar.getProgress());

        mMouseSpeedBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_MOUSESPEED = progress / 100f;
            mEditor.putInt("mousespeed", progress);
            setSeekTextPercent(mMouseSpeedText, progress);
        });
        mMouseSpeedBar.setProgress((int) (mOriginalMouseSpeed * 100f));
        setSeekTextPercent(mMouseSpeedText, mMouseSpeedBar.getProgress());

        mGestureDelayBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_LONGPRESS_TRIGGER = progress;
            mEditor.putInt("timeLongPressTrigger", progress);
            setSeekTextMillisecond(mGestureDelayText, progress);
        });
        mGestureDelayBar.setProgress(mOriginalGestureDelay);
        setSeekTextMillisecond(mGestureDelayText, mGestureDelayBar.getProgress());

        mResolutionBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_SCALE_FACTOR = progress/100f;
            mEditor.putInt("resolutionRatio", progress);
            setSeekTextPercent(mResolutionText, progress);
            onResolutionChanged();
        });
        mResolutionBar.setProgress((int) (mOriginalResolution * 100));
        setSeekTextPercent(mResolutionText, mResolutionBar.getProgress());

        mTransparencyBar.setProgress(PREF_BUTTON_TRANSPARENCY);
        setSeekTextPercent(mTransparencyText, mTransparencyBar.getProgress());
        mTransparencyBar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            PREF_BUTTON_TRANSPARENCY = (short) progress;
            mEditor.putInt("buttonTransparency", progress);
            setSeekTextPercent(mTransparencyText, progress);
            onButtonTransparencyChanged();
        });


        updateGyroVisibility(mOriginalGyroEnabled);
        updateGestureVisibility(mOriginalGestureDisabled);
    }

    private void applyReadout() {
        FpsOverlay.setLive(mFpsSwitch.isChecked(), mMemorySwitch.isChecked(), mBatterySwitch.isChecked());
    }

    private static void setSeekTextMillisecond(TextView target, int value) {
        setSeekText(target, R.string.millisecond_format, value);
    }

    private static void setSeekTextPercent(TextView target, int value) {
        setSeekText(target, R.string.percent_format, value);
    }

    private static void setSeekText(TextView target, int format, int value) {
        target.setText(target.getContext().getString(format, value));
    }

    private void updateGyroVisibility(boolean isEnabled) {
        int visibility = isEnabled ? View.VISIBLE : View.GONE;
        mGyroXSwitch.setVisibility(visibility);
        mGyroYSwitch.setVisibility(visibility);

        mGyroSensitivityBar.setVisibility(visibility);
        mGyroSensitivityText.setVisibility(visibility);
        mGyroSensitivityDisplayText.setVisibility(visibility);
    }

    private void updateGyroCompatibility() {
        boolean isGyroAvailable = Tools.deviceSupportsGyro(mDialogContent.getContext());
        if (!isGyroAvailable) {
            mGyroSwitch.setVisibility(View.GONE);
            updateGestureVisibility(false);
        }
    }

    private void updateGestureVisibility(boolean isDisabled) {
        int visibility = isDisabled ? View.GONE : View.VISIBLE;
        mGestureDelayBar.setVisibility(visibility);
        mGestureDelayText.setVisibility(visibility);
        mGestureDelayDisplayText.setVisibility(visibility);
    }

    private void removeListeners() {
        mGyroSwitch.setOnCheckedChangeListener(null);
        mGyroXSwitch.setOnCheckedChangeListener(null);
        mGyroYSwitch.setOnCheckedChangeListener(null);
        mGestureSwitch.setOnCheckedChangeListener(null);
        mFpsSwitch.setOnCheckedChangeListener(null);
        mMemorySwitch.setOnCheckedChangeListener(null);
        mBatterySwitch.setOnCheckedChangeListener(null);

        mGyroSensitivityBar.setOnSeekBarChangeListener(null);
        mMouseSpeedBar.setOnSeekBarChangeListener(null);
        mGestureDelayBar.setOnSeekBarChangeListener(null);
        mResolutionBar.setOnSeekBarChangeListener(null);
        mTransparencyBar.setOnSeekBarChangeListener(null);
    }

    private void setupCancelButton() {
        setStartButtonListener(android.R.string.cancel, v -> cancel());
        setEndButtonListener(android.R.string.ok, v -> {
            mEditor.apply();
            disappear(true);
        });
    }

    /** Resets all settings to their original values */
    public void cancel() {
        // Reset all settings if we were editing
        if(isDisplaying()) {
            PREF_ENABLE_GYRO = mOriginalGyroEnabled;
            PREF_GYRO_INVERT_X = mOriginalGyroXEnabled;
            PREF_GYRO_INVERT_Y = mOriginalGyroYEnabled;
            PREF_DISABLE_GESTURES = mOriginalGestureDisabled;

            PREF_GYRO_SENSITIVITY = mOriginalGyroSensitivity;
            PREF_MOUSESPEED = mOriginalMouseSpeed;
            PREF_LONGPRESS_TRIGGER = mOriginalGestureDelay;
            PREF_SCALE_FACTOR = mOriginalResolution;
            PREF_BUTTON_TRANSPARENCY = mOriginalTransparency;

            FpsOverlay.setLive(mOriginalFps, mOriginalMemory, mOriginalBattery);
            onGyroStateChanged();
            onResolutionChanged();
            onButtonTransparencyChanged();
        }

        disappear(true);
    }

    /** Called when the resolution is changed. Use {@link LauncherPreferences#PREF_SCALE_FACTOR} */
    public abstract void onResolutionChanged();

    /** Called when the gyro state is changed.
     * Use {@link LauncherPreferences#PREF_ENABLE_GYRO}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_X}
     * Use {@link LauncherPreferences#PREF_GYRO_INVERT_Y}
     */
    public abstract void onGyroStateChanged();

    /**
     * Called when the button transparency state is changed. Use {@link LauncherPreferences#PREF_BUTTON_TRANSPARENCY}
     */
    public abstract void onButtonTransparencyChanged();


    /** Called when the record button is pressed; the game activity overrides it. */
    public void onRecordClicked() {}
}

package com.divinedoll.launcher.input.buttons;

import static com.divinedoll.launcher.CallbackBridge.sendMouseButton;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import com.divinedoll.launcher.game.GameActivity;

import com.divinedoll.launcher.R;

import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.input.ControlData;
import com.divinedoll.launcher.input.ControlLayout;
import com.divinedoll.launcher.input.handleview.EditControlSideDialog;
import com.divinedoll.launcher.prefs.LauncherPreferences;

import com.divinedoll.launcher.CallbackBridge;

import static com.divinedoll.launcher.input.buttons.BackgroundTint.DEFAULT_TINT_LIST;
import static com.divinedoll.launcher.input.buttons.BackgroundTint.TOGGLE_TINT_LIST;
import static com.divinedoll.launcher.game.platform.Platform.PLATFORM;

@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
public class ControlButton extends TextView implements ControlInterface {
    private static final int KEY_CHAT = 84;
    private static final int KEY_ENTER = 257;

    private final Paint mRectPaint = new Paint();
    private final Paint mIconPaint = new Paint();
    private final RectF mIconRect = new RectF();
    protected ControlData mProperties;
    private final ControlLayout mControlLayout;

    /* Cache value from the ControlData radius for drawing purposes */
    private float mComputedRadius;
    private boolean mHasBitmap;

    protected boolean mIsToggled = false;

    public ControlButton(ControlLayout layout, ControlData properties) {
        super(layout.getContext());
        mControlLayout = layout;
        setGravity(Gravity.CENTER);
        setAllCaps(LauncherPreferences.PREF_BUTTON_ALL_CAPS);
        setTextColor(Color.WHITE);
        setPadding(4, 4, 4, 4);
        setTextSize(14); // Nullify the default size setting
        setOutlineProvider(null); // Disable shadow casting, removing one drawing pass

        //setOnLongClickListener(this);

        //When a button is created, the width/height has yet to be processed to fit the scaling.
        setProperties(preProcessProperties(properties, layout));

        injectBehaviors();
    }

    @Override
    public View getControlView() {return this;}

    public ControlData getProperties() {
        return mProperties;
    }

    private void setupBitmapTint() {
        BackgroundTint.applyToggleTint(getContext());
        ColorStateList tintStateList = mProperties.isToggle ? TOGGLE_TINT_LIST : DEFAULT_TINT_LIST;
        if(mProperties.imageTint != 0 || mProperties.pressedColor != 0) {
            int pressed = mProperties.pressedColor != 0 ? mProperties.pressedColor
                    : tintStateList.getColorForState(new int[]{android.R.attr.state_activated}, Color.TRANSPARENT);
            int idle = mProperties.imageTint != 0 ? mProperties.imageTint : Color.TRANSPARENT;
            tintStateList = new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_activated}, new int[]{}},
                    new int[]{pressed, idle});
        }
        setBackgroundTintList(tintStateList);
        setBackgroundTintMode(PorterDuff.Mode.SRC_ATOP);
    }

    private void setupNormalTint() {
        mComputedRadius = ControlInterface.super.computeCornerRadius(mProperties.cornerRadius);
        setBackgroundTintList(null);
        if (mProperties.isToggle) {
            //For the toggle layer
            final TypedValue value = new TypedValue();
            getContext().getTheme().resolveAttribute(R.attr.colorAccent, value, true);
            mRectPaint.setColor(value.data);
            mRectPaint.setAlpha(BackgroundTint.BACKGROUND_TOGGLE_TINT_ALPHA);
        } else {
            mRectPaint.setColor(Color.WHITE);
            mRectPaint.setAlpha(BackgroundTint.BACKGROUND_DEFAULT_TINT_ALPHA);
        }
        if(mProperties.pressedColor != 0) mRectPaint.setColor(mProperties.pressedColor);
    }

    public void setProperties(ControlData properties, boolean changePos) {
        mProperties = properties;
        ControlInterface.super.setProperties(properties, changePos);

        mHasBitmap = Tools.isValidString(mProperties.bitmapTag);

        if(mHasBitmap) setupBitmapTint();
        else setupNormalTint();

        setText(properties.name);
        setTextColor(properties.textColor != 0 ? properties.textColor : Color.WHITE);
        setTextSize(properties.textSize > 0 ? properties.textSize : 14);
        if(properties.textLeft) {
            setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            setPadding((int) (12 * getResources().getDisplayMetrics().density), 4, 4, 4);
            setTypeface(com.divinedoll.launcher.utils.McStyle.font(getContext()));
        } else {
            setGravity(Gravity.CENTER);
            setPadding(4, 4, 4, 4);
        }
    }

    /** Draws the small icon over the background and under the text, with square pixels. */
    private void drawIcon(Canvas canvas) {
        if(!Tools.isValidString(mProperties.iconTag) || mControlLayout.getBitmaps() == null) return;
        Bitmap icon = mControlLayout.getBitmaps().getBitmap(mProperties.iconTag);
        if(icon == null) return;
        float inset = Math.min(getWidth(), getHeight()) * 0.12f;
        float scale = Math.min((getWidth() - 2 * inset) / icon.getWidth(), (getHeight() - 2 * inset) / icon.getHeight());
        if(scale <= 0) return;
        if(scale >= 1f) scale = (float) Math.floor(scale);
        float w = icon.getWidth() * scale, h = icon.getHeight() * scale;
        mIconRect.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
        canvas.drawBitmap(icon, null, mIconRect, mIconPaint);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawIcon(canvas);
        super.onDraw(canvas);
        // Bitmap uses a tint list, so don't do any custom rendering
        if(mHasBitmap || !isActivated()) return;
        canvas.drawRoundRect(0, 0, getWidth(), getHeight(), mComputedRadius, mComputedRadius, mRectPaint);
    }

    @Override
    public boolean isActivated() {
        // Any possible side effects?
        return super.isActivated() || (mProperties.isToggle && mIsToggled);
    }

    public void loadEditValues(EditControlSideDialog editControlPopup){
        editControlPopup.loadValues(getProperties());
    }

    /** Add another instance of the ControlButton to the parent layout */
    public void cloneButton(){
        ControlData cloneData = new ControlData(getProperties());
        cloneData.dynamicX = "0.5 * ${screen_width}";
        cloneData.dynamicY = "0.5 * ${screen_height}";
        ((ControlLayout) getParent()).addControlButton(cloneData);
    }

    /** Remove any trace of this button from the layout */
    public void removeButton() {
        ControlLayout parent = getControlLayoutParent();
        if(parent == null) return;
        parent.getLayout().mControlDataList.remove(getProperties());
        parent.removeView(this);
    }

    @Override
    public void handlePressed() {
        if(hasCommand()) {
            setActivated(true);
            runCommand();
        } else if(getProperties().sequence) {
            setActivated(true);
            runSequence();
        } else if(!getProperties().isToggle){
            sendKeyPresses(true);
        }
    }

    private boolean hasCommand() {
        String text = getProperties().commandText;
        return text != null && !text.isEmpty();
    }

    /** Opens the chat, types the text and sends it. */
    private void runCommand() {
        String text = getProperties().commandText;
        Handler handler = new Handler(Looper.getMainLooper());
        sendKey(KEY_CHAT, true);
        handler.postDelayed(() -> sendKey(KEY_CHAT, false), 50);
        handler.postDelayed(() -> PLATFORM.sendBulkUnicodeEvent(text, CallbackBridge.getCurrentMods()), 200);
        handler.postDelayed(() -> sendKey(KEY_ENTER, true), 350);
        handler.postDelayed(() -> sendKey(KEY_ENTER, false), 400);
    }

    private void runSequence() {
        Handler handler = new Handler(Looper.getMainLooper());
        int step = getProperties().sequenceDelay > 0 ? getProperties().sequenceDelay : 130;
        int hold = Math.min(60, Math.max(10, step / 2));
        int delay = 0;
        for(int keycode : getProperties().keycodes) {
            if(keycode == 0) continue;
            handler.postDelayed(() -> sendKey(keycode, true), delay);
            handler.postDelayed(() -> sendKey(keycode, false), delay + hold);
            delay += step;
        }
    }

    @Override
    public void handleReleased() {
        if(hasCommand() || getProperties().sequence) {
            setActivated(false);
        } else if(!triggerToggle()) {
            sendKeyPresses(false);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        ControlData properties = getProperties();
        int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP: // 1
            case MotionEvent.ACTION_CANCEL: // 3
            case MotionEvent.ACTION_POINTER_UP: // 6
                if(properties.passThruEnabled){
                    //Send the event to be taken as a mouse action
                    View gameSurface = getControlLayoutParent().getGameSurface();
                    if(gameSurface != null) gameSurface.dispatchTouchEvent(event);
                }
                break;
        }

        if(getProperties().isSwipeable) {
            getControlLayoutParent().onTouch(this, event);
            return true;
        }

        switch (action){
            case MotionEvent.ACTION_DOWN: // 0
            case MotionEvent.ACTION_POINTER_DOWN: // 5
                handlePressed();
                break;
            case MotionEvent.ACTION_UP: // 1
            case MotionEvent.ACTION_CANCEL: // 3
            case MotionEvent.ACTION_POINTER_UP: // 6
                handleReleased();
                break;
            default:
                return false;
        }

        return super.onTouchEvent(event);
    }



    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean triggerToggle(){
        //returns true a the toggle system is triggered
        if(mProperties.isToggle){
            mIsToggled = !mIsToggled;
            invalidate();
            sendKeyPresses(mIsToggled);
            return true;
        }
        return false;
    }

    public void sendKeyPresses(boolean isDown){
        setActivated(isDown);
        for(int keycode : mProperties.keycodes) sendKey(keycode, isDown);
    }

    private void sendKey(int keycode, boolean isDown) {
        if(keycode == 290 && !isDown) ControlLayout.onGlobalF1();
        {
            if(keycode >= KeyEvent.KEYCODE_UNKNOWN){
                CallbackBridge.setModifiers(keycode, isDown);
                int modifiers = CallbackBridge.getCurrentMods();
                PLATFORM.sendKeyEvent(keycode, isDown ? 1 : 0, modifiers);
            }else{
                Log.i("punjabilauncher", "sendSpecialKey("+keycode+","+isDown+")");
                sendSpecialKey(keycode, isDown);
            }
        }
    }

    private void sendSpecialKey(int keycode, boolean isDown){
        switch (keycode) {
            case ControlData.SPECIALBTN_KEYBOARD:
                if(isDown) GameActivity.switchKeyboardState(false);
                break;

            case ControlData.SPECIALBTN_KEYBOARDPAN:
                if(isDown) GameActivity.switchKeyboardState(true);
                break;

            case ControlData.SPECIALBTN_TOGGLECTRL:
                if(isDown)getControlLayoutParent().toggleControlVisible();
                break;

            case ControlData.SPECIALBTN_VIRTUALMOUSE:
                if(isDown) GameActivity.toggleMouse(getContext());
                break;

            case ControlData.SPECIALBTN_MOUSEPRI:
                sendMouseButton(MotionEvent.BUTTON_PRIMARY, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSEMID:
                sendMouseButton(MotionEvent.BUTTON_TERTIARY, isDown);
                break;

            case ControlData.SPECIALBTN_MOUSESEC:
                sendMouseButton(MotionEvent.BUTTON_SECONDARY, isDown);
                break;

            case ControlData.SPECIALBTN_SCROLLDOWN:
                if (!isDown) CallbackBridge.sendScroll(0, 1d);
                break;

            case ControlData.SPECIALBTN_SCROLLUP:
                if (!isDown) CallbackBridge.sendScroll(0, -1d);
                break;
            case ControlData.SPECIALBTN_MENU:
                mControlLayout.notifyAppMenu();
                break;
        }
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }
}

package git.artdeell.mojo.input.buttons;

import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NONE;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_NORTH_WEST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH_EAST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_SOUTH_WEST;
import static git.artdeell.mojo.input.gamepad.GamepadJoystick.DIRECTION_WEST;
import static git.artdeell.mojo.game.platform.Platform.PLATFORM;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.input.ControlData;
import git.artdeell.mojo.input.ControlJoystickData;
import git.artdeell.mojo.input.ControlLayout;
import git.artdeell.mojo.input.LayoutBitmaps;
import git.artdeell.mojo.input.gamepad.GamepadJoystick;
import git.artdeell.mojo.input.handleview.EditControlSideDialog;

import git.artdeell.mojo.CallbackBridge;

import io.github.controlwear.virtual.joystick.android.JoystickView;

@SuppressLint("ViewConstructor")
public class ControlJoystick extends JoystickView implements ControlInterface {
    public final static int DIRECTION_FORWARD_LOCK = 8;
    // Directions keycode
    private final int[] mDirectionForwardLock = new int[]{KeyEvent.KEYCODE_CTRL_LEFT};
    private final int[] mDirectionForward = new int[]{KeyEvent.KEYCODE_W};
    private final int[] mDirectionRight = new int[]{KeyEvent.KEYCODE_D};
    private final int[] mDirectionBackward = new int[]{KeyEvent.KEYCODE_S};
    private final int[] mDirectionLeft = new int[]{KeyEvent.KEYCODE_A};
    private ControlJoystickData mControlData;
    private int mLastDirectionInt = GamepadJoystick.DIRECTION_NONE;
    private int mCurrentDirectionInt = GamepadJoystick.DIRECTION_NONE;
    private Bitmap mBaseBitmap;
    private Bitmap mStickBitmap;
    private final RectF mStickBounds = new RectF();
    public ControlJoystick(ControlLayout parent, ControlJoystickData data) {
        super(parent.getContext());
        init(data, parent);
    }

    private static void sendInput(int[] keys, boolean isDown) {
        for (int key : keys) {
            int modifiers = CallbackBridge.getCurrentMods();
            PLATFORM.sendKeyEvent(key, isDown, modifiers);
        }
    }

    private void init(ControlJoystickData data, ControlLayout layout) {
        mControlData = data;
        setProperties(preProcessProperties(data, layout));
        setDeadzone(35);
        setFixedCenter(data.absolute);
        setAutoReCenterButton(true);

        injectBehaviors();

        setOnMoveListener(new OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                mLastDirectionInt = mCurrentDirectionInt;
                mCurrentDirectionInt = getDirectionInt(angle, strength);

                if (mLastDirectionInt != mCurrentDirectionInt) {
                    sendDirectionalKeycode(mLastDirectionInt, false);
                    sendDirectionalKeycode(mCurrentDirectionInt, true);
                }
            }

            @Override
            public void onForwardLock(boolean isLocked) {
                sendInput(mDirectionForwardLock, isLocked);
            }
        });
    }

    @Override
    public View getControlView() {
        return this;
    }

    @Override
    public ControlData getProperties() {
        return mControlData;
    }

    @Override
    public void setProperties(ControlData properties, boolean changePos) {
        mControlData = (ControlJoystickData) properties;
        mControlData.isHideable = true;
        ControlInterface.super.setProperties(properties, changePos);
        postDelayed(() -> {
            setForwardLockDistance(mControlData.forwardLock ? (int) Tools.dpToPx(60) : 0);
            setFixedCenter(mControlData.absolute);
        }, 10);
    }

    @Override
    public void removeButton() {
        getControlLayoutParent().getLayout().mJoystickDataList.remove(getProperties());
        getControlLayoutParent().removeView(this);
    }

    @Override
    public void cloneButton() {
        ControlJoystickData data = new ControlJoystickData(mControlData);
        getControlLayoutParent().addJoystickButton(data);
    }

    @Override
    public void handlePressed() {/*STUB since non swipeable*/}

    @Override
    public void handleReleased() {/*STUB since non swipeable*/}


    @Override
    public void setBackground() {
        ControlJoystickData data = (ControlJoystickData) getProperties();
        LayoutBitmaps storage = getControlLayoutParent().getBitmaps();
        Bitmap baseBitmap = Tools.isValidString(data.bitmapTag) ? storage.getBitmap(data.bitmapTag) : null;
        Bitmap stickBitmap = Tools.isValidString(data.stickBitmapTag) ? storage.getBitmap(data.stickBitmapTag) : null;

        if (baseBitmap != null) {
            // The library only draws plain circles, so the base image goes behind it as the view background
            setBorderWidth(0);
            setBorderColor(Color.TRANSPARENT);
            setBackgroundColor(Color.TRANSPARENT);
            if (baseBitmap != mBaseBitmap) {
                super.setBackground(new BitmapDrawable(getResources(), baseBitmap));
            }
        } else {
            if (mBaseBitmap != null) super.setBackground((Drawable) null);
            setBorderWidth((int) Tools.dpToPx(getProperties().strokeWidth * (getControlLayoutParent().getLayoutScale()/100f)));
            setBorderColor(getProperties().strokeColor);
            setBackgroundColor(getProperties().bgColor);
        }
        mBaseBitmap = baseBitmap;

        // With a custom image the library circle is hidden and the image is drawn in onDraw
        setButtonColor(stickBitmap != null ? Color.TRANSPARENT : Color.BLACK);
        mStickBitmap = stickBitmap;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mStickBitmap == null) return;
        int width = getWidth();
        int height = getHeight();
        float scale = mControlData != null && mControlData.stickScale > 0 ? Math.max(0.5f, Math.min(1.5f, mControlData.stickScale)) : 1f;
        int stickRadius = (int) (Math.min(width, height) / 2f * getButtonSizeRatio() * scale);
        if (stickRadius <= 0 || width - 2 * stickRadius <= 0 || height - 2 * stickRadius <= 0) return;
        float centerX;
        float centerY;
        if (mControlData != null && mControlData.lockCenter) {
            // The stick never moves: it stays in the middle of the joystick
            centerX = width / 2f;
            centerY = height / 2f;
        } else {
            // Normalized coordinates go from 0 to 100 across the area the center of the stick can reach
            centerX = stickRadius + getNormalizedX() / 100f * (width - 2 * stickRadius);
            centerY = stickRadius + getNormalizedY() / 100f * (height - 2 * stickRadius);
        }
        mStickBounds.set(centerX - stickRadius, centerY - stickRadius, centerX + stickRadius, centerY + stickRadius);
        canvas.drawBitmap(mStickBitmap, null, mStickBounds, null);
    }

    @Override
    public void loadEditValues(EditControlSideDialog editControlPopup) {
        editControlPopup.loadJoystickValues(mControlData);
    }

    private int getDirectionInt(int angle, int intensity) {
        if (intensity == 0) return DIRECTION_NONE;
        return (int) (((angle + 22.5) / 45) % 8);
    }

    private void sendDirectionalKeycode(int direction, boolean isDown) {
        switch (direction) {
            case DIRECTION_NORTH:
                sendInput(mDirectionForward, isDown);
                break;
            case DIRECTION_NORTH_EAST:
                sendInput(mDirectionForward, isDown);
                sendInput(mDirectionRight, isDown);
                break;
            case DIRECTION_EAST:
                sendInput(mDirectionRight, isDown);
                break;
            case DIRECTION_SOUTH_EAST:
                sendInput(mDirectionRight, isDown);
                sendInput(mDirectionBackward, isDown);
                break;
            case DIRECTION_SOUTH:
                sendInput(mDirectionBackward, isDown);
                break;
            case DIRECTION_SOUTH_WEST:
                sendInput(mDirectionBackward, isDown);
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_WEST:
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_NORTH_WEST:
                sendInput(mDirectionForward, isDown);
                sendInput(mDirectionLeft, isDown);
                break;
            case DIRECTION_FORWARD_LOCK:
                sendInput(mDirectionForwardLock, isDown);
                break;
        }
    }

}

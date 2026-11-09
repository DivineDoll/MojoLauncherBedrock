package com.divinedoll.launcher.input.handleview;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.input.ControlData;
import com.divinedoll.launcher.input.ControlLayout;
import com.divinedoll.launcher.input.LayoutBitmaps;
import com.divinedoll.launcher.input.buttons.BackgroundTint;
import com.divinedoll.launcher.input.buttons.ControlInterface;

/**
 * Small preview of the button being edited, normal and pressed. It reads the properties of
 * the button a few times per second, so every change in the editor shows up here.
 */
public class ControlPreviewView extends View {
    private ControlInterface mTarget;
    private final Paint mPaint = new Paint();
    private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();

    public ControlPreviewView(Context context) {
        super(context);
    }

    public ControlPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTarget(ControlInterface target) {
        mTarget = target;
        invalidate();
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if(mTarget != null && getVisibility() == VISIBLE) {
            ControlData p = mTarget.getProperties();
            ControlLayout layout = mTarget.getControlLayoutParent();
            LayoutBitmaps bitmaps = layout == null ? null : layout.getBitmaps();
            float gap = dp(12);
            float boxW = (getWidth() - gap) / 2f, boxH = getHeight() - dp(4);
            mRect.set(0, dp(2), boxW, dp(2) + boxH);
            drawBox(canvas, p, bitmaps, false);
            mRect.set(boxW + gap, dp(2), getWidth(), dp(2) + boxH);
            drawBox(canvas, p, bitmaps, true);
        }
        postInvalidateDelayed(150);
    }

    private void drawBox(Canvas canvas, ControlData p, LayoutBitmaps bitmaps, boolean pressed) {
        float radius = (Math.min(mRect.width(), mRect.height()) / 2f) * (p.cornerRadius / 100f);
        int alpha = Math.max(20, Math.min(255, Math.round(p.opacity * 255f)));
        Bitmap background = bitmaps != null && Tools.isValidString(p.bitmapTag) ? bitmaps.getBitmap(p.bitmapTag) : null;
        mPaint.reset();
        mPaint.setAlpha(alpha);
        if(background != null) {
            if(p.imageTint != 0) mPaint.setColorFilter(new PorterDuffColorFilter(p.imageTint, PorterDuff.Mode.SRC_ATOP));
            canvas.drawBitmap(background, null, mRect, mPaint);
        } else {
            mPaint.setAntiAlias(true);
            mPaint.setColor(p.bgColor);
            mPaint.setAlpha(Math.round(Color.alpha(p.bgColor) * alpha / 255f));
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
            float stroke = Math.min(dp(p.strokeWidth), dp(6));
            if(stroke > 0) {
                mPaint.setStyle(Paint.Style.STROKE);
                mPaint.setStrokeWidth(stroke);
                mPaint.setColor(p.strokeColor);
                canvas.drawRoundRect(mRect, radius, radius, mPaint);
            }
        }
        Bitmap icon = bitmaps != null && Tools.isValidString(p.iconTag) ? bitmaps.getBitmap(p.iconTag) : null;
        if(icon != null) {
            float inset = Math.min(mRect.width(), mRect.height()) * 0.12f;
            float scale = Math.min((mRect.width() - 2 * inset) / icon.getWidth(), (mRect.height() - 2 * inset) / icon.getHeight());
            if(scale >= 1f) scale = (float) Math.floor(scale);
            float w = icon.getWidth() * scale, h = icon.getHeight() * scale;
            RectF dst = new RectF(mRect.centerX() - w / 2f, mRect.centerY() - h / 2f, mRect.centerX() + w / 2f, mRect.centerY() + h / 2f);
            mPaint.reset();
            canvas.drawBitmap(icon, null, dst, mPaint);
        }
        if(p.name != null && !p.name.isEmpty()) {
            mText.setColor(p.textColor != 0 ? p.textColor : Color.WHITE);
            mText.setTextAlign(Paint.Align.CENTER);
            mText.setTextSize(Math.min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    p.textSize > 0 ? p.textSize : 14, getResources().getDisplayMetrics()), mRect.height() * 0.6f));
            float y = mRect.centerY() - (mText.descent() + mText.ascent()) / 2f;
            canvas.drawText(p.name.length() > 8 ? p.name.substring(0, 8) : p.name, mRect.centerX(), y, mText);
        }
        if(pressed) {
            mPaint.reset();
            mPaint.setAntiAlias(true);
            mPaint.setColor(p.pressedColor != 0 ? p.pressedColor : Color.WHITE);
            if(p.pressedColor == 0) mPaint.setAlpha(BackgroundTint.BACKGROUND_DEFAULT_TINT_ALPHA);
            canvas.drawRoundRect(mRect, radius, radius, mPaint);
        }
    }
}

package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/** Hue and saturation wheel. Brightness is set from outside with {@link #setValue(float)}. */
public class ColorWheelView extends View {
    public interface Listener {
        void onWheelChanged(float hue, float saturation);
    }

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float mHue, mSat, mVal = 1f;
    private float mCx, mCy, mRadius;
    private Listener mListener;

    public ColorWheelView(Context context) {
        super(context);
        mRing.setStyle(Paint.Style.STROKE);
        mRing.setStrokeWidth(context.getResources().getDisplayMetrics().density * 2f);
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void set(float hue, float saturation, float value) {
        mHue = hue;
        mSat = saturation;
        mVal = value;
        invalidate();
    }

    public void setValue(float value) {
        mVal = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.min(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        if(MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED) size = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(resolveSize(size, widthSpec), resolveSize(size, heightSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        float pad = getResources().getDisplayMetrics().density * 8f;
        mCx = w / 2f;
        mCy = h / 2f;
        mRadius = Math.max(1f, Math.min(w, h) / 2f - pad);
        int[] colors = new int[7];
        for(int i = 0; i < colors.length; i++) colors[i] = Color.HSVToColor(new float[]{i * 60f, 1f, 1f});
        mPaint.setShader(new SweepGradient(mCx, mCy, colors, null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawCircle(mCx, mCy, mRadius, mPaint);
        Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
        white.setShader(new RadialGradient(mCx, mCy, mRadius, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(mCx, mCy, mRadius, white);
        mDim.setColor(Color.argb(Math.round((1f - mVal) * 255f), 0, 0, 0));
        canvas.drawCircle(mCx, mCy, mRadius, mDim);
        double angle = Math.toRadians(mHue);
        float x = mCx + (float) Math.cos(angle) * mSat * mRadius;
        float y = mCy + (float) Math.sin(angle) * mSat * mRadius;
        float r = getResources().getDisplayMetrics().density * 8f;
        mRing.setColor(Color.BLACK);
        canvas.drawCircle(x, y, r + 1.5f, mRing);
        mRing.setColor(Color.WHITE);
        canvas.drawCircle(x, y, r, mRing);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if(action == MotionEvent.ACTION_DOWN) getParent().requestDisallowInterceptTouchEvent(true);
        if(action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - mCx, dy = event.getY() - mCy;
            float deg = (float) Math.toDegrees(Math.atan2(dy, dx));
            mHue = (deg + 360f) % 360f;
            mSat = Math.min(1f, (float) Math.hypot(dx, dy) / mRadius);
            invalidate();
            if(mListener != null) mListener.onWheelChanged(mHue, mSat);
            return true;
        }
        return super.onTouchEvent(event);
    }
}

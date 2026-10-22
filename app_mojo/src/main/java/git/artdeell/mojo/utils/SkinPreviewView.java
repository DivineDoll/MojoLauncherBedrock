package git.artdeell.mojo.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * Draws a Minecraft player from a 64x64 skin texture, one orthogonal view at a time
 * (front, right side, back, left side). It turns by itself and when tapped.
 */
public class SkinPreviewView extends View {
    private static final int FRONT = 0, RIGHT = 1, BACK = 2, LEFT = 3;
    // face index inside a part's UV strip: 0 right, 1 front, 2 left, 3 back
    private static final int[] FACE_FOR_VIEW = {1, 0, 3, 2};

    private final Paint mPaint = new Paint();
    private final Rect mSrc = new Rect();
    private final RectF mDst = new RectF();
    private Bitmap mSkin;
    private boolean mSlim;
    private int mView = FRONT;
    private float mScale, mOriginX, mOriginY;

    private final Runnable mSpin = new Runnable() {
        @Override public void run() {
            mView = (mView + 1) % 4;
            invalidate();
            postDelayed(this, 1600);
        }
    };

    public SkinPreviewView(Context context) { this(context, null); }

    public SkinPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mPaint.setFilterBitmap(false);
        mPaint.setAntiAlias(false);
        setOnClickListener(v -> {
            removeCallbacks(mSpin);
            mView = (mView + 1) % 4;
            invalidate();
            postDelayed(mSpin, 3000);
        });
    }

    public void setSkin(Bitmap skin, boolean slim) {
        mSkin = skin;
        mSlim = slim;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        postDelayed(mSpin, 1600);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(mSpin);
        super.onDetachedFromWindow();
    }

    /** Draws a face of a body part. u,v: texture origin of the part; w,d,h: its size in pixels. */
    private void face(int u, int v, int w, int d, int h, int face, float dx, float dy) {
        int sx, sw;
        switch (face) {
            case 0: sx = u; sw = d; break;
            case 1: sx = u + d; sw = w; break;
            case 2: sx = u + d + w; sw = d; break;
            default: sx = u + 2 * d + w; sw = w; break;
        }
        mSrc.set(sx, v + d, sx + sw, v + d + h);
        mDst.set(mOriginX + dx * mScale, mOriginY + dy * mScale,
                mOriginX + (dx + sw) * mScale, mOriginY + (dy + h) * mScale);
        mCanvas.drawBitmap(mSkin, mSrc, mDst, mPaint);
    }

    private Canvas mCanvas;

    /** Draws base layer then overlay layer of the same part. */
    private void part(int u, int v, int ou, int ov, int w, int d, int h, int face, float dx, float dy) {
        face(u, v, w, d, h, face, dx, dy);
        face(ou, ov, w, d, h, face, dx, dy);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(mSkin == null || mSkin.getWidth() != 64 || mSkin.getHeight() != 64) return;
        mCanvas = canvas;
        mScale = Math.min(getWidth() / 16f, getHeight() / 32f);
        mOriginX = (getWidth() - 16 * mScale) / 2f;
        mOriginY = (getHeight() - 32 * mScale) / 2f;
        int f = FACE_FOR_VIEW[mView];
        int aw = mSlim ? 3 : 4;

        if(mView == FRONT || mView == BACK) {
            boolean front = mView == FRONT;
            // legs
            // viewer's left leg/arm is the player's right one when seen from the front
            int[] rightLeg = {0, 16, 0, 32}, leftLeg = {16, 48, 0, 48};
            int[] rightArm = {40, 16, 40, 32}, leftArm = {32, 48, 48, 48};
            int[] viewerLeftLeg = front ? rightLeg : leftLeg, viewerRightLeg = front ? leftLeg : rightLeg;
            int[] viewerLeftArm = front ? rightArm : leftArm, viewerRightArm = front ? leftArm : rightArm;
            part(viewerLeftLeg[0], viewerLeftLeg[1], viewerLeftLeg[2], viewerLeftLeg[3], 4, 4, 12, f, 4, 20);
            part(viewerRightLeg[0], viewerRightLeg[1], viewerRightLeg[2], viewerRightLeg[3], 4, 4, 12, f, 8, 20);
            part(16, 16, 16, 32, 8, 4, 12, f, 4, 8);
            part(viewerLeftArm[0], viewerLeftArm[1], viewerLeftArm[2], viewerLeftArm[3], aw, 4, 12, f, 4 - aw, 8);
            part(viewerRightArm[0], viewerRightArm[1], viewerRightArm[2], viewerRightArm[3], aw, 4, 12, f, 12, 8);
            part(0, 0, 32, 0, 8, 8, 8, f, 4, 0);
        } else {
            boolean right = mView == RIGHT;
            int[] leg = right ? new int[]{0, 16, 0, 32} : new int[]{16, 48, 0, 48};
            int[] arm = right ? new int[]{40, 16, 40, 32} : new int[]{32, 48, 48, 48};
            part(leg[0], leg[1], leg[2], leg[3], 4, 4, 12, f, 2, 20);
            part(16, 16, 16, 32, 8, 4, 12, f, 2, 8);
            part(arm[0], arm[1], arm[2], arm[3], aw, 4, 12, f, 2, 8);
            part(0, 0, 32, 0, 8, 8, 8, f, 0, 0);
        }
    }
}

package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayDeque;

/**
 * Pixel grid for drawing small icons. It has two layers, a mirror mode, shape tools and zoom.
 * Templates are loaded as real pixels into the bottom layer.
 */
public class PixelCanvasView extends View {
    public static final int PENCIL = 0, ERASER = 1, FILL = 2, PICK = 3, LINE = 4, RECT = 5, CIRCLE = 6, SELECT = 7;
    private static final int MAX_UNDO = 40;

    public interface Listener {
        void colorPicked(int color);
    }

    private int gw = 16, gh = 16;
    private int[][] layers = {new int[16 * 16], new int[16 * 16]};
    private final boolean[] visible = {true, true};
    private int active;
    private int tool = PENCIL;
    private int color = Color.WHITE;
    private boolean mirror, shapeFill;
    private Listener listener;
    private final ArrayDeque<int[][]> undo = new ArrayDeque<>();
    private final Paint paint = new Paint();
    private float cell, left, top;
    private float zoom = 1f, panX, panY;
    private boolean showGrid = true;
    private boolean preview;
    private Runnable onPreviewTap;
    private float downX, downY;
    private boolean strokeActive, gesture, shaping;
    private int sx, sy, ex, ey;
    private float lastFx, lastFy;
    private final ScaleGestureDetector scaleDetector;
    private int[] stampSrc;
    private int stampSw, stampSh, stampW;
    private float stampFx, stampFy, dragX, dragY;
    private Runnable stampListener;
    private boolean selOn, selecting;
    private int selAx, selAy, selBx, selBy;

    public PixelCanvasView(Context context) {
        super(context);
        paint.setAntiAlias(false);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoomAround(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
    }

    public void setShowGrid(boolean on) {
        showGrid = on;
        invalidate();
    }

    public float getZoom() { return zoom; }

    /** Preview shows only the drawing: no grid, no checkerboard, no drawing. A tap calls onTap. */
    public void setPreview(boolean on, Runnable onTap) {
        preview = on;
        onPreviewTap = onTap;
        invalidate();
    }

    public void zoomBy(float factor) {
        zoomAround(factor, getWidth() / 2f, getHeight() / 2f);
    }

    public void resetZoom() {
        zoom = 1f;
        panX = 0;
        panY = 0;
        invalidate();
    }

    /** Zooms keeping the pixel under (fx, fy) in place, so pinching feels like holding the drawing. */
    private void zoomAround(float factor, float fx, float fy) {
        layout();
        float gx = (fx - left) / cell, gy = (fy - top) / cell;
        zoom = Math.max(1f, Math.min(32f, zoom * factor));
        float newCell = baseCell() * zoom;
        panX = fx - gx * newCell - (getWidth() - gw * newCell) / 2f;
        panY = fy - gy * newCell - (getHeight() - gh * newCell) / 2f;
        invalidate();
    }

    private float baseCell() {
        return Math.min(getWidth() / (float) gw, getHeight() / (float) gh);
    }

    /** Computes the size and position of the grid for the current zoom and keeps it inside the view. */
    private void layout() {
        cell = baseCell() * zoom;
        float maxX = Math.max(0f, (gw * cell - getWidth()) / 2f), maxY = Math.max(0f, (gh * cell - getHeight()) / 2f);
        panX = Math.max(-maxX, Math.min(maxX, panX));
        panY = Math.max(-maxY, Math.min(maxY, panY));
        left = (getWidth() - gw * cell) / 2f + panX;
        top = (getHeight() - gh * cell) / 2f + panY;
    }


    public void setStampListener(Runnable r) { stampListener = r; }
    public boolean isStamping() { return stampSrc != null; }

    private int stampH() {
        return Math.max(1, Math.round(stampW * (float) stampSh / stampSw));
    }

    /** Starts placing the bitmap over the drawing. It can be dragged and resized until it is applied. */
    public void startStamp(Bitmap bmp) {
        stampSw = bmp.getWidth();
        stampSh = bmp.getHeight();
        stampSrc = new int[stampSw * stampSh];
        bmp.getPixels(stampSrc, 0, stampSw, 0, 0, stampSw, stampSh);
        stampW = Math.max(1, Math.min(stampSw, gw));
        stampFx = (gw - stampW) / 2f;
        stampFy = (gh - stampH()) / 2f;
        invalidate();
        if(stampListener != null) stampListener.run();
    }

    /** Same as startStamp but at a given cell, at its own size. */
    public void startStamp(Bitmap bmp, int cellX, int cellY) {
        stampSw = bmp.getWidth();
        stampSh = bmp.getHeight();
        stampSrc = new int[stampSw * stampSh];
        bmp.getPixels(stampSrc, 0, stampSw, 0, 0, stampSw, stampSh);
        stampW = stampSw;
        stampFx = cellX;
        stampFy = cellY;
        invalidate();
        if(stampListener != null) stampListener.run();
    }

    /** Grows or shrinks the stamp around its center, keeping the proportions. */
    public void resizeStamp(int deltaCells) {
        if(stampSrc == null) return;
        float cx = stampFx + stampW / 2f, cy = stampFy + stampH() / 2f;
        stampW = Math.max(1, Math.min(Math.max(gw, gh) * 2, stampW + deltaCells));
        stampFx = cx - stampW / 2f;
        stampFy = cy - stampH() / 2f;
        invalidate();
    }

    public int getStampWidth() { return stampW; }

    public void commitStamp() {
        if(stampSrc == null) return;
        pushUndo();
        int sw = stampW, sh = stampH(), ox = Math.round(stampFx), oy = Math.round(stampFy);
        for(int ty = 0; ty < sh; ty++) {
            for(int tx = 0; tx < sw; tx++) {
                int x = ox + tx, y = oy + ty;
                if(x < 0 || y < 0 || x >= gw || y >= gh) continue;
                int p = stampSrc[Math.min(stampSh - 1, ty * stampSh / sh) * stampSw + Math.min(stampSw - 1, tx * stampSw / sw)];
                if((p >>> 24) != 0) layers[active][y * gw + x] = p;
            }
        }
        cancelStamp();
    }

    public void cancelStamp() {
        stampSrc = null;
        invalidate();
        if(stampListener != null) stampListener.run();
    }

    public boolean isEmpty() {
        for(int i = 0; i < gw * gh; i++) if(layers[0][i] != 0 || layers[1][i] != 0) return false;
        return true;
    }

    public void setListener(Listener l) { listener = l; }
    public void setTool(int t) {
        tool = t;
        if(t != SELECT) clearSelection();
    }

    public boolean hasSelection() { return selOn; }

    public void clearSelection() {
        selOn = false;
        selecting = false;
        invalidate();
    }

    /**
     * Lifts the selected area of the active layer into a stamp so it can be moved or resized.
     * With cut = true the original pixels are erased (move), otherwise they stay (copy).
     * Returns false when the selection holds no pixels.
     */
    public boolean liftSelection(boolean cut) {
        if(!selOn || stampSrc != null) return false;
        int x0 = Math.min(selAx, selBx), x1 = Math.max(selAx, selBx);
        int y0 = Math.min(selAy, selBy), y1 = Math.max(selAy, selBy);
        int w = x1 - x0 + 1, h = y1 - y0 + 1;
        int[] px = new int[w * h];
        boolean any = false;
        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++) {
                int p = layers[active][(y0 + y) * gw + x0 + x];
                px[y * w + x] = p;
                if((p >>> 24) != 0) any = true;
            }
        }
        if(!any) return false;
        if(cut) {
            pushUndo();
            for(int y = y0; y <= y1; y++) for(int x = x0; x <= x1; x++) layers[active][y * gw + x] = 0;
        }
        Bitmap bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        selOn = false;
        startStamp(bmp, x0, y0);
        return true;
    }
    public void setColor(int c) { color = c | 0xFF000000; }
    public int getColor() { return color; }
    public int getGridWidth() { return gw; }
    public int getGridHeight() { return gh; }
    public void setMirror(boolean on) { mirror = on; invalidate(); }
    public boolean isMirror() { return mirror; }
    public void setShapeFill(boolean on) { shapeFill = on; }
    public boolean isShapeFill() { return shapeFill; }
    public void setActiveLayer(int i) { active = i; }
    public int getActiveLayer() { return active; }
    public boolean isLayerVisible(int i) { return visible[i]; }

    public void setLayerVisible(int i, boolean on) {
        visible[i] = on;
        invalidate();
    }

    public void setGridSize(int w, int h) {
        gw = w;
        gh = h;
        layers = new int[][]{new int[w * h], new int[w * h]};
        undo.clear();
        zoom = 1f;
        panX = 0;
        panY = 0;
        requestLayout();
        invalidate();
    }

    public void clear() {
        pushUndo();
        layers[active] = new int[gw * gh];
        invalidate();
    }

    public void undo() {
        if(undo.isEmpty()) return;
        layers = undo.pop();
        invalidate();
    }

    /** Replaces the drawing with the bitmap. The bitmap becomes the grid, so a template is editable pixel by pixel. */
    public void load(Bitmap bmp) {
        setGridSize(bmp.getWidth(), bmp.getHeight());
        bmp.getPixels(layers[0], 0, gw, 0, 0, gw, gh);
        active = 0;
        visible[0] = visible[1] = true;
    }

    private void pushUndo() {
        if(undo.size() >= MAX_UNDO) undo.removeLast();
        undo.push(new int[][]{layers[0].clone(), layers[1].clone()});
    }

    private int composite(int i) {
        int c = visible[0] ? layers[0][i] : 0;
        if(visible[1] && layers[1][i] != 0) c = layers[1][i];
        return c;
    }

    /** The finished image: the visible layers on top of each other. */
    public Bitmap toBitmap() {
        Bitmap bmp = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
        int[] flat = new int[gw * gh];
        for(int i = 0; i < flat.length; i++) flat[i] = composite(i);
        bmp.setPixels(flat, 0, gw, 0, 0, gw, gh);
        return bmp;
    }

    private Bitmap compBitmap, checkerBitmap;
    private int[] compPixels;
    private final RectF dst = new RectF();

    /** The grid is drawn as two bitmaps (checkerboard and drawing) scaled without smoothing, not one rectangle per cell. */
    @Override
    protected void onDraw(Canvas canvas) {
        layout();
        if(compBitmap == null || compBitmap.getWidth() != gw || compBitmap.getHeight() != gh) {
            compBitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            compPixels = new int[gw * gh];
            checkerBitmap = Bitmap.createBitmap(gw, gh, Bitmap.Config.ARGB_8888);
            int[] checker = new int[gw * gh];
            for(int y = 0; y < gh; y++) for(int x = 0; x < gw; x++) checker[y * gw + x] = ((x + y) & 1) == 0 ? 0xFF4A4A4A : 0xFF3C3C3C;
            checkerBitmap.setPixels(checker, 0, gw, 0, 0, gw, gh);
        }
        boolean[] shape = shaping ? shapeMask() : null;
        for(int i = 0; i < compPixels.length; i++) {
            int p = composite(i);
            if(shape != null && shape[i]) p = (color & 0x00FFFFFF) | 0xFF000000;
            compPixels[i] = p;
        }
        compBitmap.setPixels(compPixels, 0, gw, 0, 0, gw, gh);
        dst.set(left, top, left + gw * cell, top + gh * cell);
        paint.setFilterBitmap(false);
        paint.setColor(0xFFFFFFFF);
        if(!preview) canvas.drawBitmap(checkerBitmap, null, dst, paint);
        canvas.drawBitmap(compBitmap, null, dst, paint);
        int x0 = Math.max(0, (int) Math.floor(-left / cell)), x1 = Math.min(gw, (int) Math.ceil((getWidth() - left) / cell));
        int y0 = Math.max(0, (int) Math.floor(-top / cell)), y1 = Math.min(gh, (int) Math.ceil((getHeight() - top) / cell));
        if(stampSrc != null) {
            int sw = stampW, sh = stampH(), ox = Math.round(stampFx), oy = Math.round(stampFy);
            for(int ty = 0; ty < sh; ty++) {
                for(int tx = 0; tx < sw; tx++) {
                    int x = ox + tx, y = oy + ty;
                    if(x < 0 || y < 0 || x >= gw || y >= gh) continue;
                    int p = stampSrc[Math.min(stampSh - 1, ty * stampSh / sh) * stampSw + Math.min(stampSw - 1, tx * stampSw / sw)];
                    if((p >>> 24) == 0) continue;
                    paint.setColor(p);
                    canvas.drawRect(left + x * cell, top + y * cell, left + (x + 1) * cell + 0.5f, top + (y + 1) * cell + 0.5f, paint);
                }
            }
            paint.setColor(AccentColor.get());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawRect(left + ox * cell, top + oy * cell, left + (ox + sw) * cell, top + (oy + sh) * cell, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if(selOn && stampSrc == null && !preview) {
            int sx0 = Math.min(selAx, selBx), sx1 = Math.max(selAx, selBx);
            int sy0 = Math.min(selAy, selBy), sy1 = Math.max(selAy, selBy);
            paint.setColor(AccentColor.get());
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawRect(left + sx0 * cell, top + sy0 * cell, left + (sx1 + 1) * cell, top + (sy1 + 1) * cell, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if(preview) return;
        if(showGrid && cell >= 4f) {
            paint.setColor(0x33FFFFFF);
            paint.setStrokeWidth(1f);
            for(int i = x0; i <= x1; i++) canvas.drawLine(left + i * cell, top + y0 * cell, left + i * cell, top + y1 * cell, paint);
            for(int i = y0; i <= y1; i++) canvas.drawLine(left + x0 * cell, top + i * cell, left + x1 * cell, top + i * cell, paint);
        }
        if(mirror) {
            paint.setColor(AccentColor.get());
            paint.setStrokeWidth(2f);
            float mx = left + gw * cell / 2f;
            canvas.drawLine(mx, top, mx, top + gh * cell, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if(cell <= 0) return true;
        scaleDetector.onTouchEvent(e);
        int action = e.getActionMasked();
        if(action == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        }
        if(action == MotionEvent.ACTION_POINTER_DOWN) {
            if(strokeActive) {
                undo();
                strokeActive = false;
                shaping = false;
            }
            gesture = true;
            lastFx = focusX(e);
            lastFy = focusY(e);
            return true;
        }
        if(e.getPointerCount() >= 2 && action == MotionEvent.ACTION_MOVE) {
            float fx = focusX(e), fy = focusY(e);
            panX += fx - lastFx;
            panY += fy - lastFy;
            lastFx = fx;
            lastFy = fy;
            invalidate();
            return true;
        }
        if(action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if(preview && action == MotionEvent.ACTION_UP && !gesture && onPreviewTap != null
                    && Math.hypot(e.getX() - downX, e.getY() - downY) < 24) {
                onPreviewTap.run();
            }
            if(shaping && action == MotionEvent.ACTION_UP && !gesture) commitShape();
            shaping = false;
            selecting = false;
            strokeActive = false;
            gesture = false;
            invalidate();
            return true;
        }
        if(gesture) return true;
        if(stampSrc != null) {
            if(action == MotionEvent.ACTION_DOWN) {
                getParent().requestDisallowInterceptTouchEvent(true);
                dragX = e.getX();
                dragY = e.getY();
            } else if(action == MotionEvent.ACTION_MOVE) {
                stampFx += (e.getX() - dragX) / cell;
                stampFy += (e.getY() - dragY) / cell;
                dragX = e.getX();
                dragY = e.getY();
                invalidate();
            }
            return true;
        }
        int x = (int) Math.floor((e.getX() - left) / cell), y = (int) Math.floor((e.getY() - top) / cell);
        boolean inside = x >= 0 && y >= 0 && x < gw && y < gh;
        if(action == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
            if(!inside) return true;
            if(tool == PICK) {
                int p = composite(y * gw + x);
                if(p != 0) { color = p; if(listener != null) listener.colorPicked(p); }
                return true;
            }
            if(tool == SELECT) {
                selOn = true;
                selecting = true;
                selAx = selBx = x;
                selAy = selBy = y;
                invalidate();
                return true;
            }
            pushUndo();
            strokeActive = true;
            if(tool == FILL) { fill(x, y); strokeActive = false; return true; }
            if(tool >= LINE) {
                shaping = true;
                sx = ex = x;
                sy = ey = y;
                invalidate();
                return true;
            }
        }
        if(selecting && action == MotionEvent.ACTION_MOVE) {
            selBx = Math.max(0, Math.min(gw - 1, x));
            selBy = Math.max(0, Math.min(gh - 1, y));
            invalidate();
            return true;
        }
        if(shaping && action == MotionEvent.ACTION_MOVE) {
            ex = Math.max(0, Math.min(gw - 1, x));
            ey = Math.max(0, Math.min(gh - 1, y));
            invalidate();
            return true;
        }
        if(inside && strokeActive && (tool == PENCIL || tool == ERASER)
                && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE)) {
            int value = tool == PENCIL ? color : 0;
            layers[active][y * gw + x] = value;
            if(mirror) layers[active][y * gw + (gw - 1 - x)] = value;
            invalidate();
        }
        return true;
    }

    private static float focusX(MotionEvent e) {
        float sum = 0;
        for(int i = 0; i < e.getPointerCount(); i++) sum += e.getX(i);
        return sum / e.getPointerCount();
    }

    private static float focusY(MotionEvent e) {
        float sum = 0;
        for(int i = 0; i < e.getPointerCount(); i++) sum += e.getY(i);
        return sum / e.getPointerCount();
    }

    /** Cells covered by the shape being dragged, also on the mirrored side when the mirror is on. */
    private boolean[] shapeMask() {
        boolean[] mask = new boolean[gw * gh];
        fillMask(mask, sx, sy, ex, ey);
        if(mirror) fillMask(mask, gw - 1 - sx, sy, gw - 1 - ex, ey);
        return mask;
    }

    private void fillMask(boolean[] mask, int ax, int ay, int bx, int by) {
        int minX = Math.min(ax, bx), maxX = Math.max(ax, bx), minY = Math.min(ay, by), maxY = Math.max(ay, by);
        if(tool == LINE) {
            int dx = Math.abs(bx - ax), dy = -Math.abs(by - ay);
            int stepX = ax < bx ? 1 : -1, stepY = ay < by ? 1 : -1;
            int err = dx + dy, x = ax, y = ay;
            while(true) {
                mask[y * gw + x] = true;
                if(x == bx && y == by) break;
                int e2 = 2 * err;
                if(e2 >= dy) { err += dy; x += stepX; }
                if(e2 <= dx) { err += dx; y += stepY; }
            }
        } else if(tool == RECT) {
            for(int y = minY; y <= maxY; y++) {
                for(int x = minX; x <= maxX; x++) {
                    boolean edge = x == minX || x == maxX || y == minY || y == maxY;
                    if(shapeFill || edge) mask[y * gw + x] = true;
                }
            }
        } else if(tool == CIRCLE) {
            float rx = (maxX - minX + 1) / 2f, ry = (maxY - minY + 1) / 2f;
            float cx = minX + rx, cy = minY + ry;
            for(int y = minY; y <= maxY; y++) {
                for(int x = minX; x <= maxX; x++) {
                    boolean in = inEllipse(x + 0.5f - cx, y + 0.5f - cy, rx, ry);
                    boolean hollow = rx > 1.5f && ry > 1.5f && inEllipse(x + 0.5f - cx, y + 0.5f - cy, rx - 1, ry - 1);
                    if(in && (shapeFill || !hollow)) mask[y * gw + x] = true;
                }
            }
        }
    }

    private static boolean inEllipse(float dx, float dy, float rx, float ry) {
        return (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) <= 1f;
    }

    private void commitShape() {
        boolean[] mask = shapeMask();
        for(int i = 0; i < mask.length; i++) if(mask[i]) layers[active][i] = color;
    }

    private void fill(int x, int y) {
        int[] pixels = layers[active];
        int target = pixels[y * gw + x];
        if(target == color) return;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{x, y});
        while(!stack.isEmpty()) {
            int[] c = stack.pop();
            if(c[0] < 0 || c[1] < 0 || c[0] >= gw || c[1] >= gh) continue;
            if(pixels[c[1] * gw + c[0]] != target) continue;
            pixels[c[1] * gw + c[0]] = color;
            stack.push(new int[]{c[0] + 1, c[1]});
            stack.push(new int[]{c[0] - 1, c[1]});
            stack.push(new int[]{c[0], c[1] + 1});
            stack.push(new int[]{c[0], c[1] - 1});
        }
        invalidate();
    }
}

package com.divinedoll.launcher.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Pixel art button texture drawn in code for the option pack: dark outline, light edge on the top and left,
 * flat fill and a thick dark band at the bottom.
 */
public class PackTexture {
    private static final int[] GRAY = {0xFF0B0B0F, 0xFF6C6C6C, 0xFF808080, 0xFF3A3A3A};
    private static final int[] BLUE = {0xFF161C30, 0xFF7380A0, 0xFF8793B0, 0xFF485270};

    public static Bitmap make(Context c, int w, int h, boolean blue) {
        int[] col = blue ? BLUE : GRAY;
        int u = Math.max(2, Math.round(c.getResources().getDisplayMetrics().density * 1.5f));
        w = Math.max(w, u * 8);
        h = Math.max(h, u * 8);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        p.setAntiAlias(false);
        // outline
        p.setColor(col[0]);
        canvas.drawRect(u, 0, w - u, h, p);
        canvas.drawRect(0, u, w, h - u, p);
        // fill
        p.setColor(col[2]);
        canvas.drawRect(u, u, w - u, h - u, p);
        // light edge, top and left
        p.setColor(col[1]);
        canvas.drawRect(u, u, w - u, 2 * u, p);
        canvas.drawRect(u, u, 2 * u, h - 3 * u, p);
        // bottom band: one light row and two dark rows
        canvas.drawRect(u, h - 4 * u, w - u, h - 3 * u, p);
        p.setColor(col[3]);
        canvas.drawRect(u, h - 3 * u, w - u, h - u, p);
        return bmp;
    }
}

package com.divinedoll.launcher.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;

/** Turns a photo or a drawing into a small pixel grid, using the palette or fewer colors. */
public class PixelImport {
    /**
     * @param maxSide the longest side of the result, in cells
     * @param palette colors to use, or null to just reduce the original colors
     */
    public static Bitmap convert(Context c, Uri uri, int maxSide, int[] palette) throws IOException {
        ContentResolver resolver = c.getContentResolver();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try(InputStream in = resolver.openInputStream(uri)) {
            if(in == null) throw new IOException("Cannot open the image");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while(Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide * 4) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap source;
        try(InputStream in = resolver.openInputStream(uri)) {
            if(in == null) throw new IOException("Cannot open the image");
            source = BitmapFactory.decodeStream(in, null, options);
        }
        if(source == null) throw new IOException("Unsupported image");
        float scale = Math.min(1f, maxSide / (float) Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, Math.round(source.getWidth() * scale)), h = Math.max(1, Math.round(source.getHeight() * scale));
        Bitmap small = Bitmap.createScaledBitmap(source, w, h, true);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        for(int y = 0; y < h; y++) {
            for(int x = 0; x < w; x++) {
                int p = small.getPixel(x, y);
                int alpha = p >>> 24;
                out.setPixel(x, y, alpha < 128 ? 0 : (palette != null ? nearest(p, palette) : reduce(p)));
            }
        }
        return out;
    }

    private static int nearest(int p, int[] palette) {
        int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
        int best = palette[0], bestDistance = Integer.MAX_VALUE;
        for(int q : palette) {
            int dr = r - ((q >> 16) & 255), dg = g - ((q >> 8) & 255), db = b - (q & 255);
            int d = dr * dr + dg * dg + db * db;
            if(d < bestDistance) {
                bestDistance = d;
                best = q;
            }
        }
        return best | 0xFF000000;
    }

    /** Four levels per channel, 64 colors in total. */
    private static int reduce(int p) {
        int r = Math.round(((p >> 16) & 255) / 85f) * 85, g = Math.round(((p >> 8) & 255) / 85f) * 85, b = Math.round((p & 255) / 85f) * 85;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}

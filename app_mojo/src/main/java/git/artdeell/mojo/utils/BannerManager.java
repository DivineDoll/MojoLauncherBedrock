package git.artdeell.mojo.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceManager;

import git.artdeell.mojo.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Keeps track of the banner shown on the main menu: a bundled one or an image picked by the user.
 * Custom images are copied to the app private storage and are never part of the APK.
 */
public class BannerManager {
    private static final String PREF_KEY = "launcher_banner";
    private static final String CUSTOM = "custom";
    private static final String CUSTOM_FILE = "custom_banner.png";
    private static final int MAX_WIDTH = 1280;

    private static final int[] DEFAULT_BANNERS = {
            R.drawable.bedrock_banner_art,
            R.drawable.bedrock_banner_day,
            R.drawable.bedrock_banner_night,
            R.drawable.bedrock_banner_desert
    };
    private static final int[] DEFAULT_NAMES = {
            R.string.fork_banner_sunset,
            R.string.fork_banner_day,
            R.string.fork_banner_night,
            R.string.fork_banner_desert
    };

    private static SharedPreferences prefs(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static File customFile(Context context) {
        return new File(context.getFilesDir(), CUSTOM_FILE);
    }

    /** Returns true if a custom image is being shown, so the caller can hide the title text. */
    public static boolean apply(Context context, ImageView target) {
        String selected = prefs(context).getString(PREF_KEY, "0");
        if(CUSTOM.equals(selected)) {
            File file = customFile(context);
            if(file.isFile()) {
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if(bitmap != null) {
                    target.setImageBitmap(bitmap);
                    return true;
                }
            }
            selected = "0";
        }
        int index = 0;
        try {
            index = Math.max(0, Math.min(DEFAULT_BANNERS.length - 1, Integer.parseInt(selected)));
        } catch (NumberFormatException ignored) {}
        target.setImageResource(DEFAULT_BANNERS[index]);
        return false;
    }

    public static void select(Context context, int index) {
        prefs(context).edit().putString(PREF_KEY, Integer.toString(index)).apply();
    }

    public static void saveCustom(Context context, Uri uri) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if(in == null) throw new IOException("Cannot open the image");
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int sample = 1;
        while(bounds.outWidth / (sample * 2) >= MAX_WIDTH) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if(in == null) throw new IOException("Cannot open the image");
            bitmap = BitmapFactory.decodeStream(in, null, options);
        }
        if(bitmap == null) throw new IOException("Unsupported image");
        try (FileOutputStream out = new FileOutputStream(customFile(context))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        prefs(context).edit().putString(PREF_KEY, CUSTOM).apply();
    }

    /**
     * Shows the banner chooser.
     * @param pickCustom called when the user wants to choose an image from the device
     * @param onChanged called after a bundled banner was selected
     */
    public static void showChooser(Context context, Runnable pickCustom, Runnable onChanged) {
        CharSequence[] items = new CharSequence[DEFAULT_NAMES.length + 1];
        for(int i = 0; i < DEFAULT_NAMES.length; i++) items[i] = context.getString(DEFAULT_NAMES[i]);
        items[DEFAULT_NAMES.length] = context.getString(R.string.fork_banner_custom);
        new AlertDialog.Builder(context)
                .setTitle(R.string.fork_banner_pick_title)
                .setItems(items, (dialog, which) -> {
                    if(which == DEFAULT_NAMES.length) {
                        pickCustom.run();
                    } else {
                        select(context, which);
                        onChanged.run();
                    }
                })
                .show();
    }

    public static void setTitleVisible(View title, boolean customImage) {
        title.setVisibility(customImage ? View.GONE : View.VISIBLE);
    }
}

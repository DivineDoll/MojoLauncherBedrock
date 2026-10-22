package git.artdeell.mojo.authenticator.accounts;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Skin storage for offline accounts. The skin is kept in the app private storage and copied
 * into the CustomSkinLoader local skin folder of the game directory when the game starts.
 * The game only shows it when CustomSkinLoader (or a similar mod) is installed.
 */
public class OfflineSkin {
    private static File skinFile(Context context, String username) {
        return new File(context.getFilesDir(), "offline_skins/" + FileUtils.escapeFileName(username) + ".png");
    }

    public static boolean hasSkin(Context context, Account account) {
        return account != null && account.isLocal() && skinFile(context, account.username).isFile();
    }

    /**
     * Validates and stores a skin image for the given offline account.
     * @throws IOException if the file is not a valid Minecraft skin (64x64 or 64x32 PNG)
     */
    public static void importSkin(Context context, Account account, Uri uri) throws IOException {
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if(in == null) throw new IOException("Cannot open the image");
            bitmap = BitmapFactory.decodeStream(in);
        }
        if(bitmap == null) throw new IOException("Unsupported image");
        boolean validSize = bitmap.getWidth() == 64 && (bitmap.getHeight() == 64 || bitmap.getHeight() == 32);
        if(!validSize) {
            bitmap.recycle();
            throw new IOException("A skin must be 64x64 or 64x32 pixels");
        }
        File target = skinFile(context, account.username);
        FileUtils.ensureParentDirectory(target);
        try (FileOutputStream out = new FileOutputStream(target)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally {
            bitmap.recycle();
        }
    }

    /** Uses an already validated library skin for the offline account. */
    public static void setFromFile(Context context, Account account, File png) throws IOException {
        File target = skinFile(context, account.username);
        FileUtils.ensureParentDirectory(target);
        org.apache.commons.io.FileUtils.copyFile(png, target);
    }

    /** @return the stored skin file of the offline account, or null when it has none */
    public static File getSkinFile(Context context, Account account) {
        return hasSkin(context, account) ? skinFile(context, account.username) : null;
    }

    public static void removeSkin(Context context, Account account) {
        File file = skinFile(context, account.username);
        if(file.exists()) file.delete();
    }

    /** Copies the stored skin into the CustomSkinLoader local skin folder of the instance. */
    public static void applyToGameDirectory(Context context, Account account, File gameDirectory) {
        if(!hasSkin(context, account)) return;
        File source = skinFile(context, account.username);
        File targetDirectory = new File(gameDirectory, "CustomSkinLoader/LocalSkin/skins");
        if(!FileUtils.ensureDirectorySilently(targetDirectory)) return;
        File target = new File(targetDirectory, FileUtils.escapeFileName(account.username) + ".png");
        try {
            org.apache.commons.io.FileUtils.copyFile(source, target);
        } catch (IOException e) {
            android.util.Log.w("OfflineSkin", "Could not copy the skin to the game directory", e);
        }
    }
}

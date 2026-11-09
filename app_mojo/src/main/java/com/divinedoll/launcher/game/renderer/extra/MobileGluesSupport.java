package com.divinedoll.launcher.game.renderer.extra;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.divinedoll.launcher.Tools;

/**
 * Compatibility with the MobileGlues plugin app (package com.fcl.plugin.mobileglues): its settings
 * are written to /sdcard/MG, so the renderer reads that folder when it has a config. The library
 * itself can be imported from the installed plugin.
 */
public final class MobileGluesSupport {
    private MobileGluesSupport() {}

    public static final String PACKAGE = "com.fcl.plugin.mobileglues";
    private static final String LIBRARY = "libmobileglues.so";

    /** The folder MobileGlues reads: the plugin one (/sdcard/MG) when it has a config, otherwise the launcher's own. */
    public static File configDir() {
        File shared = new File(Environment.getExternalStorageDirectory(), "MG");
        File own = new File(Tools.DIR_GAME_HOME, "MobileGlues");
        return new File(shared, "config.json").isFile() && shared.canRead() ? shared : own;
    }

    public static void setupEnvironment(Map<String, String> env) {
        File dir = configDir();
        if(dir.isDirectory() || dir.mkdirs()) env.put("MG_DIR_PATH", dir.getAbsolutePath());
        env.put("MG_COUNT_LAUNCH", "1");
    }

    public static boolean isInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static Intent settingsIntent(Context c) {
        return c.getPackageManager().getLaunchIntentForPackage(PACKAGE);
    }

    private static File extract(Context c, String apkPath) throws IOException {
        try(ZipFile zip = new ZipFile(apkPath)) {
            for(String abi : Build.SUPPORTED_ABIS) {
                ZipEntry entry = zip.getEntry("lib/" + abi + "/" + LIBRARY);
                if(entry == null) continue;
                File out = new File(c.getCacheDir(), LIBRARY);
                try(InputStream in = zip.getInputStream(entry); FileOutputStream fos = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while((n = in.read(buf)) > 0) fos.write(buf, 0, n);
                }
                return out;
            }
        }
        return null;
    }

    /** Finds libmobileglues.so in the installed plugin (extracted libs or inside its APK files). */
    public static File findLibrary(Context c) throws IOException {
        ApplicationInfo info;
        try {
            info = c.getPackageManager().getApplicationInfo(PACKAGE, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
        if(info.nativeLibraryDir != null) {
            File direct = new File(info.nativeLibraryDir, LIBRARY);
            if(direct.isFile()) return direct;
        }
        File found = info.sourceDir != null ? extract(c, info.sourceDir) : null;
        if(found == null && info.splitSourceDirs != null) {
            for(String split : info.splitSourceDirs) {
                found = extract(c, split);
                if(found != null) break;
            }
        }
        return found;
    }

    /** Copies the plugin library to the launcher and registers it as the MobileGlues renderer. */
    public static boolean importFromPlugin(Context c) throws IOException {
        File lib = findLibrary(c);
        if(lib == null) return false;
        ExtraRenderers.importFile(c, lib, LIBRARY, "MobileGlues (plugin)", 3);
        return true;
    }
}

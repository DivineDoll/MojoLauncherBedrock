package git.artdeell.mojo.modding.modpacks.api;

import android.content.Context;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.modding.modpacks.models.ModDetail;
import git.artdeell.mojo.progresskeeper.DownloaderProgressWrapper;
import git.artdeell.mojo.utils.DownloadUtils;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.lifecycle.ContextExecutor;

import java.io.File;
import java.io.IOException;

/**
 * Downloads a single mod file into the mods folder of the selected instance.
 */
public class ModInstaller {

    public static void installSingleMod(Context context, ModDetail modDetail, int selectedVersion) {
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                Instance instance = Instances.loadSelectedInstance();
                if(instance == null) {
                    toast(R.string.no_instance);
                    return;
                }
                String url = modDetail.versionUrls[selectedVersion];
                if(url == null || url.isEmpty()) {
                    toast(R.string.fork_mod_no_download);
                    return;
                }
                File modsDirectory = new File(instance.getGameDirectory(), "mods");
                if(!FileUtils.ensureDirectorySilently(modsDirectory)) throw new IOException("Failed to create the mods directory");

                String fileName = url.substring(url.lastIndexOf('/') + 1);
                int queryStart = fileName.indexOf('?');
                if(queryStart >= 0) fileName = fileName.substring(0, queryStart);
                File target = new File(modsDirectory, FileUtils.escapeFileName(fileName));

                byte[] downloadBuffer = new byte[8192];
                DownloadUtils.ensureSha1(target, modDetail.versionHashes[selectedVersion], () ->
                        DownloadUtils.downloadFileMonitored(url, target, downloadBuffer,
                                new DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata,
                                        ProgressLayout.INSTALL_MODPACK)));
                toast(R.string.fork_mod_installed);
            } catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    private static void toast(int resId) {
        ContextExecutor.executeActivity(activity -> Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show());
    }
}

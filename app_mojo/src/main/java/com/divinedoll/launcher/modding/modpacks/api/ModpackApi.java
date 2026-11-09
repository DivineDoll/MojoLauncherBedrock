package com.divinedoll.launcher.modding.modpacks.api;


import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import com.divinedoll.launcher.MojoApplication;
import com.divinedoll.launcher.R;
import com.divinedoll.launcher.Tools;
import com.divinedoll.launcher.modding.modpacks.api.modloader.LoaderInstaller;
import com.divinedoll.launcher.modding.modpacks.models.ModDetail;
import com.divinedoll.launcher.modding.modpacks.models.ModItem;
import com.divinedoll.launcher.modding.modpacks.models.SearchFilters;
import com.divinedoll.launcher.modding.modpacks.models.SearchResult;

import java.io.File;
import java.io.IOException;

/**
 *
 */
public interface ModpackApi {

    /**
     * @param searchFilters Filters
     * @param previousPageResult The result from the previous page
     * @return the list of mod items from specified offset
     */
    SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult);

    /**
     * @param searchFilters Filters
     * @return A list of mod items
     */
    default SearchResult searchMod(SearchFilters searchFilters) {
        return searchMod(searchFilters, null);
    }

    /**
     * Fetch the mod details
     * @param item The moditem that was selected
     * @return Detailed data about a mod(pack)
     */
    ModDetail getModDetails(ModItem item);

    /**
     * Download and install the modpack
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    default void handleModpackInstallation(Context context, ModDetail modDetail, int selectedVersion) {
        if(!modDetail.isModpack) {
            ModInstaller.installSingleMod(context, modDetail, selectedVersion);
            return;
        }
        // Doing this here since when starting installation, the progress does not start immediately
        // which may lead to two concurrent installations (very bad)
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        MojoApplication.sExecutorService.execute(() -> {
            try {
                installModpack(modDetail, selectedVersion);
            }catch (IOException e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            }
        });
    }

    LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException;

    /**
     * Install the mod(pack).
     * May require the download of additional files.
     * May requires launching the installation of a modloader
     * @param modDetail The mod detail data
     * @param selectedVersion The selected version
     */
    LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException;
}

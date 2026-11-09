package com.divinedoll.launcher.fragments;

import android.content.Context;
import android.view.LayoutInflater;
import android.widget.ExpandableListAdapter;

import com.divinedoll.launcher.R;
import com.divinedoll.launcher.modding.bta.BTADownloadTask;
import com.divinedoll.launcher.modding.bta.BTAUtils;
import com.divinedoll.launcher.modding.bta.BTAVersionListAdapter;
import com.divinedoll.launcher.modding.ModloaderListenerProxy;

import java.io.File;
import java.io.IOException;

public class BTAInstallFragment extends ModVersionListFragment<BTAUtils.BTAVersionList> {
    public static final String TAG = "BTAInstallFragment";

    public BTAInstallFragment() {
        super(TAG);
    }

    @Override
    public int getTitleText() {
        return R.string.select_bta_version;
    }

    @Override
    public int getNoDataMsg() {
        return R.string.modloader_dl_failed_to_load_list;
    }

    @Override
    public BTAUtils.BTAVersionList loadVersionList() throws IOException {
        return BTAUtils.downloadVersionList();
    }

    @Override
    public ExpandableListAdapter createAdapter(BTAUtils.BTAVersionList versionList, LayoutInflater layoutInflater) {
        return new BTAVersionListAdapter(versionList, layoutInflater);
    }

    @Override
    public Runnable createDownloadTask(Object selectedVersion, ModloaderListenerProxy listenerProxy) {
        return new BTADownloadTask(listenerProxy, (BTAUtils.BTAVersion) selectedVersion);
    }

    @Override
    public void onDownloadFinished(Context context, File downloadedFile) {
        // We don't have to do anything after the BTADownloadTask ends, so this is a stub
    }
}

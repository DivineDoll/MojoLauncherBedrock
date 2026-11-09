package com.divinedoll.launcher.fragments;

import com.divinedoll.launcher.modding.fabric.FabriclikeUtils;
import com.divinedoll.launcher.modding.ModloaderListenerProxy;

public class QuiltInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "QuiltInstallFragment";
    private static ModloaderListenerProxy sTaskProxy;

    public QuiltInstallFragment() {
        super(FabriclikeUtils.QUILT_UTILS, TAG);
    }
}

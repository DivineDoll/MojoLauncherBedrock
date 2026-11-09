package com.divinedoll.launcher.fragments;

import com.divinedoll.launcher.modding.fabric.FabriclikeUtils;

public class LegacyFabricInstallFragment extends FabriclikeInstallFragment {

    public static final String TAG = "LegacyFabricInstallFragment";
    public LegacyFabricInstallFragment() {
        super(FabriclikeUtils.LEGACY_FABRIC_UTILS, TAG);
    }
}

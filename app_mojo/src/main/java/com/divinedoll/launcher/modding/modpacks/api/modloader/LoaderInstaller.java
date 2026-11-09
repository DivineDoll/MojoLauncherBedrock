package com.divinedoll.launcher.modding.modpacks.api.modloader;

import com.divinedoll.launcher.instances.InstanceInstaller;

import java.io.IOException;

public interface LoaderInstaller {
    boolean requiresGuiInstallation();
    InstanceInstaller createInstaller() throws IOException;
    String installHeadlessly() throws IOException;
}

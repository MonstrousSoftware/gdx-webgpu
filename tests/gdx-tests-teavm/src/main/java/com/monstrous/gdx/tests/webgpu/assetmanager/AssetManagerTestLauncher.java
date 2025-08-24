package com.monstrous.gdx.tests.webgpu.assetmanager;

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.AssetManagerTest;
import com.monstrous.gdx.tests.webgpu.TeaVMTestLauncher;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaApplication;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaPreloadApplicationListener;


public class AssetManagerTestLauncher {

    public static void main(String[] args) {
        TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = true;
        WgTeaPreloadApplicationListener preloadAppListener = new WgTeaPreloadApplicationListener();
        preloadAppListener.startupLogo = TeaVMTestLauncher.startupLogo;
        new WgTeaApplication(new AssetManagerTest(), preloadAppListener, config);
    }
}

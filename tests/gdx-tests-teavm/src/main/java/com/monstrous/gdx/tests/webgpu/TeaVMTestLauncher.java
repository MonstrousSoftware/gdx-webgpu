package com.monstrous.gdx.tests.webgpu;

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.ASimpleGame;
import com.monstrous.gdx.tests.webgpu.ComputeMoldSlime;
import com.monstrous.gdx.tests.webgpu.LightingTest;
import com.monstrous.gdx.tests.webgpu.LoadModelTest;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaApplication;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaPreloadApplicationListener;

public class TeaVMTestLauncher {

    public static String startupLogo = "webgpu-preload.png";

    public static void main(String[] args) {
        TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = false;

        // example of overriding the default start-up logo
        WgTeaPreloadApplicationListener preloadAppListener = new WgTeaPreloadApplicationListener();
        preloadAppListener.startupLogo = startupLogo;

        // new WgTeaApplication(new ComputeMoldSlime(), config);
        new WgTeaApplication(new TestChooser(), preloadAppListener, config);
        // new WgTeaApplication(new LoadModelTest(), config);
        // new WgTeaApplication(new HelloTexture(), config);
    }
}

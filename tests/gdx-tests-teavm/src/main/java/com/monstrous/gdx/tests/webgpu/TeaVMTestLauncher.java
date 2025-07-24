package com.monstrous.gdx.tests.webgpu;

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.ASimpleGame;
import com.monstrous.gdx.tests.webgpu.ComputeMoldSlime;
import com.monstrous.gdx.tests.webgpu.LightingTest;
import com.monstrous.gdx.tests.webgpu.LoadModelTest;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaApplication;


public class TeaVMTestLauncher {

    public static void main(String[] args) {
        TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = true;
        config.preloadAssets = true;

//        new WgTeaApplication(new ComputeMoldSlime(), config);
        new WgTeaApplication(new TestChooser(), config);
//        new WgTeaApplication(new LoadModelTest(), config);
//        new WgTeaApplication(new HelloTexture(), config);
    }
}

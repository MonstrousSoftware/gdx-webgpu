package com.monstrous.gdx.tests.webgpu;


import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;

public class Launcher {

    public static void main (String[] argv) {

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setWindowedMode(640, 480);
        config.setTitle("WebGPU");
        config.backend = WebGPUContext.Backend.DEFAULT;

        config.enableGPUtiming = false; // todo feature needs to be enabled

        config.useVsync(false);

        new WgDesktopApplication(new SpriteBatchTest(), config);
    }

}

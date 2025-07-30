package com.monstrous.gdx.tests.webgpu;


import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;

public class Launcher {

    public static void main (String[] argv) {

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setWindowedMode(640, 480);
        config.setTitle("WebGPU");
        config.backend = WebGPUContext.Backend.VULKAN;

        config.enableGPUtiming = true;

        config.useVsync(true);

        //new WgDesktopApplication(new ImmediateModeRendererTest(), config);
        //new WgDesktopApplication(new ColorTest(), config);
        //new WgDesktopApplication(new TransparencyTest(), config);

        new WgDesktopApplication(new CubeMapTest(), config);
        //new WgDesktopApplication(new FontTest(), config);
        //new WgDesktopApplication(new FullScreenTest(), config);
    }

}

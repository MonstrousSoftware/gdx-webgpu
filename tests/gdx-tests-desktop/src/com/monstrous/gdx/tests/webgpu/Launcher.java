package com.monstrous.gdx.tests.webgpu;


import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;

public class Launcher {

    public static void main (String[] argv) {

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setWindowedMode(800, 480);
        config.setTitle("WebGPU");
        config.backendWebGPU = JWebGPUBackend.DAWN;  // WGPU or DAWN
        config.backend = WebGPUContext.Backend.DEFAULT; // Vulkan, DX12, etc.

        config.enableGPUtiming = false;
        //config.samples = 4;

        config.useVsync(false);

        //new WgDesktopApplication(new ImmediateModeRendererTest(), config);
        //new WgDesktopApplication(new ColorTest(), config);
        //new WgDesktopApplication(new TransparencyTest(), config);

        new WgDesktopApplication(new IBLTest3(), config);
        //new WgDesktopApplication(new FontTest(), config);
        //new WgDesktopApplication(new FullScreenTest(), config);
    }

}

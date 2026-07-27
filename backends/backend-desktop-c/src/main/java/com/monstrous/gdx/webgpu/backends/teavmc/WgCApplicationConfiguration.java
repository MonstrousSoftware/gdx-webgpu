package com.monstrous.gdx.webgpu.backends.teavmc;

import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWApplicationConfiguration;
import com.monstrous.gdx.webgpu.application.WebGPUContext;

/** Runtime configuration for {@link WgCApplication}. */
public class WgCApplicationConfiguration extends GLFWApplicationConfiguration {

    /** WebGPU graphics backend requested from wgpu-native. */
    public WebGPUContext.Backend backend = WebGPUContext.Backend.DEFAULT;

    /** Reserved for GPU timestamp collection; TeaVM C currently keeps timestamp readback disabled. */
    public boolean enableGPUtiming;

    public WgCApplicationConfiguration() {
        // WebGPU requires a valid sample count. GLFW's OpenGL-oriented default is zero.
        samples = 1;
        installGraphicsFactory();
    }

    void installGraphicsFactory() {
        setGraphicsFactory(window -> new WgCGraphics(window, this));
    }
}

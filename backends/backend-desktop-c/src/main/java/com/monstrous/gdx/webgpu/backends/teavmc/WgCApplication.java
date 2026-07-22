package com.monstrous.gdx.webgpu.backends.teavmc;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWApplication;

/** A TeaVM C desktop application backed by GLFW and WebGPU. */
public class WgCApplication extends GLFWApplication {

    public WgCApplication(ApplicationListener listener) {
        this(listener, new WgCApplicationConfiguration());
    }

    public WgCApplication(ApplicationListener listener, WgCApplicationConfiguration config) {
        super(listener, prepare(config));
    }

    private static WgCApplicationConfiguration prepare(WgCApplicationConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        config.installGraphicsFactory();
        return config;
    }
}

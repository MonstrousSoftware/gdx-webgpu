package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.WebGraphics;
import com.github.xpenatan.webgpu.JWebGPULoader;

public class WgTeaApplication extends WebApplication {
    public WgTeaApplication(ApplicationListener appListener, WebApplicationConfiguration config) {
        super(appListener, config);
    }

    public WgTeaApplication(ApplicationListener appListener, ApplicationListener preloadApplication,
            WebApplicationConfiguration config) {
        super(appListener, preloadApplication, config);
    }

    @Override
    protected ApplicationListener createDefaultPreloadAppListener() {
        return new WgTeaPreloadApplicationListener();
    }

    @Override
    protected WebGraphics createGraphics(WebApplicationConfiguration config) {
        addInitQueue();
        JWebGPULoader.init((isSuccess, e) -> {
            if (isSuccess) {
                WgTeaGraphics graphics = (WgTeaGraphics) getGraphics();
                graphics.init(getConfig());
                subtractInitQueue();
            } else {
                e.printStackTrace();
            }
        });
        return new WgTeaGraphics(config);
    }
}

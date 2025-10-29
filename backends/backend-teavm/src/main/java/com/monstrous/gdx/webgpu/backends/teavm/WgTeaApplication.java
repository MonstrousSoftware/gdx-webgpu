package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.backends.teavm.TeaApplication;
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.github.xpenatan.gdx.backends.teavm.TeaGraphics;
import com.github.xpenatan.webgpu.JWebGPULoader;

public class WgTeaApplication extends TeaApplication {
    public WgTeaApplication(ApplicationListener appListener, TeaApplicationConfiguration config) {
        super(appListener, config);
    }

    public WgTeaApplication(ApplicationListener appListener, ApplicationListener preloadApplication, TeaApplicationConfiguration config) {
        super(appListener, preloadApplication, config);
    }

    @Override
    protected ApplicationListener createDefaultPreloadAppListener() {
        return new WgTeaPreloadApplicationListener();
    }

    @Override
    protected TeaGraphics createGraphics(TeaApplicationConfiguration config) {
        return new WgTeaGraphics(config);
    }

    @Override
    protected void init() {
        addInitQueue();
        super.init();
        JWebGPULoader.init((isSuccess, e) -> {
            if(isSuccess) {
                WgTeaGraphics graphics = (WgTeaGraphics)getGraphics();
                graphics.init(getConfig());
                subtractInitQueue();
            }
            else {
                e.printStackTrace();
            }
        });
    }
}

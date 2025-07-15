package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.backends.teavm.TeaApplication;
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.github.xpenatan.gdx.backends.teavm.TeaGraphics;

public class WgTeaApplication extends TeaApplication {
    public WgTeaApplication(ApplicationListener appListener, TeaApplicationConfiguration config) {
        super(appListener, config);
    }

    @Override
    protected TeaGraphics createGraphics(TeaApplicationConfiguration config) {
        return new WgTeaGraphics(config);
    }
}

package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.WebGraphics;
import com.github.xpenatan.webgpu.JWebGPULoader;
import org.teavm.jso.browser.AnimationFrameCallback;
import org.teavm.jso.browser.Window;

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
                // Don't subtract init queue yet — the adapter/device request started by
                // init() is asynchronous.  Poll until context.isReady() is true so that
                // step() (and thus listener.create()) only runs after beginFrame() can
                // be called, preventing "writeTexture outside of beginFrame" warnings.
                waitForContextReady(graphics);
            } else {
                e.printStackTrace();
            }
        });
        return new WgTeaGraphics(config);
    }

    /** Polls each animation frame until the WebGPU adapter/device are fully initialised, then unblocks the init queue. */
    private void waitForContextReady(WgTeaGraphics graphics) {
        graphics.context.update();
        if (graphics.context.isReady()) {
            subtractInitQueue();
        } else {
            Window.requestAnimationFrame(new AnimationFrameCallback() {
                @Override
                public void onAnimationFrame(double timestamp) {
                    waitForContextReady(graphics);
                }
            });
        }
    }
}

package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.github.xpenatan.gdx.teavm.backends.web.TeaApplication;
import com.github.xpenatan.gdx.teavm.backends.web.TeaApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.TeaGraphics;
import com.github.xpenatan.gdx.teavm.backends.web.dom.HTMLDocumentExt;
import com.github.xpenatan.gdx.teavm.backends.web.dom.impl.TeaWindow;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.utils.WgGL20;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;

public class WgTeaGraphics extends TeaGraphics implements WgGraphics {

    private static final String canvasName = "webgpuCanvas";
    private static final String canvasWGPU = "#" + canvasName;

    public WebGPUApplication context;
    private WGPUInstance instance;
    private boolean webGPUReady = false;
    private WgGL20 gl20;

    public WgTeaGraphics(TeaApplicationConfiguration config) {
        this.config = config;
        TeaWindow window = new TeaWindow();
        HTMLDocumentExt document = window.getDocument();
        HTMLElement elementID = document.getElementById(config.canvasID);
        this.canvas = (HTMLCanvasElement) elementID;
        canvas.setId(canvasName);
        this.gl20 = new WgGL20();
    }

    public void init(TeaApplicationConfiguration config) {
        TeaApplication teaApplication = TeaApplication.get();
        teaApplication.addInitQueue();

        if (config.width >= 0 || config.height >= 0) {
            if (config.isFixedSizeApplication()) {
                setCanvasSize(config.width, config.height, false);
            } else {
                TeaWindow currentWindow = TeaWindow.get();
                int width = currentWindow.getClientWidth() - config.padHorizontal;
                int height = currentWindow.getClientHeight() - config.padVertical;
                setCanvasSize(width, height, config.usePhysicalPixels);
            }
        }

        WebGPUApplication.Configuration configg = new WebGPUApplication.Configuration(1, true, false,
            WebGPUContext.Backend.WEBGPU);

        instance = WGPU.setupInstance();
        if (!instance.isValid()) {
            throw new RuntimeException("WebGPU: cannot get instance");
        }
        WGPUSurface surface = instance.createWebSurface(canvasWGPU);
        if (surface == null) {
            throw new RuntimeException("WebGPU: cannot get surface");
        }

        this.context = new WebGPUApplication(configg, instance, surface);

        // listen to fullscreen changes
        addFullscreenChangeListener(canvas, new FullscreenChanged() {
            @Override
            public void fullscreenChanged() {
                // listening to fullscreen mode changes
            }
        });
    }

    @Override
    public void resize(ApplicationListener appListener, int width, int height) {
        context.resize(width, height);
        Gdx.gl.glViewport(0, 0, width, height);
        appListener.resize(width, height);
    }

    @Override
    public void render(ApplicationListener listener) {
        context.renderFrame(listener);
    }

    @Override
    public void update() {
        if (context != null) {
            context.update();
            if (!webGPUReady && context.isReady()) {
                TeaApplication teaApplication = TeaApplication.get();
                teaApplication.subtractInitQueue();
                webGPUReady = true;
            }
        }
        super.update();
    }

    @Override
    public WebGPUContext getContext() {
        return context;
    }

    @Override
    public GL20 getGL20() {
        return gl20;
    }

}

package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.github.xpenatan.gdx.backends.teavm.TeaGraphics;
import com.github.xpenatan.gdx.backends.teavm.assetloader.AssetInstance;
import com.github.xpenatan.gdx.backends.teavm.dom.HTMLDocumentExt;
import com.github.xpenatan.gdx.backends.teavm.dom.impl.TeaWindow;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.utils.WgGL20;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;

public class WgTeaGraphics extends TeaGraphics implements WgGraphics {

    private static String canvasName = "webgpuCanvas";
    private static String canvasWGPU = "#" + canvasName;

    public WebGPUApplication context;
    private WGPUInstance instance;

    public WgTeaGraphics(TeaApplicationConfiguration config) {
        this.config = config;
        TeaWindow window = new TeaWindow();
        HTMLDocumentExt document = window.getDocument();
        HTMLElement elementID = document.getElementById(config.canvasID);
        this.canvas = (HTMLCanvasElement)elementID;
        canvas.setId(canvasName);

        this.gl20 = new WgGL20();

        JWebGPULoader.init((isSuccess, e) -> {
            System.out.println("WebGPU Init Success: " + isSuccess);
            if(isSuccess) {
                init(config);
            }
            else {
                e.printStackTrace();
            }
        });
    }

    private void init(TeaApplicationConfiguration config) {
        AssetInstance.getDownloaderInstance().addQueue();

        if(config.width >= 0 || config.height >= 0) {
            if(config.isFixedSizeApplication()) {
                setCanvasSize(config.width, config.height, false);
            }
            else {
                TeaWindow currentWindow = TeaWindow.get();
                int width = currentWindow.getClientWidth() - config.padHorizontal;
                int height = currentWindow.getClientHeight() - config.padVertical;
                setCanvasSize(width, height, config.usePhysicalPixels);
            }
        }

        WebGPUApplication.Configuration configg = new WebGPUApplication.Configuration(
            1,
            true,
            false,
            WebGPUContext.Backend.WEBGPU);

        System.out.println("Creating instance");
        instance = WGPU.createInstance();
        if(!instance.isValid()) {
            throw new RuntimeException("WebGPU: cannot get instance");
        }
        System.out.println("Creating surface");
        WGPUSurface surface = instance.createWebSurface(canvasWGPU);
        if(surface ==null) {
            throw new RuntimeException("WebGPU: cannot get surface");
        }

        System.out.println("Creating context");
        this.context = new WebGPUApplication(configg, instance, surface);



        // not sure what this does, but it seems important...
        AssetInstance.getDownloaderInstance().subtractQueue();

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
        System.out.println("resize");
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
        if(context != null) {
            context.update();
        }
        super.update();
    }

    @Override
    public WebGPUContext getContext() {
        return context;
    }
}

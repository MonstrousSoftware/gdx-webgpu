package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.math.Rectangle;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.wrappers.GPUTimer;

public abstract class WebGPUContext {
    public static enum Backend {
        DEFAULT,
        D3D11,
        D3D12,
        METAL,
        OPENGL,
        OPENGL_ES,
        VULKAN,
        WEBGPU,
        HEADLESS
    }

    static public class RenderOutputState {
        WGPUTextureView targetView;
        WGPUTextureFormat surfaceFormat;
        WgTexture depthTexture;
        Rectangle viewport;

        public RenderOutputState(WGPUTextureView targetView, WGPUTextureFormat surfaceFormat, WgTexture depthTexture, Rectangle viewport) {
            this.targetView = targetView;
            this.surfaceFormat = surfaceFormat;
            this.depthTexture = depthTexture;
            this.viewport = new Rectangle(viewport);
        }
    }

    public WGPUInstance instance;
    public WGPUAdapter adapter;
    public WGPUDevice device;
    public WGPUSurface surface;
    public WGPUQueue queue;
    public WGPUCommandEncoder encoder;
    public WGPUTextureFormat surfaceFormat;
    public WGPUTextureView targetView;
    public WgTexture depthTexture;
    public int frameNumber;

    abstract WGPUDevice getDevice ();
    abstract WGPUQueue getQueue ();
    public abstract WGPUTextureFormat getSurfaceFormat();
    public abstract WGPUTextureView getTargetView();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param depthTexture depth texture to use for output, may be null
     * @return Render output state, to restore current output state with popTargetView().
     */
    public abstract RenderOutputState pushTargetView(WgTexture texture, WgTexture depthTexture);

    /** Restore previous output target.
     *
     */
    public abstract void popTargetView(RenderOutputState prevState);

    abstract WGPUCommandEncoder getCommandEncoder ();
    public abstract WgTexture getDepthTexture ();
//    public abstract WgTexture pushDepthTexture(WgTexture depth);
//    public abstract void popDepthTexture(WgTexture prevDepth);
    //abstract WGPUBackendType getRequestedBackendType();
    public abstract  int getSamples();
    public abstract WgTexture getMultiSamplingTexture();

    public abstract void setViewportRectangle(int x, int y, int w, int h);
    public abstract Rectangle getViewportRectangle();

    public abstract void enableScissor(boolean mode);
    public abstract boolean isScissorEnabled();
    public abstract void setScissor(int x, int y, int w, int h);
    public abstract Rectangle getScissor();

    public abstract GPUTimer getGPUTimer();
    public abstract float getAverageGPUtime(int pass);


    public abstract void resize(int width, int height);
}

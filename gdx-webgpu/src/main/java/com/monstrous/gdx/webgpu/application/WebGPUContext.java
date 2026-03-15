package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.math.Rectangle;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.wrappers.GPUTimer;

public abstract class WebGPUContext {
    public static enum Backend {
        DEFAULT, D3D11, D3D12, METAL, OPENGL, OPENGL_ES, VULKAN, WEBGPU, HEADLESS
    }

    static public class RenderOutputState {
        WGPUTextureView[] targetViews;
        WGPUTextureFormat[] surfaceFormats;
        WgTexture depthTexture;
        Rectangle viewport = new Rectangle();
        int numSamples;

        /** Default constructor for pre-allocation and reuse. Use set() to fill the fields. */
        public RenderOutputState() {
        }

        /** Fill this instance with the given state. Copies the viewport rectangle by value. */
        public void set(WGPUTextureView[] targetViews, WGPUTextureFormat[] surfaceFormats, WgTexture depthTexture,
                Rectangle viewport, int numSamples) {
            this.targetViews = targetViews;
            this.surfaceFormats = surfaceFormats;
            this.depthTexture = depthTexture;
            this.viewport.set(viewport);
            this.numSamples = numSamples;
        }
    }

    public WGPUInstance instance;
    public WGPUDevice device;
    public WGPUSurface surface;
    public WGPUQueue queue;
    public WGPUCommandEncoder encoder;
    public WGPUTextureFormat[] surfaceFormats; // MRT support
    public WGPUTextureView[] targetViews; // MRT support
    public WgTexture depthTexture;
    public int frameNumber;

    abstract WGPUDevice getDevice();

    abstract WGPUQueue getQueue();

    public abstract WGPUTextureFormat getSurfaceFormat();

    public abstract boolean hasLinearOutput();

    public abstract WGPUTextureView getTargetView();

    public abstract WGPUTextureView[] getTargetViews(); // MRT

    /**
     * Use provided texture for output (must have usage RenderAttachment).
     *
     * @param outState pre-allocated state object that will be filled with the current output state for later restore.
     * @return outState (for convenience)
     */
    // public abstract RenderOutputState pushTargetView(WgTexture texture, WgTexture depthTexture);
    public abstract RenderOutputState pushTargetView(RenderOutputState outState, WGPUTextureView textureView,
            WGPUTextureFormat textureFormat, int width, int height, WgTexture depthTexture);

    public abstract RenderOutputState pushTargetView(RenderOutputState outState, WGPUTextureView[] textureViews,
            WGPUTextureFormat[] textureFormats, int width, int height, WgTexture depthTexture);

    /**
     * Restore previous output target.
     *
     */
    public abstract void popTargetView(RenderOutputState prevState);

    abstract WGPUCommandEncoder getCommandEncoder();

    public abstract WgTexture getDepthTexture();

    // public abstract WgTexture pushDepthTexture(WgTexture depth);
    // public abstract void popDepthTexture(WgTexture prevDepth);
    // abstract WGPUBackendType getRequestedBackendType();
    public abstract int getSamples();

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

    public abstract void setVSync(boolean vsync);

    public abstract boolean isFrameStarted();
}

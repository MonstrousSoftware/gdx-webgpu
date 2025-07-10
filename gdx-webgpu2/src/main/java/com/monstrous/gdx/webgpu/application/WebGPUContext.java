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

    public WGPUInstance instance;
    public WGPUAdapter adapter;
    public WGPUDevice device;
    public WGPUSurface surface;
    public WGPUQueue queue;
    public WGPUCommandEncoder encoder;
    public WGPUTextureFormat surfaceFormat;
    public WGPUTextureView targetView;
    public WgTexture depthTexture;

    abstract WGPUDevice getDevice ();
    abstract WGPUQueue getQueue ();
    public abstract WGPUTextureFormat getSurfaceFormat();
    public abstract WGPUTextureView getTargetView();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param oldViewport out: previous viewport dimensions
     * @return Pointer of previous TextureView
     */
    public abstract WGPUTextureView pushTargetView(WgTexture texture, Rectangle oldViewport);

    /** Restore previous output target.
     *
     * @param prevPointer TextureView returned by pushTargetView()
     * @param prevViewport in: Viewport rectangle returned by pushTargetView()
     */
    public abstract void popTargetView(WGPUTextureView prevPointer, Rectangle prevViewport);

    abstract WGPUCommandEncoder getCommandEncoder ();
    public abstract WgTexture getDepthTexture ();
    public abstract WgTexture pushDepthTexture(WgTexture depth);
    public abstract void popDepthTexture(WgTexture prevDepth);
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

}

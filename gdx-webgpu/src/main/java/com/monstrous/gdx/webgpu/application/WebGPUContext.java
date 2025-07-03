package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.math.Rectangle;
import com.monstrous.gdx.webgpu.webgpu.WGPUBackendType;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.monstrous.gdx.webgpu.wrappers.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import jnr.ffi.Pointer;

public abstract class WebGPUContext {
    public WebGPUDevice device;
    public WebGPUQueue queue;
    public Pointer surface;
    public WGPUTextureFormat surfaceFormat;
    public Pointer targetView;
    public WebGPUCommandEncoder commandEncoder;
    public WgTexture depthTexture;

    abstract WebGPU_JNI getWebGPU();

    abstract WebGPUDevice getDevice ();
    abstract WebGPUQueue getQueue ();
    abstract WGPUTextureFormat getSurfaceFormat ();
    abstract Pointer getTargetView ();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param oldViewport previous viewport dimensions
     * @return Pointer of previous TextureView
     */
    abstract Pointer pushTargetView(WgTexture texture, Rectangle oldViewport);

    /** Restore previous output target.
     *
     * @param prevPointer TextureView returned by pushTargetView()
     * @param prevViewport Viewport rectangle returned by pushTargetView()
     */
    abstract void popTargetView(Pointer prevPointer, Rectangle prevViewport);

    abstract WebGPUCommandEncoder getCommandEncoder ();
    abstract WgTexture getDepthTexture ();
    abstract WgTexture pushDepthTexture(WgTexture depth);
    abstract void popDepthTexture(WgTexture prevDepth);
    abstract WGPUBackendType getRequestedBackendType();
    abstract  int getSamples();
    abstract WgTexture getMultiSamplingTexture();

    abstract void setViewportRectangle(int x, int y, int w, int h);
    abstract Rectangle getViewportRectangle();

    abstract void enableScissor(boolean mode);
    abstract boolean isScissorEnabled();
    abstract void setScissor(int x, int y, int w, int h);
    abstract Rectangle getScissor();

    abstract GPUTimer getGPUTimer();
    abstract float getAverageGPUtime(int pass);

}

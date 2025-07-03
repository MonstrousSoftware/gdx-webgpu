package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.math.Rectangle;
import com.github.xpenatan.webgpu.*;

public abstract class WebGPUContext {
    public WebGPUInstance instance;
    public WebGPUAdapter adapter;
    public WebGPUDevice device;
    public WebGPUSurface surface;
    public WebGPUQueue queue;
    public WebGPUCommandEncoder encoder;
    public WGPUTextureFormat surfaceFormat;
    public WebGPUTextureView targetView;
//    public WgTexture depthTexture;

    abstract WebGPUDevice getDevice ();
    abstract WebGPUQueue getQueue ();
    abstract WGPUTextureFormat getSurfaceFormat ();
    abstract WebGPUTextureView getTargetView ();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param oldViewport previous viewport dimensions
     * @return Pointer of previous TextureView
     */
//    abstract WebGPUTextureView pushTargetView(WgTexture texture, Rectangle oldViewport);

    /** Restore previous output target.
     *
     * @param prevPointer TextureView returned by pushTargetView()
     * @param prevViewport Viewport rectangle returned by pushTargetView()
     */
    abstract void popTargetView(WebGPUTextureView prevPointer, Rectangle prevViewport);

    abstract WebGPUCommandEncoder getCommandEncoder ();
//    abstract WgTexture getDepthTexture ();
//    abstract WgTexture pushDepthTexture(WgTexture depth);
//    abstract void popDepthTexture(WgTexture prevDepth);
    //abstract WGPUBackendType getRequestedBackendType();
    abstract  int getSamples();
//    abstract WgTexture getMultiSamplingTexture();

    abstract public void setViewportRectangle(int x, int y, int w, int h);
    abstract public Rectangle getViewportRectangle();

    public abstract void enableScissor(boolean mode);
    abstract boolean isScissorEnabled();
    public abstract void setScissor(int x, int y, int w, int h);
    public abstract Rectangle getScissor();

    //abstract GPUTimer getGPUTimer();
    //abstract float getAverageGPUtime(int pass);

}

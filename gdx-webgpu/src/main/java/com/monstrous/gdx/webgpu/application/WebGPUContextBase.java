package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.math.Rectangle;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.wrappers.GPUTimer;
import jnr.ffi.Pointer;

public interface WebGPUContextBase {

    //WebGPU_JNI getWebGPU (); // to be phased out

    WebGPUDevice getDevice ();
    WebGPUQueue getQueue ();
    WGPUTextureFormat getSurfaceFormat ();
    WebGPUTextureView getTargetView ();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param oldViewport previous viewport dimensions
     * @return Pointer of previous TextureView
     */
    WebGPUTextureView pushTargetView(WgTexture texture, Rectangle oldViewport);

    /** Restore previous output target.
     *
     * @param prevPointer TextureView returned by pushTargetView()
     * @param prevViewport Viewport rectangle returned by pushTargetView()
     */
    void popTargetView(WebGPUTextureView prevPointer, Rectangle prevViewport);

    WebGPUCommandEncoder getCommandEncoder ();
    WgTexture getDepthTexture ();
    WgTexture pushDepthTexture(WgTexture depth);
    void popDepthTexture(WgTexture prevDepth);
    WGPUBackendType getRequestedBackendType();
    int getSamples();
    WgTexture getMultiSamplingTexture();

    void setViewportRectangle(int x, int y, int w, int h);
    Rectangle getViewportRectangle();

    void enableScissor(boolean mode);
    boolean isScissorEnabled();
    void setScissor(int x, int y, int w, int h);
    Rectangle getScissor();

    GPUTimer getGPUTimer();
    float getAverageGPUtime(int pass);

}

package com.monstrous.gdx.webgpu;

import com.badlogic.gdx.math.Rectangle;
import com.monstrous.gdx.webgpu.webgpu.WGPUBackendType;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.monstrous.gdx.webgpu.wrappers.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import jnr.ffi.Pointer;

public interface WebGPUGraphicsBase {

    WebGPU_JNI getWebGPU ();

    WebGPUDevice getDevice ();
    WebGPUQueue getQueue ();
    WGPUTextureFormat getSurfaceFormat ();
    Pointer getTargetView ();

    /** Use provided texture for output (must have usage RenderAttachment).
     *
     * @param texture texture to use for output
     * @param oldViewport previous viewport dimensions
     * @return Pointer of previous TextureView
     */
    Pointer pushTargetView(WgTexture texture, Rectangle oldViewport);

    /** Restore previous output target.
     *
     * @param prevPointer TextureView returned by pushTargetView()
     * @param prevViewport Viewport rectangle returned by pushTargetView()
     */
    void popTargetView(Pointer prevPointer, Rectangle prevViewport);

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

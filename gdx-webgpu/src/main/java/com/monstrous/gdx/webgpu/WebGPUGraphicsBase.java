package com.monstrous.gdx.webgpu;

import com.badlogic.gdx.math.Rectangle;
import com.monstrous.gdx.webgpu.webgpu.WGPUBackendType;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.monstrous.gdx.webgpu.wrappers.GPUTimer;
import com.monstrous.gdx.webgpu.wrappers.WebGPUCommandEncoder;
import com.monstrous.gdx.webgpu.wrappers.WebGPUDevice;
import com.monstrous.gdx.webgpu.wrappers.WebGPUQueue;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import jnr.ffi.Pointer;

public interface WebGPUGraphicsBase {

    WebGPU_JNI getWebGPU ();

    WebGPUDevice getDevice ();
    WebGPUQueue getQueue ();
    WGPUTextureFormat getSurfaceFormat ();
    Pointer getTargetView ();
    WebGPUCommandEncoder getCommandEncoder ();
    WgTexture getDepthTexture ();
    WGPUBackendType getRequestedBackendType();
    int getSamples();
    WgTexture getMultiSamplingTexture();

    void setViewport(int x, int y, int w, int h);
    Rectangle getViewport();

    void enableScissor(boolean mode);
    boolean isScissorEnabled();
    void setScissor(int x, int y, int w, int h);
    Rectangle getScissor();

    GPUTimer getGPUTimer();
    float getAverageGPUtime(int pass);

}

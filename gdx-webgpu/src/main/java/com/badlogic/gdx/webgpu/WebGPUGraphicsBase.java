package com.badlogic.gdx.webgpu;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.webgpu.webgpu.WGPUBackendType;
import com.badlogic.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.badlogic.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.webgpu.wrappers.WebGPUCommandEncoder;
import com.badlogic.gdx.webgpu.wrappers.WebGPUDevice;
import com.badlogic.gdx.webgpu.wrappers.WebGPUQueue;
import com.badlogic.gdx.webgpu.wrappers.WebGPUTexture;
import jnr.ffi.Pointer;

public interface WebGPUGraphicsBase {

    WebGPU_JNI getWebGPU ();

    WebGPUDevice getDevice ();
    WebGPUQueue getQueue ();
    WGPUTextureFormat getSurfaceFormat ();
    Pointer getTargetView ();
    WebGPUCommandEncoder getCommandEncoder ();
    WebGPUTexture getDepthTexture ();
    WGPUBackendType getRequestedBackendType();
    int getSamples();
    WebGPUTexture getMultiSamplingTexture();

    void setViewport(int x, int y, int w, int h);
    Rectangle getViewport();

    void setScissor(int x, int y, int w, int h);
    Rectangle getScissor();

}

package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.webgpu.WGPUCommandEncoderDescriptor;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;

public class WebGPUCommandEncoder implements Disposable {
    private final WebGPU_JNI webGPU;

    private final Pointer encoder;

    public WebGPUCommandEncoder(WebGPUDevice device) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        // create a command encoder
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.createDirect();
        encoderDesc.setNextInChain();
        encoder = webGPU.wgpuDeviceCreateCommandEncoder(device.getHandle(), encoderDesc);
    }

    public Pointer getHandle(){
        return encoder;
    }

    public WebGPUComputePass beginComputePass(){
        return new WebGPUComputePass(this);
    }

    public WebGPUCommandBuffer finish(){
        return new WebGPUCommandBuffer(this);
    }

    public void copyBufferToBuffer(WebGPUBuffer buffer1, int offset1, WebGPUBuffer buffer2, int offset2, int byteCount){
        webGPU.wgpuCommandEncoderCopyBufferToBuffer(encoder, buffer1.getHandle(), offset1, buffer2.getHandle(), offset2, byteCount);
    }

    @Override
    public void dispose() {
           webGPU.wgpuCommandEncoderRelease(encoder);
    }
}

package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.webgpu.WGPUPipelineLayoutDescriptor;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

public class WebGPUPipelineLayout implements Disposable {
    private final WebGPU_JNI webGPU;
    private final Pointer handle;

    public WebGPUPipelineLayout(String label, WebGPUBindGroupLayout... bindGroupLayouts ) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        int count = bindGroupLayouts.length;
        try (MemoryStack stack = stackPush()) {
            ByteBuffer pLayouts = stack.malloc(count * Long.BYTES);
            for (int i = 0; i < count; i++)
                pLayouts.putLong(i*Long.BYTES, bindGroupLayouts[i].getHandle().address());

            WGPUPipelineLayoutDescriptor pipelineLayoutDesc = WGPUPipelineLayoutDescriptor.createDirect();
            pipelineLayoutDesc.setNextInChain();
            pipelineLayoutDesc.setLabel(label);
            pipelineLayoutDesc.setBindGroupLayoutCount(count);
            pipelineLayoutDesc.setBindGroupLayouts(JavaWebGPU.createByteBufferPointer(pLayouts));  // expects an array of layouts in native memory
            handle = webGPU.wgpuDeviceCreatePipelineLayout(gfx.getDevice().getHandle(), pipelineLayoutDesc);
        } // free malloced memory
    }

    public Pointer getHandle() {
        return handle;
    }

    @Override
    public void dispose() {
        webGPU.wgpuPipelineLayoutRelease(handle);
    }
}

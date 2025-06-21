package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

public class WebGPUQueue implements Disposable {
    private final WebGPU_JNI webGPU;
    private final Pointer queue;

    public WebGPUQueue(WebGPUDevice device) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();
        queue = webGPU.wgpuDeviceGetQueue(device.getHandle());
    }

    public Pointer getHandle(){
        return queue;
    }

    public void submit(WebGPUCommandBuffer commandBuffer) {
        try (MemoryStack stack = stackPush()) {
            // create native array of command buffer pointers
            ByteBuffer pBuffers = stack.malloc(Long.BYTES);
            pBuffers.putLong(0, commandBuffer.getHandle().address());

            webGPU.wgpuQueueSubmit(queue, 1, JavaWebGPU.createByteBufferPointer(pBuffers));
        }
    }

    public void writeBuffer(WebGPUBuffer buffer, int bufferOffset, Pointer data, int dataSize) {
        webGPU.wgpuQueueWriteBuffer(queue, buffer.getHandle(),bufferOffset, data, dataSize);
    }

    @Override
    public void dispose() {
        webGPU.wgpuQueueRelease(queue);
    }
}

package com.badlogic.gdx.webgpu.wrappers;


import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.WGPUBufferUsage;
import jnr.ffi.Pointer;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class WebGPUVertexBuffer extends WebGPUBuffer {

    /** size in bytes */
    public WebGPUVertexBuffer(long bufferSize) {
        this(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Vertex, bufferSize);
    }

    /** size in bytes */
    public WebGPUVertexBuffer(long usage, long bufferSize) {
        super("vertex buffer", usage, bufferSize);
    }

    public void setVertices(float[] vertexData) {
        // Create vertex buffer
        int size = vertexData.length *Float.BYTES;
        if(size > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");
        Pointer dataBuf = JavaWebGPU.createDirectPointer( size );
        dataBuf.put(0L, vertexData, 0, vertexData.length);
        // Upload geometry data to the buffer
        gfx.getQueue().writeBuffer(this, 0, dataBuf, size);
    }

    public void setVertices(ArrayList<Float> floats) {
        int size = floats.size()*Float.BYTES;
        if(size > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");

        Pointer vertData = JavaWebGPU.createDirectPointer( size );
        for (int i = 0; i < floats.size(); i++) {
            vertData.putFloat((long) i *Float.BYTES, floats.get(i));
        }
        // Upload geometry data to the buffer
        gfx.getQueue().writeBuffer(this, 0, vertData, size);
    }

    public void setVertices(ByteBuffer byteData, int targetOffset, int sizeInBytes) {
        //int sizeInBytes = byteData.limit();
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        if(sizeInBytes > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: ByteBuffer contents too large.");

        // Upload data to the buffer
        gfx.getQueue().writeBuffer(this, targetOffset, JavaWebGPU.createByteBufferPointer(byteData), sizeInBytes);
    }

}

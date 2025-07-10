package com.monstrous.gdx.webgpu.wrappers;


import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUByteBuffer;
import com.github.xpenatan.webgpu.WGPUFloatBuffer;
import java.util.ArrayList;

public class WebGPUVertexBuffer extends WebGPUBuffer {

    /** size in bytes */
    public WebGPUVertexBuffer(int bufferSizeInBytes) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Vertex), bufferSizeInBytes);
    }

    /** size in bytes */
    public WebGPUVertexBuffer(WGPUBufferUsage usage, int bufferSizeInBytes) {
        super("vertex buffer", usage, bufferSizeInBytes);
    }

    public void setVertices(float[] vertexData) {
        // Create vertex buffer
        int size = vertexData.length * Float.BYTES;
        if(size > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");
        WGPUByteBuffer dataBuf = WGPUByteBuffer.obtain(size);
        WGPUFloatBuffer floatBuf = dataBuf.asFloatBuffer();
        for(float f : vertexData)
            floatBuf.put(f);
        // Upload geometry data to the buffer
        webgpu.queue.writeBuffer(buffer, 0, dataBuf );
    }

    public void setVertices(ArrayList<Float> floats) {
        int size = floats.size()*Float.BYTES;
        if(size > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");

        WGPUByteBuffer dataBuf = WGPUByteBuffer.obtain(size);
        WGPUFloatBuffer floatBuf = dataBuf.asFloatBuffer();
        for (int i = 0; i < floats.size(); i++) {
            floatBuf.put(floats.get(i));
        }
        // Upload geometry data to the buffer
        webgpu.queue.writeBuffer(buffer, 0, dataBuf );
    }


    public void setVertices(WGPUByteBuffer byteData, int targetOffset) {
        int sizeInBytes = byteData.getLimit();
        // pad to multiple of 4
        while(sizeInBytes % 4 != 0){
            byteData.put((byte)0);
            sizeInBytes++;
        }
        if(sizeInBytes > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: ByteBuffer contents too large.");

        // Upload data to the buffer
        System.out.println("write buffer in setVertices: size:"+sizeInBytes+" byteData: "+byteData.getLimit());
        webgpu.queue.writeBuffer(buffer, targetOffset, byteData );
    }

}

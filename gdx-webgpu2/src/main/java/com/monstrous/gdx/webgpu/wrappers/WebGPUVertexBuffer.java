package com.monstrous.gdx.webgpu.wrappers;


import com.github.xpenatan.webgpu.WGPUBufferUsage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
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
        ByteBuffer dataBuf = ByteBuffer.allocateDirect(size);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer floatBuf = dataBuf.asFloatBuffer();
        for(float f : vertexData)
            floatBuf.put(f);
        // Upload geometry data to the buffer
        write(0, dataBuf, size);
    }

    public void setVertices(ArrayList<Float> floats) {
        int size = floats.size()*Float.BYTES;
        if(size > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");

        ByteBuffer dataBuf = ByteBuffer.allocateDirect(size);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer floatBuf = dataBuf.asFloatBuffer();
        for (int i = 0; i < floats.size(); i++) {
            floatBuf.put(floats.get(i));
        }
        // Upload geometry data to the buffer
        write(0, dataBuf, size);
    }

    public void setVertices(ByteBuffer byteData, int targetOffset, int sizeInBytes) {
//        for(int i = 0; i < byteData.limit()/Float.BYTES; i++){
//            System.out.println("vertex "+i+" : "+byteData.getFloat());
//        }
//        byteData.position(0);

        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        if(sizeInBytes > getSize()) throw new IllegalArgumentException("VertexBuffer.setVertices: ByteBuffer contents too large.");

        // Upload data to the buffer
        //System.out.println("write buffer in setVertices: size:"+sizeInBytes+" byteData: "+byteData.getLimit());
        write(targetOffset, byteData, sizeInBytes);
    }

}

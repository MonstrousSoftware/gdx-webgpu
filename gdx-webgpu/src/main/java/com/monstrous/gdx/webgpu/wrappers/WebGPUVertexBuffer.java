package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.utils.BufferUtils;
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
        setVertices(vertexData, 0, vertexData.length, 0);
//        // Create vertex buffer
//        int size = vertexData.length * Float.BYTES;
//        if (size > getSize())
//            throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");
//        ByteBuffer dataBuf = BufferUtils.newUnsafeByteBuffer(size);
//        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
//        FloatBuffer floatBuf = dataBuf.asFloatBuffer();
//        for (float f : vertexData)
//            floatBuf.put(f);
//        // Upload geometry data to the buffer
//        write(0, dataBuf, size);
//        BufferUtils.disposeUnsafeByteBuffer(dataBuf);
    }

    /** Add vertex data from a section of a float array.
     *
     * @param vertexData floats
     * @param offset    start offset in vertexData
     * @param count     number of floats
     * @param targetOffset  offset in destination (number of floats)
     */
    public void setVertices(float[] vertexData, int offset, int count, int targetOffset) {
        // Create vertex buffer
        int size = count * Float.BYTES;
        if (size > getSize())
            throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");
        ByteBuffer dataBuf = BufferUtils.newUnsafeByteBuffer(size);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer floatBuf = dataBuf.asFloatBuffer();
        floatBuf.put(vertexData, offset, count);
        // Upload geometry data to the buffer
        write(targetOffset*Float.BYTES, dataBuf, size);
        BufferUtils.disposeUnsafeByteBuffer(dataBuf);
    }

    public void setVertices(ArrayList<Float> floats) {
        int size = floats.size() * Float.BYTES;
        if (size > getSize())
            throw new IllegalArgumentException("VertexBuffer.setVertices: data set too large.");

        ByteBuffer dataBuf = BufferUtils.newUnsafeByteBuffer(size);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer floatBuf = dataBuf.asFloatBuffer();
        for (int i = 0; i < floats.size(); i++) {
            floatBuf.put(floats.get(i));
        }
        // Upload geometry data to the buffer
        write(0, dataBuf, size);

        BufferUtils.disposeUnsafeByteBuffer(dataBuf);
    }

    public void setVertices(ByteBuffer byteData, int targetOffset, int sizeInBytes) {
        // for(int i = 0; i < byteData.limit()/Float.BYTES; i++){
        // System.out.println("vertex "+i+" : "+byteData.getFloat());
        // }
        // byteData.position(0);

        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        if (sizeInBytes > getSize())
            throw new IllegalArgumentException("VertexBuffer.setVertices: ByteBuffer contents too large.");

        // Upload data to the buffer
        // System.out.println("write buffer in setVertices: size:"+sizeInBytes+" byteData: "+byteData.getLimit());
        write(targetOffset, byteData, sizeInBytes);
    }

    public void setVertices(ByteBuffer byteData) {
        // sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        // if (sizeInBytes > getSize())
        // throw new IllegalArgumentException("VertexBuffer.setVertices: ByteBuffer contents too large.");

        // Upload data to the buffer
        // System.out.println("write buffer in setVertices: size:"+sizeInBytes+" byteData: "+byteData.getLimit());
        write(0, byteData);
    }

}

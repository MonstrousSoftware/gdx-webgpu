package com.badlogic.gdx.webgpu.wrappers;


import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.WGPUBufferUsage;
import com.badlogic.gdx.webgpu.webgpu.WGPUIndexFormat;
import jnr.ffi.Pointer;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;

public class WebGPUIndexBuffer extends WebGPUBuffer {

    private int indexSizeInBytes;   // 2 or 4
    private int indexCount;

    public WebGPUIndexBuffer(long usage, int bufferSize, int indexSizeInBytes) {
        super("index buffer", usage, align(bufferSize));
        this.indexSizeInBytes = indexSizeInBytes;
    }

    public WebGPUIndexBuffer(ArrayList<Integer> indexValues, int indexSizeInBytes) {
        this(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Index, align(indexValues.size()*indexSizeInBytes),indexSizeInBytes);
        setIndices(indexValues);
    }

    public WebGPUIndexBuffer(short[] indexValues, int offset, int indexCount) {
        this(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Index, align(indexCount*2), 2);
        setIndices(0, indexValues, offset, indexCount);
    }

    public WebGPUIndexBuffer(ShortBuffer shortBuffer) {
        this(shortBuffer.array(), shortBuffer.arrayOffset(), shortBuffer.limit());      // to be tested....
    }

    private static int align(int indexBufferSize ){
        return (indexBufferSize + 3) & ~3; // round up to the next multiple of 4
    }

    public int getIndexCount(){
        return indexCount;
    }

    public WGPUIndexFormat getFormat(){
        return determineFormat(indexSizeInBytes);
    }

    public static WGPUIndexFormat determineFormat(int indexSizeInBytes ){
        if(indexSizeInBytes == 2)
            return WGPUIndexFormat.Uint16;
        else if(indexSizeInBytes == 4)
            return WGPUIndexFormat.Uint32;
        else
            throw new RuntimeException("setIndices: support only 16 bit or 32 bit indices.");
    }

    public void setIndices(int bufferOffset, short[] indices, int srcOffset, int indexCount){
        this.indexSizeInBytes = 2;  // 2 bytes per short
        this.indexCount = indexCount;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        Pointer iData = JavaWebGPU.createDirectPointer(indexBufferSize);    // allocate native memory
        iData.put(0, indices, srcOffset, indexCount);
        setIndices(iData, bufferOffset, indexBufferSize);
    }

    public void setIndices(int bufferOffset, int[] indices, int indexCount){
        this.indexSizeInBytes = 4;
        this.indexCount = indexCount;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        Pointer iData = JavaWebGPU.createDirectPointer(indexBufferSize);
        iData.put(0, indices, 0, indexCount);
        setIndices(iData, bufferOffset, indexBufferSize);
    }

    public void setIndices(ArrayList<Integer> indexValues) {
        if(indexValues == null) {
            indexCount = 0;
            return;
        }
        indexCount = indexValues.size();
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        Pointer idata = JavaWebGPU.createDirectPointer(indexBufferSize);
        if (indexSizeInBytes == 2) {
            for (int i = 0; i < indexCount; i++) {
                idata.putShort((long) i * indexSizeInBytes, (short) (int) indexValues.get(i));
            }
        } else if (indexSizeInBytes == 4) {
            for (int i = 0; i < indexCount; i++) {
                idata.putInt((long) i * indexSizeInBytes, indexValues.get(i));
            }
        }
        setIndices(idata, 0, indexBufferSize);
    }

    public void setIndices(ByteBuffer byteData) {
        int sizeInBytes = byteData.capacity();
        indexCount = sizeInBytes/2;
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        if(sizeInBytes > getSize()) throw new IllegalArgumentException("IndexBuffer.setIndices: data too large.");

        // Upload data to the buffer
        gfx.getQueue().writeBuffer(this, 0, JavaWebGPU.createByteBufferPointer(byteData), sizeInBytes);
    }

    /** fill index buffer with raw data. */
    private void setIndices(Pointer idata, int bufferOffset, int indexBufferSize) {
        if(bufferOffset + indexBufferSize > getSize()) throw new IllegalArgumentException("IndexBuffer.setIndices: data too large.");

        // Upload data to the buffer
        gfx.getQueue().writeBuffer(this, bufferOffset, idata, indexBufferSize);
    }
}

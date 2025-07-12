package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.utils.Array;
import com.github.xpenatan.webgpu.*;

public class WebGPUIndexBuffer extends WebGPUBuffer {

    private int indexSizeInBytes;   // 2 or 4
    private int indexCount;

    public WebGPUIndexBuffer(WGPUBufferUsage usage, int bufferSize, int indexSizeInBytes) {
        super("index buffer", usage, align(bufferSize));
        this.indexSizeInBytes = indexSizeInBytes;
    }

    public WebGPUIndexBuffer(Array<Integer> indexValues, int indexSizeInBytes) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexValues.size*indexSizeInBytes),indexSizeInBytes);
        setIndices(indexValues);
    }

    public WebGPUIndexBuffer(short[] indexValues, int offset, int indexCount) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexCount*2), 2);
        setIndices(0, indexValues, offset, indexCount);
    }

    public WebGPUIndexBuffer(short[] indexValues) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexValues.length*2), 2);
        setIndices(0, indexValues, 0, indexValues.length);
    }

//    public WebGPUIndexBuffer(WGPUShortBuffer shortBuffer) {
//        this(shortBuffer.array(), shortBuffer.arrayOffset(), shortBuffer.limit());      // to be tested....
//    }

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

        WGPUByteBuffer byteBuffer = WGPUByteBuffer.obtain(indexBufferSize);
        WGPUShortBuffer iData = byteBuffer.asShortBuffer();
        for(int i = 0; i < indexCount; i++){
            iData.put(indices[i+srcOffset]);
        }
        write(bufferOffset, byteBuffer);
    }

    public void setIndices(int bufferOffset, int[] indices, int indexCount){
        this.indexSizeInBytes = 4;
        this.indexCount = indexCount;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        WGPUByteBuffer byteBuffer = WGPUByteBuffer.obtain(indexBufferSize);
        WGPUIntBuffer iData = byteBuffer.asIntBuffer();
        for(int i = 0; i < indexCount; i++){
            iData.put(indices[i]);
        }
        write(bufferOffset, byteBuffer);
    }

    public void setIndices(Array<Integer> indexValues) {
        if(indexValues == null) {
            indexCount = 0;
            return;
        }
        indexCount = indexValues.size;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        WGPUByteBuffer byteBuffer = WGPUByteBuffer.obtain(indexBufferSize);
        if (indexSizeInBytes == 2) {
            WGPUShortBuffer iData = byteBuffer.asShortBuffer();
            for (int i = 0; i < indexCount; i++) {
                iData.put(indexValues.get(i).shortValue());
            }
        } else if (indexSizeInBytes == 4) {
            WGPUIntBuffer iData = byteBuffer.asIntBuffer();
            for (int i = 0; i < indexCount; i++) {
                iData.put(indexValues.get(i));
            }
        }
        write(0, byteBuffer);
    }

    public void setIndices(WGPUByteBuffer byteData) {
        int sizeInBytes = byteData.getCapacity();
        indexCount = sizeInBytes/2;
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4 for writeBuffer
        if(sizeInBytes > getSize()) throw new IllegalArgumentException("IndexBuffer.setIndices: data too large.");

        // Upload data to the buffer
        write( 0, byteData);      // todo sizeInBytes not supported
    }

    /** fill index buffer with raw data. */
    // indexBufferSize is ignored
    private void setIndices(WGPUByteBuffer idata, int bufferOffset, int indexBufferSize) {
        if(bufferOffset + indexBufferSize > getSize()) throw new IllegalArgumentException("IndexBuffer.setIndices: data too large.");

        // Upload data to the buffer
        write(bufferOffset, idata);
    }
}

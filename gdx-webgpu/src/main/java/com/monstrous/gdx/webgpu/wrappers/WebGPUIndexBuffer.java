package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.*;

import java.nio.*;

public class WebGPUIndexBuffer extends WebGPUBuffer {

    private int indexSizeInBytes; // 2 or 4
    private int indexCount;

    public WebGPUIndexBuffer(WGPUBufferUsage usage, int bufferSize, int indexSizeInBytes) {
        super("index buffer", usage, align(bufferSize));
        this.indexSizeInBytes = indexSizeInBytes;
    }

    public WebGPUIndexBuffer(Array<Integer> indexValues, int indexSizeInBytes) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexValues.size * indexSizeInBytes),
                indexSizeInBytes);
        setIndices(indexValues);
    }

    public WebGPUIndexBuffer(short[] indexValues, int offset, int indexCount) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexCount * 2), 2);
        setIndices(0, indexValues, offset, indexCount);
    }

    public WebGPUIndexBuffer(short[] indexValues) {
        this(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index), align(indexValues.length * 2), 2);
        setIndices(0, indexValues, 0, indexValues.length);
    }

    // public WebGPUIndexBuffer(WGPUShortBuffer shortBuffer) {
    // this(shortBuffer.array(), shortBuffer.arrayOffset(), shortBuffer.limit()); // to be tested....
    // }

    private static int align(int indexBufferSize) {
        return (indexBufferSize + 3) & ~3; // round up to the next multiple of 4
    }

    public int getIndexCount() {
        return indexCount;
    }

    public WGPUIndexFormat getFormat() {
        return determineFormat(indexSizeInBytes);
    }

    public static WGPUIndexFormat determineFormat(int indexSizeInBytes) {
        if (indexSizeInBytes == 2)
            return WGPUIndexFormat.Uint16;
        else if (indexSizeInBytes == 4)
            return WGPUIndexFormat.Uint32;
        else
            throw new RuntimeException("setIndices: support only 16 bit or 32 bit indices.");
    }

    /**
     * set a range of indices in the GPU index buffer *
     *
     * @param dstOffset offset in destination buffer in bytes
     * @param indices short array of indices
     * @param srcOffset offset in the short array to start (in indices)
     * @param indexCount number of indices to copy
     */
    public void setIndices(int dstOffset, short[] indices, int srcOffset, int indexCount) {
        this.indexSizeInBytes = 2; // 2 bytes per short
        this.indexCount = indexCount; // note: not so reliable because we are writing with a bufferOffset
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        ByteBuffer byteBuffer = BufferUtils.newUnsafeByteBuffer(indexBufferSize);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer shorts = byteBuffer.asShortBuffer();
        shorts.put(indices, srcOffset, indexCount);
        ((Buffer) byteBuffer).limit(indexBufferSize);
        write(dstOffset, byteBuffer);
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }

    /**
     * set a range of integer indices in the GPU index buffer *
     *
     * @param dstOffset offset in destination buffer in bytes
     * @param indices integer array of indices
     * @param srcOffset offset in the integer array to start (in indices)
     * @param indexCount number of indices to copy
     */
    public void setIndices(int dstOffset, int[] indices, int srcOffset, int indexCount) {
        this.indexSizeInBytes = 4;
        this.indexCount = indexCount;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        ByteBuffer byteBuffer = BufferUtils.newUnsafeByteBuffer(indexBufferSize);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(indices, srcOffset, indexCount);
        ((Buffer) byteBuffer).limit(indexBufferSize);
        write(dstOffset, byteBuffer);
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }

    public void setIndices(Array<Integer> indexValues) {
        if (indexValues == null) {
            indexCount = 0;
            return;
        }
        indexCount = indexValues.size;
        int indexBufferSize = align(indexCount * indexSizeInBytes);

        ByteBuffer byteBuffer = BufferUtils.newUnsafeByteBuffer(indexBufferSize);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        if (indexSizeInBytes == 2) {
            ShortBuffer iData = byteBuffer.asShortBuffer();
            for (int i = 0; i < indexCount; i++) {
                iData.put(indexValues.get(i).shortValue());
            }

        } else if (indexSizeInBytes == 4) {
            IntBuffer iData = byteBuffer.asIntBuffer();
            for (int i = 0; i < indexCount; i++) {
                iData.put(indexValues.get(i));
            }
        }
        ((Buffer) byteBuffer).limit(indexBufferSize);
        write(0, byteBuffer);
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }

    public void setIndices(ByteBuffer byteData) {
        // for(int i = 0; i < byteData.limit()/2; i++){
        // System.out.println("index "+i/2+" : "+byteData.getShort());
        // }
        int limit = byteData.limit();
        if (limit % 4 != 0) // round up the limit to multiple of 4 for writeBuffer
            byteData.limit(limit + 4 - (limit % 4));
        int sizeInBytes = byteData.limit();
        indexCount = sizeInBytes / 2;
        if (sizeInBytes > getSize())
            throw new IllegalArgumentException("IndexBuffer.setIndices: data too large.");

        // Upload data to the buffer
        write(0, byteData);
    }
}

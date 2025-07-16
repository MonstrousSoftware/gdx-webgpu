package com.monstrous.gdx.webgpu.graphics.g3d;

import com.monstrous.gdx.webgpu.webgpu.WGPUBufferUsage;
import com.monstrous.gdx.webgpu.webgpu.WGPUIndexFormat;
import com.monstrous.gdx.webgpu.wrappers.WebGPUIndexBuffer;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class WgIndexBuffer implements IndexData {

    final ShortBuffer shortBuffer;
    final IntBuffer intBuffer;
    final ByteBuffer byteBuffer;
    protected WebGPUIndexBuffer indexBuffer = null;
    private boolean isDirty = true;
    private final boolean wideIndices;


    /** Create index buffer.
     *
     * @param maxIndices maximum number of indices to be stored
     */
    public WgIndexBuffer(int maxIndices) {
        // use wide indices (i.e. int rather than short) is maxIndices is too large for shorts
        this(maxIndices, (maxIndices >= Short.MAX_VALUE));
    }

    /** Create index buffer.
     *
     * @param maxIndices maximum number of indices to be stored
     * @param wideIndices false for 16 bit indices (short), true for 32 bit indices (int)
     */
    public WgIndexBuffer(int maxIndices, boolean wideIndices) {
        this.wideIndices = wideIndices;
        int indexSize = wideIndices ? 4 : 2;
        int sizeInBytes = maxIndices * indexSize;
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4
        byteBuffer = BufferUtils.newUnsafeByteBuffer(sizeInBytes);
        shortBuffer = byteBuffer.asShortBuffer();
        intBuffer = byteBuffer.asIntBuffer();
        ((Buffer) shortBuffer).flip();
        ((Buffer) intBuffer).flip();
        ((Buffer)byteBuffer).flip();
        int usage = WGPUBufferUsage.CopyDst | WGPUBufferUsage.Index;
        indexBuffer = new WebGPUIndexBuffer(usage, sizeInBytes, indexSize);
    }

    @Override
    public int getNumIndices() {
        return wideIndices ? intBuffer.limit() : shortBuffer.limit();
    }

    @Override
    public int getNumMaxIndices() {
        return wideIndices ? intBuffer.capacity() : shortBuffer.capacity();
    }

    public int getIndexSize(){
        return wideIndices ? 4 : 2;
    }

    @Override
    public void setIndices(short[] indices, int offset, int count) {
        ((Buffer) shortBuffer).clear();
        shortBuffer.put(indices, offset, count);
        ((Buffer) shortBuffer).flip();
        ((Buffer)byteBuffer).position(0);
        ((Buffer)byteBuffer).limit(count << 1);
        isDirty = true;
    }

    @Override
    public void setIndices(ShortBuffer indices) {
        int pos = indices.position();
        ((Buffer) shortBuffer).clear();
        ((Buffer) shortBuffer).limit(indices.remaining());
        shortBuffer.put(indices);
        ((Buffer) shortBuffer).flip();
        ((Buffer)indices).position(pos);
        ((Buffer)byteBuffer).position(0);
        ((Buffer)byteBuffer).limit(shortBuffer.limit() << 1);
        isDirty = true;
    }

    @Override
    public void updateIndices(int targetOffset, short[] indices, int offset, int count) {
        final int pos = byteBuffer.position();
        ((Buffer)byteBuffer).position(targetOffset * 2);
        BufferUtils.copy(indices, offset, byteBuffer, count);
        ((Buffer)byteBuffer).position(pos);
        isDirty = true;
    }

    @Override
    public ShortBuffer getBuffer() {
        return shortBuffer;
    }

    @Override
    public ShortBuffer getBuffer(boolean forWriting) {
        isDirty |= forWriting;
        return shortBuffer;
    }

    public IntBuffer getIntBuffer(boolean forWriting) {
        isDirty |= forWriting;
        return intBuffer;
    }

    @Override
    public void bind() {
        if(isDirty){
            // upload data to GPU buffer if needed
            indexBuffer.setIndices(byteBuffer);
            isDirty = false;
        }
    }

    public void bind(WebGPURenderPass renderPass){
        bind();
        // bind index buffer to render pass
        long size = indexBuffer.getSize();  // in bytes
        renderPass.setIndexBuffer( indexBuffer.getHandle(), (wideIndices ? WGPUIndexFormat.Uint32 : WGPUIndexFormat.Uint16), 0, size);
    }

    @Override
    public void unbind() {
        // no-op
    }

    @Override
    public void invalidate() {

    }

    @Override
    public void dispose() {
        indexBuffer.dispose();
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }
}

package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.monstrous.gdx.webgpu.wrappers.WebGPUIndexBuffer;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

import java.nio.*;

/** equivalent to IndexBufferObject, or IndexArray
 * Supports 16-bit (short) and 32-bit (int) indices
 * */
public class WgIndexBuffer implements IndexData {

    final ByteBuffer byteBuffer;
    final ShortBuffer shortBuffer;
    final IntBuffer intBuffer;
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
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        shortBuffer = byteBuffer.asShortBuffer();
        intBuffer = byteBuffer.asIntBuffer();
//        ((Buffer) shortBuffer).flip();
//        ((Buffer) intBuffer).flip();
//        ((Buffer)byteBuffer).flip();
        WGPUBufferUsage usage = WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Index);
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
//
//        ((Buffer) shortBuffer).flip();
//        ((Buffer)byteBuffer).position(0);
//        ((Buffer)byteBuffer).limit(count << 1);
        isDirty = true;
    }

    @Override
    public void setIndices(ShortBuffer indices) {
        int pos = indices.position();
        ((Buffer) shortBuffer).clear();
        ((Buffer) shortBuffer).limit(indices.remaining());
        shortBuffer.put(indices);
//        ((Buffer) shortBuffer).flip();
//        ((Buffer)indices).position(pos);
//        ((Buffer)byteBuffer).position(0);
//        ((Buffer)byteBuffer).limit(shortBuffer.limit() << 1);
        isDirty = true;
    }


    /** targetOffset in indices */
    @Override
    public void updateIndices(int targetOffset, short[] indices, int offset, int count) {
        ((Buffer)shortBuffer).position(targetOffset);
        shortBuffer.put(indices, offset, count);
        isDirty = true;
    }

    /** targetOffset in indices */
    public void updateIndices(int targetOffset, int[] indices, int offset, int count) {
        ((Buffer)intBuffer).position(targetOffset);
        intBuffer.put(indices, offset, count);
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
            ((Buffer)shortBuffer).flip();
            ((Buffer)intBuffer).flip();
            // copy limit from short or int buffer to byte buffer
            int byteLimit;
            if(wideIndices)
                byteLimit = Integer.BYTES * intBuffer.limit();
            else
                byteLimit = Short.BYTES * shortBuffer.limit();
            byteBuffer.limit(byteLimit);
            byteBuffer.position(0);     // reset position for reading
            indexBuffer.setIndices(byteBuffer);
            isDirty = false;
        }
    }

    public void bind(WebGPURenderPass renderPass){
        bind();
        // bind index buffer to render pass
        int size = indexBuffer.getSize();  // in bytes
        renderPass.setIndexBuffer( indexBuffer.getBuffer(), (wideIndices ? WGPUIndexFormat.Uint32 : WGPUIndexFormat.Uint16), 0, size);
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

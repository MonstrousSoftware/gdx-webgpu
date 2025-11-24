package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.monstrous.gdx.webgpu.wrappers.WebGPUIndexBuffer;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

import java.nio.*;

/**
 * equivalent to IndexBufferObject, or IndexArray Supports 16-bit (short) and 32-bit (int) indices
 */
public class WgIndexBuffer implements IndexData {

    final ByteBuffer byteBuffer;
    final ShortBuffer shortBuffer;
    final IntBuffer intBuffer;
    protected WebGPUIndexBuffer indexBuffer = null;
    private final boolean isStatic;
    private boolean isDirty = true;
    private boolean isFrozen;
    private final boolean wideIndices;

    /**
     * Create index buffer.
     *
     * @param isStatic will this index buffer never change? Allows to free the internal backing buffer after use.
     * @param maxIndices maximum number of indices to be stored
     */
    public WgIndexBuffer(boolean isStatic, int maxIndices) {
        // use wide indices (i.e. int rather than short) is maxIndices is too large for shorts
        this(isStatic, maxIndices, (maxIndices >= 65535)); //Short.MAX_VALUE));
    }

    /**
     * Create index buffer.
     *
     * @param isStatic will this index buffer never change? Allows to free the internal backing buffer after use.
     * @param maxIndices maximum number of indices to be stored
     * @param wideIndices false for 16 bit indices (short), true for 32 bit indices (int)
     */
    public WgIndexBuffer(boolean isStatic, int maxIndices, boolean wideIndices) {
        this.isStatic = isStatic;
        this.wideIndices = wideIndices;
        int indexSize = wideIndices ? 4 : 2;
        int sizeInBytes = maxIndices * indexSize;
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4
        byteBuffer = BufferUtils.newUnsafeByteBuffer(sizeInBytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        isFrozen = false;
        shortBuffer = byteBuffer.asShortBuffer();
        intBuffer = byteBuffer.asIntBuffer();
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

    public int getIndexSize() {
        return wideIndices ? 4 : 2;
    }

    @Override
    public void setIndices(short[] indices, int offset, int count) {
        if (isStatic && isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        ((Buffer) shortBuffer).clear();
        shortBuffer.put(indices, offset, count);
        isDirty = true;
    }

    /**
     * Set indices using a ShortBuffer. Uses values from the ShortBuffer's current position to its limit
     */
    @Override
    public void setIndices(ShortBuffer indices) {
        if (isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        int pos = indices.position();
        ((Buffer) shortBuffer).clear();
        ((Buffer) shortBuffer).limit(indices.remaining());
        shortBuffer.put(indices);
        isDirty = true;
    }

    /** targetOffset in indices */
    @Override
    public void updateIndices(int targetOffset, short[] indices, int offset, int count) {
        if (isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        ((Buffer) shortBuffer).position(targetOffset);
        shortBuffer.put(indices, offset, count);
        isDirty = true;
    }

    /** targetOffset in indices */
    public void updateIndices(int targetOffset, int[] indices, int offset, int count) {
        if (isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        ((Buffer) intBuffer).position(targetOffset);
        intBuffer.put(indices, offset, count);
        isDirty = true;
    }

    @Override
    @Deprecated
    public ShortBuffer getBuffer() {
        return shortBuffer;
    }

    @Override
    public ShortBuffer getBuffer(boolean forWriting) {
        if (forWriting && isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        isDirty |= forWriting;
        return shortBuffer;
    }

    public IntBuffer getIntBuffer(boolean forWriting) {
        if (forWriting && isFrozen)
            throw new GdxRuntimeException("WgIndexBuffer: static buffer cannot be modified.");
        isDirty |= forWriting;
        return intBuffer;
    }

    @Override
    public void bind() {
        if (isDirty) {
            // upload data to GPU buffer if needed
            ((Buffer) shortBuffer).flip();
            ((Buffer) intBuffer).flip();
            // copy limit from short or int buffer to byte buffer
            int byteLimit;
            if (wideIndices)
                byteLimit = Integer.BYTES * intBuffer.limit();
            else
                byteLimit = Short.BYTES * shortBuffer.limit();
            byteBuffer.limit(byteLimit);
            byteBuffer.position(0); // reset position for reading
            indexBuffer.setIndices(byteBuffer); // write to GPU buffer
            isDirty = false;

            // if this is a static buffer, we can release the backing buffer.
            if (isStatic) {
                BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
                isFrozen = true; // no more modifications allowed
            }
        }
    }

    public void bind(WebGPURenderPass renderPass) {
        bind();
        // bind index buffer to render pass
        int size = indexBuffer.getSize(); // in bytes
        renderPass.setIndexBuffer(indexBuffer.getBuffer(),
                (wideIndices ? WGPUIndexFormat.Uint32 : WGPUIndexFormat.Uint16), 0, size);
    }

    @Override
    public void unbind() {
        // no-op
    }

    @Override
    public void invalidate() {
        // no-op
    }

    @Override
    public void dispose() {
        indexBuffer.dispose();
        if (!isFrozen)
            BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }
}

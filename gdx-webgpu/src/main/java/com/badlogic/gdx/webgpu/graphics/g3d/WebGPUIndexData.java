package com.badlogic.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.webgpu.webgpu.WGPUBufferUsage;
import com.badlogic.gdx.webgpu.webgpu.WGPUIndexFormat;
import com.badlogic.gdx.webgpu.wrappers.WebGPUIndexBuffer;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.graphics.glutils.IndexData;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class WebGPUIndexData implements IndexData {

    final ShortBuffer buffer;
    final ByteBuffer byteBuffer;
    protected WebGPUIndexBuffer indexBuffer = null;
    private boolean isDirty = true;

    public WebGPUIndexData(int maxIndices) {
        int sizeInBytes = maxIndices * 2;
        sizeInBytes = (sizeInBytes + 3) & ~3; // round up to multiple of 4
        byteBuffer = BufferUtils.newUnsafeByteBuffer(sizeInBytes);
        buffer = byteBuffer.asShortBuffer();
        ((Buffer)buffer).flip();
        ((Buffer)byteBuffer).flip();
        int usage = WGPUBufferUsage.CopyDst | WGPUBufferUsage.Index;
        indexBuffer = new WebGPUIndexBuffer(usage, maxIndices*2, 2);
    }

    @Override
    public int getNumIndices() {
        return buffer.limit();
    }

    @Override
    public int getNumMaxIndices() {
        return buffer.capacity();
    }

    @Override
    public void setIndices(short[] indices, int offset, int count) {
        ((Buffer)buffer).clear();
        buffer.put(indices, offset, count);
        ((Buffer)buffer).flip();
        ((Buffer)byteBuffer).position(0);
        ((Buffer)byteBuffer).limit(count << 1);
        isDirty = true;
    }

    @Override
    public void setIndices(ShortBuffer indices) {
        int pos = indices.position();
        ((Buffer)buffer).clear();
        ((Buffer)buffer).limit(indices.remaining());
        buffer.put(indices);
        ((Buffer)buffer).flip();
        ((Buffer)indices).position(pos);
        ((Buffer)byteBuffer).position(0);
        ((Buffer)byteBuffer).limit(buffer.limit() << 1);
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
        return buffer;
    }

    @Override
    public ShortBuffer getBuffer(boolean forWriting) {
        isDirty |= forWriting;
        return buffer;
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
        long size = indexBuffer.getSize();
        renderPass.setIndexBuffer( indexBuffer.getHandle(), WGPUIndexFormat.Uint16, 0, size);
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

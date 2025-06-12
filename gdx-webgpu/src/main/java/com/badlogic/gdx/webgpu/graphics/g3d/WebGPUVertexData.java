package com.badlogic.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.webgpu.wrappers.WebGPUVertexBuffer;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.utils.BufferUtils;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;


public class WebGPUVertexData implements VertexData {
    final VertexAttributes attributes;
    final FloatBuffer buffer;
    final ByteBuffer byteBuffer;
    protected WebGPUVertexBuffer vertexBuffer = null;
    private boolean isDirty = true;

    public WebGPUVertexData (int numVertices, VertexAttribute... attributes) {
        this(numVertices, new VertexAttributes(attributes));
    }

    public WebGPUVertexData(int numVertices, VertexAttributes attributes) {
        this.attributes = attributes;
        byteBuffer = BufferUtils.newUnsafeByteBuffer(this.attributes.vertexSize * numVertices);
        buffer = byteBuffer.asFloatBuffer();
        ((Buffer)buffer).flip();
        ((Buffer)byteBuffer).flip();
        vertexBuffer = new WebGPUVertexBuffer((long) this.attributes.vertexSize * numVertices);
        isDirty = true;
    }

    @Override
    public int getNumVertices() {
        return buffer.limit() * 4 / attributes.vertexSize;
    }

    @Override
    public int getNumMaxVertices() {
        return buffer.capacity() * 4 / attributes.vertexSize;
    }

    @Override
    public VertexAttributes getAttributes() {
        return attributes;
    }

    @Override
    public void setVertices(float[] vertices, int offset, int count) {
        BufferUtils.copy(vertices, byteBuffer, count, offset);
        ((Buffer)buffer).position(0);
        ((Buffer)buffer).limit(count);
        isDirty = true;
    }

    /** copy floats to specific offset in buffer, expands the buffer limit if necessary */
    public void setVertices(int targetOffset, float[] vertices, int sourceOffset, int count) {
        final int pos = byteBuffer.position();
        ((Buffer)byteBuffer).position(targetOffset * 4);
        ((Buffer)byteBuffer).limit(targetOffset * 4 + count*4);
        BufferUtils.copy(vertices, sourceOffset, count, byteBuffer);
        ((Buffer)byteBuffer).position(pos);
        isDirty = true;
    }

    @Override
    public void updateVertices(int targetOffset, float[] vertices, int sourceOffset, int count) {
        final int pos = byteBuffer.position();
        ((Buffer)byteBuffer).position(targetOffset * 4);
        BufferUtils.copy(vertices, sourceOffset, count, byteBuffer);
        ((Buffer)byteBuffer).position(pos);
        isDirty = true;
    }

    @Override
    public FloatBuffer getBuffer() {
        return buffer;
    }

    @Override
    public FloatBuffer getBuffer(boolean forWriting) {
        isDirty |= forWriting;
        return buffer;
    }

    @Override
    public void bind(ShaderProgram shader) {
        if(isDirty){
            int numBytes = buffer.limit() * Float.BYTES;
            vertexBuffer.setVertices(byteBuffer, 0, numBytes);
            isDirty = false;
        }
    }

    @Override
    public void bind(ShaderProgram shader, int[] locations) {

    }

    public void bind(WebGPURenderPass renderPass){
        bind((ShaderProgram) null);
        // bind vertex buffer to render pass
        renderPass.setVertexBuffer(0, vertexBuffer.getHandle(), 0, vertexBuffer.getSize());
    }



    @Override
    public void unbind(ShaderProgram shader) {
        // no-op
    }

    @Override
    public void unbind(ShaderProgram shader, int[] locations) {
        // no-op
    }

    @Override
    public void invalidate() {

    }

    @Override
    public void dispose() {
        Gdx.app.log("WebGPUVertexData", "dispose"+getNumMaxVertices());
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
        vertexBuffer.dispose();
    }
}

package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.utils.BufferUtils;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;
import com.monstrous.gdx.webgpu.wrappers.WebGPUVertexBuffer;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/** equivalent to VertexBufferObject, or VertexArray */
public class WgVertexBuffer implements VertexData {
    final VertexAttributes attributes;
    final ByteBuffer byteBuffer;
    final FloatBuffer floatBuffer;
    protected WebGPUVertexBuffer vertexBuffer = null;
    private boolean isDirty = true;

    public WgVertexBuffer(int numVertices, VertexAttribute... attributes) {
        this(numVertices, new VertexAttributes(attributes));
    }

    public WgVertexBuffer(int numVertices, VertexAttributes attributes) {
        this.attributes = attributes;
        byteBuffer = BufferUtils.newUnsafeByteBuffer(this.attributes.vertexSize * numVertices);
        floatBuffer = byteBuffer.asFloatBuffer();
        vertexBuffer = new WebGPUVertexBuffer( this.attributes.vertexSize * numVertices);
        isDirty = true;
    }

    @Override
    public int getNumVertices() {
        return floatBuffer.limit() * 4 / attributes.vertexSize;
    }

    @Override
    public int getNumMaxVertices() {
        return floatBuffer.capacity() * 4 / attributes.vertexSize;
    }

    @Override
    public VertexAttributes getAttributes() {
        return attributes;
    }

    @Override
    public void setVertices(float[] vertices, int offset, int count) {
        ((Buffer)floatBuffer).clear();
        floatBuffer.put(vertices, offset, count);
        isDirty = true;
    }


    @Override
    public void updateVertices(int targetOffset, float[] vertices, int sourceOffset, int count) {
        ((Buffer)floatBuffer).position(targetOffset);
        floatBuffer.put(vertices, sourceOffset, count);
        isDirty = true;
    }

    @Override
    public FloatBuffer getBuffer() {
        return floatBuffer;
    }

    @Override
    public FloatBuffer getBuffer(boolean forWriting) {
        isDirty |= forWriting;
        return floatBuffer;
    }

    @Override
    public void bind(ShaderProgram shader) {
        if(isDirty){
            int numBytes = ((Buffer)floatBuffer).limit() * Float.BYTES;
            ((Buffer)byteBuffer).limit(numBytes);
            ((Buffer)byteBuffer).position(0);
            vertexBuffer.setVertices(byteBuffer);
            isDirty = false;
        }
    }

    @Override
    public void bind(ShaderProgram shader, int[] locations) {

    }

    public void bind(WebGPURenderPass renderPass){
        bind((ShaderProgram) null);
        // bind vertex buffer to render pass
        renderPass.setVertexBuffer(0, vertexBuffer.getBuffer(), 0, vertexBuffer.getSize());
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
        //Gdx.app.log("WebGPUVertexData", "dispose"+getNumMaxVertices());
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
        vertexBuffer.dispose();
    }
}

package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
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
    private final boolean isStatic;
    private boolean isFrozen;

    /** Create vertex buffer.
     *
     * @param isStatic will this vertex buffer never change? Allows to free the internal backing buffer after use.
     * @param maxVertices maximum number of vertices to be stored
     * @param attributes attributes per vertex
     */
    public WgVertexBuffer(boolean isStatic, int maxVertices, VertexAttribute... attributes) {
        this(isStatic, maxVertices, new VertexAttributes(attributes));
    }

    /** Create vertex buffer.
     *
     * @param isStatic will this vertex buffer never change? Allows to free the internal backing buffer after use.
     * @param maxVertices maximum number of vertices to be stored
     * @param attributes attributes per vertex
     */
    public WgVertexBuffer(boolean isStatic, int maxVertices, VertexAttributes attributes) {
        this.isStatic = isStatic;
        this.attributes = attributes;
        byteBuffer = BufferUtils.newUnsafeByteBuffer(this.attributes.vertexSize * maxVertices);
        floatBuffer = byteBuffer.asFloatBuffer();
        vertexBuffer = new WebGPUVertexBuffer( this.attributes.vertexSize * maxVertices);
        isDirty = true;
        isFrozen = false;
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
        if(isFrozen) throw new GdxRuntimeException("WgVertexBuffer: static vertex buffer cannot be modified.");
        ((Buffer)floatBuffer).clear();
        floatBuffer.put(vertices, offset, count);
        isDirty = true;
    }

    @Override
    public void updateVertices(int targetOffset, float[] vertices, int sourceOffset, int count) {
        if(isFrozen) throw new GdxRuntimeException("WgVertexBuffer: static vertex buffer cannot be modified.");
        ((Buffer)floatBuffer).position(targetOffset);
        floatBuffer.put(vertices, sourceOffset, count);
        isDirty = true;
    }

    @Override
    @Deprecated
    public FloatBuffer getBuffer() {
        return floatBuffer;
    }

    @Override
    public FloatBuffer getBuffer(boolean forWriting) {
        if(forWriting && isFrozen) throw new GdxRuntimeException("WgVertexBuffer: static vertex buffer cannot be modified.");
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
            if(isStatic){
                BufferUtils.disposeUnsafeByteBuffer(byteBuffer);    // release memory of backing buffer
                isFrozen = true;    // no more changes allowed
            }
        }
    }

    @Override
    public void bind(ShaderProgram shader, int[] locations) {
        throw new GdxRuntimeException("WgVertexBuffer: bind(ShaderProgram shader, int[] locations) not supported");
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
        Gdx.app.log("WebGPUVertexData", "dispose"+getNumMaxVertices());

        vertexBuffer.dispose();
        if(!isFrozen)
            BufferUtils.disposeUnsafeByteBuffer(byteBuffer);    // release memory of backing buffer
    }
}

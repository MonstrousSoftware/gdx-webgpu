/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.wrappers.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Immediate mode rendering class for WebGPU. The renderer will allow you to specify vertices on the fly and provides a
 * default shader for (unlit) rendering. Use setTexture() to bind a texture (the GL version assumes the texture is bound
 * already). Setting/getting ShaderProgram is not supported.
 **/
public class WgImmediateModeRenderer implements ImmediateModeRenderer {
    private int primitiveType;
    private int vertexIdx;
    private final int maxVertices;
    private int numVertices;
    private int vbOffset;
    private int frameNumber;
    private final int maxFlushes = 100;

    private final int vertexSize;
    private final int normalOffset;
    private final int colorOffset;
    private final int texCoordOffset;
    private final Matrix4 projModelView = new Matrix4();
    private final Matrix4 shiftDepthMatrix;

    private WebGPUVertexBuffer vertexBuffer;
    private WebGPUUniformBuffer uniformBuffer;
    private int uniformBufferSize;
    private final WebGPUBindGroupLayout bindGroupLayout;
    private WGPUPipelineLayout pipelineLayout;
    private final PipelineCache pipelines;
    private final PipelineSpecification pipelineSpec;
    private WgTexture texture;
    private WebGPURenderPass renderPass;
    private final ByteBuffer vertexByteBuffer;
    private final FloatBuffer vertexFloatBuffer;
    private WebGPUPipeline prevPipeline;
    private final WebGPUContext webgpu;

    public WgImmediateModeRenderer(boolean hasNormals, boolean hasColors, int numTexCoords) {
        this(5000, hasNormals, hasColors, numTexCoords, createDefaultShader(hasNormals, hasColors, numTexCoords));
    }

    public WgImmediateModeRenderer(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords) {
        this(maxVertices, hasNormals, hasColors, numTexCoords,
                createDefaultShader(hasNormals, hasColors, numTexCoords));
    }

    /** hasNormals, hasColors and numTexCoords are ignored */
    public WgImmediateModeRenderer(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords,
            ShaderProgram shader) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
        frameNumber = -1;
        vbOffset = 0;

        // matrix which will transform an opengl ortho matrix to a webgpu ortho matrix
        // by scaling the Z range from [-1..1] to [0..1]
        shiftDepthMatrix = new Matrix4().idt().scl(1, 1, 0.5f).trn(0, 0, 0.5f);

        this.maxVertices = maxVertices;

        VertexAttributes vertexAttributes = new VertexAttributes(VertexAttribute.Position(),
                VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0), VertexAttribute.Normal());

        vertexSize = vertexAttributes.vertexSize / Float.BYTES; // size in floats

        // PPP C UU NNN
        normalOffset = 6;
        colorOffset = 3;
        texCoordOffset = 4;

        createBuffers();

        vertexByteBuffer = BufferUtils.newUnsafeByteBuffer(maxVertices * vertexSize * Float.BYTES);
        vertexByteBuffer.order(ByteOrder.LITTLE_ENDIAN); // webgpu expects little endian data
        vertexFloatBuffer = vertexByteBuffer.asFloatBuffer(); // float view on the byte buffer

        bindGroupLayout = createBindGroupLayout();
        pipelineLayout = createPipelineLayout("ImmediateModeRenderer pipeline layout", bindGroupLayout);

        pipelines = new PipelineCache();
        pipelineSpec = new PipelineSpecification("ImmediateModeRenderer pipeline", vertexAttributes,
                defaultShaderSource());
        // define locations of vertex attributes in line with shader code
        // @location(0) position: vec4f,
        // @location(1) normal: vec3f,
        // @location(2) color: vec4f,
        // @location(3) uv: vec2f,
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "0", 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.NORMAL_ATTRIBUTE, 3);
        pipelineSpec.enableDepthTest();

        // default blending values
        pipelineSpec.disableBlending();
        // pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);

        prevPipeline = null;

        // fallback texture (1 white pixel)
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        texture = new WgTexture(pm, "white pixel");
    }

    public void setShader(ShaderProgram shader) {
        throw new RuntimeException("WebGPUImmediateModeRenderer: setShader() not supported");
    }

    public ShaderProgram getShader() {
        return null; // shader;
    }

    public void begin(Matrix4 projModelView, int primitiveType) {
        if (webgpu.frameNumber != frameNumber) {
            frameNumber = webgpu.frameNumber;
            vbOffset = 0;
            uniformBuffer.beginSlices();
        }
        // we reset vertexIdx and numVertices at the start of every FLUSH/end of FLUSH,
        // so no need to do it here for every begin call in the same frame.

        this.projModelView.set(shiftDepthMatrix).mul(projModelView);
        this.primitiveType = primitiveType;

        if (primitiveType == GL20.GL_LINES)
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;
        else if (primitiveType == GL20.GL_POINTS)
            pipelineSpec.topology = WGPUPrimitiveTopology.PointList;
        else
            pipelineSpec.topology = WGPUPrimitiveTopology.TriangleList;
        pipelineSpec.invalidateHashCode();
        pipelineSpec.setCullMode(WGPUCullMode.None);

        renderPass = RenderPassBuilder.create("ImmediateModeRenderer", null, false, webgpu.getSamples());
    }

    public void color(Color color) {
        vertexFloatBuffer.put(vertexIdx + colorOffset, color.toFloatBits());
    }

    public void color(float r, float g, float b, float a) {
        vertexFloatBuffer.put(vertexIdx + colorOffset, Color.toFloatBits(r, g, b, a));
    }

    public void color(float colorBits) {
        vertexFloatBuffer.put(vertexIdx + colorOffset, colorBits);
    }

    public void texCoord(float u, float v) {
        final int idx = vertexIdx + texCoordOffset;
        vertexFloatBuffer.put(idx, u);
        vertexFloatBuffer.put(idx + 1, v);
    }

    public void normal(float x, float y, float z) {
        final int idx = vertexIdx + normalOffset;
        vertexFloatBuffer.put(idx, x);
        vertexFloatBuffer.put(idx + 1, y);
        vertexFloatBuffer.put(idx + 2, z);
    }

    public void vertex(float x, float y, float z) {
        final int idx = vertexIdx;
        vertexFloatBuffer.put(idx, x);
        vertexFloatBuffer.put(idx + 1, y);
        vertexFloatBuffer.put(idx + 2, z);

        vertexIdx += vertexSize;
        numVertices++;
        if (numVertices > maxVertices)
            throw new ArrayIndexOutOfBoundsException("Too many vertices");
    }

    public void flush() {
        if (numVertices == 0)
            return;

        setUniforms(); // push matrix to uniform buffer data

        int uOffset = uniformBuffer.getSliceOffset();
        uniformBuffer.nextSlice(); // write matrix to GPU at uOffset

        // bind texture
        WebGPUBindGroup bg = makeBindGroup(bindGroupLayout, uniformBuffer, texture, uOffset, uniformBufferSize);
        setPipeline();
        renderPass.setPipeline(prevPipeline);

        // write current batch of vertices to the GPU's vertex buffer
        //
        int numBytes = numVertices * vertexSize * Float.BYTES;

        // ensure vbOffset is aligned to 4 bytes
        vbOffset = (vbOffset + 3) & ~3;
        if (vbOffset + numBytes > vertexBuffer.getSize()) {
            Gdx.app.error("WgImmediateModeRenderer", "Vertex buffer overflow. Increase maxVertices or maxFlushes.");
            return;
        }

        // The current batch always starts at 0 in our local vertexByteBuffer
        vertexByteBuffer.position(0);
        vertexByteBuffer.limit(numBytes);

        vertexBuffer.setVertices(vertexByteBuffer, vbOffset, numBytes);

        // Set vertex buffer while encoding the render pass
        renderPass.setVertexBuffer(0, vertexBuffer.getBuffer(), vbOffset, numBytes);

        renderPass.setBindGroup(0, bg.getBindGroup());

        renderPass.draw(numVertices);

        bg.dispose(); // done with bind group

        // Prepare for next batch/flush
        vbOffset += numBytes;
        numVertices = 0;
        vertexIdx = 0;
        vertexFloatBuffer.clear(); // resets position to 0 for next batch
        vertexByteBuffer.clear(); // resets position/limit for next batch
    }

    public void end() {
        flush();
        uniformBuffer.endSlices();
        renderPass.end();
        renderPass = null;
    }

    public int getNumVertices() {
        return numVertices;
    }

    @Override
    public int getMaxVertices() {
        return maxVertices;
    }

    public void setTexture(WgTexture texture) {
        this.texture = texture;
    }

    // Note: the default shader ignores the normal vectors

    static private String defaultShaderSource() {
        return "struct Uniforms {\n" + "    projectionMatrix: mat4x4f,\n" + "};\n" + "\n"
                + "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n"
                + "@group(0) @binding(1) var texture: texture_2d<f32>;\n"
                + "@group(0) @binding(2) var textureSampler: sampler;\n" + "\n" + "struct VertexInput {\n"
                + "    @location(0) position: vec3f,\n" + "    @location(1) color: vec4f,\n"
                + "    @location(2) uv: vec2f,\n" + "    @location(3) normal: vec3f,\n" + "};\n" + "\n"
                + "struct VertexOutput {\n" + "    @builtin(position) position: vec4f,\n"
                + "    @location(0) uv : vec2f,\n" + "    @location(1) color: vec4f,\n" + "};\n" + "\n" + "\n"
                + "@vertex\n" + "fn vs_main(in: VertexInput) -> VertexOutput {\n" + "   var out: VertexOutput;\n" + "\n"
                + "   var pos =  uniforms.projectionMatrix * vec4f(in.position, 1.0);\n" + "   out.position = pos;\n"
                + "   out.uv = in.uv;\n" + "   out.color = in.color;\n" + "\n" + "   return out;\n" + "}\n" + "\n"
                + "@fragment\n" + "fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" + "\n"
                + "    let color = in.color * textureSample(texture, textureSampler, in.uv);\n"
                + "    return vec4f(color);\n" + "}";
    }

    /** Returns a new instance of the default shader used by SpriteBatch for GL2 when no shader is specified. */
    static public ShaderProgram createDefaultShader(boolean hasNormals, boolean hasColors, int numTexCoords) {
        return null;
    }

    private void createBuffers() {

        // Create vertex buffer (no index buffer)
        vertexBuffer = new WebGPUVertexBuffer(WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Vertex),
                maxVertices * maxFlushes * vertexSize * Float.BYTES);

        // Create uniform buffer for the projection matrix
        uniformBufferSize = 16 * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer("ImmediateModeRenderer uniform buffer", uniformBufferSize,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform), maxFlushes);
    }

    private void setUniforms() {
        uniformBuffer.set(0, projModelView);
    }

    private WebGPUBindGroupLayout createBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("SpriteBatch bind group layout");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.Uniform, uniformBufferSize, false);
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUBuffer uniformBuffer,
            WgTexture texture, int uOffset, int uSize) {
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, uniformBuffer, uOffset, uSize);
        bg.setTexture(1, texture.getTextureView());
        bg.setSampler(2, texture.getSampler());
        bg.end();
        return bg;
    }

    // create or reuse pipeline on demand to match the pipeline spec
    private void setPipeline() {
        WebGPUPipeline pipeline = pipelines.findPipeline(pipelineLayout, pipelineSpec);
        if (pipeline != prevPipeline) { // avoid unneeded switches
            renderPass.setPipeline(pipeline);
            prevPipeline = pipeline;
        }
    }

    private WGPUPipelineLayout createPipelineLayout(String label, WebGPUBindGroupLayout... bindGroupLayouts) {
        WGPUPipelineLayoutDescriptor pipelineLayoutDesc = WGPUPipelineLayoutDescriptor.obtain();
        pipelineLayoutDesc.setNextInChain(WGPUChainedStruct.NULL);
        pipelineLayoutDesc.setLabel(label);
        WGPUVectorBindGroupLayout layouts = WGPUVectorBindGroupLayout.obtain();
        for (int i = 0; i < bindGroupLayouts.length; i++)
            layouts.push_back(bindGroupLayouts[i].getLayout());
        pipelineLayoutDesc.setBindGroupLayouts(layouts);

        pipelineLayout = new WGPUPipelineLayout();
        webgpu.device.createPipelineLayout(pipelineLayoutDesc, pipelineLayout);
        return pipelineLayout;
    }

    @Override
    public void dispose() {
        pipelines.dispose();
        vertexBuffer.dispose();
        texture.dispose();

        uniformBuffer.dispose();
        bindGroupLayout.dispose();
        pipelineLayout.dispose();
        BufferUtils.disposeUnsafeByteBuffer(vertexByteBuffer);
    }
}

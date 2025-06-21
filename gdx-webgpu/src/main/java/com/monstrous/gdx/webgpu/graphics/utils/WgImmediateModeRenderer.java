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
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;
import jnr.ffi.Pointer;
import org.lwjgl.opengl.GL20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;



/** Immediate mode rendering class for WebGPU. The renderer will allow you to specify vertices on the fly and provides a default
 * shader for (unlit) rendering.
 * Use setTexture() to bind a texture (the GL version assumes the texture is bound already).
 * Setting/getting ShaderProgram is not supported.
 **/
public class WgImmediateModeRenderer implements ImmediateModeRenderer {
	private int primitiveType;
	private int vertexIdx;
	private final int maxVertices;
	private int numVertices;

	private final int vertexSize;
	private final int normalOffset;
	private final int colorOffset;
	private final int texCoordOffset;
	private final Matrix4 projModelView = new Matrix4();


	private VertexAttributes vertexAttributes;
	private WebGPUVertexBuffer vertexBuffer;
	private WebGPUUniformBuffer uniformBuffer;
	private int uniformBufferSize;
	private WebGPUBindGroupLayout bindGroupLayout;
	private WebGPUPipelineLayout pipelineLayout;
	private PipelineCache pipelines;
	private PipelineSpecification pipelineSpec;
	private WgTexture texture;
	private WebGPURenderPass renderPass;
	private Pointer vertexDataPtr;
	private FloatBuffer vertexData;
	private WebGPUPipeline prevPipeline;
	private WebGPUGraphicsBase gfx;

	public WgImmediateModeRenderer(boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(5000, hasNormals, hasColors, numTexCoords, createDefaultShader(hasNormals, hasColors, numTexCoords));

	}

	public WgImmediateModeRenderer(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords) {
		this(maxVertices, hasNormals, hasColors, numTexCoords, createDefaultShader(hasNormals, hasColors, numTexCoords));

	}

	/** hasNormals, hasColors and numTexCoords are ignored */
	public WgImmediateModeRenderer(int maxVertices, boolean hasNormals, boolean hasColors, int numTexCoords,
                                   ShaderProgram shader) {

		gfx = (WebGPUGraphicsBase) Gdx.graphics;

		this.maxVertices = maxVertices;

		vertexAttributes = new VertexAttributes( VertexAttribute.Position(),
				VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0), VertexAttribute.Normal() );

		vertexSize = vertexAttributes.vertexSize/Float.BYTES;	// size in floats

		// PPP C UU NNN
		normalOffset = 6;
		colorOffset = 3;
		texCoordOffset = 4;


		createBuffers();

		ByteBuffer vertexBB = ByteBuffer.allocateDirect(maxVertices * vertexSize*Float.BYTES);
		vertexBB.order(ByteOrder.LITTLE_ENDIAN); // webgpu expects little endian data
		vertexDataPtr = Pointer.wrap(JavaWebGPU.getRuntime(), vertexBB);


		bindGroupLayout = createBindGroupLayout();
		pipelineLayout = new WebGPUPipelineLayout("ImmediateModeRenderer pipeline layout", bindGroupLayout);

		pipelines = new PipelineCache();
		pipelineSpec = new PipelineSpecification(vertexAttributes, defaultShaderSource());
		pipelineSpec.name = "ImmediateModeRenderer pipeline";
		pipelineSpec.enableDepthTest();



		// default blending values
		pipelineSpec.disableBlending();
		//pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);

		prevPipeline = null;

		// fallback texture (1 white pixel)
		Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		pm.setColor(Color.WHITE);
		pm.fill();
		texture = new WgTexture(pm);
	}

	public void setShader (ShaderProgram shader) {
		throw new RuntimeException("WebGPUImmediateModeRenderer: setShader() not supported");
	}

	public ShaderProgram getShader () {
		return null; //shader;
	}

	public void begin (Matrix4 projModelView, int primitiveType) {
		this.projModelView.set(projModelView);
		this.primitiveType = primitiveType;

		if(primitiveType == GL20.GL_LINES)
			pipelineSpec.topology = WGPUPrimitiveTopology.LineList;
		else if (primitiveType == GL20.GL_POINTS)
			pipelineSpec.topology = WGPUPrimitiveTopology.PointList;
		else
			pipelineSpec.topology = WGPUPrimitiveTopology.TriangleList;
		pipelineSpec.setCullMode(WGPUCullMode.None);


		renderPass = RenderPassBuilder.create(null, gfx.getSamples());
	}

	public void color (Color color) {
		vertexData.put(vertexIdx + colorOffset, color.toFloatBits());
	}

	public void color (float r, float g, float b, float a) {
		vertexData.put(vertexIdx + colorOffset, Color.toFloatBits(r, g, b, a));
	}

	public void color (float colorBits) {
		vertexData.put(vertexIdx + colorOffset, colorBits);
	}

	public void texCoord (float u, float v) {
		final int idx = vertexIdx + texCoordOffset;
		vertexData.put(idx, u);
		vertexData.put(idx+1, v);
	}

	public void normal (float x, float y, float z) {
		final int idx = vertexIdx + normalOffset;
		vertexData.put(idx, 		x);
		vertexData.put(idx+1, 	y);
		vertexData.put(idx+2, 	z);
	}

	public void vertex (float x, float y, float z) {
		final int idx = vertexIdx;
		vertexData.put(idx, 		x);
		vertexData.put(idx+1, 	y);
		vertexData.put(idx+2, 	z);

		vertexIdx += vertexSize;
		numVertices++;
		if(numVertices > maxVertices) throw new ArrayIndexOutOfBoundsException("Too many vertices");
	}

	public void flush () {
		if (numVertices == 0) return;

		setUniforms();	// push matrix to uniform buffer

		// bind texture
		WebGPUBindGroup bg = makeBindGroup(bindGroupLayout, uniformBuffer, texture);
		setPipeline();
		renderPass.setPipeline(prevPipeline.getHandle());

		// write number of vertices to the GPU's vertex buffer
		//
		int numBytes = numVertices * vertexSize * Float.BYTES;

		// copy vertex data to GPU vertex buffer
		gfx.getQueue().writeBuffer(vertexBuffer, 0, vertexDataPtr, numBytes);

		// Set vertex buffer while encoding the render pass
		renderPass.setVertexBuffer( 0, vertexBuffer.getHandle(), 0, numBytes);

		renderPass.setBindGroup( 0, bg.getHandle(), 0, JavaWebGPU.createNullPointer());

		renderPass.draw( numVertices);

		bg.dispose();	// done with bind group

		// reset
		vertexIdx = 0;
		numVertices = 0;
	}

	public void end () {
		flush();
		renderPass.end();
		renderPass = null;
	}

	public int getNumVertices () {
		return numVertices;
	}

	@Override
	public int getMaxVertices () {
		return maxVertices;
	}

	public void setTexture( WgTexture texture ){
		this.texture = texture;
	}


	// Note: the default shader ignores the normal vectors

	static private String defaultShaderSource() {
		return "struct Uniforms {\n" +
				"    projectionMatrix: mat4x4f,\n" +
				"};\n" +
				"\n" +
				"@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
				"@group(0) @binding(1) var texture: texture_2d<f32>;\n" +
				"@group(0) @binding(2) var textureSampler: sampler;\n" +
				"\n" +
				"struct VertexInput {\n" +
				"    @location(0) position: vec3f,\n" +
				"    @location(5) color: vec4f,\n" +
				"    @location(1) uv: vec2f,\n" +
				"    @location(2) normal: vec3f,\n" +
				"};\n" +
				"\n" +
				"struct VertexOutput {\n" +
				"    @builtin(position) position: vec4f,\n" +
				"    @location(0) uv : vec2f,\n" +
				"    @location(1) color: vec4f,\n" +
				"};\n" +
				"\n" +
				"\n" +
				"@vertex\n" +
				"fn vs_main(in: VertexInput) -> VertexOutput {\n" +
				"   var out: VertexOutput;\n" +
				"\n" +
				"   var pos =  uniforms.projectionMatrix * vec4f(in.position, 1.0);\n" +
				"   out.position = pos;\n" +
				"   out.uv = in.uv;\n" +
				"   out.color = in.color;\n" +
				"\n" +
				"   return out;\n" +
				"}\n" +
				"\n" +
				"@fragment\n" +
				"fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" +
				"\n" +
				"    let color = in.color * textureSample(texture, textureSampler, in.uv);\n" +
				"    return vec4f(color);\n" +
				"}";
	}


	/** Returns a new instance of the default shader used by SpriteBatch for GL2 when no shader is specified. */
	static public ShaderProgram createDefaultShader (boolean hasNormals, boolean hasColors, int numTexCoords) {
		return null;
	}


	private void createBuffers() {

		// Create vertex buffer (no index buffer)
		vertexBuffer = new WebGPUVertexBuffer(WGPUBufferUsage.CopyDst | WGPUBufferUsage.Vertex, (long) maxVertices * vertexSize);

		// Create uniform buffer for the projection matrix
		uniformBufferSize = 16 * Float.BYTES;
		uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize,WGPUBufferUsage.CopyDst |WGPUBufferUsage.Uniform  );
	}

	private void setUniforms(){
		uniformBuffer.set(0, projModelView);
        uniformBuffer.flush();
	}

	private WebGPUBindGroupLayout createBindGroupLayout() {
		WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("SpriteBatch bind group layout");
		layout.begin();
		layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.Uniform, uniformBufferSize, false);
		layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
		layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
		layout.end();
		return layout;
	}


	private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUBuffer uniformBuffer, WgTexture texture) {
		WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
		bg.begin();
		bg.setBuffer(0, uniformBuffer);
		bg.setTexture(1, texture.getTextureView());
		bg.setSampler(2, texture.getSampler());
		bg.end();
		return bg;
	}

	// create or reuse pipeline on demand to match the pipeline spec
	private void setPipeline() {
		WebGPUPipeline pipeline = pipelines.findPipeline( pipelineLayout.getHandle(), pipelineSpec);
		if (pipeline != prevPipeline) { // avoid unneeded switches
			//renderPass.setPipeline(pipeline.getHandle());
			prevPipeline = pipeline;
		}
	}

	//@Override
	public void dispose(){
		pipelines.dispose();
		vertexBuffer.dispose();

		uniformBuffer.dispose();
		bindGroupLayout.dispose();
		pipelineLayout.dispose();
	}
}

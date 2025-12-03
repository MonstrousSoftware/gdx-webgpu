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

package com.monstrous.gdx.webgpu.graphics.g3d.particles;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.VertexData;
import com.badlogic.gdx.math.Vector2;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.*;
import com.monstrous.gdx.webgpu.graphics.g3d.WgVertexBuffer;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgShader;
import com.monstrous.gdx.webgpu.graphics.utils.BlendMapper;
import com.monstrous.gdx.webgpu.wrappers.*;

import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/**
 * This is a custom shader to render the particles. Usually is not required, because the {@link DefaultShader} will be
 * used instead. This shader will be used when dealing with billboards using GPU mode or point sprites.
 *
 * @author inferno
 */
public class WgParticleShader extends WgShader {
    public enum ParticleType {
        Billboard, Point
    }

    public static enum AlignMode {
        Screen, ViewPoint// , ParticleDirection
    }

    public static class Config {
        /** The uber shader to use, null to use the default vertex shader. */
        public String shaderSource = null;

        public boolean ignoreUnimplemented = true;
        /** Set to 0 to disable culling */
        public int defaultCullFace = -1;
        /** Set to 0 to disable depth test */
        public int defaultDepthFunc = -1;
        public AlignMode align = AlignMode.Screen;
        public ParticleType type = ParticleType.Billboard;

        public Config() {
        }

        public Config(AlignMode align, ParticleType type) {
            this.align = align;
            this.type = type;
        }

        public Config(AlignMode align) {
            this.align = align;
        }

        public Config(ParticleType type) {
            this.type = type;
        }

        public Config(final String shaderSource) {
            this.shaderSource = shaderSource;
        }
    }

    private static String defaultShader = null;

    public static String getDefaultShader() {
        if (defaultShader == null)
            defaultShader = Gdx.files.classpath("shaders/pointsprites.wgsl").readString();
        return defaultShader;
    }

    /** The renderable used to create this shader, invalid after the call to init */
    private Renderable renderable;
    private long materialMask;
    private long vertexMask;
    protected final Config config;
    /** Material attributes which are not required but always supported. */
    private final static long optionalAttributes = IntAttribute.CullFace | DepthTestAttribute.Type;

    protected int uniformBufferSize;
    protected WebGPUUniformBuffer uniformBuffer;
    protected Binder binder;
    private WebGPURenderPass renderPass;
    protected WgTexture defaultTexture;
    protected WebGPUPipeline pipeline;

    //
    public WgParticleShader(final Renderable renderable) {
        this(renderable, new Config());
    }

    public WgParticleShader(final Renderable renderable, final Config config) {
        this(renderable, config, createPrefix(renderable, config));
    }

    public WgParticleShader(final Renderable renderable, final Config config, final String prefix) {
        this(renderable, config, prefix, config.shaderSource != null ? config.shaderSource : getDefaultShader());
    }

    public WgParticleShader(final Renderable renderable, final Config config, final String prefix,
            final String shader) {
        this(renderable, config, new WgShaderProgram("PointSprite shader", shader, prefix));
    }

    public WgParticleShader(final Renderable renderable, final Config config, final WgShaderProgram shaderProgram) {
        this.config = config;
        this.renderable = renderable;
        materialMask = renderable.material.getMask() | optionalAttributes;
        vertexMask = renderable.meshPart.mesh.getVertexAttributes().getMask();

        defaultTexture = createDefaultTexture();

        // Create uniform buffer for global (per-frame) uniforms, i.e. projection matrix
        uniformBufferSize = (16 + 16) * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("texture", 0, 1);
        binder.defineBinding("sampler", 0, 2);
        // define uniforms
        binder.defineUniform("projectionViewTransform", 0, 0, 0);
        binder.defineUniform("projectionTransform", 0, 1, 16 * Float.BYTES);

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("PointSprite pipeline layout");

        // vertexAttributes will be set from the renderable
        VertexAttributes vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification("PointSprite pipeline", vertexAttributes,
                shaderProgram);

        // define locations of vertex attributes in line with shader code
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation("a_sizeAndRotation", 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation("a_region", 3);

        pipelineSpec.vertexLayout.setStepMode(WGPUVertexStepMode.Instance); // advance once per instance (i.e.per quad)
        pipelineSpec.topology = WGPUPrimitiveTopology.TriangleList;

        // get blending factors from renderable
        for (Attribute attr : renderable.material) {
            final long t = attr.type;
            if (BlendingAttribute.is(t)) {
                BlendingAttribute blend = (BlendingAttribute) attr;
                pipelineSpec.enableBlending();
                pipelineSpec.setBlendFactor(BlendMapper.blendFactor(blend.sourceFunction),
                        BlendMapper.blendFactor(blend.destFunction));
            }
        }
        pipelineSpec.enableDepthTest();

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);

    }

    @Override
    public void init() {

    }

    @Override
    public void begin(final Camera camera, final RenderContext context) {
        throw new IllegalArgumentException("Use begin(Camera, WebGPURenderPass)");
    }

    @Override
    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass) {
        this.renderPass = renderPass;

        // set global uniforms, that do not depend on renderables
        binder.setUniform("projectionViewTransform", camera.combined);
        binder.setUniform("projectionTransform", camera.projection);
        // now that we've set all the uniforms, write the buffer to the gpu
        uniformBuffer.flush();

        // bind texture using the initial renderable
        // note that the texture is set per Particle Batch, not per Particle Effect
        bindMaterial(renderable.material);

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        renderPass.setPipeline(pipeline);
    }

    public void bindMaterial(Material material) {
        // diffuse texture
        WgTexture diffuseTexture;
        if (material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            diffuseTexture = (WgTexture) tex;
            diffuseTexture.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
            diffuseTexture.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);
        } else {
            diffuseTexture = defaultTexture;
        }

        // System.out.println("bind "+bindCount+ ": "+mat.attributesHash());
        binder.setTexture("texture", diffuseTexture.getTextureView());
        binder.setSampler("sampler", diffuseTexture.getSampler());
    }

    private WgTexture createDefaultTexture() {
        // fallback texture
        Pixmap pixmap = new Pixmap(1, 1, RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return new WgTexture(pixmap, "default (white)");
    }

    private final Attributes combinedAttributes = new Attributes();

    @Override
    public void render(final Renderable renderable) {
        render(renderable, null);
    }

    @Override
    public void render(Renderable renderable, Attributes attributes) {

        // bind vertices
        VertexData vertexData = ((WgMesh) renderable.meshPart.mesh).getVertexData();
        ((WgVertexBuffer) vertexData).bind(renderPass);

        int numParticles = renderable.meshPart.size;

        // 6 vertices per instance (2 triangles to make a quad)
        // each "vertex" in the vertex buffer is a particle
        // should firstVertex be meshPart.offset?
        renderPass.draw(6, numParticles, 0, 0);
    }

    @Override
    public void end() {

    }

    private static final StringBuffer sb = new StringBuffer();

    public static String createPrefix(final Renderable renderable, final Config config) {
        sb.setLength(0);
        if (!ShaderPrefix.hasLinearOutput()) {
            sb.append("#define GAMMA_CORRECTION\n");
        }
        return sb.toString();
    }

    @Override
    public boolean canRender(final Renderable renderable) {
        return true;
    }

    @Override
    public int compareTo(Shader other) {
        if (other == null)
            return -1;
        if (other == this)
            return 0;
        return 0; // FIXME compare shaders on their impact on performance
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof WgParticleShader) && equals((WgParticleShader) obj);
    }

    public boolean equals(WgParticleShader obj) {
        return (obj == this);
    }

    @Override
    public void dispose() {
        // program.dispose();
        // super.dispose();
    }

    // @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    // @group(0) @binding(1) var texture: texture_2d<f32>;
    // @group(0) @binding(2) var textureSampler: sampler;
    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("PointSprite bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, false);

        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);

        layout.end();
        return layout;
    }

}

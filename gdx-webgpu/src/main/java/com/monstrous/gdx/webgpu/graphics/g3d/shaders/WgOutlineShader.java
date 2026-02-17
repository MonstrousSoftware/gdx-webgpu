package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.wrappers.*;

/** Outline shader to render models with solid color for outline effect */
public class WgOutlineShader extends WgShader {

    private final WgModelBatch.Config config;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUPipeline pipeline;
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;
    public final Color outlineColor; // Public so provider can check it for caching

    public WgOutlineShader(final Renderable renderable, WgModelBatch.Config config, Color outlineColor) {
        this.config = config;
        this.outlineColor = new Color(outlineColor); // Store the color this shader will use

        // Create uniform buffer for global uniforms (projection matrix + outline color)
        uniformBufferSize = (16 + 4) * Float.BYTES; // mat4 + vec4
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));
        binder.defineGroup(1, createInstancingBindGroupLayout());

        // define bindings in the groups
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("instanceUniforms", 1, 0);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset);
        offset += 16 * 4;
        binder.defineUniform("outlineColor", 0, 0, offset);
        offset += 4 * 4;

        // sanity check
        if (offset > uniformBufferSize)
            throw new RuntimeException("Mismatch in frame uniform buffer size");

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        int instanceSize = 16 * Float.BYTES; // data size per instance

        instanceBuffer = new WebGPUUniformBuffer(instanceSize * config.maxInstances,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage));

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, instanceSize * config.maxInstances);

        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("OutlineBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();

        // Get shader source from provider
        String shaderSource = getOutlineShaderSource();

        PipelineSpecification pipelineSpec = new PipelineSpecification("OutlineBatch pipeline", vertexAttributes,
                shaderSource);

        // define locations of vertex attributes - we only use position in the shader,
        // but we need to define all attributes that are in the vertex buffer
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.NORMAL_ATTRIBUTE, 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 5);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "0", 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TANGENT_ATTRIBUTE, 3);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BINORMAL_ATTRIBUTE, 4);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BONEWEIGHT_ATTRIBUTE, 7);

        pipelineSpec.fragmentShaderEntryPoint = "fs_main";
        pipelineSpec.vertexShaderEntryPoint = "vs_main";

        // Enable backface culling but cull FRONT faces to show only the "outline" back faces
        pipelineSpec.cullMode = WGPUCullMode.Front;

        // default blending values
        pipelineSpec.enableBlending();
        pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        pipelineSpec.environment = renderable.environment;

        if (renderable.meshPart.primitiveType == GL20.GL_LINES)
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);
    }

    private static String getOutlineShaderSource() {
        return "// Outline shader\n" +
                "struct Uniforms {\n" +
                "    projectionViewTransform: mat4x4<f32>,\n" +
                "    outlineColor: vec4<f32>,\n" +
                "}\n" +
                "\n" +
                "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
                "\n" +
                "struct InstanceData {\n" +
                "    modelMatrix: mat4x4<f32>,\n" +
                "}\n" +
                "\n" +
                "@group(1) @binding(0) var<storage, read> instanceData: array<InstanceData>;\n" +
                "\n" +
                "struct VertexInput {\n" +
                "    @location(0) position: vec3<f32>,\n" +
                "}\n" +
                "\n" +
                "struct VertexOutput {\n" +
                "    @builtin(position) position: vec4<f32>,\n" +
                "}\n" +
                "\n" +
                "@vertex\n" +
                "fn vs_main(in: VertexInput, @builtin(instance_index) instanceIndex: u32) -> VertexOutput {\n" +
                "    var out: VertexOutput;\n" +
                "    let worldPosition = instanceData[instanceIndex].modelMatrix * vec4<f32>(in.position, 1.0);\n" +
                "    out.position = uniforms.projectionViewTransform * worldPosition;\n" +
                "    return out;\n" +
                "}\n" +
                "\n" +
                "@fragment\n" +
                "fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {\n" +
                "    return uniforms.outlineColor;\n" +
                "}\n";
    }

    @Override
    public void init() {
    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        throw new IllegalArgumentException("Use begin(Camera, WebGPURenderPass)");
    }

    @Override
    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass) {
        this.renderPass = renderPass;

        // set global uniforms
        binder.setUniform("projectionViewTransform", camera.combined);
        binder.setUniform("outlineColor", outlineColor);

        // Flush uniform buffer to upload to GPU BEFORE binding
        uniformBuffer.flush();

        // bind group 0 (frame) - this must happen AFTER flush so GPU sees updated data
        binder.bindGroup(renderPass, 0);

        // idem for group 1 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 1);

        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null;

        renderPass.setPipeline(pipeline);
    }

    @Override
    public int compareTo(Shader other) {
        if (other == null)
            return -1;
        if (other == this)
            return 0;
        return 0;
    }

    @Override
    public boolean canRender(Renderable instance) {
        return instance.meshPart.mesh.getVertexAttributes().getMask() == vertexAttributes.getMask();
    }

    private final Attributes combinedAttributes = new Attributes();

    @Override
    public void render(Renderable renderable) {
        if (renderable.worldTransform.det3x3() == 0)
            return;
        combinedAttributes.clear();
        if (renderable.environment != null)
            combinedAttributes.set(renderable.environment);
        if (renderable.material != null)
            combinedAttributes.set(renderable.material);
        render(renderable, combinedAttributes);
    }

    private Renderable prevRenderable;
    private int firstInstance;
    private int instanceCount;

    @Override
    public void render(Renderable renderable, Attributes attributes) {
        if (numRenderables > config.maxInstances) {
            Gdx.app.error("WgOutlineShader", "Too many instances, max is " + config.maxInstances);
            return;
        }

        // add instance data to instance buffer (instance transform)
        int offset = numRenderables * 16 * Float.BYTES;
        instanceBuffer.set(offset, renderable.worldTransform);

        if (prevRenderable != null && renderable.meshPart.equals(prevRenderable.meshPart)) {
            instanceCount++;
        } else {
            if (prevRenderable != null) {
                renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
            }
            instanceCount = 1;
            firstInstance = numRenderables;
            prevRenderable = renderable;
        }
        numRenderables++;
    }

    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables) {
        final WgMesh mesh = (WgMesh) meshPart.mesh;
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        drawCalls++;
    }

    @Override
    public void end() {
        if (prevRenderable != null) {
            renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
        }
        instanceBuffer.flush();
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgOutlineShader bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, false);
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createInstancingBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgOutlineShader Binding Group Layout (instance)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.ReadOnlyStorage,
                16 * Float.BYTES * config.maxInstances, false);
        layout.end();
        return layout;
    }

    @Override
    public void dispose() {
        binder.dispose();
        instanceBuffer.dispose();
        uniformBuffer.dispose();
    }
}


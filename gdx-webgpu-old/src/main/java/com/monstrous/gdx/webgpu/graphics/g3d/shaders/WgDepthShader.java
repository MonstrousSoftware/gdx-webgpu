/*******************************************************************************
 * Copyright 2025 Monstrous Software.
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

package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Matrix4;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;

import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/** Depth shader to render renderables to a depth buffer */
public class WgDepthShader extends WgShader {

    private final WgDefaultShader.Config config;
    private static String defaultShader;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUPipeline pipeline;
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;


    public WgDepthShader(final Renderable renderable) {
        this(renderable, new WgDefaultShader.Config());
    }

    public WgDepthShader(final Renderable renderable, WgDefaultShader.Config config) {
        this.config = config;

        // Create uniform buffer for global (per-frame) uniforms, i.e. projection matrix
        uniformBufferSize = 16* Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform);

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));
        binder.defineGroup(1, createInstancingBindGroupLayout());

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("instanceUniforms", 1, 0);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset); offset += 16*4;

        // sanity check
        if(offset > uniformBufferSize) throw new RuntimeException("Mismatch in frame uniform buffer size");

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        int instanceSize = 16*Float.BYTES;      // data size per instance

        // for now we use a uniform buffer, but we organize data as an array of modelMatrix
        // we are not using dynamic offsets, but we will index the array in the shader code using the instance_index
        instanceBuffer = new WebGPUUniformBuffer(instanceSize*config.maxInstances, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Storage);

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, (long) instanceSize *config.maxInstances);


        // get pipeline layout which aggregates all the bind group layouts
        WebGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("DepthBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification(vertexAttributes, getDefaultShaderSource());
        pipelineSpec.name = "DepthBatch pipeline";
        // no fragment shader
        pipelineSpec.colorFormat = WGPUTextureFormat.Undefined;
        pipelineSpec.fragmentShaderEntryPoint = null;

        // default blending values
        pipelineSpec.enableBlending();
        pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        pipelineSpec.environment = renderable.environment;
        if(renderable.meshPart.primitiveType == GL20.GL_LINES)  // todo all cases
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);
    }

    @Override
    public void init() {
        // todo some constructor stuff to init()?
    }


    @Override
    public void begin(Camera camera, RenderContext context) {
        throw new IllegalArgumentException("Use begin(Camera, WebGPURenderPass)");
    }

    @Override
    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass){
        this.renderPass = renderPass;

        // set global uniforms, that do not depend on renderables
        // e.g. camera, lighting, environment uniforms
        //
         // todo: we are working here with an OpenGL projection matrix, which provides a different Z range than for WebGPU.

        binder.setUniform("projectionViewTransform", camera.combined);
        uniformBuffer.flush();

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 1);

        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null;  // to store renderable that still needs to be rendered

        renderPass.setPipeline(pipeline.getHandle());
    }



    @Override
    public int compareTo(Shader other) {
        if (other == null) return -1;
        if (other == this) return 0;
        return 0; // FIXME compare shaders on their impact on performance
    }

    @Override
    public boolean canRender(Renderable instance) {
        return instance.meshPart.mesh.getVertexAttributes().getMask() == vertexAttributes.getMask();
    }

    private final Attributes combinedAttributes = new Attributes();

    @Override
    public void render (Renderable renderable) {
        if (renderable.worldTransform.det3x3() == 0) return;
        combinedAttributes.clear();
        if (renderable.environment != null) combinedAttributes.set(renderable.environment);
        if (renderable.material != null) combinedAttributes.set(renderable.material);
        render(renderable, combinedAttributes);
    }

    private Renderable prevRenderable;
    private int firstInstance;
    private int instanceCount;

    @Override
    public void render (Renderable renderable, Attributes attributes) {
        if(numRenderables > config.maxInstances) {
            Gdx.app.error("WgDepthShader", "Too many instances, max is " + config.maxInstances);
            return;
        }

        // renderable-specific data

        // add instance data to instance buffer (instance transform)
        int offset = numRenderables * 16 * Float.BYTES;
        instanceBuffer.set(offset,  renderable.worldTransform);
        // todo normal matrix per instance



        if( prevRenderable != null && renderable.meshPart.equals(prevRenderable.meshPart)){
            // note that renderables get a copy of a mesh part not a reference to the Model's mesh part, so you can just compare references.
            instanceCount++;
        } else {    // either a new material or a new mesh part, we need to flush the run of instances
            if(prevRenderable != null) {
                renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
            }
            instanceCount = 1;
            firstInstance = numRenderables;
            prevRenderable = renderable;
        }
        numRenderables++;
    }

    // to combine instances in single draw call if they have same mesh part
    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables){
        final WgMesh mesh = (WgMesh) meshPart.mesh;
        // use an instance offset to find the right modelMatrix in the instanceBuffer
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        drawCalls++;
    }

    @Override
    public void end(){
        if(prevRenderable != null) {
            renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
        }
        instanceBuffer.flush();
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgDepthShader bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex|WGPUShaderStage.Fragment, WGPUBufferBindingType.Uniform, uniformBufferSize, false);
        layout.end();
        return layout;
    }


    private WebGPUBindGroupLayout createInstancingBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgDepthShader Binding Group Layout (instance)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex , WGPUBufferBindingType.ReadOnlyStorage, 16L *Float.BYTES*config.maxInstances, false);
        layout.end();
        return layout;
    }


    private String getDefaultShaderSource() {
        if(defaultShader == null){
            defaultShader = Gdx.files.classpath("shaders/depthshader.wgsl").readString();
        }
        return defaultShader;
    }

    @Override
    public void dispose() {
        binder.dispose();
        instanceBuffer.dispose();
        uniformBuffer.dispose();
    }

}

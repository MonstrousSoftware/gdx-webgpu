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
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.wrappers.*;

/** Depth shader to render renderables to a depth buffer */
public class WgDepthShader extends WgShader {

    private static final int GROUP_SKIN = 2;

    private final WgModelBatch.Config config;
    private static String defaultShader;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer jointMatricesBuffer;
    private boolean hasBones;
    private int numRigged;
    private int rigSize; // bytes per rigged instance
    private final WebGPUPipeline pipeline;
    private WebGPURenderPass renderPass;
    private VertexAttributes vertexAttributes;

    public WgDepthShader(final Renderable renderable) {
        this(renderable, new WgModelBatch.Config());
    }

    public WgDepthShader(final Renderable renderable, WgModelBatch.Config config) {
        this(renderable, config, getDefaultShaderSource());
    }

    public WgDepthShader(final Renderable renderable, WgModelBatch.Config config, String shaderSource) {
        this(renderable, config, shaderSource, null); // null = no fragment shader for normal depth rendering
    }

    public WgDepthShader(final Renderable renderable, WgModelBatch.Config config, String shaderSource,
                         String fragmentEntryPoint) {
        this.config = config;

        // Create uniform buffer for global (per-frame) uniforms, i.e. projection matrix
        uniformBufferSize = 16 * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        hasBones = renderable.bones != null;
        rigSize = config.numBones * 16 * Float.BYTES;

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));
        binder.defineGroup(1, createInstancingBindGroupLayout());
        if (hasBones)
            binder.defineGroup(GROUP_SKIN, createSkinningBindGroupLayout(rigSize));

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("instanceUniforms", 1, 0);
        binder.defineBinding("jointMatrices", GROUP_SKIN, 0);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset);
        offset += 16 * 4;

        // sanity check
        if (offset > uniformBufferSize)
            throw new RuntimeException("Mismatch in frame uniform buffer size");

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        // Calculate instance size based on whether model has morph targets
        // ModelUniforms contains: modelMatrix (16 floats) + morphWeights (4 floats) + morphWeights2 (4 floats)
        boolean hasMorph = false;
        VertexAttributes meshAttribs = renderable.meshPart.mesh.getVertexAttributes();
        for (VertexAttribute attr : meshAttribs) {
            if (attr.alias.startsWith("a_position_morph_")) {
                hasMorph = true;
                break;
            }
        }

        int instanceSize = 16 * Float.BYTES; // modelMatrix
        if (hasMorph) {
            instanceSize += 8 * Float.BYTES; // morphWeights (vec4) + morphWeights2 (vec4)
        }

        // for now we use a uniform buffer, but we organize data as an array of modelMatrix
        // we are not using dynamic offsets, but we will index the array in the shader code using the instance_index
        instanceBuffer = new WebGPUUniformBuffer(instanceSize * config.maxInstances,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage));

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, instanceSize * config.maxInstances);

        if (hasBones) {
            if (renderable.bones.length > config.numBones)
                throw new GdxRuntimeException("Too many bones in model. NumBones is configured as " + config.numBones
                        + ". Renderable has " + renderable.bones.length);
            jointMatricesBuffer = new WebGPUUniformBuffer(rigSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage),
                    config.maxRigged);
            binder.setBuffer("jointMatrices", jointMatricesBuffer, 0, rigSize);
        } else {
            jointMatricesBuffer = null;
        }

        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("DepthBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification("DepthBatch pipeline", vertexAttributes,
                shaderSource);
        // define locations of vertex attributes in line with shader code
        // we're only using position and bones for depth shading but the vertex buffer is shared with the renderer
        // so we have to cover all possible vertex attributes
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.NORMAL_ATTRIBUTE, 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 5);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "0", 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TANGENT_ATTRIBUTE, 3);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BINORMAL_ATTRIBUTE, 4);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BONEWEIGHT_ATTRIBUTE, 7);

        // Add support for morph target attributes - only set locations for attributes that actually exist
        for (VertexAttribute attr : vertexAttributes) {
            if (attr.alias.startsWith("a_position_morph_")) {
                // Extract the morph index from the attribute name (e.g., "a_position_morph_0" -> 0)
                String indexStr = attr.alias.substring("a_position_morph_".length());
                try {
                    int morphIndex = Integer.parseInt(indexStr);
                    pipelineSpec.vertexLayout.setVertexAttributeLocation(attr.alias, 8 + morphIndex);
                } catch (NumberFormatException e) {
                    // Ignore malformed morph attribute names
                }
            }
        }

        // Fragment shader entry point (null for normal depth rendering, "fs_main" for masking)
        if (fragmentEntryPoint == null) {
            // No color output for normal depth-only rendering
            pipelineSpec.colorFormats = null;
        } else {
            // If fragment is present but writes only to builtin(frag_depth) (i.e. no color outputs),
            // keep depth-only pipeline. We detect that by searching the provided shader source for
            // the annotation '@builtin(frag_depth)'.
            if (shaderSource != null) {
                boolean writesFragDepth = shaderSource.contains("@builtin(frag_depth)");
                boolean writesLocation = shaderSource.contains("@location(");
                // If the fragment writes only frag_depth and does not write any color outputs (@location),
                // keep depth-only pipeline. Otherwise, enable color targets.
                if (writesFragDepth && !writesLocation) {
                    pipelineSpec.colorFormats = null;
                } else {
                    pipelineSpec.colorFormats = new WGPUTextureFormat[] { WGPUTextureFormat.RGBA8UnormSrgb };
                }
            } else {
                // Fallback: assume color output
                pipelineSpec.colorFormats = new WGPUTextureFormat[] { WGPUTextureFormat.RGBA8UnormSrgb };
            }
        }
        pipelineSpec.fragmentShaderEntryPoint = fragmentEntryPoint;
        pipelineSpec.isDepthPass = true; // Mark this as a depth pass

        // default blending values
        pipelineSpec.enableBlending();
        pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        pipelineSpec.environment = renderable.environment;
        if (renderable.meshPart.primitiveType == GL20.GL_LINES) // todo all cases
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
    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass) {
        this.renderPass = renderPass;

        // Determine if this renderable has morph targets and calculate instance stride
        hasMorph = false;
        for (VertexAttribute attr : vertexAttributes) {
            if (attr.alias.startsWith("a_position_morph_")) {
                hasMorph = true;
                break;
            }
        }

        // Calculate instance stride based on whether morph is present
        instanceStride = 16 * Float.BYTES; // modelMatrix
        if (hasMorph) {
            instanceStride += 8 * Float.BYTES; // morphWeights (vec4) + morphWeights2 (vec4)
        }

        // set global uniforms, that do not depend on renderables
        // Note: camera.combined is already remapped from OpenGL [-1,1] to WebGPU [0,1] depth
        // by WgModelBatch.begin() before shaders are invoked.
        binder.setUniform("projectionViewTransform", camera.combined);
        uniformBuffer.flush();

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 1 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 1);

        numRigged = 0;
        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null; // to store renderable that still needs to be rendered
        if (jointMatricesBuffer != null)
            jointMatricesBuffer.beginSlices();

        renderPass.setPipeline(pipeline);
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
    public boolean canRender(Renderable instance) {
        if (hasBones && instance.bones == null)
            return false;
        if (!hasBones && instance.bones != null)
            return false;
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
    private final float[] tmpWeights = new float[8];
    private boolean hasMorph;
    private int instanceStride; // bytes per instance in the buffer

    @Override
    public void render(Renderable renderable, Attributes attributes) {
        if (numRenderables > config.maxInstances) {
            Gdx.app.error("WgDepthShader", "Too many instances, max is " + config.maxInstances);
            return;
        }

        // renderable-specific data

        // Calculate offset based on instance stride (which includes morph weights if present)
        int offset = numRenderables * instanceStride;
        instanceBuffer.set(offset, renderable.worldTransform);

        // Write morph weights if this model has morph targets
        if (hasMorph) {
            // Clear previous weights
            for (int i = 0; i < 8; i++)
                tmpWeights[i] = 0;

            int numWeights = 0;
            Node node = null;
            if (renderable.userData instanceof Node) {
                node = (Node) renderable.userData;
            }

            if (node != null) {
                for (Node child : node.getChildren()) {
                    String childId = child.id;
                    int morphIdx = childId.indexOf(".morph.");
                    if (morphIdx >= 0) {
                        try {
                            // Parse integer directly from string without creating substring to avoid GC overhead
                            int index = Integer.parseInt(childId, morphIdx + 7, childId.length(), 10);
                            if (index < 8) {
                                // Read from localTransform because AnimationController updates localTransform,
                                // not the node.translation field
                                tmpWeights[index] = child.localTransform.val[Matrix4.M03];
                                numWeights = Math.max(numWeights, index + 1);
                            }
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }

            // Write weights to buffer (after modelMatrix at offset 16*Float.BYTES)
            for (int i = 0; i < 8; i++) {
                instanceBuffer.set(offset + 16 * Float.BYTES + i * Float.BYTES, tmpWeights[i]);
            }
        }

        if (renderable.bones == null && prevRenderable != null && renderable.meshPart.equals(prevRenderable.meshPart)) {
            // note that renderables get a copy of a mesh part not a reference to the Model's mesh part, so you can just
            // compare references.
            instanceCount++;
        } else { // either a new material or a new mesh part, we need to flush the run of instances
            if (prevRenderable != null) {
                if (prevRenderable.bones != null) {
                    setBones(prevRenderable.bones);
                }
                renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
            }
            instanceCount = 1;
            firstInstance = numRenderables;
            prevRenderable = renderable;
        }
        numRenderables++;
    }

    // to combine instances in single draw call if they have same mesh part
    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables) {
        final WgMesh mesh = (WgMesh) meshPart.mesh;
        // use an instance offset to find the right modelMatrix in the instanceBuffer
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        drawCalls++;
    }

    @Override
    public void end() {
        if (prevRenderable != null) {
            if (prevRenderable.bones != null) {
                setBones(prevRenderable.bones);
                jointMatricesBuffer.endSlices();
            }
            renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
        }
        instanceBuffer.flush();
    }

    private final Matrix4 idt = new Matrix4();

    // fill the skinning buffers (group 3
    private void setBones(Matrix4[] bones) {
        if (numRigged == config.maxRigged - 1) {
            Gdx.app.error("setBones", "Too many rigged instances. Increase config.maxRigged.");
            return;
        }
        int dynamicOffset = jointMatricesBuffer.nextSlice();
        jointMatricesBuffer.set(0, bones);

        binder.bindGroup(renderPass, GROUP_SKIN, dynamicOffset);
        numRigged++;
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgDepthShader bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, false);
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createInstancingBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgDepthShader Binding Group Layout (instance)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.ReadOnlyStorage,
                16 * Float.BYTES * config.maxInstances, false);
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createSkinningBindGroupLayout(int rigSize) {
        // binding 0: joint matrices for skeletal animation
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("WgDepthShader Binding Group Layout (Skinning)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.ReadOnlyStorage, rigSize, true);
        layout.end();
        return layout;
    }

    public static String getDefaultShaderSource() {
        if (defaultShader == null) {
            defaultShader = Gdx.files.classpath("shaders/depthshader.wgsl").readString();
        }
        return defaultShader;
    }

    @Override
    public void dispose() {
        binder.dispose();
        instanceBuffer.dispose();
        uniformBuffer.dispose();
        if (pipeline != null) pipeline.dispose();
    }

}


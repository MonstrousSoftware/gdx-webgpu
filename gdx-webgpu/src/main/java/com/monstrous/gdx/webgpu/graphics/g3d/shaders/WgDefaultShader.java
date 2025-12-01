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
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRTextureAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.wrappers.*;

import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/** Default shader to render renderables */
public class WgDefaultShader extends WgShader implements Disposable {

    private WebGPUContext webgpu;
    private final WgModelBatch.Config config;
    private static String defaultShader;
    private final Matrix4 dummyMatrix = new Matrix4();
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer jointMatricesBuffer;
    private MaterialsCache materials;
    private int numRigged;
    private int rigSize; // bytes per rigged instance
    private final WebGPUPipeline pipeline; // a shader has one pipeline
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;
    private final Matrix4 combined;
    private final Matrix4 projection;
    private final Matrix4 shiftDepthMatrix;

    protected int numDirectionalLights;
    protected DirectionalLight[] directionalLights;
    protected int numPointLights;
    protected PointLight[] pointLights;
    private final Vector4 tmpVec4 = new Vector4();
    private final long materialAttributesMask;
    private final long vertexAttributesHash;
    private final long environmentMask;
    private final boolean blended;
    private final boolean hasShadowMap;
    private final boolean hasCubeMap;
    private final boolean hasBones;
    private final int primitiveType;
    private int frameNumber;
    private int instanceIndex;
    private Color linearFogColor;
    private final WgTexture brdfLUT;

    public WgDefaultShader(final Renderable renderable) {
        this(renderable, new WgModelBatch.Config());
    }

    public WgDefaultShader(final Renderable renderable, WgModelBatch.Config config) {
        this.config = config;
        this.materials = config.materials;

        // System.out.println("Create WgDefaultShader "+renderable.meshPart.id+" vert mask:
        // "+renderable.meshPart.mesh.getVertexAttributes().getMask()+
        // "mat mask: "+renderable.material.getMask());

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        linearFogColor = new Color();

        hasShadowMap = renderable.environment != null && renderable.environment.shadowMap != null;
        hasCubeMap = renderable.environment != null && renderable.environment.has(WgCubemapAttribute.EnvironmentMap);
        hasBones = renderable.bones != null;
        final boolean hasDiffuseCubeMap = renderable.environment != null
                && renderable.environment.has(WgCubemapAttribute.DiffuseCubeMap);
        final boolean hasSpecularCubeMap = renderable.environment != null
                && renderable.environment.has(WgCubemapAttribute.SpecularCubeMap);

        // Create uniform buffer for global (per-frame) uniforms, e.g. projection matrix, camera position, etc.
        uniformBufferSize = (16 + 16 + 4 + 4 + 4 + 4 + +32 + 8 * config.maxDirectionalLights
                + 12 * config.maxPointLights) * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        rigSize = config.numBones * 16 * Float.BYTES;

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize, hasShadowMap, hasCubeMap, hasDiffuseCubeMap,
                hasSpecularCubeMap));
        binder.defineGroup(1, materials.createMaterialBindGroupLayout());
        binder.defineGroup(2, createInstancingBindGroupLayout());
        if (hasBones)
            binder.defineGroup(3, createSkinningBindGroupLayout(rigSize));

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        if (hasShadowMap) {
            binder.defineBinding("shadowMap", 0, 1);
            binder.defineBinding("shadowSampler", 0, 2);
        }
        if (hasCubeMap) {
            binder.defineBinding("cubeMap", 0, 3);
            binder.defineBinding("cubeSampler", 0, 4);
        }
        if (hasDiffuseCubeMap) {
            binder.defineBinding("irradianceMap", 0, 5);
            binder.defineBinding("irradianceSampler", 0, 6);
        }
        if (hasSpecularCubeMap) {
            binder.defineBinding("radianceMap", 0, 7);
            binder.defineBinding("radianceSampler", 0, 8);
            binder.defineBinding("brdfLUT", 0, 9);
            binder.defineBinding("lutSampler", 0, 10);

            // set 'isColor' to false in order to avoid gamma correction on this data texture
            brdfLUT = new WgTexture(Gdx.files.internal("brdfLUT.png"), false, false);
            brdfLUT.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            brdfLUT.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        } else
            brdfLUT = null;

        binder.defineBinding("instanceUniforms", 2, 0);

        binder.defineBinding("jointMatrices", 3, 0);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset);
        offset += 16 * 4;
        binder.defineUniform("shadowProjViewTransform", 0, 0, offset);
        offset += 16 * 4;

        if (config.maxDirectionalLights > 0) {
            // define only a uniform for the initial array elements
            // you will have to add an offset of i * array element size
            binder.defineUniform("dirLight[0].color", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("dirLight[0].direction", 0, 0, offset);
            offset += 4 * 4;
            offset += 8 * Float.BYTES * (config.maxDirectionalLights - 1);
        }

        if (config.maxPointLights > 0) {
            binder.defineUniform("pointLight[0].color", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("pointLight[0].position", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("pointLight[0].intensity", 0, 0, offset);
            offset += 4 * 4; // added padding
            offset += 12 * Float.BYTES * (config.maxPointLights - 1);
        }

        binder.defineUniform("ambientLight", 0, 0, offset);
        offset += 4 * 4;
        binder.defineUniform("cameraPosition", 0, 0, offset);
        offset += 4 * 4;
        binder.defineUniform("fogColor", 0, 0, offset);
        offset += 4 * 4;
        binder.defineUniform("numDirectionalLights", 0, 0, offset);
        offset += 4;
        binder.defineUniform("numPointLights", 0, 0, offset);
        offset += 4;
        binder.defineUniform("shadowPcfOffset", 0, 0, offset);
        offset += 4;
        binder.defineUniform("shadowBias", 0, 0, offset);
        offset += 4;
        binder.defineUniform("normalMapStrength", 0, 0, offset);
        offset += 4;
        binder.defineUniform("numRoughnessLevels", 0, 0, offset);
        offset += 4;

        // note: put shorter uniforms last for padding reasons

        // System.out.println("offset:"+offset+" "+uniformBufferSize);
        if (offset > uniformBufferSize)
            throw new RuntimeException("Mismatch in frame uniform buffer size");
        // binder.defineUniform("modelMatrix", 2, 0, 0);

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        // binder.setBuffer("materialUniforms", materialBuffer, 0, materialSize);

        int instanceSize = 2 * 16 * Float.BYTES; // data size per instance

        // for now we use a uniform buffer, but we organize data as an array of modelMatrix
        // we are not using dynamic offsets, but we will index the array in teh shader code using the instance_index
        instanceBuffer = new WebGPUUniformBuffer(instanceSize * config.maxInstances,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage));

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, instanceSize * config.maxInstances);

        if (hasBones) {
            int numJoints = config.numBones; // todo fixed number or renderable dependent?
            if (renderable.bones.length > config.numBones)
                throw new GdxRuntimeException("Too many bones in model. NumBones is configured as " + config.numBones
                        + ". Renderable has " + renderable.bones.length);

            jointMatricesBuffer = new WebGPUUniformBuffer(rigSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage),
                    config.maxRigged);
            binder.setBuffer("jointMatrices", jointMatricesBuffer, 0, rigSize);
        } else {
            jointMatricesBuffer = null;
        }

        vertexAttributesHash = renderable.meshPart.mesh.getVertexAttributes().hashCode();
        materialAttributesMask = renderable.material.getMask();

        environmentMask = renderable.environment == null ? 0 : renderable.environment.getMask();

        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("ModelBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification("ModelBatch pipeline", vertexAttributes, getDefaultShaderSource());
        // define locations of vertex attributes in line with shader code
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.NORMAL_ATTRIBUTE, 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 5);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE+"0", 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TANGENT_ATTRIBUTE, 3);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BINORMAL_ATTRIBUTE, 4);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BONEWEIGHT_ATTRIBUTE, 7);



        // default blending values
        blended = renderable.material.has(BlendingAttribute.Type)
                && ((BlendingAttribute) renderable.material.get(BlendingAttribute.Type)).blended;
        if (blended) {
            pipelineSpec.enableBlending();
            pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
            pipelineSpec.cullMode = WGPUCullMode.None;
        } else {
            pipelineSpec.disableBlending();
            pipelineSpec.cullMode = WGPUCullMode.Back;
        }

        pipelineSpec.environment = renderable.environment;
        primitiveType = renderable.meshPart.primitiveType;
        if (primitiveType == GL20.GL_LINES) // todo all cases
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;

        pipelineSpec.maxDirLights = config.maxDirectionalLights;
        pipelineSpec.maxPointLights = config.maxPointLights;
        pipelineSpec.usePBR = config.usePBR;
        // System.out.println("pipeline spec: "+pipelineSpec.hashCode()+pipelineSpec.vertexAttributes);

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);

        directionalLights = new DirectionalLight[config.maxDirectionalLights];
        for (int i = 0; i < config.maxDirectionalLights; i++)
            directionalLights[i] = new DirectionalLight();
        pointLights = new PointLight[config.maxPointLights];
        for (int i = 0; i < config.maxPointLights; i++)
            pointLights[i] = new PointLight();

        projection = new Matrix4();
        combined = new Matrix4();
        // matrix to transform OpenGL projection to WebGPU projection by modifying the Z scale
        shiftDepthMatrix = new Matrix4().scl(1, 1, -0.5f).trn(0, 0, 0.5f);

        frameNumber = -1;
    }

    @Override
    public void init() {
        // todo some constructor stuff to init()?

    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        throw new IllegalArgumentException("Use begin(Camera, WebGPURenderPass)");
    }

    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass) {
        this.renderPass = renderPass;

        // set global uniforms, that do not depend on renderables
        // e.g. camera, lighting, environment uniforms
        //
        // todo: we are working here with an OpenGL projection matrix, which provides a different Z range than for
        // WebGPU.

        // projection.set(camera.projection);
        // projection.set(shiftDepthMatrix).mul(camera.projection);
        // combined.set(projection).mul(camera.view);
        binder.setUniform("projectionViewTransform", camera.combined);

        // pass a special value in the w component of camera position that is used by the fog calculation
        tmpVec4.set(camera.position.x, camera.position.y, camera.position.z, 1.1881f / (camera.far * camera.far));
        binder.setUniform("cameraPosition", tmpVec4);

        // note: if we call WgModelBatch multiple times per frame with a different camera, the old ones are lost

        // todo: different shaders may overwrite lighting uniforms if renderables have other environments ...
        bindLights(renderable.environment);

        binder.setUniform("normalMapStrength", 0.5f); // emphasis factor for normal map [0-1]

        // now that we've set all the uniforms (camera,lights, etc.) write the buffer to the gpu
        uniformBuffer.flush();

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 2);

        if (webgpu.frameNumber != this.frameNumber) { // reset at the start of a frame
            instanceIndex = 0;
            numRigged = 0;
            this.frameNumber = webgpu.frameNumber;
            if (jointMatricesBuffer != null)
                jointMatricesBuffer.beginSlices();
        }
        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null; // to store renderable that still needs to be rendered
        appliedMaterialHash = -1;

        materials.start(); // indicate that no material is currently bound

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

    /**
     * check if the provided renderable is similar enough to the one used to create this shader, that it can be
     * rendered. Look at vertex attributes, material attributes.
     */
    @Override
    public boolean canRender(Renderable renderable) {
        // System.out.println("Can Render? "+renderable.meshPart.id+" mask:
        // "+renderable.meshPart.mesh.getVertexAttributes().getMask()+" ==? "+vertexAttributes.getMask() +
        // "mat mask: "+renderable.material.getMask()+" ==? "+material.getMask());

        // note: it is not sufficient to compare the mask of the vertex attributes, they need to be in the same order as
        // well
        if (renderable.meshPart.mesh.getVertexAttributes().hashCode() != vertexAttributesHash)
            return false;
        if (renderable.material.getMask() != materialAttributesMask)
            return false;

        if (hasBones && renderable.bones == null)
            return false;
        if (!hasBones && renderable.bones != null)
            return false;

        long renderableEnvironmentMask = renderable.environment == null ? 0 : renderable.environment.getMask();
        if (environmentMask != renderableEnvironmentMask)
            return false;
        boolean renderableHasShadowMap = renderable.environment != null && renderable.environment.shadowMap != null;
        if (hasShadowMap != renderableHasShadowMap)
            return false;

        if (renderable.meshPart.primitiveType != primitiveType)
            return false;
        boolean renderableBlended = renderable.material.has(BlendingAttribute.Type)
                && ((BlendingAttribute) renderable.material.get(BlendingAttribute.Type)).blended;
        if (blended != renderableBlended)
            return false;

        return true;
    }

    private final Attributes combinedAttributes = new Attributes();

    public void render(Renderable renderable) {
        if (renderable.worldTransform.det3x3() == 0)
            return;
        // combine attributes (not used)
        combinedAttributes.clear();
        if (renderable.environment != null)
            combinedAttributes.set(renderable.environment);
        if (renderable.material != null)
            combinedAttributes.set(renderable.material);
        render(renderable, combinedAttributes);
    }

    private Renderable prevRenderable;
    private int prevMaterialHash;
    private int appliedMaterialHash;
    private int firstInstance;
    private int instanceCount;
    private final Matrix4 tmpM = new Matrix4();

    // note: the combinedAttributes are not used. The signature is maintained to remain compatible with WgShader.
    public void render(Renderable renderable, Attributes attributes) {
        if (instanceIndex > config.maxInstances) {
            Gdx.app.error("WebGPUModelBatch", "Too many instances, max is configured as " + config.maxInstances);
            return;
        }

        // renderable-specific data

        // add instance data to instance buffer (instance transform)
        int offset = instanceIndex * 2 * 16 * Float.BYTES;
        // set world transform for this instance
        instanceBuffer.set(offset, renderable.worldTransform);
        // normal matrix is transpose of inverse of world transform
        instanceBuffer.set(offset + 16 * Float.BYTES, tmpM.set(renderable.worldTransform).inv().tra());

        // don't use Material.hashCode() because that also looks at the id which is not relevant
        int materialHash = renderable.material.attributesHash();
        boolean meshPartMatch = prevRenderable != null && renderable.meshPart.equals(prevRenderable.meshPart);

        if (!hasBones && prevRenderable != null && materialHash == prevMaterialHash && meshPartMatch) { // renderable.meshPart.equals(prevRenderable.meshPart)){
            // renderable is similar to the previous one, add to an instance batch
            // note that renderables get a copy of a mesh part not a reference to the Model's mesh part, so you can just
            // compare references.
            // can't put rigged renderables in a batch, because the animation may be different
            instanceCount++;
        } else { // either a new material or a new mesh part, we need to flush the run of instances

            if (prevRenderable != null) {

                if (hasBones) {
                    setBones(prevRenderable.bones);
                }
                materials.bindMaterial(renderPass, prevRenderable.material);
                renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
            }
            // and start a new run with the new renderable
            instanceCount = 1;
            firstInstance = numRenderables;
            prevRenderable = renderable;
            prevMaterialHash = materialHash;
        }
        numRenderables++;
        instanceIndex++;
    }

    // to combine instances in single draw call if they have same mesh part
    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables) {

        // System.out.println("numInstances: "+numInstances);
        final WgMesh mesh = (WgMesh) meshPart.mesh;
        // use an instance offset to find the right modelMatrix in the instanceBuffer
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        // we can't use the following statement, because meshPart was unmodified and doesn't know about WebGPUMesh
        // and we're not using WebGPUMeshPart because then we need to modify Renderable.
        // renderable.meshPart.render(renderPass);
        drawCalls++;
    }

    public void end() {
        if (prevRenderable != null) {
            if (hasBones) {
                setBones(prevRenderable.bones);
                jointMatricesBuffer.endSlices();
            }
            materials.bindMaterial(renderPass, prevRenderable.material);
            renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
        }
        instanceBuffer.flush();
    }

    private Matrix4 idt = new Matrix4();

    // fill the skinning buffers (group 3)
    private void setBones(Matrix4[] bones) {
        int matrixSize = 16 * Float.BYTES;

        if (numRigged == config.maxRigged - 1) {
            Gdx.app.error("setBones", "Too many rigged instances. Increase config.maxRigged.");
            return;
        }
        int dynamicOffset = jointMatricesBuffer.nextSlice();
        jointMatricesBuffer.set(0, bones);
        binder.bindGroup(renderPass, 3, dynamicOffset); // numRigged*jointMatricesBuffer.getUniformStride());
        numRigged++;
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize, boolean hasShadowMap,
            boolean hasCubeMap, boolean hasDiffuseCubeMap, boolean hasSpecularCubeMap) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, false);
        if (hasShadowMap) {
            layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Depth, WGPUTextureViewDimension._2D,
                    false);
            layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Comparison);
        }
        if (hasCubeMap) {
            layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube,
                    false);
            layout.addSampler(4, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        }
        if (hasDiffuseCubeMap) {
            layout.addTexture(5, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube,
                    false);
            layout.addSampler(6, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        }
        if (hasSpecularCubeMap) {
            layout.addTexture(7, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube,
                    false);
            layout.addSampler(8, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
            layout.addTexture(9, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                    false);
            layout.addSampler(10, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        }
        layout.end();
        return layout;
    }

    // 2 mat4 per instance: worldTransform and normalTransform
    private WebGPUBindGroupLayout createInstancingBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch Binding Group Layout (instance)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.ReadOnlyStorage,
                2 * 16 * Float.BYTES * config.maxInstances, false);
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createSkinningBindGroupLayout(int rigStride) {
        // binding 0: joint matrices
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch Binding Group Layout (Skinning)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex, WGPUBufferBindingType.ReadOnlyStorage, rigStride, true);
        layout.end();
        return layout;
    }

    // todo vertex attributes are hardcoded, should use conditional compilation.

    private String getDefaultShaderSource() {
        if (defaultShader == null) {
            defaultShader = Gdx.files.classpath("shaders/modelbatch.wgsl").readString();
        }
        return defaultShader;

    }

    @Override
    public void dispose() {
        binder.dispose();
        instanceBuffer.dispose();
        uniformBuffer.dispose();
        if (brdfLUT != null)
            brdfLUT.dispose();
    }

    /**
     * place lighting information in frame uniform buffer: ambient light, directional lights, point lights.
     */
    private void bindLights(Environment lights) {
        if (lights == null) {
            return;
        }
        final DirectionalLightsAttribute dla = lights.get(DirectionalLightsAttribute.class,
                DirectionalLightsAttribute.Type);
        final Array<DirectionalLight> dirs = dla == null ? null : dla.lights;
        final PointLightsAttribute pla = lights.get(PointLightsAttribute.class, PointLightsAttribute.Type);
        final Array<PointLight> points = pla == null ? null : pla.lights;

        if (dirs != null) {
            if (dirs.size > config.maxDirectionalLights)
                throw new RuntimeException("Too many directional lights. Increase the configured maximum.");

            for (int i = 0; i < dirs.size; i++) {
                directionalLights[i].color.set(dirs.get(i).color);
                directionalLights[i].direction.set(dirs.get(i).direction);
            }
        }

        numDirectionalLights = dirs == null ? 0 : dirs.size;
        for (int i = 0; i < numDirectionalLights; i++) {
            int offset = i * 8 * Float.BYTES;
            binder.setUniform("dirLight[0].color", offset, directionalLights[i].color);
            binder.setUniform("dirLight[0].direction", offset, directionalLights[i].direction);
        }
        binder.setUniform("numDirectionalLights", numDirectionalLights);
        // System.out.println("numDirectionalLights "+ numDirectionalLights);

        if (points != null) {
            if (points.size > config.maxPointLights)
                throw new RuntimeException("Too many point lights. Increase the configured maximum.");
            // is it useful to copy from attributes to a local array?
            for (int i = 0; i < points.size; i++) {
                pointLights[i].color.set(points.get(i).color);
                pointLights[i].position.set(points.get(i).position);
                pointLights[i].intensity = points.get(i).intensity;
            }
        }

        numPointLights = points == null ? 0 : points.size;
        for (int i = 0; i < numPointLights; i++) {
            int offset = i * 12 * Float.BYTES;
            binder.setUniform("pointLight[0].color", offset, pointLights[i].color);
            binder.setUniform("pointLight[0].position", offset, pointLights[i].position);
            binder.setUniform("pointLight[0].intensity", offset, pointLights[i].intensity);
        }
        binder.setUniform("numPointLights", numPointLights);
        // System.out.println("numPointLights "+ numPointLights);

        final ColorAttribute ambient = lights.get(ColorAttribute.class, ColorAttribute.AmbientLight);
        if (ambient != null)
            binder.setUniform("ambientLight", ambient.color);

        final ColorAttribute fog = lights.get(ColorAttribute.class, ColorAttribute.Fog);
        if (fog != null) {
            // Convert provided fog color from SRGB to linear, e.g. to match the background color
            linearFogColor.set(fog.color);
            GammaCorrection.toLinear(linearFogColor);
            binder.setUniform("fogColor", linearFogColor);
        }

        if (lights.shadowMap != null) {
            binder.setUniform("shadowProjViewTransform", lights.shadowMap.getProjViewTrans());

            WgTexture shadowMap = (WgTexture) (lights.shadowMap.getDepthMap().texture);
            binder.setTexture("shadowMap", shadowMap.getTextureView());
            binder.setSampler("shadowSampler", shadowMap.getDepthSampler());
            Rectangle rect = webgpu.getViewportRectangle();
            float screenSize = Math.max(rect.width, rect.height);
            binder.setUniform("shadowPcfOffset", 1f / screenSize);
            binder.setUniform("shadowBias", 0.07f);

        }

        final WgCubemapAttribute cubemapAttribute = lights.get(WgCubemapAttribute.class,
                WgCubemapAttribute.EnvironmentMap);
        if (cubemapAttribute != null) {
            // System.out.println("Setting cube map via binder");
            WgTexture cubeMap = cubemapAttribute.textureDescription.texture;
            binder.setTexture("cubeMap", cubeMap.getTextureView());
            binder.setSampler("cubeSampler", cubeMap.getSampler());
        }

        final WgCubemapAttribute diffuseCubeMapAttribute = lights.get(WgCubemapAttribute.class,
                WgCubemapAttribute.DiffuseCubeMap);
        if (diffuseCubeMapAttribute != null) {
            // System.out.println("Setting cube map via binder");
            WgTexture cubeMap = diffuseCubeMapAttribute.textureDescription.texture;
            binder.setTexture("irradianceMap", cubeMap.getTextureView());
            binder.setSampler("irradianceSampler", cubeMap.getSampler());
        }
        final WgCubemapAttribute specularCubeMapAttribute = lights.get(WgCubemapAttribute.class,
                WgCubemapAttribute.SpecularCubeMap);
        if (specularCubeMapAttribute != null) {
            // System.out.println("Setting cube map via binder");
            WgTexture cubeMap = specularCubeMapAttribute.textureDescription.texture;
            binder.setTexture("radianceMap", cubeMap.getTextureView());
            binder.setSampler("radianceSampler", cubeMap.getSampler());
            binder.setTexture("brdfLUT", brdfLUT.getTextureView());
            binder.setSampler("lutSampler", brdfLUT.getSampler());
            binder.setUniform("numRoughnessLevels", cubeMap.getMipLevelCount());
        }
    }

}

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
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
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
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.CSMShadowAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.CascadedShadowAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgCascadedShadowLight;
import com.monstrous.gdx.webgpu.wrappers.*;

/** Default shader to render renderables */
public class WgDefaultShader extends WgShader implements Disposable {

    protected final WebGPUContext webgpu;
    protected final WgModelBatch.Config config;
    private static String defaultShader;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer jointMatricesBuffer;
    private MaterialsCache materials;
    // Fallback textures used when createMaterialLayout() is overridden — owned and disposed here.
    private WgTexture fallbackWhiteTexture;
    private WgTexture fallbackNormalTexture;
    private WgTexture fallbackBlackTexture;
    private int numRigged;
    private int rigSize; // bytes per rigged instance
    protected final PipelineCache pipelineCache; // cache of pipelines for different render targets
    private final float[] tmpWeights = new float[8];
    protected final WGPUPipelineLayout pipelineLayout;
    protected final PipelineSpecification pipelineSpec;
    protected WebGPURenderPass renderPass;
    protected final VertexAttributes vertexAttributes;
    protected final Matrix4 combined;
    private final Matrix4 projection;
    // Remap OpenGL depth range [-1,1] to WebGPU [0,1].
    // Used in bindLights() to shift the shadow camera's ProjViewTrans to match the shadow map depths
    // (which were rendered through WgModelBatch, which applies this same shift during the shadow pass).
    private static final Matrix4 shiftDepthMatrix = new Matrix4().idt().scl(1, 1, 0.5f).trn(0, 0, 0.5f);

    protected int numDirectionalLights;
    protected DirectionalLight[] directionalLights;
    protected int numPointLights;
    protected PointLight[] pointLights;
    protected final Vector4 tmpVec4 = new Vector4();
    protected final long materialAttributesMask;
    protected final long vertexAttributesHash;
    protected final long environmentMask;
    protected final boolean blended;
    protected final boolean hasShadowMap;
    protected final boolean hasCascadedShadowMap;
    protected final int maxCascades; // 0 when hasCascadedShadowMap is false
    protected final boolean hasCubeMap;
    protected final boolean hasBones;
    protected final int primitiveType;
    protected int frameNumber;
    protected int instanceIndex;
    protected Color linearFogColor;
    protected final WgTexture brdfLUT;
    protected int maxRenderPassesPerFrame = 16; // Support up to 16 render passes per frame with this shader

    public WgDefaultShader(final Renderable renderable) {
        this(renderable, new WgModelBatch.Config());
    }

    public WgDefaultShader(final Renderable renderable, WgModelBatch.Config config) {
        this.config = config;

        // If no custom MaterialsCache has been supplied via config, create one now.
        // - Default case (not subclassed): use MaterialsCache(int) directly so the fallback
        //   textures are per-instance fields owned and disposed by the cache.
        // - Subclassed case: createMaterialLayout() is overridden to return a custom layout;
        //   the cache is built from that layout (caller is responsible for fallback textures
        //   inside their resolvers).
        if (config.materials == null) {
            config.materials = new MaterialsCache(config.maxMaterials, createMaterialLayout());
        }
        this.materials = config.materials;

        // System.out.println("Create WgDefaultShader "+renderable.meshPart.id+" vert mask:
        // "+renderable.meshPart.mesh.getVertexAttributes().getMask()+
        // "mat mask: "+renderable.material.getMask());

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        linearFogColor = new Color();
        hasCascadedShadowMap = renderable.environment != null
                && renderable.environment.has(CascadedShadowAttribute.Type);
        // CSM and single shadow map are mutually exclusive — CSM takes priority.
        hasShadowMap = !hasCascadedShadowMap
                && renderable.environment != null && renderable.environment.shadowMap != null;
        if (hasCascadedShadowMap) {
            CascadedShadowAttribute csmAttr = renderable.environment.get(
                    CascadedShadowAttribute.class, CascadedShadowAttribute.Type);
            maxCascades = csmAttr.csmLight.getCascadeCount();
        } else {
            maxCascades = 0;
        }
        hasCubeMap = renderable.environment != null && renderable.environment.has(WgCubemapAttribute.EnvironmentMap);
        hasBones = renderable.bones != null;
        final boolean hasDiffuseCubeMap = renderable.environment != null
                && renderable.environment.has(WgCubemapAttribute.DiffuseCubeMap);
        final boolean hasSpecularCubeMap = renderable.environment != null
                && renderable.environment.has(WgCubemapAttribute.SpecularCubeMap);

        // Create uniform buffer for global (per-frame) uniforms, e.g. projection matrix, camera position, etc.
        // Use multiple slices to support multiple render passes per frame with different camera/lighting state
        // Shadow section: single shadow map uses 1 mat4 (16 floats);
        // CSM uses N mat4s + vec4 splits + vec4 biases + 1 mat4 csmCameraProjectionView.
        int shadowUniformFloats = hasCascadedShadowMap ? (maxCascades * 16 + 4 + 4 + 16) : 16;
        uniformBufferSize = (16 + shadowUniformFloats + 4 + 4 + 4 + 4 + 32 + 8 * config.maxDirectionalLights
                + 12 * config.maxPointLights) * Float.BYTES;

        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform),
                maxRenderPassesPerFrame);

        rigSize = config.numBones * 16 * Float.BYTES;

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize, hasShadowMap, hasCascadedShadowMap,
                hasCubeMap, hasDiffuseCubeMap, hasSpecularCubeMap));
        binder.defineGroup(1, materials.getBindGroupLayout());
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
        if (hasCascadedShadowMap) {
            binder.defineBinding("csmShadowMap", 0, 1);
            binder.defineBinding("csmShadowSampler", 0, 2);
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
            brdfLUT = new WgTexture(Gdx.files.classpath("brdfLUT.png"), false, false);
            brdfLUT.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            brdfLUT.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);

        } else
            brdfLUT = null;

        binder.defineBinding("instanceUniforms", 2, 0);

        binder.defineBinding("jointMatrices", 3, 0);

        boolean hasMorph = false;
        for (VertexAttribute attr : renderable.meshPart.mesh.getVertexAttributes()) {
            if (attr.alias.startsWith("a_position_morph_")) {
                hasMorph = true;
                break;
            }
        }


        defineUniforms();

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        // binder.setBuffer("materialUniforms", materialBuffer, 0, materialSize);

        int instanceSize = (2 * 16 + 8) * Float.BYTES; // 2 matrices + 8 morph weights

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
        pipelineLayout = binder.getPipelineLayout("ModelBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        pipelineSpec = new PipelineSpecification("ModelBatch pipeline", vertexAttributes, getShaderSource());
        // define locations of vertex attributes in line with shader code
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.POSITION_ATTRIBUTE, 0);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.NORMAL_ATTRIBUTE, 2);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.COLOR_ATTRIBUTE, 5);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "0", 1);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.TANGENT_ATTRIBUTE, 3);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BINORMAL_ATTRIBUTE, 4);
        pipelineSpec.vertexLayout.setVertexAttributeLocation(ShaderProgram.BONEWEIGHT_ATTRIBUTE, 7);
        for (int i = 0; i < 8; i++) {
            pipelineSpec.vertexLayout.setVertexAttributeLocation("a_position_morph_" + i, 8 + i);
        }

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

        pipelineCache = new PipelineCache();

        directionalLights = new DirectionalLight[config.maxDirectionalLights];
        for (int i = 0; i < config.maxDirectionalLights; i++)
            directionalLights[i] = new DirectionalLight();
        pointLights = new PointLight[config.maxPointLights];
        for (int i = 0; i < config.maxPointLights; i++)
            pointLights[i] = new PointLight();

        projection = new Matrix4();
        combined = new Matrix4();

        frameNumber = -1;
    }

    protected int uniformOffset;

    public void defineUniform(String name, int sizeInBytes){
        binder.defineUniform(name, 0, 0, uniformOffset);
        uniformOffset += sizeInBytes;
    }

    protected void defineUniforms(){
        // define uniforms in uniform buffers with their offset
        // frame uniforms
        //int offset = 0;
        uniformOffset = 0;
        defineUniform("projectionViewTransform", 16*4);
//        binder.defineUniform("projectionViewTransform", 0, 0, offset);
//        offset += 16 * 4;

        if (hasCascadedShadowMap) {
            // N cascade matrices followed by cascadeSplits (vec4f), cascadeBiases (vec4f),
            // and csmCameraProjectionView (mat4x4f)
            defineUniform("shadowProjViewTransforms[0]",maxCascades * 16 * 4);
            defineUniform("cascadeSplits",4 * 4);
            defineUniform("cascadeBiases",4 * 4);
            defineUniform("csmCameraProjectionView", 16 * 4);



//            binder.defineUniform("shadowProjViewTransforms[0]", 0, 0, offset);
//            offset += maxCascades * 16 * 4;
//            binder.defineUniform("cascadeSplits", 0, 0, offset);
//            offset += 4 * 4;
//            binder.defineUniform("cascadeBiases", 0, 0, offset);
//            offset += 4 * 4;
//            binder.defineUniform("csmCameraProjectionView", 0, 0, offset);
//            offset += 16 * 4;
        } else {
            defineUniform("shadowProjViewTransform",16 * 4);

//            binder.defineUniform("shadowProjViewTransform", 0, 0, offset);
//            offset += 16 * 4;
        }

        if (config.maxDirectionalLights > 0) {
            // define only a uniform for the initial array elements
            // you will have to add an offset of i * array element size
            defineUniform("dirLight[0].color",4 * 4);
            defineUniform("dirLight[0].direction",4 * 4);
            defineUniform("dirLight filler",8 * 4 * (config.maxDirectionalLights - 1));
//            binder.defineUniform("dirLight[0].color", 0, 0, offset);
//            offset += 4 * 4;
//            binder.defineUniform("dirLight[0].direction", 0, 0, offset);
//            offset += 4 * 4;
//            offset += 8 * Float.BYTES * (config.maxDirectionalLights - 1);
        }

        if (config.maxPointLights > 0) {
            defineUniform("pointLight[0].color",4 * 4);
            defineUniform("pointLight[0].position",4 * 4);
            defineUniform("pointLight[0].intensity",4 * 4);// added padding
            defineUniform("pointLight filler",12 * 4 * (config.maxPointLights - 1));

//            binder.defineUniform("pointLight[0].color", 0, 0, offset);
//            offset += 4 * 4;
//            binder.defineUniform("pointLight[0].position", 0, 0, offset);
//            offset += 4 * 4;
//            binder.defineUniform("pointLight[0].intensity", 0, 0, offset);
//            offset += 4 * 4; // added padding
//            offset += 12 * Float.BYTES * (config.maxPointLights - 1);
        }
        defineUniform("ambientLight",4 * 4);
        defineUniform("cameraPosition",4 * 4);
        defineUniform("fogColor",4 * 4);
        defineUniform("numDirectionalLights",4);
        defineUniform("numPointLights",4);
        defineUniform("shadowPcfOffset", 4);
        defineUniform("shadowBias",4);
        defineUniform("normalMapStrength", 4);
        defineUniform("numRoughnessLevels", 4);


//        binder.defineUniform("ambientLight", 0, 0, offset);
//        offset += 4 * 4;
//        binder.defineUniform("cameraPosition", 0, 0, offset);
//        offset += 4 * 4;
//        binder.defineUniform("fogColor", 0, 0, offset);
//        offset += 4 * 4;
//        binder.defineUniform("numDirectionalLights", 0, 0, offset);
//        offset += 4;
//        binder.defineUniform("numPointLights", 0, 0, offset);
//        offset += 4;
//        binder.defineUniform("shadowPcfOffset", 0, 0, offset);
//        offset += 4;
//        binder.defineUniform("shadowBias", 0, 0, offset);
//        offset += 4;
//        binder.defineUniform("normalMapStrength", 0, 0, offset);
//        offset += 4;
//        binder.defineUniform("numRoughnessLevels", 0, 0, offset);
//        offset += 4;
        if (hasCascadedShadowMap) {
            defineUniform("shadowPcfRadius", 4);
            defineUniform("shadowFilterMode", 4);
            defineUniform("cascadeBlendFraction", 4);

//            binder.defineUniform("shadowPcfRadius", 0, 0, offset);
//            offset += 4;
//            binder.defineUniform("shadowFilterMode", 0, 0, offset);
//            offset += 4;
//            binder.defineUniform("cascadeBlendFraction", 0, 0, offset);
//            offset += 4;
        }

        // note: put shorter uniforms last for padding reasons

         System.out.println("offset:"+ uniformOffset +" uniformBufferSize: "+uniformBufferSize);
        if (uniformOffset > uniformBufferSize)
            throw new RuntimeException("Mismatch in frame uniform buffer size");
        // binder.defineUniform("modelMatrix", 2, 0, 0);

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

        // Reset buffer slices at the start of each frame
        if (webgpu.frameNumber != this.frameNumber) {
            instanceIndex = 0;
            numRigged = 0;
            this.frameNumber = webgpu.frameNumber;
            uniformBuffer.beginSlices(); // Reset uniform buffer slices for new frame
            if (jointMatricesBuffer != null)
                jointMatricesBuffer.beginSlices();
        }

        // Get a new slice of the uniform buffer for this render pass
        // This ensures each render pass has its own independent camera/lighting data
        int dynamicOffset = uniformBuffer.nextSlice();

        // set global uniforms, that do not depend on renderables
        // e.g. camera, lighting, environment uniforms
        // Note: camera.combined is already remapped from OpenGL [-1,1] to WebGPU [0,1] depth
        // by WgModelBatch.begin() before shaders are invoked.
        binder.setUniform("projectionViewTransform", camera.combined);

        // pass a special value in the w component of camera position that is used by the fog calculation
        tmpVec4.set(camera.position.x, camera.position.y, camera.position.z, 1.1881f / (camera.far * camera.far));
        binder.setUniform("cameraPosition", tmpVec4);

        // todo: different shaders may overwrite lighting uniforms if renderables have other environments ...
        bindLights(renderable.environment);

        binder.setUniform("normalMapStrength", 0.5f); // emphasis factor for normal map [0-1]

        // now that we've set all the uniforms (camera,lights, etc.) write the buffer to the gpu
        uniformBuffer.flush();

        // bind group 0 (frame) with dynamic offset for this render pass
        binder.bindGroup(renderPass, 0, dynamicOffset);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 2);

        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null; // to store renderable that still needs to be rendered
        appliedMaterialHash = -1;

        materials.start(); // indicate that no material is currently bound

        setPipeline(renderPass);
    }

    private void setPipeline(WebGPURenderPass pass) {
        // Update pipeline spec to match the current render pass's format and sample count.
        // Clone the formats array — getColorFormats() returns the render pass's internal array,
        // which could be mutated when the pooled pass is reused, corrupting the cached spec.
        WGPUTextureFormat[] formats = pass.getColorFormats();
        boolean formatsChanged = pipelineSpec.colorFormats == null
                || pipelineSpec.colorFormats.length != formats.length;
        if (!formatsChanged) {
            for (int i = 0; i < formats.length; i++) {
                if (formats[i] != pipelineSpec.colorFormats[i]) {
                    formatsChanged = true;
                    break;
                }
            }
        }
        if (formatsChanged) {
            // Reuse existing array if length matches, only allocate on length change (rare: single-target vs MRT)
            if (pipelineSpec.colorFormats == null || pipelineSpec.colorFormats.length != formats.length) {
                pipelineSpec.colorFormats = new WGPUTextureFormat[formats.length];
            }
            System.arraycopy(formats, 0, pipelineSpec.colorFormats, 0, formats.length);
            // Clear the cached shader so WebGPUPipeline will call buildPrefix() fresh for the new
            // target format — this re-evaluates hasLinearOutput() and sets GAMMA_CORRECTION correctly.
            // Only do this for auto-compiled shaders (shaderSource != null); pre-built shaders are
            // external and do not depend on hasLinearOutput(), so they must be kept as-is.
            if (pipelineSpec.shaderSource != null) {
                pipelineSpec.shader = null;
            }
        }
        pipelineSpec.numSamples = pass.getSampleCount();
        pipelineSpec.invalidateHashCode();

        WebGPUPipeline pipeline = pipelineCache.findPipeline(pipelineLayout, pipelineSpec);
        pass.setPipeline(pipeline);
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
        boolean renderableHasCsm = renderable.environment != null
                && renderable.environment.has(CascadedShadowAttribute.Type);
        if (hasCascadedShadowMap != renderableHasCsm)
            return false;
        // CSM and single shadow map are mutually exclusive — CSM takes priority
        boolean renderableHasShadowMap = !renderableHasCsm
                && renderable.environment != null && renderable.environment.shadowMap != null;
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
        int offset = instanceIndex * (2 * 16 + 8) * Float.BYTES;
        // set world transform for this instance
        instanceBuffer.set(offset, renderable.worldTransform);
        // normal matrix is transpose of inverse of world transform
        instanceBuffer.set(offset + 16 * Float.BYTES, tmpM.set(renderable.worldTransform).inv().tra());

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

        // Update weights in the buffer
        for (int i = 0; i < 8; i++) {
            instanceBuffer.set(offset + 32 * Float.BYTES + i * Float.BYTES, tmpWeights[i]);
        }

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
            boolean hasCascadedShadowMap, boolean hasCubeMap, boolean hasDiffuseCubeMap, boolean hasSpecularCubeMap) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, true); // Enable dynamic offset for multiple render passes per frame
        if (hasShadowMap) {
            layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Depth, WGPUTextureViewDimension._2D,
                    false);
            layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Comparison);
        }
        if (hasCascadedShadowMap) {
            // Depth texture 2D array — one layer per cascade
            layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Depth,
                    WGPUTextureViewDimension._2DArray, false);
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
                (2 * 16 + 8) * Float.BYTES * config.maxInstances, false);
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

    /**
     * Declares the material uniform group (group 1) for this shader.
     *
     * The base implementation creates per-instance fallback textures (owned by this shader
     * and disposed with it) and returns the complete standard PBR layout.
     *
     * Override to add custom uniforms or textures — call {@code super.createMaterialLayout()}
     * to get the standard layout, then chain your additions:
     *
     *   {@literal @}Override
     *   protected MaterialUniformLayout createMaterialLayout() {
     *       return super.createMaterialLayout()
     *           .registerUniform("tintColor", TYPE_VEC4,
     *               (mat, b, n) -> b.setUniform(n, Color.WHITE))
     *           .registerTexture("detailTexture", "detailSampler",
     *               mat -> myDetailTexture);
     *   }
     *
     * Then add the matching fields and bindings in your WGSL — that is all.
     */
    protected MaterialUniformLayout createMaterialLayout() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);  pixmap.fill();
        fallbackWhiteTexture  = new WgTexture(pixmap, "default (white)");
        pixmap.setColor(Color.GREEN);  pixmap.fill();
        fallbackNormalTexture = new WgTexture(pixmap, "default normal texture");
        pixmap.setColor(Color.BLACK);  pixmap.fill();
        fallbackBlackTexture  = new WgTexture(pixmap, "default (black)");
        pixmap.dispose();
        return MaterialsCache.buildDefaultLayout(fallbackWhiteTexture, fallbackNormalTexture, fallbackBlackTexture);
    }

    protected String getShaderSource() {
        if (config.shaderSource != null)
            return config.shaderSource;

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
        pipelineCache.dispose();
        if (brdfLUT != null)
            brdfLUT.dispose();
        if (fallbackWhiteTexture  != null) fallbackWhiteTexture.dispose();
        if (fallbackNormalTexture != null) fallbackNormalTexture.dispose();
        if (fallbackBlackTexture  != null) fallbackBlackTexture.dispose();
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
                // Assign fields directly: Color.set() calls clamp(), which caps to [0,1]
                // and would silently discard HDR light intensities above 1.0.
                Color src = dirs.get(i).color;
                directionalLights[i].color.r = src.r;
                directionalLights[i].color.g = src.g;
                directionalLights[i].color.b = src.b;
                directionalLights[i].color.a = src.a;
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
                // Assign fields directly: Color.set() calls clamp() — see directional lights above.
                Color src = points.get(i).color;
                pointLights[i].color.r = src.r;
                pointLights[i].color.g = src.g;
                pointLights[i].color.b = src.b;
                pointLights[i].color.a = src.a;
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
            // The shadow map was rendered through WgModelBatch which applies the depth shift
            // during the shadow pass. The shadow PVT must be shifted to match those depth values.
            combined.set(shiftDepthMatrix).mul(lights.shadowMap.getProjViewTrans());
            binder.setUniform("shadowProjViewTransform", combined);

            WgTexture shadowMap = (WgTexture) (lights.shadowMap.getDepthMap().texture);
            binder.setTexture("shadowMap", shadowMap.getTextureView());
            binder.setSampler("shadowSampler", shadowMap.getDepthSampler());

            // PCF offset: 1 texel in shadow-map UV space for a basic 3×3 soft shadow kernel.
            binder.setUniform("shadowPcfOffset", 1f / shadowMap.getWidth());

            FloatAttribute biasAttr = lights.get(FloatAttribute.class, PBRFloatAttribute.ShadowBias);
            float shadowBias = biasAttr != null ? biasAttr.value : 0.01f;
            binder.setUniform("shadowBias", shadowBias);
        }

        final CascadedShadowAttribute csmAttr = lights.get(CascadedShadowAttribute.class, CascadedShadowAttribute.Type);
        if (csmAttr != null) {
            WgCascadedShadowLight csm = csmAttr.csmLight;
            Matrix4[] pvts = csm.getLightSpaceProjViews();
            // Upload each cascade's pre-shifted projection-view matrix
            for (int i = 0; i < pvts.length; i++) {
                binder.setUniform("shadowProjViewTransforms[0]", i * 16 * Float.BYTES, pvts[i]);
            }
            // Upload cascade split NDC-z thresholds as a vec4f
            float[] splits = csm.getCascadeSplitNDC();
            tmpVec4.set(splits[0], splits[1], splits[2], splits[3]);
            binder.setUniform("cascadeSplits", tmpVec4);
            // Upload per-cascade shadow biases (scaled by each cascade's depth range)
            float[] biases = csm.getCascadeBiases();
            tmpVec4.set(biases[0], biases[1], biases[2], biases[3]);
            binder.setUniform("cascadeBiases", tmpVec4);
            // Upload the CSM driver camera's shifted projection-view for cascade selection
            // (allows correct cascade selection even when rendering from a different camera)
            binder.setUniform("csmCameraProjectionView", csm.getCsmCameraProjectionView());
            binder.setTexture("csmShadowMap", csm.getArrayView());
            binder.setSampler("csmShadowSampler", csm.getDepthArraySampler());

            // Shadow parameters from CSMShadowAttribute (or defaults)
            CSMShadowAttribute csmShadowAttr = lights.get(CSMShadowAttribute.class, CSMShadowAttribute.Type);
            float pcfSoftness = csmShadowAttr != null ? csmShadowAttr.softness : CSMShadowAttribute.DEFAULT_SOFTNESS;
            float pcfRadius   = csmShadowAttr != null ? csmShadowAttr.pcfRadius : CSMShadowAttribute.DEFAULT_PCF_RADIUS;
            float shadowBias  = csmShadowAttr != null ? csmShadowAttr.bias      : CSMShadowAttribute.DEFAULT_BIAS;

            binder.setUniform("shadowPcfOffset", pcfSoftness / csm.getShadowMapSize());
            binder.setUniform("shadowPcfRadius", pcfRadius);
            binder.setUniform("shadowBias", shadowBias);
            float filterMode = csmShadowAttr != null ? (float) csmShadowAttr.shadowFilterMode : (float) CSMShadowAttribute.DEFAULT_FILTER_MODE;
            binder.setUniform("shadowFilterMode", filterMode);
            float cascadeBlend = csmShadowAttr != null ? csmShadowAttr.cascadeBlend : CSMShadowAttribute.DEFAULT_CASCADE_BLEND;
            binder.setUniform("cascadeBlendFraction", cascadeBlend);
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

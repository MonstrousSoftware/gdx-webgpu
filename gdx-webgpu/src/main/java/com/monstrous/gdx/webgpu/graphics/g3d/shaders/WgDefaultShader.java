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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRTextureAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.wrappers.*;

import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/** Default shader to render renderables  */
public class WgDefaultShader extends WgShader implements Disposable {

    private WebGPUContext webgpu;
    private final Config config;
    private static String defaultShader;
    private final WgTexture defaultTexture;
    private final WgTexture defaultNormalTexture;
    private final WgTexture defaultBlackTexture;
    private final Matrix4 dummyMatrix = new Matrix4();
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer materialBuffer;
    private final int materialSize;
    private final WebGPUPipeline pipeline;            // a shader has one pipeline
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;
    private final Material material;
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
    private final int primitiveType;
    private int frameNumber;
    private int instanceIndex;


    public static class Config {
        public int maxInstances;
        public int maxMaterials;
        public int maxDirectionalLights;
        public int maxPointLights;

        public Config() {
            this.maxInstances = 1024;
            this.maxMaterials = 512;
            this.maxDirectionalLights = 3;  // todo hard coded in shader, don't change
            this.maxPointLights = 3;  // todo hard coded in shader, don't change
        }
    }

    public WgDefaultShader(final Renderable renderable) {
        this(renderable, new Config());
    }

    public WgDefaultShader(final Renderable renderable, Config config) {
        this.config = config;

//        System.out.println("Create WgDefaultShader "+renderable.meshPart.id+" vert mask: "+renderable.meshPart.mesh.getVertexAttributes().getMask()+
//            "mat mask: "+renderable.material.getMask());

        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        webgpu = gfx.getContext();

        // fallback texture
        // todo these could be static?
        Pixmap pixmap = new Pixmap(1,1,RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        defaultTexture = new WgTexture(pixmap);
        defaultTexture.setLabel("default (white)");
        pixmap.setColor(Color.GREEN);
        pixmap.fill();
        defaultNormalTexture = new WgTexture(pixmap);
        defaultNormalTexture.setLabel("default normal texture");
        pixmap.setColor(Color.BLACK);
        pixmap.fill();
        defaultBlackTexture = new WgTexture(pixmap);
        defaultBlackTexture.setLabel("default (black))");

        hasShadowMap = renderable.environment != null && renderable.environment.shadowMap != null;
        hasCubeMap = renderable.environment != null && renderable.environment.has(WgCubemapAttribute.EnvironmentMap);

        // Create uniform buffer for global (per-frame) uniforms, e.g. projection matrix, camera position, etc.
        uniformBufferSize = (16 + 16 + 4 + 4 +4+4 +   +32
                +8*config.maxDirectionalLights
                +12*config.maxPointLights)* Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));


        materialSize = 20*Float.BYTES;      // data size per material (should be enough for now)
        // buffer for uniforms per material, e.g. color, shininess, ...
        // this does not include textures

        // allocate a uniform buffer with dynamic offsets
        materialBuffer = new WebGPUUniformBuffer(materialSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform),  config.maxMaterials);

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize, hasShadowMap, hasCubeMap));
        binder.defineGroup(1, createMaterialBindGroupLayout(materialSize));
        binder.defineGroup(2, createInstancingBindGroupLayout());

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        if(hasShadowMap) {
            binder.defineBinding("shadowMap", 0, 1);
            binder.defineBinding("shadowSampler", 0, 2);
        }
        if(hasCubeMap) {
            binder.defineBinding("cubeMap", 0, 3);
            binder.defineBinding("cubeSampler", 0, 4);
        }


        binder.defineBinding("materialUniforms", 1, 0);
        binder.defineBinding("diffuseTexture", 1, 1);
        binder.defineBinding("diffuseSampler", 1, 2);
        binder.defineBinding("normalTexture", 1, 3);
        binder.defineBinding("normalSampler", 1, 4);
        binder.defineBinding("metallicRoughnessTexture", 1, 5);
        binder.defineBinding("metallicRoughnessSampler", 1, 6);
        binder.defineBinding("emissiveTexture", 1, 7);
        binder.defineBinding("emissiveSampler", 1, 8);
        binder.defineBinding("instanceUniforms", 2, 0);
        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset); offset += 16*4;
        binder.defineUniform("shadowProjViewTransform", 0, 0, offset); offset += 16*4;

        for(int i = 0; i < config.maxDirectionalLights; i++) {
            binder.defineUniform("dirLight["+i+"].color", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("dirLight["+i+"].direction", 0, 0, offset);
            offset += 4 * 4;
        }
        for(int i = 0; i < config.maxPointLights; i++) {
            binder.defineUniform("pointLight["+i+"].color", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("pointLight["+i+"].position", 0, 0, offset);
            offset += 4 * 4;
            binder.defineUniform("pointLight["+i+"].intensity", 0, 0, offset);
            offset += 4*4;    // added padding
        }
        binder.defineUniform("ambientLight", 0, 0, offset); offset += 4*4;
        binder.defineUniform("cameraPosition", 0, 0, offset); offset += 4*4;
        binder.defineUniform("fogColor", 0, 0, offset); offset += 4*4;
        binder.defineUniform("numDirectionalLights", 0, 0, offset); offset += 4;
        binder.defineUniform("numPointLights", 0, 0, offset); offset += 4;




        // note: put shorter uniforms last for padding reasons

        //System.out.println("offset:"+offset+" "+uniformBufferSize);
        if(offset > uniformBufferSize) throw new RuntimeException("Mismatch in frame uniform buffer size");
        //binder.defineUniform("modelMatrix", 2, 0, 0);

        // material uniforms
        offset = 0;
        binder.defineUniform("diffuseColor", 1, 0, offset); offset += 4*4;
        binder.defineUniform("shininess", 1, 0, offset); offset += 4;

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        binder.setBuffer("materialUniforms", materialBuffer, 0,  materialSize);

        int instanceSize = 16*Float.BYTES;      // data size per instance

        // for now we use a uniform buffer, but we organize data as an array of modelMatrix
        // we are not using dynamic offsets, but we will index the array in teh shader code using the instance_index
        instanceBuffer = new WebGPUUniformBuffer(instanceSize*config.maxInstances, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage));

        binder.setBuffer("instanceUniforms", instanceBuffer, 0,  instanceSize *config.maxInstances);

        vertexAttributesHash = renderable.meshPart.mesh.getVertexAttributes().hashCode();
        materialAttributesMask = renderable.material.getMask();
        environmentMask = renderable.environment != null ? renderable.environment.getMask() : 0;

        material = new Material(renderable.material);

        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("ModelBatch pipeline layout");

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification(vertexAttributes, getDefaultShaderSource());
        pipelineSpec.name = "ModelBatch pipeline";

        // default blending values
        blended = renderable.material.has(BlendingAttribute.Type)
            && ((BlendingAttribute)renderable.material.get(BlendingAttribute.Type)).blended;
        if(blended) {
            pipelineSpec.enableBlending();
            pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
            pipelineSpec.cullMode = WGPUCullMode.None;
        } else {
            pipelineSpec.disableBlending();
            pipelineSpec.cullMode = WGPUCullMode.Back;
        }

        pipelineSpec.environment = renderable.environment;
        primitiveType = renderable.meshPart.primitiveType;
        if(primitiveType == GL20.GL_LINES)  // todo all cases
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;

        //System.out.println("pipeline spec: "+pipelineSpec.hashCode()+pipelineSpec.vertexAttributes);

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);

        directionalLights = new DirectionalLight[config.maxDirectionalLights];
        for(int i = 0; i <config.maxDirectionalLights; i++)
            directionalLights[i] = new DirectionalLight();
        pointLights = new PointLight[config.maxPointLights];
        for(int i = 0; i <config.maxPointLights; i++)
            pointLights[i] = new PointLight();

        projection = new Matrix4();
        combined = new Matrix4();
        // matrix to transform OpenGL projection to WebGPU projection by modifying the Z scale
        shiftDepthMatrix = new Matrix4().scl(1,1,-0.5f).trn(0,0,0.5f);

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

    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass){
        this.renderPass = renderPass;



        // set global uniforms, that do not depend on renderables
        // e.g. camera, lighting, environment uniforms
        //
         // todo: we are working here with an OpenGL projection matrix, which provides a different Z range than for WebGPU.

//        projection.set(camera.projection);
//        projection.set(shiftDepthMatrix).mul(camera.projection);
//        combined.set(projection).mul(camera.view);
        binder.setUniform("projectionViewTransform", camera.combined);

        // pass a special value in the w component of camera position that is used by the fog calculation
        tmpVec4.set(camera.position.x, camera.position.y, camera.position.z, 1.1881f / (camera.far * camera.far));
        binder.setUniform("cameraPosition", tmpVec4);
        uniformBuffer.flush();
        // note: if we call WgModelBatch multiple times per frame with a different camera, the old ones are lost


        // todo: different shaders may overwrite lighting uniforms if renderables have other environments ...
        bindLights(renderable.environment);

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 2);

        if(webgpu.frameNumber != this.frameNumber ){
            instanceIndex = 0;
            numMaterials = 0;
            this.frameNumber = webgpu.frameNumber;
        }
        numRenderables = 0;
        drawCalls = 0;
        prevRenderable = null;  // to store renderable that still needs to be rendered

        renderPass.setPipeline(pipeline);
    }



    @Override
    public int compareTo(Shader other) {
        if (other == null) return -1;
        if (other == this) return 0;
        return 0; // FIXME compare shaders on their impact on performance
    }


    /** check if the provided renderable is similar enough to the one used to create this shader, that it can be rendered.
     * Look at vertex attributes, material attributes.
     */
    @Override
    public boolean canRender(Renderable renderable) {
//        System.out.println("Can Render? "+renderable.meshPart.id+" mask: "+renderable.meshPart.mesh.getVertexAttributes().getMask()+" ==? "+vertexAttributes.getMask() +
//            "mat mask: "+renderable.material.getMask()+" ==? "+material.getMask());

        // note: it is not sufficient to compare the mask of the vertex attributes, they need to be in the same order as well
        if (renderable.meshPart.mesh.getVertexAttributes().hashCode() != vertexAttributesHash)
            return false;
        if (renderable.material.getMask() != materialAttributesMask)
            return false;
        if(environmentMask == 0 && renderable.environment != null)
            return false;
        if (environmentMask != 0 && (renderable.environment == null || renderable.environment.getMask() != environmentMask))
            return false;
        if(hasShadowMap && (renderable.environment == null || renderable.environment.shadowMap == null))
            return false;

        if(renderable.meshPart.primitiveType != primitiveType)
            return false;
        boolean renderableBlended = renderable.material.has(BlendingAttribute.Type)
            && ((BlendingAttribute)renderable.material.get(BlendingAttribute.Type)).blended;
        if(blended != renderableBlended)
            return false;

        return true;
    }

    private final Attributes combinedAttributes = new Attributes();


    public void render (Renderable renderable) {
        if (renderable.worldTransform.det3x3() == 0) return;
        combinedAttributes.clear();
        if (renderable.environment != null) combinedAttributes.set(renderable.environment);
        if (renderable.material != null) combinedAttributes.set(renderable.material);
        render(renderable, combinedAttributes);
    }

    private Renderable prevRenderable;
    private int prevMaterialHash;
    private int firstInstance;
    private int instanceCount;

    public void render (Renderable renderable, Attributes attributes) {
        if(instanceIndex > config.maxInstances) {
            Gdx.app.error("WebGPUModelBatch", "Too many instances, max is " + config.maxInstances);
            return;
        }

        // renderable-specific data

        // add instance data to instance buffer (instance transform)
        int offset = instanceIndex * 16 * Float.BYTES;
        instanceBuffer.set(offset,  renderable.worldTransform);
        // todo normal matrix per instance


        int materialHash = renderable.material.hashCode();

        if( prevRenderable != null && materialHash == prevMaterialHash && renderable.meshPart.equals(prevRenderable.meshPart)){
            // renderable is similar to the previous one, add to an instance batch
            // note that renderables get a copy of a mesh part not a reference to the Model's mesh part, so you can just compare references.
            instanceCount++;
        } else {    // either a new material or a new mesh part, we need to flush the run of instances

            if(prevRenderable != null) {
                applyMaterial(prevRenderable.material);
                renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
            }
            instanceCount = 1;
            firstInstance = numRenderables;
            prevRenderable = renderable;
            prevMaterialHash = materialHash;
        }
        numRenderables++;
        instanceIndex++;
    }

    // to combine instances in single draw call if they have same mesh part
    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables){
        //System.out.println("numInstances: "+numInstances);
        final WgMesh mesh = (WgMesh) meshPart.mesh;
        // use an instance offset to find the right modelMatrix in the instanceBuffer
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        // we can't use the following statement, because meshPart was unmodified and doesn't know about WebGPUMesh
        // and we're not using WebGPUMeshPart because then we need to modify Renderable.
        //renderable.meshPart.render(renderPass);
        drawCalls++;
    }

    public void end(){
        if(prevRenderable != null) {
            applyMaterial(prevRenderable.material);
            renderBatch(prevRenderable.meshPart, instanceCount, firstInstance);
        }
        instanceBuffer.flush();
    }

    private void applyMaterial(Material material){
        if(numMaterials >= config.maxMaterials)
            throw new RuntimeException("Too many materials (> "+config.maxMaterials+"). Increase shader.maxMaterials");

        // move to new buffer offset for this new material (flushes previous one).
        materialBuffer.setDynamicOffsetIndex(numMaterials); // indicate which section of the uniform buffer to use

        // diffuse color
        ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
        binder.setUniform("diffuseColor", diffuse == null ? Color.WHITE : diffuse.color);

        final FloatAttribute shiny = material.get(FloatAttribute.class,FloatAttribute.Shininess);
        binder.setUniform("shininess",  shiny == null ? 20 : shiny.value );


        // diffuse texture
        WgTexture diffuseTexture;
        if(material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            diffuseTexture = (WgTexture)tex;
            diffuseTexture.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
            diffuseTexture.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);
        } else {
            diffuseTexture = defaultTexture;
        }


        //System.out.println("binding diffuse texture: "+diffuseTexture.getLabel()+diffuseTexture.getUWrap()+diffuseTexture.getVWrap());


        binder.setTexture("diffuseTexture", diffuseTexture.getTextureView());
        binder.setSampler("diffuseSampler", diffuseTexture.getSampler());

        // normal texture
        WgTexture normalTexture;
        if(material.has(TextureAttribute.Normal)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Normal);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            normalTexture = (WgTexture)tex;
        } else {
            normalTexture = defaultNormalTexture;   // green texture, ie. Y = 1
        }
        binder.setTexture("normalTexture", normalTexture.getTextureView());
        binder.setSampler("normalSampler", normalTexture.getSampler());

        // metallic roughness texture
        WgTexture metallicRoughnessTexture;
        if(material.has(PBRTextureAttribute.MetallicRoughness)) {
            TextureAttribute ta = (TextureAttribute) material.get(PBRTextureAttribute.MetallicRoughness);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            metallicRoughnessTexture = (WgTexture)tex;
        } else {
            metallicRoughnessTexture = defaultTexture;  // suitable?
        }
        binder.setTexture("metallicRoughnessTexture", metallicRoughnessTexture.getTextureView());
        binder.setSampler("metallicRoughnessSampler", metallicRoughnessTexture.getSampler());

        // metallic roughness texture
        WgTexture emissiveTexture;
        if(material.has(TextureAttribute.Emissive)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Emissive);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            emissiveTexture = (WgTexture)tex;
        } else {
            emissiveTexture = defaultBlackTexture;
        }
        binder.setTexture("emissiveTexture", emissiveTexture.getTextureView());
        binder.setSampler("emissiveSampler", emissiveTexture.getSampler());

        materialBuffer.flush(); // write to GPU
        binder.bindGroup(renderPass, 1, numMaterials*materialBuffer.getUniformStride());

        numMaterials++;
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize, boolean hasShadowMap, boolean hasCubeMap) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform, uniformBufferSize, false);
        if(hasShadowMap) {
            layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Depth, WGPUTextureViewDimension._2D, false);
            layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Comparison);
        }
        if(hasCubeMap) {
            layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube, false);
            layout.addSampler(4, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        }
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createMaterialBindGroupLayout(int materialStride) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (material)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform, materialStride, true);
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
        layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(4, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
        layout.addTexture(5, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(6, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
        layout.addTexture(7, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(8, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createInstancingBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch Binding Group Layout (instance)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex , WGPUBufferBindingType.ReadOnlyStorage, 16 *Float.BYTES*config.maxInstances, false);
        layout.end();
        return layout;
    }


    // todo vertex attributes are hardcoded, should use conditional compilation.



    private String getDefaultShaderSource() {
        if(defaultShader == null){
            defaultShader = Gdx.files.classpath("shaders/modelbatch.wgsl").readString();
        }
        return defaultShader;

    }

    @Override
    public void dispose() {
        binder.dispose();
        defaultTexture.dispose();
        instanceBuffer.dispose();
        uniformBuffer.dispose();
    }

    /** place lighting information in frame uniform buffer:
     * ambient light, directional lights, point lights.
     */
    private void bindLights( Environment lights ){
        if(lights == null)
            return;
        final DirectionalLightsAttribute dla = lights.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
        final Array<DirectionalLight> dirs = dla == null ? null : dla.lights;
        final PointLightsAttribute pla = lights.get(PointLightsAttribute.class, PointLightsAttribute.Type);
        final Array<PointLight> points = pla == null ? null : pla.lights;

        if(dirs != null){
            if( dirs.size > config.maxDirectionalLights)
                throw new RuntimeException("Too many directional lights");

            for(int i = 0; i < dirs.size; i++) {
                directionalLights[i].color.set(dirs.get(i).color);
                directionalLights[i].direction.set(dirs.get(i).direction);
            }
        }

        numDirectionalLights = dirs == null ? 0 : dirs.size;
        for(int i = 0; i < numDirectionalLights; i++) {
            // todo probably not so great for memory use to concatenate strings like this
            binder.setUniform("dirLight["+i+"].color", directionalLights[i].color);
            binder.setUniform("dirLight["+i+"].direction", directionalLights[i].direction);
        }
        binder.setUniform("numDirectionalLights", numDirectionalLights);

        if(points != null){
            if( points.size > config.maxPointLights)
                throw new RuntimeException("Too many point lights");
            // is it useful to copy from attributes to a local array?
            for(int i = 0; i < points.size; i++) {
                pointLights[i].color.set(points.get(i).color);
                pointLights[i].position.set(points.get(i).position);
                pointLights[i].intensity = points.get(i).intensity;
            }
        }

        numPointLights = points == null ? 0 : points.size;
        for(int i = 0; i < numPointLights; i++) {
            binder.setUniform("pointLight["+i+"].color", pointLights[i].color);
            binder.setUniform("pointLight["+i+"].position", pointLights[i].position);
            binder.setUniform("pointLight["+i+"].intensity", pointLights[i].intensity);
        }
        binder.setUniform("numPointLights", numPointLights);

        final ColorAttribute ambient = lights.get(ColorAttribute.class,ColorAttribute.AmbientLight);
        if(ambient != null)
            binder.setUniform("ambientLight", ambient.color);

        final ColorAttribute fog = lights.get(ColorAttribute.class,ColorAttribute.Fog);
        if(fog != null)
            binder.setUniform("fogColor", fog.color);

        if ( lights.shadowMap != null) {
            binder.setUniform("shadowProjViewTransform", lights.shadowMap.getProjViewTrans());

            WgTexture shadowMap = (WgTexture)(lights.shadowMap.getDepthMap().texture);
            binder.setTexture("shadowMap", shadowMap.getTextureView());
            binder.setSampler("shadowSampler", shadowMap.getDepthSampler());

        }

        final WgCubemapAttribute cubemapAttribute = lights.get(WgCubemapAttribute.class, WgCubemapAttribute.EnvironmentMap);
        if ( cubemapAttribute != null) {
            //System.out.println("Setting cube map via binder");
            WgTexture cubeMap = cubemapAttribute.textureDescription.texture;
            binder.setTexture("cubeMap", cubeMap.getTextureView());
            binder.setSampler("cubeSampler", cubeMap.getSampler());
        }
    }


}

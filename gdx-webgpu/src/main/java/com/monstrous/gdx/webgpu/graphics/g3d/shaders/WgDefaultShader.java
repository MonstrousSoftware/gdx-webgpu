package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRTextureAttribute;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;


import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/** Default shader to render renderables  */
public class WgDefaultShader implements Shader {

    private final Config config;
    private static String defaultShader;
    private final WgTexture defaultTexture;
    private final WgTexture defaultNormalTexture;
    private final WgTexture defaultBlackTexture;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer materialBuffer;
    private final int materialSize;
    public int numMaterials;
    private final WebGPUPipeline pipeline;            // a shader has one pipeline
    public int numRenderables;
    public int drawCalls;
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;
    private final Matrix4 combined;
    private final Matrix4 projection;
    private final Matrix4 shiftDepthMatrix;

    protected int numDirectionalLights;
    protected DirectionalLight[] directionalLights;
    protected int numPointLights;
    protected PointLight[] pointLights;


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

        // fallback texture
        Pixmap pixmap = new Pixmap(1,1,RGBA8888);
        pixmap.setColor(Color.PINK);
        pixmap.fill();
        defaultTexture = new WgTexture(pixmap);
        pixmap.setColor(Color.GREEN);
        pixmap.fill();
        defaultNormalTexture = new WgTexture(pixmap);
        pixmap.setColor(Color.BLACK);
        pixmap.fill();
        defaultBlackTexture = new WgTexture(pixmap);

        // Create uniform buffer for global (per-frame) uniforms, e.g. projection matrix, camera position, etc.
        uniformBufferSize = (16 + 4 + 4 +4
                +8*config.maxDirectionalLights
                +12*config.maxPointLights)* Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform);


        materialSize = 20*Float.BYTES;      // data size per material (should be enough for now)
        // buffer for uniforms per material, e.g. color, shininess, ...
        // this does not include textures

        // allocate a uniform buffer with dynamic offsets
        materialBuffer = new WebGPUUniformBuffer(materialSize, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform,  config.maxMaterials);

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));
        binder.defineGroup(1, createMaterialBindGroupLayout(materialSize));
        binder.defineGroup(2, createInstancingBindGroupLayout());
        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
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
        binder.defineUniform("numDirectionalLights", 0, 0, offset); offset += 4;
        binder.defineUniform("numPointLights", 0, 0, offset); offset += 4;




        // note: put shorter uniforms last for padding reasons

        System.out.println("offset:"+offset+" "+uniformBufferSize);
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
        instanceBuffer = new WebGPUUniformBuffer(instanceSize*config.maxInstances, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Storage);

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, (long) instanceSize *config.maxInstances);


        // get pipeline layout which aggregates all the bind group layouts
        WebGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("ModelBatch pipeline layout");

        //pipelines = new PipelineCache();    // use static cache?

        // vertexAttributes will be set from the renderable
        vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification(vertexAttributes, getDefaultShaderSource());
        pipelineSpec.name = "ModelBatch pipeline";

        // default blending values
        pipelineSpec.enableBlending();
        pipelineSpec.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        pipelineSpec.environment = renderable.environment;
        if(renderable.meshPart.primitiveType == GL20.GL_LINES)  // todo all cases
            pipelineSpec.topology = WGPUPrimitiveTopology.LineList;

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
        binder.setUniform("cameraPosition", camera.position);
        uniformBuffer.flush();

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 2);

        // todo: different shaders may overwrite lighting uniforms if renderables have other environments ...
        bindLights(renderable.environment);

        numRenderables = 0;
        numMaterials = 0;
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

    public void render (Renderable renderable, Attributes attributes) {
        if(numRenderables > config.maxInstances) {
            Gdx.app.error("WebGPUModelBatch", "Too many instances, max is " + config.maxInstances);
            return;
        }

        // renderable-specific data

        // add instance data to instance buffer (instance transform)
        int offset = numRenderables * 16 * Float.BYTES;
        instanceBuffer.set(offset,  renderable.worldTransform);
        // todo normal matrix per instance

        boolean newMaterial = false;
        int hash = renderable.material.attributesHash();
        if(prevRenderable == null || hash != prevRenderable.material.attributesHash())
            newMaterial = true;

        if(!newMaterial && prevRenderable != null && renderable.meshPart.equals(prevRenderable.meshPart)){
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
        }


        numRenderables++;
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

        // is this material the same as the previous? then we are done.
//        if(prevMaterial != null && material.attributesHash() == prevMaterial.attributesHash())  // store hash instead of prev mat?
//            return;
//        int hash = material.attributesHash();

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
        } else {
            diffuseTexture = defaultTexture;
        }
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

        //prevMaterial = material;
    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex|WGPUShaderStage.Fragment, WGPUBufferBindingType.Uniform, uniformBufferSize, false);
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createMaterialBindGroupLayout(int materialStride) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (material)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex|WGPUShaderStage.Fragment, WGPUBufferBindingType.Uniform, materialStride, true);
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
        layout.addBuffer(0, WGPUShaderStage.Vertex , WGPUBufferBindingType.ReadOnlyStorage, 16L *Float.BYTES*config.maxInstances, false);
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
    private void bindLights( Environment lights){
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
    }
}

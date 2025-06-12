package com.badlogic.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.webgpu.graphics.Binder;
import com.badlogic.gdx.webgpu.graphics.WebGPUMesh;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.webgpu.webgpu.*;
import com.badlogic.gdx.webgpu.wrappers.*;


import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

public class WebGPUDefaultShader implements Shader {

    private final Config config;
    private static String defaultShader;
    private final WebGPUTexture defaultTexture;
    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final int uniformBufferSize;
    private final WebGPUUniformBuffer instanceBuffer;
    private final WebGPUUniformBuffer materialBuffer;
    private final int materialSize;
    private final int materialBufferSize;
    public int numMaterials;
    private final WebGPUPipeline pipeline;            // a shader has one pipeline
    public int numRenderables;
    public int drawCalls;
    private WebGPURenderPass renderPass;
    private final VertexAttributes vertexAttributes;

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
            this.maxMaterials = 128;
            this.maxDirectionalLights = 3;  // todo hard coded in shader, don't change
            this.maxPointLights = 3;  // todo hard coded in shader, don't change
        }
    }

    public WebGPUDefaultShader(final Renderable renderable) {
        this(renderable, new Config());
    }

    public WebGPUDefaultShader(final Renderable renderable, Config config) {
        this.config = config;

        // fallback texture
        Pixmap pixmap = new Pixmap(1,1,RGBA8888);
        pixmap.setColor(Color.PINK);
        pixmap.fill();
        defaultTexture = new WebGPUTexture(pixmap);

        // Create uniform buffer for the projection matrix
        uniformBufferSize = (16 + 4 + 4 +4
                +8*config.maxDirectionalLights
                +12*config.maxPointLights)* Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform);


        materialSize = 256; //4*Float.BYTES;      // data size per material
        // buffer for uniforms per material, e.g. color
        // this does not include textures

        materialBufferSize = materialSize * config.maxMaterials;
        materialBuffer = new WebGPUUniformBuffer(materialBufferSize, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform);

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize));
        binder.defineGroup(1, createMaterialBindGroupLayout(materialSize));
        binder.defineGroup(2, createInstancingBindGroupLayout());
        // define bindings in the groups
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("materialUniforms", 1, 0);
        binder.defineBinding("diffuseTexture", 1, 1);
        binder.defineBinding("diffuseSampler", 1, 2);
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

        int instanceSize = 16*Float.BYTES;      // data size per instance

        // for now we use a uniform buffer, but we organize data as an array of modelMatrix
        // and write with an dynamic offset (numRenderables * sizeof matrix4)
        instanceBuffer = new WebGPUUniformBuffer(instanceSize*config.maxInstances, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Storage);

        binder.setBuffer("instanceUniforms", instanceBuffer, 0, (long) instanceSize *config.maxInstances);



        binder.setBuffer("materialUniforms", materialBuffer, 0,  materialSize);




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
        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);

        directionalLights = new DirectionalLight[config.maxDirectionalLights];
        for(int i = 0; i <config.maxDirectionalLights; i++)
            directionalLights[i] = new DirectionalLight();
        pointLights = new PointLight[config.maxPointLights];
        for(int i = 0; i <config.maxPointLights; i++)
            pointLights[i] = new PointLight();
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
        binder.setUniform("projectionViewTransform", camera.combined);
        binder.setUniform("cameraPosition", camera.position);

        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);

        // idem for group 2 (instances), we will fill in the buffer as we go
        binder.bindGroup(renderPass, 2);

        // todo: different shaders may overwrite lighting uniforms if renderables have other environments ...
        bindLights(renderable.environment);

        numRenderables = 0;
        numMaterials = 0;
        prevMeshPart = null;
        drawCalls = 0;
        prevMaterial = null;

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

    private MeshPart prevMeshPart;
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

        // apply renderable's material
        applyMaterial(renderable.material);


        final MeshPart meshPart = renderable.meshPart;
        if (!(meshPart.mesh instanceof WebGPUMesh))
            throw new RuntimeException("WebGPUMeshPart supports only WebGPUMesh");
        if(prevMeshPart != null && meshPart.id.contentEquals(prevMeshPart.id)){     // todo: should compare mesh parts not just id which is even optional
            // note that renderables get a copy of a mesh part not a reference to the Model's mesh part, so you can just compare references.
            instanceCount++;
        } else {
            if(prevMeshPart != null)
                renderBatch(prevMeshPart, instanceCount, firstInstance);
            instanceCount = 1;
            firstInstance = numRenderables;
            prevMeshPart = meshPart;
        }
        numRenderables++;
    }

    // to combine instances in single draw call if they have same mesh part
    private void renderBatch(MeshPart meshPart, int numInstances, int numRenderables){
        //System.out.println("numInstances: "+numInstances);
        final WebGPUMesh mesh = (WebGPUMesh) meshPart.mesh;
        // use an instance offset to find the right modelMatrix in the instanceBuffer
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, numInstances, numRenderables);
        // we can't use the following statement, because meshPart was unmodified and doesn't know about WebGPUMesh
        // and we're not using WebGPUMeshPart because then we need to modify Renderable.
        //renderable.meshPart.render(renderPass);
        drawCalls++;
    }

    public void end(){
        if(prevMeshPart != null)
            renderBatch(prevMeshPart, instanceCount, firstInstance);
        instanceBuffer.flush();
    }

    private Material prevMaterial;

    private void applyMaterial(Material material){

        // is this material the same as the previous? then we are done.
        if(prevMaterial != null && material.attributesHash() == prevMaterial.attributesHash())  // store hash instead of prev mat?
            return;

        if(numMaterials >= config.maxMaterials)
            throw new RuntimeException("Too many materials (> "+config.maxMaterials+"). Increase shader.maxMaterials");

        // diffuse color
        ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
        binder.setUniform("diffuseColor", diffuse == null ? Color.WHITE : diffuse.color, numMaterials*materialSize);

        final FloatAttribute shiny = material.get(FloatAttribute.class,FloatAttribute.Shininess);
        binder.setUniform("shininess",  shiny == null ? 20 : shiny.value,numMaterials*materialSize );


        // diffuse texture
        WebGPUTexture diffuseTexture;
        if(material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            diffuseTexture = (WebGPUTexture)tex;
        } else {
            diffuseTexture = defaultTexture;
        }

        binder.setTexture("diffuseTexture", diffuseTexture.getTextureView());
        binder.setSampler("diffuseSampler", diffuseTexture.getSampler());

        binder.bindGroup(renderPass, 1, numMaterials*materialSize);

        numMaterials++;

        prevMaterial = material;

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

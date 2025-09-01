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

package com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.*;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgShader;
import com.monstrous.gdx.webgpu.wrappers.*;


/** Simple shader for IBL generation.
 * Is used to generate textures for a cube map, such as an environment map, an irradiance map or a radiance map.
 * Supports only diffuse texture for materials and ignores environment.
 * */

public class IBLShader extends WgShader implements Disposable {

    public final Binder binder;
    private final WebGPUUniformBuffer uniformBuffer;
    private final WebGPUPipeline pipeline;
    private WebGPURenderPass renderPass;
    private int numRoughnessLevels = 5;
    private Vector3 tmpVec = new Vector3();

    public static class Config {
        public String shaderSource;

        public Config(String shaderSource) {
            this.shaderSource = shaderSource;
        }
    }

    public IBLShader(final Renderable renderable, Config config) {


        boolean hasCubeMap = renderable.environment != null && renderable.environment.has(WgCubemapAttribute.EnvironmentMap);

        // Group 0
        // binding 0 : uniform buffer (projectionView matrix, sunColor, sunDirection and numRoughnessLevels)
        // binding 3 : cube map
        // binding 4 : cube map sampler
        //
        // Group 1
        // binding 1 : texture view
        // binding 2 : texture sampler

        // Create uniform buffer for global (per-frame) uniforms, i.e. projectionView matrix and numRoughnessLevels
        final int uniformBufferSize = (16 + 4 + 4 + 4)* Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));


        binder = new Binder();
        // define groups
        binder.defineGroup(0, createFrameBindGroupLayout(uniformBufferSize, hasCubeMap));
        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset); offset += 16*4;
        binder.defineUniform("sunColor", 0, 0, offset); offset += 4*4;
        binder.defineUniform("sunDirection", 0, 0, offset); offset += 4*4;
        binder.defineUniform("numRoughnessLevels", 0, 0, offset); offset += 4;


        if (hasCubeMap) {
            binder.defineBinding("cubeMap", 0, 3);
            binder.defineBinding("cubeSampler", 0, 4);
        }

        if (renderable.material.has(TextureAttribute.Diffuse)) {
            binder.defineGroup(1, createMaterialBindGroupLayout());
            binder.defineBinding("diffuseTexture", 1, 1);
            binder.defineBinding("diffuseSampler", 1, 2);
        }


        // get pipeline layout which aggregates all the bind group layouts
        WGPUPipelineLayout pipelineLayout = binder.getPipelineLayout("IBL Gen pipeline layout");

        // vertexAttributes will be set from the renderable
        VertexAttributes vertexAttributes = renderable.meshPart.mesh.getVertexAttributes();
        PipelineSpecification pipelineSpec = new PipelineSpecification(vertexAttributes, config.shaderSource);
        pipelineSpec.name = "IBL Gen pipeline";
        pipelineSpec.disableBlending();
        pipelineSpec.cullMode = WGPUCullMode.None;
        pipelineSpec.environment = renderable.environment;
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        pipelineSpec.colorFormat = webgpu.surfaceFormat; //WGPUTextureFormat.BGRA8Unorm;

        pipeline = new WebGPUPipeline(pipelineLayout, pipelineSpec);
    }


    @Override
    public void init() {

    }

    @Override
    public void begin(Camera camera, RenderContext context) {
        throw new IllegalArgumentException("Use begin(Camera, WebGPURenderPass)");
    }

    public void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass){
        this.renderPass = renderPass;

        binder.setUniform("projectionViewTransform", camera.combined);
        binder.setUniform("numRoughnessLevels", numRoughnessLevels);
        uniformBuffer.flush();

        if(renderable.environment != null) {
            final WgCubemapAttribute cubemapAttribute = renderable.environment.get(WgCubemapAttribute.class, WgCubemapAttribute.EnvironmentMap);
            if (cubemapAttribute != null) {
                //System.out.println("Setting cube map via binder");
                WgTexture cubeMap = cubemapAttribute.textureDescription.texture;
                binder.setTexture("cubeMap", cubeMap.getTextureView());
                binder.setSampler("cubeSampler", cubeMap.getSampler());
            }

            final DirectionalLightsAttribute dla = renderable.environment.get(DirectionalLightsAttribute.class, DirectionalLightsAttribute.Type);
            final Array<DirectionalLight> dirs = dla == null ? null : dla.lights;
            int numDirectionalLights = dirs == null ? 0 : dirs.size;
            if(numDirectionalLights > 0) {  // we actually only look at the first directional light
                binder.setUniform("sunColor",  dirs.get(0).color);
                // change from light direction to sun vector
                tmpVec.set(dirs.get(0).direction).scl(-1);
                binder.setUniform("sunDirection",  tmpVec);
            }
        }


        // bind group 0 (frame) once per frame
        binder.bindGroup(renderPass, 0);
        renderPass.setPipeline(pipeline);
    }

    @Override
    public int compareTo(Shader other) {
        return 0;
    }

    @Override
    public boolean canRender(Renderable renderable) {
        return true;
    }

    @Override
    public void render (Renderable renderable) {
        if (renderable.worldTransform.det3x3() == 0) return;

        applyMaterial(renderable.material);
        final WgMesh mesh = (WgMesh) renderable.meshPart.mesh;
        final MeshPart meshPart = renderable.meshPart;
        mesh.render(renderPass, meshPart.primitiveType, meshPart.offset, meshPart.size, 1, 0);
    }

    @Override
    public void render(Renderable renderable, Attributes attributes) {
        render(renderable);
    }

    @Override
    public void end(){
    }

    private void applyMaterial(Material material){
        // diffuse texture
        WgTexture diffuseTexture;
        if(material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            diffuseTexture = (WgTexture)tex;
            diffuseTexture.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
            diffuseTexture.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);

            binder.setTexture("diffuseTexture", diffuseTexture.getTextureView());
            binder.setSampler("diffuseSampler", diffuseTexture.getSampler());
            binder.bindGroup(renderPass, 1);
        }


    }

    private WebGPUBindGroupLayout createFrameBindGroupLayout(int uniformBufferSize, boolean hasCubeMap) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform, uniformBufferSize, false);
        if(hasCubeMap) {
            layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube, false);
            layout.addSampler(4, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        }
        layout.end();
        return layout;
    }

    private WebGPUBindGroupLayout createMaterialBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (material)");
        layout.begin();
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering );
        layout.end();
        return layout;
    }


    @Override
    public void dispose() {
        binder.dispose();
        uniformBuffer.dispose();
    }

}

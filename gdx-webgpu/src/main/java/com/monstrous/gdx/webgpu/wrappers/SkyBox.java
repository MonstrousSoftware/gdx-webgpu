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

package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;

import java.util.HashMap;
import java.util.Map;

import static com.badlogic.gdx.math.Matrix4.M33;

/**
 * SkyBox Following the approach from https://webgpufundamentals.org/webgpu/lessons/webgpu-skybox.html This uses a
 * dedicated shader that renders one screen filling triangle using a cube map. The camera projection view matrix is
 * inverted and use to look up screen pixels in the cube map. The sky box should be rendered after all opaque
 * renderables.
 *
 */

public class SkyBox implements Disposable {

    protected final int FRAME_UB_SIZE = 16 * Float.BYTES; // to hold one 4x4 matrix

    protected final WgCubemap cubeMap;
    protected final WebGPUUniformBuffer uniformBuffer;
    protected final WebGPUBindGroupLayout bindGroupLayout;
    protected final WebGPUBindGroup bindGroup;
    protected final WebGPUPipelineLayout pipelineLayout;

    /** Cache of pipelines keyed by "format_samples" to support rendering to different targets (screen, FBO, MRT). */
    protected final Map<String, WebGPUPipeline> pipelineCache = new HashMap<>();

    protected final String shaderSource;
    protected final Matrix4 invertedProjectionView;

    /** Construct a skybox from a cube map */
    public SkyBox(WgCubemap cubeMap) {
        this.cubeMap = cubeMap;
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        uniformBuffer = new WebGPUUniformBuffer(FRAME_UB_SIZE, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        bindGroupLayout = createBindGroupLayout();

        pipelineLayout = new WebGPUPipelineLayout("SkyBox Pipeline Layout", bindGroupLayout);

        shaderSource = Gdx.files.internal("shaders/skybox.wgsl").readString();

        bindGroup = makeBindGroup(bindGroupLayout, uniformBuffer);

        invertedProjectionView = new Matrix4();

        // Pre-create pipeline for the current surface format/sample count
        getOrCreatePipeline(webgpu.getSurfaceFormat(), webgpu.getSamples());
    }

    /**
     * Get or create a pipeline for the given color format and sample count.
     * Pipelines are cached so each configuration is only compiled once.
     */
    protected WebGPUPipeline getOrCreatePipeline(WGPUTextureFormat colorFormat, int numSamples) {
        String key = colorFormat.name() + "_" + numSamples;
        WebGPUPipeline cached = pipelineCache.get(key);
        if (cached != null) return cached;

        PipelineSpecification pipelineSpec = new PipelineSpecification();
        pipelineSpec.name = "skybox pipeline";
        pipelineSpec.vertexAttributes = null;
        pipelineSpec.environment = null;
        pipelineSpec.shader = null;
        pipelineSpec.shaderSource = shaderSource;
        pipelineSpec.enableDepthTest();
        pipelineSpec.setCullMode(WGPUCullMode.Back);
        pipelineSpec.colorFormats = new WGPUTextureFormat[] { colorFormat };
        pipelineSpec.depthFormat = WGPUTextureFormat.Depth24Plus;
        pipelineSpec.numSamples = numSamples;
        pipelineSpec.isSkyBox = true;

        WebGPUPipeline pipeline = new WebGPUPipeline(pipelineLayout.getLayout(), pipelineSpec);
        pipelineCache.put(key, pipeline);
        return pipeline;
    }

    /**
     * execute a render pass to show the sky box. Use this at the end of the 3d scene.
     *
     * @param cam camera
     */
    public void renderPass(Camera cam) {
        renderPass(cam, false);
    }

    /**
     * execute a render pass to show the sky box.
     * Automatically adapts to the current render target (screen or FBO) by selecting the matching pipeline.
     * Always renders to the first color attachment only, so it is safe to call inside an MRT FBO.
     *
     * @param cam camera
     * @param clearDepth true to clear the depth buffer
     */
    public void renderPass(Camera cam, boolean clearDepth) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        // Resolve the pipeline that matches the current render target's first color format + sample count
        WGPUTextureFormat currentFormat = webgpu.surfaceFormats[0];
        int currentSamples = webgpu.getSamples();
        WebGPUPipeline pipeline = getOrCreatePipeline(currentFormat, currentSamples);

        // Create a render pass that only writes to the first color target.
        // This makes the skybox safe to use inside MRT FBOs (which may have extra color targets).
        WebGPURenderPass pass = RenderPassBuilder.createFirstTargetOnly("skybox", clearDepth, currentSamples);
        renderWithPipeline(cam, pass, pipeline);
        pass.end();
    }

    /** Render skybox within an existing render pass using the pre-created pipeline. */
    public void render(Camera camera, WebGPURenderPass pass) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        WebGPUPipeline pipeline = getOrCreatePipeline(webgpu.surfaceFormats[0], webgpu.getSamples());
        renderWithPipeline(camera, pass, pipeline);
    }

    /** Internal: write uniforms and issue draw call with the given pipeline. */
    protected void renderWithPipeline(Camera camera, WebGPURenderPass pass, WebGPUPipeline pipeline) {
        writeUniforms(uniformBuffer, camera);
        pass.setPipeline(pipeline.getPipeline());
        pass.setBindGroup(0, bindGroup.getBindGroup());
        pass.draw(3); // one triangle covering the full screen
    }

    @Override
    public void dispose() {
        for (WebGPUPipeline p : pipelineCache.values()) {
            p.dispose();
        }
        pipelineCache.clear();
        pipelineLayout.dispose();
        bindGroup.dispose();
        bindGroupLayout.dispose();
        uniformBuffer.dispose();
    }

    // Bind Group Layout:
    // 0 uniforms,
    // 1 cube map texture,
    // 2 sampler

    protected WebGPUBindGroupLayout createBindGroupLayout() {

        // Define binding layout
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Fragment, WGPUBufferBindingType.Uniform, FRAME_UB_SIZE, false); // uniform
                                                                                                            // buffer
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension.Cube,
                false); // cube map texture
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering); // cube map sampler
        layout.end();
        return layout;
    }

    // bind group
    protected WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUBuffer uniformBuffer) {
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, uniformBuffer);
        bg.setTexture(1, cubeMap.getTextureView());
        bg.setSampler(2, cubeMap.getSampler());
        bg.end();
        return bg;
    }

    protected void writeUniforms(WebGPUUniformBuffer uniformBuffer, Camera camera) {
        invertedProjectionView.set(camera.combined);
        invertedProjectionView.setTranslation(Vector3.Zero);
        invertedProjectionView.val[M33] = 1.0f;

        try {
            invertedProjectionView.inv();
        } catch (RuntimeException e) { // don't crash on non-invertible matrix, just skip the update
            Gdx.app.error("Skybox", "camera matrix not invertible");
            return;
        }

        uniformBuffer.set(0, invertedProjectionView);
        uniformBuffer.flush();
    }
}

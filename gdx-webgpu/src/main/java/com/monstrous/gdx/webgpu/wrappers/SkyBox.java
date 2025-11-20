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

    protected final WebGPUPipeline pipeline;
    protected final Matrix4 invertedProjectionView;

    /** Construct a skybox from a cube map */
    public SkyBox(WgCubemap cubeMap) {
        this.cubeMap = cubeMap;
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        uniformBuffer = new WebGPUUniformBuffer(FRAME_UB_SIZE, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));

        bindGroupLayout = createBindGroupLayout();

        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("SkyBox Pipeline Layout", bindGroupLayout);

        PipelineSpecification pipelineSpec = new PipelineSpecification();
        pipelineSpec.name = "skybox pipeline";
        pipelineSpec.vertexAttributes = null;
        pipelineSpec.environment = null;
        pipelineSpec.shader = null;
        pipelineSpec.shaderSource = Gdx.files.internal("shaders/skybox.wgsl").readString();
        pipelineSpec.enableDepthTest();
        pipelineSpec.setCullMode(WGPUCullMode.Back);
        pipelineSpec.colorFormat = webgpu.getSurfaceFormat();
        pipelineSpec.depthFormat = WGPUTextureFormat.Depth24Plus;
        pipelineSpec.numSamples = webgpu.getSamples();
        pipelineSpec.isSkyBox = true;

        pipeline = new WebGPUPipeline(pipelineLayout.getLayout(), pipelineSpec);

        bindGroup = makeBindGroup(bindGroupLayout, uniformBuffer);

        invertedProjectionView = new Matrix4();
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
     *
     * @param cam camera
     * @param clearDepth true to clear the depth buffer
     */
    public void renderPass(Camera cam, boolean clearDepth) {
        WebGPURenderPass pass = RenderPassBuilder.create("skybox", null, clearDepth,
                ((WgGraphics) Gdx.graphics).getContext().getSamples());
        render(cam, pass);
        pass.end();
    }

    /** Render skybox within a render pass. */
    public void render(Camera camera, WebGPURenderPass pass) {
        writeUniforms(uniformBuffer, camera);

        pass.setPipeline(pipeline.getPipeline());
        pass.setBindGroup(0, bindGroup.getBindGroup());
        pass.draw(3); // one triangle covering the full screen
    }

    @Override
    public void dispose() {
        pipeline.dispose();
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

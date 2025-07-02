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
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.webgpu.WGPUComputePipelineDescriptor;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import jnr.ffi.Pointer;

public class WebGPUComputePipeline implements Disposable {
    private final WebGPU_JNI webGPU;
    private final WgGraphics gfx;
    private final WebGPUContext webgpu;
    private final Pointer pipeline;


    public WebGPUComputePipeline(WgShaderProgram shader, String entryPoint, WebGPUPipelineLayout layout) {
        gfx = (WgGraphics) Gdx.graphics;
        webGPU = gfx.getWebGPU();
        webgpu = gfx.getContext();

        WGPUComputePipelineDescriptor pipelineDesc = WGPUComputePipelineDescriptor.createDirect();

        pipelineDesc.setNextInChain();

        pipelineDesc.getCompute().setModule(shader.getHandle());
        pipelineDesc.getCompute().setEntryPoint(entryPoint);
        pipelineDesc.getCompute().setConstantCount(0);
        pipelineDesc.getCompute().setConstants();

        pipelineDesc.setLayout(layout.getHandle());

        pipeline = webGPU.wgpuDeviceCreateComputePipeline(webgpu.device.getHandle(), pipelineDesc);

        if(pipeline == null)
            throw new RuntimeException("Compute pipeline creation failed");
    }


    public Pointer getHandle(){
        return pipeline;
    }

    @Override
    public void dispose() {
        webGPU.wgpuComputePipelineRelease(pipeline);
    }

}

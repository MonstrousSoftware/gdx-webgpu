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
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.github.xpenatan.webgpu.WGPUPipelineLayoutDescriptor;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupLayout;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;


public class WebGPUPipelineLayout implements Disposable {
    private final WGPUPipelineLayout layout;

    public WebGPUPipelineLayout(String label, WebGPUBindGroupLayout... bindGroupLayouts ) {
        WGPUVectorBindGroupLayout layouts = WGPUVectorBindGroupLayout.obtain();
        for (int i = 0; i < bindGroupLayouts.length; i++)
            layouts.push_back( bindGroupLayouts[i].getLayout());

        WGPUPipelineLayoutDescriptor pipelineLayoutDesc = WGPUPipelineLayoutDescriptor.obtain();
        pipelineLayoutDesc.setNextInChain(null);
        pipelineLayoutDesc.setLabel(label);
        pipelineLayoutDesc.setBindGroupLayouts(layouts);
        layout = new WGPUPipelineLayout();
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        webgpu.device.createPipelineLayout(pipelineLayoutDesc, layout);
    }

    public WGPUPipelineLayout getLayout() {
        return layout;
    }

    @Override
    public void dispose() {
        layout.release();
        layout.dispose();
    }
}

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
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.ShaderPrefix;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

public class WebGPUPipeline implements Disposable {
    private final WgGraphics gfx = (WgGraphics) Gdx.graphics;
    private final WebGPU_JNI webGPU = gfx.getWebGPU();
    private final WebGPUContext webgpu = gfx.getContext();
    private final Pointer pipeline;
    public PipelineSpecification specification;
    private final WgShaderProgram shader;
    private final boolean ownsShader;

    public WebGPUPipeline(WebGPUPipelineLayout pipelineLayout, PipelineSpecification spec) {
        this(pipelineLayout == null ? null : pipelineLayout.getHandle(), spec);
    }

    public WebGPUPipeline(Pointer pipelineLayout, PipelineSpecification spec) {
        // if the specification does not already have a shader, create one from the source file, customized to the vertex attributes.
        if(spec.shader != null) {
            shader = spec.shader;   // make use of the shader in the spec
            ownsShader = false;     // we don't have to dispose it
        } else {
            String prefix = ShaderPrefix.buildPrefix(spec.vertexAttributes, spec.environment);
            //System.out.println("Compiling shader Source [] Prefix: ["+prefix+"]");
            shader = new WgShaderProgram(spec.name, spec.shaderSource, prefix);
            spec.shader = shader;
            spec.recalcHash();
            ownsShader = true;
        }
        this.specification = new PipelineSpecification(spec);

        Pointer shaderModule = shader.getHandle();
        WGPUVertexBufferLayout vertexBufferLayout = spec.vertexAttributes == null ? null : WebGPUVertexLayout.buildVertexBufferLayout(spec.vertexAttributes);

        WGPURenderPipelineDescriptor pipelineDesc = WGPURenderPipelineDescriptor.createDirect();        // todo worth reusing these?

        pipelineDesc.setNextInChain();
        pipelineDesc.setLabel(spec.name);

        pipelineDesc.getVertex().setBufferCount(vertexBufferLayout != null ? 1 : 0);
        pipelineDesc.getVertex().setBuffers(vertexBufferLayout);

//        WGPUConstantEntry entry = WGPUConstantEntry.createDirect();
//        entry.setKey("numDirLights");
//        entry.setValue(3f);

        pipelineDesc.getVertex().setModule(shaderModule);
        pipelineDesc.getVertex().setEntryPoint(spec.vertexShaderEntryPoint);
        pipelineDesc.getVertex().setConstantCount(0);
        pipelineDesc.getVertex().setConstants();

        pipelineDesc.getPrimitive().setTopology(spec.topology);
        pipelineDesc.getPrimitive().setStripIndexFormat(isStripTopology(spec.topology) ? spec.indexFormat : WGPUIndexFormat.Undefined);
        pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
        pipelineDesc.getPrimitive().setCullMode(spec.cullMode);

        if (spec.colorFormat != WGPUTextureFormat.Undefined) {   // if there is a color attachment
            WGPUFragmentState fragmentState = WGPUFragmentState.createDirect();
            fragmentState.setNextInChain();
            fragmentState.setModule(shaderModule);
            fragmentState.setEntryPoint(spec.fragmentShaderEntryPoint);
            fragmentState.setConstantCount(0);
            fragmentState.setConstants();

            // blend
            WGPUBlendState blendState = null;   // to disable blending
            if(spec.blendingEnabled) {
                blendState = WGPUBlendState.createDirect();
                blendState.getColor().setSrcFactor(spec.blendSrcColor);
                blendState.getColor().setDstFactor(spec.blendDstColor);
                blendState.getColor().setOperation(spec.blendOpColor);
                blendState.getAlpha().setSrcFactor(spec.blendSrcAlpha);
                blendState.getAlpha().setDstFactor(spec.blendDstAlpha);
                blendState.getAlpha().setOperation(spec.blendOpAlpha);
            }


            WGPUColorTargetState colorTarget = WGPUColorTargetState.createDirect();
            colorTarget.setFormat(spec.colorFormat);
            colorTarget.setBlend(blendState);
            colorTarget.setWriteMask(WGPUColorWriteMask.All);

            fragmentState.setTargetCount(1);
            fragmentState.setTargets(colorTarget);

            pipelineDesc.setFragment(fragmentState);
        }

        WGPUDepthStencilState depthStencilState = WGPUDepthStencilState.createDirect();
        setDefaultStencilState(depthStencilState);

        if (spec.isSkyBox) {
            depthStencilState.setDepthCompare(WGPUCompareFunction.LessEqual);// we are clearing to 1.0 and rendering at 1.0, i.e. at max distance
            depthStencilState.setDepthWriteEnabled(1L);
        } else {
            if (!spec.useDepthTest) {
                // disable depth testing
                depthStencilState.setDepthCompare(WGPUCompareFunction.Always);
                depthStencilState.setDepthWriteEnabled(0L);
            } else {
                if (spec.afterDepthPrepass) {   // rely on Z pre-pass
                    depthStencilState.setDepthCompare(WGPUCompareFunction.Equal);
                    depthStencilState.setDepthWriteEnabled(1L);
                } else {
                    // this is the usual depth compare: smaller values are closer, near plane has z = 0.0 and far plane has z = 1.0
                    // so render if the fragment z is less or equal than the depth buffer value
                    depthStencilState.setDepthCompare(WGPUCompareFunction.LessEqual);
                    depthStencilState.setDepthWriteEnabled(1L); // true
                }
            }
        }

        if (!spec.noDepthAttachment) {
            depthStencilState.setFormat(spec.depthFormat);
            // deactivate stencil
            depthStencilState.setStencilReadMask(0L);
            depthStencilState.setStencilWriteMask(0L);

            pipelineDesc.setDepthStencil(depthStencilState);
        }

        pipelineDesc.getMultisample().setCount(spec.numSamples);
        pipelineDesc.getMultisample().setMask(-1L);
        pipelineDesc.getMultisample().setAlphaToCoverageEnabled(0);

        pipelineDesc.setLayout(pipelineLayout);
        pipeline = webGPU.wgpuDeviceCreateRenderPipeline(webgpu.device.getHandle(), pipelineDesc);

        if(pipeline == null)
            throw new RuntimeException("Pipeline creation failed");
    }

    private boolean isStripTopology( WGPUPrimitiveTopology topology ){
        return topology == WGPUPrimitiveTopology.TriangleStrip || topology == WGPUPrimitiveTopology.LineStrip;
    }

    public boolean canRender(PipelineSpecification spec){    // perhaps we need more params
        // crude check, to be refined
        int h = spec.hashCode();
        int h2 = this.specification.hashCode();
        return h == h2;
    }

    public Pointer getHandle(){
        return pipeline;
    }

    @Override
    public void dispose() {
        webGPU.wgpuRenderPipelineRelease(pipeline);
        if(ownsShader)
            shader.dispose();
    }

    private void setDefaultStencilState(WGPUDepthStencilState  depthStencilState ) {
        depthStencilState.setFormat(WGPUTextureFormat.Undefined);
        depthStencilState.setDepthWriteEnabled(0L);
        depthStencilState.setDepthCompare(WGPUCompareFunction.Always);
        depthStencilState.setStencilReadMask(0xFFFFFFFF);
        depthStencilState.setStencilWriteMask(0xFFFFFFFF);
        depthStencilState.setDepthBias(0);
        depthStencilState.setDepthBiasSlopeScale(0);
        depthStencilState.setDepthBiasClamp(0);
        setDefaultStencilFaceState(depthStencilState.getStencilFront());
        setDefaultStencilFaceState(depthStencilState.getStencilBack());
    }

    private void setDefaultStencilFaceState(WGPUStencilFaceState stencilFaceState) {
        stencilFaceState.setCompare( WGPUCompareFunction.Always);
        stencilFaceState.setFailOp( WGPUStencilOperation.Keep);
        stencilFaceState.setDepthFailOp( WGPUStencilOperation.Keep);
        stencilFaceState.setPassOp( WGPUStencilOperation.Keep);
    }
}

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
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.ShaderPrefix;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;


public class WebGPUPipeline implements Disposable {
    private final WGPURenderPipeline pipeline;
    public PipelineSpecification specification;
    private final WgShaderProgram shader;
    private final boolean ownsShader;


    public WebGPUPipeline(WGPUPipelineLayout pipelineLayout, PipelineSpecification spec) {
        System.out.println("Creating pipeline");
        // if the specification does not already have a shader, create one from the source file, customized to the vertex attributes.
        if (spec.shader != null) {
            shader = spec.shader;   // make use of the shader in the spec
            ownsShader = false;     // we don't have to dispose it
        } else {
            String prefix = ShaderPrefix.buildPrefix(spec.vertexAttributes, spec.environment, spec.maxDirLights, spec.maxPointLights);
            //System.out.println("Compiling shader Source [] Prefix: ["+prefix+"]");
            shader = new WgShaderProgram(spec.name, spec.shaderSource, prefix);
            spec.shader = shader;
            ownsShader = true;
        }
        this.specification = new PipelineSpecification(spec);

        WGPUVertexBufferLayout vertexBufferLayout = spec.vertexAttributes == null ? null : WebGPUVertexLayout.buildVertexBufferLayout(spec.vertexAttributes);

        WGPURenderPipelineDescriptor pipelineDesc = WGPURenderPipelineDescriptor.obtain();
        pipelineDesc.setNextInChain(WGPUChainedStruct.NULL);
        pipelineDesc.setLabel(spec.name);

        WGPUVectorVertexBufferLayout bufferLayout = WGPUVectorVertexBufferLayout.obtain();
        if (vertexBufferLayout != null)
            bufferLayout.push_back(vertexBufferLayout);
        pipelineDesc.getVertex().setBuffers(bufferLayout);

        pipelineDesc.getVertex().setModule(shader.getShaderModule());
        pipelineDesc.getVertex().setEntryPoint(spec.vertexShaderEntryPoint);
        WGPUVectorConstantEntry constants = WGPUVectorConstantEntry.obtain();
        pipelineDesc.getVertex().setConstants(constants);

        pipelineDesc.getPrimitive().setTopology(spec.topology);
        pipelineDesc.getPrimitive().setStripIndexFormat(isStripTopology(spec.topology) ? spec.indexFormat : WGPUIndexFormat.Undefined);
        pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
        pipelineDesc.getPrimitive().setCullMode(spec.cullMode);

        if (spec.colorFormat != WGPUTextureFormat.Undefined) {   // if there is a color attachment
            WGPUFragmentState fragmentState = WGPUFragmentState.obtain();
            fragmentState.setNextInChain(WGPUChainedStruct.NULL);
            fragmentState.setModule(shader.getShaderModule());
            fragmentState.setEntryPoint(spec.fragmentShaderEntryPoint);
            fragmentState.setConstants(WGPUVectorConstantEntry.NULL);

            // blending
            WGPUBlendState blendState = WGPUBlendState.NULL;
            if (spec.blendingEnabled) {
                blendState = WGPUBlendState.obtain();
                blendState.getColor().setSrcFactor(spec.blendSrcColor);
                blendState.getColor().setDstFactor(spec.blendDstColor);
                blendState.getColor().setOperation(spec.blendOpColor);
                blendState.getAlpha().setSrcFactor(spec.blendSrcAlpha);
                blendState.getAlpha().setDstFactor(spec.blendDstAlpha);
                blendState.getAlpha().setOperation(spec.blendOpAlpha);
            }


            WGPUColorTargetState colorTarget = WGPUColorTargetState.obtain();
            colorTarget.setFormat(spec.colorFormat);
            colorTarget.setBlend(blendState);
            colorTarget.setWriteMask(WGPUColorWriteMask.All);

            WGPUVectorColorTargetState colorStateTargets = WGPUVectorColorTargetState.obtain();
            colorStateTargets.push_back(colorTarget);
            fragmentState.setTargets(colorStateTargets);

            pipelineDesc.setFragment(fragmentState);
        }

        WGPUDepthStencilState depthStencilState = WGPUDepthStencilState.obtain();
        setDefaultStencilState(depthStencilState);

        if (spec.isSkyBox) {
            depthStencilState.setDepthCompare(WGPUCompareFunction.LessEqual);// we are clearing to 1.0 and rendering at 1.0, i.e. at max distance
            depthStencilState.setDepthWriteEnabled(WGPUOptionalBool.True);
        } else {
            if (!spec.useDepthTest) {
                // disable depth testing
                depthStencilState.setDepthCompare(WGPUCompareFunction.Always);
                depthStencilState.setDepthWriteEnabled(WGPUOptionalBool.False);
            } else {
                if (spec.afterDepthPrepass) {   // rely on Z pre-pass
                    depthStencilState.setDepthCompare(WGPUCompareFunction.Equal);
                    depthStencilState.setDepthWriteEnabled(WGPUOptionalBool.True);
                } else {
                    // this is the usual depth compare: smaller values are closer, near plane has z = 0.0 and far plane has z = 1.0
                    // so render if the fragment z is less or equal than the depth buffer value
                    depthStencilState.setDepthCompare(WGPUCompareFunction.LessEqual);
                    depthStencilState.setDepthWriteEnabled(WGPUOptionalBool.True); // true
                }
            }
        }

        if (!spec.noDepthAttachment) {
            depthStencilState.setFormat(spec.depthFormat);
            // deactivate stencil
            depthStencilState.setStencilReadMask(0);
            depthStencilState.setStencilWriteMask(0);

            pipelineDesc.setDepthStencil(depthStencilState);
        } else {
            pipelineDesc.setDepthStencil(WGPUDepthStencilState.NULL); // no depth or stencil buffer
        }
        pipelineDesc.getMultisample().setCount(spec.numSamples);
        pipelineDesc.getMultisample().setMask(-1);
        pipelineDesc.getMultisample().setAlphaToCoverageEnabled(false);

        pipelineDesc.setLayout(pipelineLayout);

        pipeline = new WGPURenderPipeline();
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        webgpu.device.createRenderPipeline(pipelineDesc, pipeline);

        //shaderModule.release();

        System.out.println("RenderPipeline created");
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

    public WGPURenderPipeline getPipeline(){
        return pipeline;
    }

    @Override
    public void dispose() {
        pipeline.release();
        pipeline.dispose();
        if(ownsShader)
            shader.dispose();
    }

    private void setDefaultStencilState(WGPUDepthStencilState  depthStencilState ) {
        depthStencilState.setFormat(WGPUTextureFormat.Undefined);
        depthStencilState.setDepthWriteEnabled(WGPUOptionalBool.False);
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

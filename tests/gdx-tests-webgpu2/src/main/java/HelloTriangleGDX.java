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

package main.java;


import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;


// Render a triangle in a window
//
 // This follows the ApplicationAdapter template, but uses direct WebGPU calls

public class HelloTriangleGDX extends ApplicationAdapter {

    public WebGPUContext webgpu;
    private WebGPURenderPipeline pipeline;
    private WebGPURenderPassEncoder renderPass;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloTriangleGDX());
    }

    @Override
    public void create() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
        pipeline = initPipeline();
        renderPass = new WebGPURenderPassEncoder();
    }

    @Override
    public void render() {
        // create a render pass
        WebGPURenderPassDescriptor renderPassDesc = getRenderPassDescriptor();
        webgpu.encoder.beginRenderPass(renderPassDesc, renderPass);

        // draw using render pass
        renderPass.setPipeline(pipeline);
        renderPass.draw(3, 1, 0, 0);

        // end render pass
        renderPass.end();
        renderPass.release();
    }

    private WebGPURenderPassDescriptor getRenderPassDescriptor(){
        WebGPURenderPassColorAttachment renderPassColorAttachment = WebGPURenderPassColorAttachment.obtain();
        renderPassColorAttachment.setView(webgpu.targetView);
        renderPassColorAttachment.setResolveTarget(null);
        renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
        renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
        renderPassColorAttachment.getClearValue().setColor(.7f, .6f, .8f, 1.0f);

        WGPUVectorRenderPassColorAttachment colorAttachmentVector = WGPUVectorRenderPassColorAttachment.obtain();
        colorAttachmentVector.push_back(renderPassColorAttachment);

        WebGPURenderPassDescriptor renderPassDesc  = WebGPURenderPassDescriptor.obtain();
        renderPassDesc.setColorAttachments(colorAttachmentVector);
        renderPassDesc.setDepthStencilAttachment(null);
        renderPassDesc.setTimestampWrites(null);
        return renderPassDesc;
    }


    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
    }

    @Override
    public void dispose() {
        pipeline.release();
        pipeline.dispose();
    }

    public WebGPURenderPipeline initPipeline() {
        WebGPURenderPipeline pipeline;
        WebGPUShaderModule shaderModule = makeShaderModule();

        WebGPURenderPipelineDescriptor pipelineDesc = WebGPURenderPipelineDescriptor.obtain();
        pipelineDesc.setLabel("my pipeline");

        pipelineDesc.getVertex().setBuffers(null);
        pipelineDesc.getVertex().setModule(shaderModule);
        pipelineDesc.getVertex().setEntryPoint("vs_main");
        pipelineDesc.getVertex().setConstants(null);

        pipelineDesc.getPrimitive().setTopology(WGPUPrimitiveTopology.TriangleList);
        pipelineDesc.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Undefined);
        pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
        pipelineDesc.getPrimitive().setCullMode(WGPUCullMode.None);

        WebGPUFragmentState fragmentState = WebGPUFragmentState.obtain();
        fragmentState.setNextInChain(null);
        fragmentState.setModule(shaderModule);
        fragmentState.setEntryPoint("fs_main");
        fragmentState.setConstants(null);

        // blending
        WebGPUBlendState blendState = WebGPUBlendState.obtain();
        blendState.getColor().setSrcFactor(WGPUBlendFactor.SrcAlpha);
        blendState.getColor().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        blendState.getColor().setOperation(WGPUBlendOperation.Add);
        blendState.getAlpha().setSrcFactor(WGPUBlendFactor.One);
        blendState.getAlpha().setDstFactor(WGPUBlendFactor.Zero);
        blendState.getAlpha().setOperation(WGPUBlendOperation.Add);

        WebGPUColorTargetState colorTarget = WebGPUColorTargetState.obtain();
        colorTarget.setFormat(webgpu.surfaceFormat); // match output surface
        colorTarget.setBlend(blendState);
        colorTarget.setWriteMask(WGPUColorWriteMask.All);

        WGPUVectorColorTargetState colorStateTargets = WGPUVectorColorTargetState.obtain();
        colorStateTargets.push_back(colorTarget);
        fragmentState.setTargets(colorStateTargets);

        pipelineDesc.setFragment(fragmentState);
        pipelineDesc.setDepthStencil(null); // no depth or stencil buffer
        pipelineDesc.getMultisample().setCount(1);
        pipelineDesc.getMultisample().setMask(-1);
        pipelineDesc.getMultisample().setAlphaToCoverageEnabled(false);
        pipelineDesc.setLayout(null);

        pipeline = new WebGPURenderPipeline();
        webgpu.device.createRenderPipeline(pipelineDesc, pipeline);

        shaderModule.release();

        System.out.println("RenderPipeline created");
        return pipeline;
    }

    public WebGPUShaderModule makeShaderModule() {
        String shaderSource = readShaderSource();

        WebGPUShaderModuleDescriptor shaderDesc = WebGPUShaderModuleDescriptor.obtain();
        shaderDesc.setLabel("triangle shader");

        WebGPUShaderSourceWGSL shaderCodeDesc = WebGPUShaderSourceWGSL.obtain();
        shaderCodeDesc.getChain().setNext(null);
        shaderCodeDesc.getChain().setSType(WGPUSType.ShaderSourceWGSL);
        shaderCodeDesc.setCode(shaderSource);

        shaderDesc.setNextInChain(shaderCodeDesc.getChain());
        WebGPUShaderModule shaderModule = WebGPUShaderModule.obtain();
        webgpu.device.createShaderModule(shaderDesc, shaderModule);
        return shaderModule;
    }

    private String readShaderSource () {

        return "// triangleShader.wgsl\n" + "\n" + "@vertex\n"
            + "fn vs_main(@builtin(vertex_index) in_vertex_index: u32) -> @builtin(position) vec4f {\n"
            + "    var p = vec2f(0.0, 0.0);\n" + "    if (in_vertex_index == 0u) {\n" + "        p = vec2f(-0.5, -0.5);\n"
            + "    } else if (in_vertex_index == 1u) {\n" + "        p = vec2f(0.5, -0.5);\n" + "    } else {\n"
            + "        p = vec2f(0.0, 0.5);\n" + "    }\n" + "    return vec4f(p, 0.0, 1.0);\n" + "}\n" + "\n" + "@fragment\n"
            + "fn fs_main() -> @location(0) vec4f {\n" + "    return vec4f(0.0, 0.4, 1.0, 1.0);\n" + "}";
    }

}

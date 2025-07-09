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
import com.monstrous.gdx.webgpu.wrappers.PipelineSpecification;
import com.monstrous.gdx.webgpu.wrappers.WebGPUPipeline;


// Render a triangle in a window
//
 // This follows the ApplicationAdapter template, but uses direct WebGPU calls
// except for creating the pipeline.

public class HelloTrianglePipeline extends ApplicationAdapter {

    public WebGPUContext webgpu;
    private WGPURenderPipeline pipeline;
    private WGPURenderPassEncoder renderPass;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloTrianglePipeline());
    }

    @Override
    public void create() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
        pipeline = initPipeline();
        renderPass = new WGPURenderPassEncoder();
    }

    @Override
    public void render() {
        // create a render pass
        WGPURenderPassDescriptor renderPassDesc = getRenderPassDescriptor();
        webgpu.encoder.beginRenderPass(renderPassDesc, renderPass);

        // draw using render pass
        renderPass.setPipeline(pipeline);
        renderPass.draw(3, 1, 0, 0);

        // end render pass
        renderPass.end();
        renderPass.release();
    }

    private WGPURenderPassDescriptor getRenderPassDescriptor(){
        WGPURenderPassColorAttachment renderPassColorAttachment = WGPURenderPassColorAttachment.obtain();
        renderPassColorAttachment.setView(webgpu.targetView);
        renderPassColorAttachment.setResolveTarget(null);
        renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
        renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
        renderPassColorAttachment.getClearValue().setColor(.7f, .6f, .8f, 1.0f);

        WGPUVectorRenderPassColorAttachment colorAttachmentVector = WGPUVectorRenderPassColorAttachment.obtain();
        colorAttachmentVector.push_back(renderPassColorAttachment);

        WGPURenderPassDescriptor renderPassDesc  = WGPURenderPassDescriptor.obtain();
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

    public WGPURenderPipeline initPipeline() {
        PipelineSpecification spec = new PipelineSpecification();
        spec.shaderSource = readShaderSource();

        WebGPUPipeline pipeline = new WebGPUPipeline(null, spec);
        return pipeline.getPipeline();
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

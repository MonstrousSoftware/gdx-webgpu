package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.wrappers.PipelineSpecification;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.WebGPUPipeline;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

// Render a triangle in a window
//
// This follows the ApplicationAdapter template, but uses wrapped WebGPU classes for pipeline and renderPass

public class HelloTriangleRenderPass extends ApplicationAdapter {

    private WebGPUPipeline pipeline;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloTriangleRenderPass());
    }

    @Override
    public void create() {
        PipelineSpecification spec = new PipelineSpecification();
        spec.shaderSource = readShaderSource();
        pipeline = new WebGPUPipeline(null, spec);

    }

    @Override
    public void render() {
        // create a render pass
        WebGPURenderPass renderPass = RenderPassBuilder.create("my render pass", Color.YELLOW);

        // draw using render pass
        renderPass.setPipeline(pipeline);
        renderPass.draw(3);

        // end render pass
        renderPass.end();
    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
    }

    @Override
    public void dispose() {
        pipeline.dispose();
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

package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.wrappers.*;

// Render a shape using a vertex buffer
//


public class HelloVertexBuffer extends ApplicationAdapter {

    private WebGPUPipeline pipeline;
    private WebGPUVertexBuffer vertexBuffer;
    private WebGPUIndexBuffer indexBuffer;
    private int vertexCount;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloVertexBuffer());
    }

    @Override
    public void create() {
        vertexBuffer = makeVertexBuffer();
        indexBuffer = makeIndexBuffer();

        // only one vertex attribute: a 2d position
        VertexAttribute positionAttrib = new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE);

        // compile the set of vertex attributes
        VertexAttributes vattr = new VertexAttributes(positionAttrib);


        vertexCount = vertexBuffer.getSize()/vattr.vertexSize;  // number of vertices

        PipelineSpecification spec = new PipelineSpecification(vattr, shaderSource);
        pipeline = new WebGPUPipeline(null, spec);


    }

    @Override
    public void render() {
        // create a render pass
        WebGPURenderPass renderPass = RenderPassBuilder.create("my render pass", Color.ORANGE);

        // draw using render pass
        renderPass.setPipeline(pipeline);
        renderPass.setVertexBuffer(0, vertexBuffer.getBuffer(), 0, vertexBuffer.getSize()); // bind vertex buffer as binding 0

        // alternative: use indexes
//        renderPass.setIndexBuffer(indexBuffer.getBuffer(), WGPUIndexFormat.Uint16, 0, indexBuffer.getSize());
//        renderPass.drawIndexed(indexBuffer.getIndexCount(), 1, 0, 0, 0);

        renderPass.draw(vertexCount);

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

    private WebGPUVertexBuffer makeVertexBuffer(){
        float[] floats = {
            -0.5f, -0.5f, +0.5f, -0.5f, +0.0f, +0.5f,//  first triangle
            -0.55f, -0.5f, -0.05f, +0.5f, -0.55f, +0.5f,// second triangle
        };
        WebGPUVertexBuffer vb = new WebGPUVertexBuffer( floats.length*Float.BYTES);
        vb.setVertices(floats);
        return vb;
    }

    private WebGPUIndexBuffer makeIndexBuffer(){
       short[] shorts = {  0, 1, 2, 3, 4, 5 };
       return new WebGPUIndexBuffer( shorts);
    }

    public String shaderSource =
        "@vertex\n" +
            "fn vs_main(@location(0) in_vertex_position: vec2f) -> @builtin(position) vec4f {\n" +
            "    return vec4f(in_vertex_position, 0.0, 1.0);\n" +
            "}\n" +
            "\n" +
            "@fragment\n" +
            "fn fs_main() -> @location(0) vec4f {\n" +
            "    return vec4f(0.0, 0.4, 1.0, 1.0);\n" +
            "}";
}

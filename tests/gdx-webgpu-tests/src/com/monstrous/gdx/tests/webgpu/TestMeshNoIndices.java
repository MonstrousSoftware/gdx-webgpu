
package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.wrappers.PipelineSpecification;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.WebGPUPipeline;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

// Basic test of Mesh and MeshPart without indices (only vertices)
// Renders a rectangle as a mesh using the included WGSL shader
//
public class TestMeshNoIndices extends GdxTest {

    private WgMesh mesh;
    private WebGPUPipeline pipeline;
    private WgMeshPart meshPart;

    @Override
    public void create() {
        VertexAttributes vattr = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0),
                VertexAttribute.ColorUnpacked());

        // create a render pipeline with the given shader code
        PipelineSpecification pipelineSpec = new PipelineSpecification("pipeline", vattr, getShaderSource());
        pipeline = new WebGPUPipeline(WGPUPipelineLayout.NULL, pipelineSpec);

        // create a mesh for a square: 6 verts (to make two triangles)
        mesh = new WgMesh(true, 6, 0, vattr);
        mesh.setVertices(new float[] {
                // x,y,z // u,v // r,b,g,a
                -0.5f, -0.5f, 0, 0, 1, 1, 0, 1, 1, 0.5f, -0.5f, 0, 1, 1, 0, 1, 1, 1, 0.5f, 0.5f, 0, 1, 0, 1, 1, 0, 1,
                0.5f, 0.5f, 0, 1, 0, 1, 1, 0, 1, -0.5f, 0.5f, 0, 0, 0, 0, 1, 0, 1, -0.5f, -0.5f, 0, 0, 1, 1, 0, 1, 1,});

        // mesh.setIndices(new short[] {0, 1, 2, 2, 3, 0});

        // Now create a mesh part including only the second triangle, i.e. using the last 3 indices

        int offset = 0; // offset
        int size = 6; // nr of verts
        int type = GL20.GL_TRIANGLES; // primitive type using GL constant
        meshPart = new WgMeshPart("part", mesh, offset, size, type);
    }

    @Override
    public void render() {

        // create a render pass
        WebGPURenderPass pass = RenderPassBuilder.create("Test Mesh", Color.SKY);

        pass.setPipeline(pipeline);

        // to render the whole mesh (a rectangle)
        // mesh.render(pass, GL20.GL_TRIANGLES, 0, mesh.getNumIndices(), 1, 0);

        // to render a mesh part (one triangle)
        meshPart.render(pass);

        // end the render pass
        pass.end();
    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("", "resize");
    }

    @Override
    public void dispose() {
        pipeline.dispose();
        mesh.dispose();
    }

    private String getShaderSource() {
        return "\n" + "\n" + "\n" + "struct VertexInput {\n" + "    @location(0) position: vec3f,\n"
                + "    @location(5) color: vec4f,\n" + "    @location(1) uv: vec2f,\n" +

                "};\n" + "\n" + "struct VertexOutput {\n" + "    @builtin(position) position: vec4f,\n"
                + "    @location(0) uv : vec2f,\n" + "    @location(1) color: vec4f,\n" + "};\n" + "\n" + "\n"
                + "@vertex\n" + "fn vs_main(in: VertexInput) -> VertexOutput {\n" + "   var out: VertexOutput;\n" + "\n"
                + "   var pos =  vec4f(in.position,  1.0);\n" + "   out.position = pos;\n" + "   out.uv = vec2f(0,0);\n"
                + "   let color:vec4f = in.color;\n" + "   out.color = color;\n" + "\n" + "   return out;\n" + "}\n"
                + "\n" + "@fragment\n" + "fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" + "\n"
                + "    let color = in.color;\n" + "    return vec4f(color);\n" + "}";
    }

}

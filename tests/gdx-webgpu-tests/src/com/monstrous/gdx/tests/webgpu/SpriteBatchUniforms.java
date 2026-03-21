package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.WebGPUBindGroupLayout;
import com.monstrous.gdx.webgpu.wrappers.WebGPUUniformBuffer;

// Demonstration of additional per-frame uniforms to a derived sprite batch shader

public class SpriteBatchUniforms extends GdxTest {

    private MySpriteBatch batch;

    private WgTexture texture;
    private ScreenViewport viewport;
    private float time = 0f;

    // create a subclass of WgSpriteBatch
    public static class MySpriteBatch extends WgSpriteBatch {
        private final static int UB_BINDING = 3;    // next available binding id (0,1 and 2 are already in use)
        private final static int UB_SIZE = 128;     // plenty of space for a few uniforms
        private WebGPUUniformBuffer uniformBuffer;


        // replace standard sprite batch shader code
        @Override
        protected String getDefaultShaderSource(){
            return shaderSource;
        }

        // add an extra binding to bind group 0 for a uniform buffer
        @Override
        protected void defineBindGroup0Layout(WebGPUBindGroupLayout layout) {
            super.defineBindGroup0Layout(layout);
            layout.addBuffer(UB_BINDING, WGPUShaderStage.Fragment, WGPUBufferBindingType.Uniform, UB_SIZE, false);
        }

        @Override
        protected void defineBindings(Binder binder) {
            super.defineBindings(binder);   // bindings 0, 1 and 2
            // add binding 3
            binder.defineBinding("myUniforms", 0, UB_BINDING);

            // create a uniform buffer of 128 bytes
            uniformBuffer = new WebGPUUniformBuffer(UB_SIZE, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
            binder.setBuffer("myUniforms", uniformBuffer, 0, UB_SIZE); //uniformBuffer.getSize());

            binder.defineUniform("u_time", 0, UB_BINDING, 0);
        }

        public void setTime(float time){
            binder.setUniform("u_time", time);
            uniformBuffer.flush();  // push uniform buffer contents to GPU
        }

        @Override
        public void dispose() {
            super.dispose();
            uniformBuffer.dispose();
        }

    }

    @Override
    public void create() {
        texture = new WgTexture("data/badlogic.jpg", true);
        batch = new MySpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render() {
        time += Gdx.graphics.getDeltaTime();

        // set per-frame values for the special sprite batch shader
        batch.setTime(time);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        WgScreenUtils.clear(Color.WHITE);
        batch.begin();
        batch.draw(texture, (Gdx.graphics.getWidth() - texture.getWidth()) / 2f,
                (Gdx.graphics.getHeight() - texture.getHeight()) / 2f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // cleanup
        texture.dispose();
        batch.dispose();
    }

    // this is a copy of the default sprite batch shader but modifies the red component based on a time
    // value (float32) passed via a uniform buffer in binding 3
    // Hard coded string for this demo, for a real app you'd probably want to read this from a file
    public static final String shaderSource = "struct Uniforms {\n" +
        "    projectionViewTransform: mat4x4f,\n" +
        "};\n" +
        "struct MyUniforms {\n" +   // new
        "    time: f32,\n" +
        "};\n" +
        "\n" +
        "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
        "@group(0) @binding(1) var texture: texture_2d<f32>;\n" +
        "@group(0) @binding(2) var textureSampler: sampler;\n" +
        "@group(0) @binding(3) var<uniform> myUniforms: MyUniforms;\n" +    // new
        "\n" +
        "\n" +
        "struct VertexInput {\n" +
        "    @location(0) position: vec2f,\n" +
        "#ifdef TEXTURE_COORDINATE\n" +
        "    @location(1) uv: vec2f,\n" +
        "#endif\n" +
        "#ifdef COLOR\n" +
        "    @location(5) color: vec4f,\n" +
        "#endif\n" +
        "};\n" +
        "\n" +
        "struct VertexOutput {\n" +
        "    @builtin(position) position: vec4f,\n" +
        "#ifdef TEXTURE_COORDINATE\n" +
        "    @location(0) uv : vec2f,\n" +
        "#endif\n" +
        "    @location(1) color: vec4f,\n" +
        "};\n" +
        "\n" +
        "\n" +
        "@vertex\n" +
        "fn vs_main(in: VertexInput) -> VertexOutput {\n" +
        "   var out: VertexOutput;\n" +
        "\n" +
        "   var pos =  uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);\n" +
        "   out.position = pos;\n" +
        "#ifdef TEXTURE_COORDINATE\n" +
        "   out.uv = in.uv;\n" +
        "#endif\n" +
        "\n" +
        "#ifdef COLOR\n" +
        "   let color:vec4f = vec4f(pow(in.color.rgb, vec3f(2.2)), in.color.a);\n" +
        "#else\n" +
        "   let color:vec4f = vec4f(1,1,1,1);   // white\n" +
        "#endif\n" +
        "   out.color = color;\n" +
        "\n" +
        "   return out;\n" +
        "}\n" +
        "\n" +
        "@fragment\n" +
        "fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" +
        "\n" +
        "#ifdef TEXTURE_COORDINATE\n" +
        "    var color = in.color * textureSample(texture, textureSampler, in.uv);\n" +
        "#else\n" +
        "    var color = in.color;\n" +
        "#endif\n" +
        "\n" +
        "// textures are loaded into linear space already.\n" +
        "\n" +
        "// if the output surface is Srgb (i.e. WGPU) we can output linear values,\n" +
        "// otherwise (i.e Dawn) we have to do inverse gamma correction here in the shader\n" +
        "#ifdef GAMMA_CORRECTION\n" +
        "    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));\n" +
        "    color = vec4f(linearColor, color.a);\n" +
        "#endif\n" +
            "color.r = abs(sin(2.0*myUniforms.time));\n" + // new
        "    return color;\n" +
        "};";


}

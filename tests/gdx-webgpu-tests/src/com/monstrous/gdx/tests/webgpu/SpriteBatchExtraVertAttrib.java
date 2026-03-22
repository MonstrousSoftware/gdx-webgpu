package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.WebGPUBindGroupLayout;
import com.monstrous.gdx.webgpu.wrappers.WebGPUVertexLayout;

// Sprite batch with additional vertex attributes.
// By default, every vertex has 2d position, packed vertex color and texture coordinates (5 floats in total).
// In this demo we derive our own SpriteBatch class to add an extra texture binding, and we add an extra set of texture coordinates per vertex.
// This will modulate the textures with a large scale "cloud" texture.
//

public class SpriteBatchExtraVertAttrib extends GdxTest {

    private WgSpriteBatch batch;
    private MySpriteBatch myBatch;
    private WgTexture texture;
    private ScreenViewport viewport;
    private BitmapFont font;
    private float time;

    // create a subclass of WgSpriteBatch
    public static class MySpriteBatch extends WgSpriteBatch {

        private float textureOffset = 0;
        private WgTexture texture2;


        // replace standard sprite batch shader code
        @Override
        protected String getDefaultShaderSource(){
            return shaderSource;
        }

        // add an extra binding to binding group 0 for a second texture view
        @Override
        protected void defineBindGroup0Layout(WebGPUBindGroupLayout layout) {
            super.defineBindGroup0Layout(layout);
            layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        }

        // set the binding to a noise texture
        @Override
        protected void defineBindings(Binder binder) {
            super.defineBindings(binder);   // default bindings 0, 1 and 2
            // add binding 3
            binder.defineBinding("texture2", 0, 3);

            texture2 = new WgTexture("data/noiseTexture.png", true);
            texture2.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            binder.setTexture("texture2", texture2.getTextureView());
        }

        // define the vertex attributes per vertex adding an extra texture coordinate
        // this will create extra space per vertex in the vertex buffer (2 extra floats)
        @Override
        protected void setVertexAttributes(){
            vertexAttributes = new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE), // 2D position
                VertexAttribute.ColorPacked(),
                VertexAttribute.TexCoords(0),
                VertexAttribute.TexCoords(1)        //  new
                );
        }

        // define a location (10) for the new vertex attribute
        @Override
        protected void setVertexAttributeLocations(WebGPUVertexLayout vertexLayout) {
            // define locations of vertex attributes in line with shader code
            super.setVertexAttributeLocations(vertexLayout);
            // add extra vertex attribute
            vertexLayout.setVertexAttributeLocation(ShaderProgram.TEXCOORD_ATTRIBUTE + "1", 10);
        }

        // override addVertex to set the extra vertex attributes
        @Override
        protected void addVertex(float x, float y, float u, float v) {
            // set 2nd texture coordinate
            float u2 = textureOffset + (x / 512);
            float v2 = textureOffset + (384- y) / 256;
            // texture coordinates go into offsets 5 and 6 in the vertex buffer
            vertexFloats.put(vertexOffset+5, u2);
            vertexFloats.put(vertexOffset+6, v2);

            // then do the regular vertex attributes
            // note: needs to be done after, because this advances vertexOffset
            // if you do super.addVertex() before you need to put in vertexOffset-2 and vertexOffset-1
            super.addVertex(x,y,u,v);
        }

        public void setTextureOffset( float time ){
            // move texture coordinates over time to scroll the texture
            textureOffset = (time/16f);
        }

        @Override
        public void dispose() {
            super.dispose();
            texture2.dispose();
        }
    }


    @Override
    public void create() {
        texture = new WgTexture("data/stone2.png", true);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        myBatch = new MySpriteBatch();
        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
        font = new WgBitmapFont();
    }

    @Override
    public void render() {
        time += Gdx.graphics.getDeltaTime();

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        myBatch.setProjectionMatrix(viewport.getCamera().combined);

        // draw a grid of the texture
        float sw = 128;
        float sh = 128;
        int repsx = (int) (Gdx.graphics.getWidth() / sw) - 1;
        int repsy = (int) (Gdx.graphics.getHeight() / sw) - 1;


        WgScreenUtils.clear(Color.BLACK);
        myBatch.begin();
        myBatch.setTextureOffset(time);
        for(int ix = 0; ix < repsx; ix++){
            for(int iy = 0; iy < repsy; iy++){
                float sx = sw * (0.5f+ix);
                float sy = sh * (0.5f+iy);
                myBatch.draw(texture, sx, sy, sw, sh);
            }
        }

        myBatch.end();

        batch.begin();
        font.draw(batch, "WgSpriteBatch with extra vertex attributes (2nd texture coordinate)", 20, 20);
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
        myBatch.dispose();
        font.dispose();
    }
    // this is a copy of the default sprite batch shader but modulates the color using a second texture
    // and an extra pair of texture coordinates per vertex
    // Hard coded string for this demo, for a real app you'd probably want to read this from a file
    public static final String shaderSource = "struct Uniforms {\n" +
        "    projectionViewTransform: mat4x4f,\n" +
        "};\n" +
        "\n" +
        "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
        "@group(0) @binding(1) var texture: texture_2d<f32>;\n" +
        "@group(0) @binding(2) var textureSampler: sampler;\n" +
        "@group(0) @binding(3) var texture2: texture_2d<f32>;\n" + // new
        "\n" +
        "\n" +
        "struct VertexInput {\n" +
        "    @location(0) position: vec2f,\n" +
        "    @location(1) uv: vec2f,\n" +
        "    @location(5) color: vec4f,\n" +
        "    @location(10) uv2 : vec2f,\n" + // new
        "};\n" +
        "\n" +
        "struct VertexOutput {\n" +
        "    @builtin(position) position: vec4f,\n" +
        "    @location(0) uv : vec2f,\n" +
        "    @location(1) color: vec4f,\n" +
        "    @location(2) uv2 : vec2f,\n" + // new
        "};\n" +
        "\n" +
        "\n" +
        "@vertex\n" +
        "fn vs_main(in: VertexInput) -> VertexOutput {\n" +
        "   var out: VertexOutput;\n" +
        "\n" +
        "   var pos =  uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);\n" +
        "   out.position = pos;\n" +
        "   out.uv = in.uv;\n" +
        "   out.uv2 = in.uv2;\n" +
        "\n" +
        "   let color:vec4f = vec4f(pow(in.color.rgb, vec3f(2.2)), in.color.a);\n" +
        "   out.color = color;\n" +
        "\n" +
        "   return out;\n" +
        "}\n" +
        "\n" +
        "@fragment\n" +
        "fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" +
        "\n" +
        "    var color = in.color * textureSample(texture, textureSampler, in.uv) * textureSample(texture2, textureSampler, in.uv2);\n" + // new
        "\n" +
        "// textures are loaded into linear space already.\n" +
        "\n" +
        "// if the output surface is Srgb (i.e. WGPU) we can output linear values,\n" +
        "// otherwise (i.e Dawn) we have to do inverse gamma correction here in the shader\n" +
        "#ifdef GAMMA_CORRECTION\n" +
        "    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));\n" +
        "    color = vec4f(linearColor, color.a);\n" +
        "#endif\n" +
        "    return color;\n" +
        "};";


}

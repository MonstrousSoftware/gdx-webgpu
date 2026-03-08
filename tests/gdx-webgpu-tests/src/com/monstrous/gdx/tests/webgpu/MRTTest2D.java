package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Tests Multiple Render Targets (MRT) by rendering to a FrameBuffer with two color attachments.
 */
public class MRTTest2D extends GdxTest {

    WgSpriteBatch batch;
    WgSpriteBatch mrtBatch;
    WgBitmapFont font;
    WgGraphics gfx;
    WgFrameBuffer mrtFbo;
    Texture texture;
    WgShaderProgram mrtShader;

    public void create() {
        gfx = (WgGraphics) Gdx.graphics;
        batch = new WgSpriteBatch();
        font = new WgBitmapFont(); // default font

        // Create a custom shader that outputs to two targets
        String shaderSource = getMRTShaderSource();
        mrtShader = new WgShaderProgram("mrt_shader", shaderSource, "");

        // Create a separate batch for rendering into the MRT FBO using the custom shader
        mrtBatch = new WgSpriteBatch(1000, mrtShader);

        texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));

        // Create FrameBuffer with 2 RGBA8Unorm attachments
        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {WGPUTextureFormat.RGBA8Unorm,
                WGPUTextureFormat.RGBA8Unorm};
        mrtFbo = new WgFrameBuffer(formats, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    }

    public void render() {
        // 1. Render to MRT FrameBuffer
        mrtFbo.begin();
        {
            // Clear both attachments to black/transparent
            // WgScreenUtils.clear clears the FIRST attachment primarily,
            // but RenderPassBuilder handles clearing logic for attachments if LoadOp is Clear.
            // For simplicity, we assume clear works or we just draw over it
            WgScreenUtils.clear(Color.BLACK, true);

            mrtBatch.begin();
            mrtBatch.draw(texture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            mrtBatch.end();
        }
        mrtFbo.end();

        // 2. Render FBO attachments to screen
        WgScreenUtils.clear(Color.DARK_GRAY, true);

        batch.begin();

        float w = Gdx.graphics.getWidth() / 2f;
        float h = Gdx.graphics.getHeight() / 2f;

        // Draw Attachment 0 (Should be Red channel)
        batch.draw(mrtFbo.getColorBufferTexture(0), 0, h, w, h);
        font.draw(batch, "Target 0 (Red)", 10, h + 20);

        // Draw Attachment 1 (Should be Green channel)
        batch.draw(mrtFbo.getColorBufferTexture(1), w, h, w, h);
        font.draw(batch, "Target 1 (Green)", w + 10, h + 20);

        // Draw Original for reference
        batch.draw(texture, w / 2, 0, w, h);
        font.draw(batch, "Original", w / 2 + 10, 20);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        mrtBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        // recreate FBO if needed or just handle scaling
    }

    @Override
    public void dispose() {
        batch.dispose();
        mrtBatch.dispose();
        font.dispose();
        texture.dispose();
        mrtFbo.dispose();
        mrtShader.dispose();
    }

    private String getMRTShaderSource() {
        return "struct Uniforms {\n" + "    projectionMatrix: mat4x4f,\n" + "};\n" + "\n"
                + "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n"
                + "@group(0) @binding(1) var texture: texture_2d<f32>;\n"
                + "@group(0) @binding(2) var textureSampler: sampler;\n" + "\n" + "\n" + "struct VertexInput {\n"
                + "    @location(0) position: vec2f,\n" + "    @location(1) uv: vec2f,\n"
                + "    @location(5) color: vec4f,\n" + "};\n" + "\n" + "struct VertexOutput {\n"
                + "    @builtin(position) position: vec4f,\n" + "    @location(0) uv : vec2f,\n"
                + "    @location(1) color: vec4f,\n" + "};\n" + "\n" + "\n" + "@vertex\n"
                + "fn vs_main(in: VertexInput) -> VertexOutput {\n" + "   var out: VertexOutput;\n" + "\n"
                + "   var pos =  uniforms.projectionMatrix * vec4f(in.position, 0.0, 1.0);\n"
                + "   out.position = pos;\n" + "   out.uv = in.uv;\n" + "\n" + "   let color:vec4f = in.color;\n"
                + "   out.color = color;\n" + "\n" + "   return out;\n" + "}\n" + "\n" + "struct FragmentOutput {\n"
                + "    @location(0) color0 : vec4f,\n" + "    @location(1) color1 : vec4f,\n" + "};\n" + "\n"
                + "@fragment\n" + "fn fs_main(in : VertexOutput) -> FragmentOutput {\n" + "\n"
                + "    var out: FragmentOutput;\n"
                + "    var color = in.color * textureSample(texture, textureSampler, in.uv);\n"
                + "    out.color0 = vec4f(color.r, 0.0, 0.0, color.a);\n"
                + "    out.color1 = vec4f(0.0, color.g, 0.0, color.a);\n" + "    return out;\n" + "}";
    }
}

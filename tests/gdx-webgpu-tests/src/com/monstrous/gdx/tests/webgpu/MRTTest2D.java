package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgShaderChunk;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgSpriteBatchShaderBuilder;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Tests Multiple Render Targets (MRT) by rendering to a FrameBuffer with two color attachments.
 * The MRT shader is built via {@link WgSpriteBatchShaderBuilder} — only 3 targeted chunk
 * replacements on top of the standard sprite-batch shader.
 */
public class MRTTest2D extends GdxTest {

    // MRT output struct inserted before the FS signature
    private static final WgShaderChunk MRT_OUTPUT_STRUCT = new WgShaderChunk("mrt_output_struct",
            "struct FragmentOutput {\n"
          + "    @location(0) color0 : vec4f,\n"
          + "    @location(1) color1 : vec4f,\n"
          + "};\n");

    // Replacement FS signature that returns the MRT struct
    private static final WgShaderChunk MRT_FS_SIGNATURE = new WgShaderChunk(WgSpriteBatchShaderBuilder.FS_SIGNATURE,
            "@fragment\n"
          + "fn fs_main(in : VertexOutput) -> FragmentOutput {\n");

    // Replacement return that writes red channel to target 0, green to target 1
    private static final WgShaderChunk MRT_FS_RETURN = new WgShaderChunk(WgSpriteBatchShaderBuilder.FS_RETURN,
            "    var o: FragmentOutput;\n"
          + "    o.color0 = vec4f(color.r, 0.0, 0.0, color.a);\n"
          + "    o.color1 = vec4f(0.0, color.g, 0.0, color.a);\n"
          + "    return o;\n"
          + "};\n");

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
        font = new WgBitmapFont();

        // Build the MRT shader: standard sprite-batch + 3 targeted chunk changes
        String shaderSource = WgSpriteBatchShaderBuilder.defaultSpriteBatch()
                .insertBefore(WgSpriteBatchShaderBuilder.FS_SIGNATURE, MRT_OUTPUT_STRUCT)
                .replaceChunk(WgSpriteBatchShaderBuilder.FS_SIGNATURE, MRT_FS_SIGNATURE)
                .replaceChunk(WgSpriteBatchShaderBuilder.FS_RETURN, MRT_FS_RETURN)
                .build();

        // SpriteBatch always uses TEXTURE_COORDINATE and COLOR vertex attributes
        String prefix = "#define TEXTURE_COORDINATE\n#define COLOR\n";
        mrtShader = new WgShaderProgram("mrt_sprite_shader", shaderSource, prefix);
        mrtBatch = new WgSpriteBatch(1000, mrtShader);

        texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));

        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {
                WGPUTextureFormat.RGBA8Unorm,
                WGPUTextureFormat.RGBA8Unorm
        };
        mrtFbo = new WgFrameBuffer(formats, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
    }

    public void render() {
        // 1. Render to MRT FrameBuffer
        mrtFbo.begin();
        WgScreenUtils.clear(Color.BLACK, true);
        mrtBatch.begin();
        mrtBatch.draw(texture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        mrtBatch.end();
        mrtFbo.end();

        // 2. Render FBO attachments to screen
        WgScreenUtils.clear(Color.DARK_GRAY, true);
        batch.begin();

        float w = Gdx.graphics.getWidth() / 2f;
        float h = Gdx.graphics.getHeight() / 2f;

        batch.draw(mrtFbo.getColorBufferTexture(0), 0, h, w, h);
        font.draw(batch, "Target 0 (Red)", 10, h + 20);

        batch.draw(mrtFbo.getColorBufferTexture(1), w, h, w, h);
        font.draw(batch, "Target 1 (Green)", w + 10, h + 20);

        batch.draw(texture, w / 2, 0, w, h);
        font.draw(batch, "Original", w / 2 + 10, 20);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        mrtBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
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
}

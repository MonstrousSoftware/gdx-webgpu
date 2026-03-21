package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.xpenatan.webgpu.WGPUBufferBindingType;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUShaderStage;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.WebGPUBindGroupLayout;
import com.monstrous.gdx.webgpu.wrappers.WebGPUUniformBuffer;

// Sprite batch with fewer vertex attributes.
// By default, every vertex has 2d position, packed vertex color and texture coordinates (5 floats in total).
// The 2d position is mandatory, but depending on your use case you could omit the vertex color or the texture coordinates if you
// want to save one or two floats per vertex.
//

public class SpriteBatchVattr extends GdxTest {

    private WgSpriteBatch batch;
    private MySpriteBatch myBatch;
    private WgTexture texture;
    private ScreenViewport viewport;
    private BitmapFont font;

    // create a subclass of WgSpriteBatch
    public static class MySpriteBatch extends WgSpriteBatch {

        @Override
        protected void setVertexAttributes(){
            vertexAttributes = new VertexAttributes(
                new VertexAttribute(VertexAttributes.Usage.Position, 2, ShaderProgram.POSITION_ATTRIBUTE), // 2D position
                //VertexAttribute.ColorPacked(),
                VertexAttribute.TexCoords(0)
                );
        }
    }



    @Override
    public void create() {
        texture = new WgTexture("data/webgpu.png", true);

        myBatch = new MySpriteBatch();
        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
        font = new WgBitmapFont();
    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        WgScreenUtils.clear(Color.TEAL);
        myBatch.begin();
        myBatch.draw(texture, (Gdx.graphics.getWidth() - texture.getWidth()) / 2f,
                (Gdx.graphics.getHeight() - texture.getHeight()) / 2f);
        myBatch.end();

        batch.begin();
        font.draw(batch, "Test of WgSpriteBatch with fewer vertex attributes: no vertex colors", 20, 20);
        batch.end();

    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
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

}

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class TestTextureMipMap extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private Viewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/jackRussel.png", true);
        // texture = new Texture("textures/input.jpg", true);

        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        batch.draw(texture, 0, 0, 1024, 1024);
        batch.draw(texture, 0, 0, 512, 512);
        batch.draw(texture, 512, 0, 256, 256);
        batch.draw(texture, 512 + 256, 0, 128, 128);
        batch.draw(texture, 512 + 256 + 128, 0, 64, 64);
        batch.draw(texture, 512 + 256 + 128 + 64, 0, 32, 32);
        batch.draw(texture, 512 + 256 + 128 + 64 + 32, 0, 16, 16);
        batch.draw(texture, 512 + 256 + 128 + 64 + 32 + 16, 0, 8, 8);
        batch.draw(texture, 512 + 256 + 128 + 64 + 32 + 16 + 8, 0, 4, 4);
        batch.draw(texture, 512 + 256 + 128 + 64 + 32 + 16 + 8 + 4, 0, 2, 2);

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
        System.out.println("demo exit");
        texture.dispose();
        batch.dispose();
    }

}

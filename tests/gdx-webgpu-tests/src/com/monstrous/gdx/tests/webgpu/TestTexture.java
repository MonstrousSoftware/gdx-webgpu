package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class TestTexture extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/webgpu.png", true);

        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render(  ){

        viewport.apply();

        batch.begin(Color.WHITE);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.draw(texture, 0, 0); //, 640, 480); //, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        batch.end();
    }

    @Override
    public void resize (int width, int height) {
        Gdx.app.log("resize", "");
        viewport.update(width, height, true);
    }

    @Override
    public void dispose(){
        // cleanup
        texture.dispose();
        batch.dispose();
    }



}

package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// Test you can use sprite batch multiple times in a render frame
// without the last loop overwriting the earlier ones.


public class TestDoubleSpriteBatch extends GdxTest {

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
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // do 2 sprite batch loops
        // we should see the result of both

        batch.begin(Color.WHITE);
        batch.draw(texture, 0, 0);
        batch.end();

        batch.begin();
        batch.draw(texture, 300, 200);
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

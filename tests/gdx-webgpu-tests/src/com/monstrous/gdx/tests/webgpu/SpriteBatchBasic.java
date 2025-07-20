package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

// Texture centred on screen.
public class SpriteBatchBasic extends GdxTest {

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

        WgScreenUtils.clear(Color.WHITE);
        batch.begin();
        batch.draw(texture,
            (Gdx.graphics.getWidth()-texture.getWidth())/2f,
            (Gdx.graphics.getHeight()-texture.getHeight())/2f);
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

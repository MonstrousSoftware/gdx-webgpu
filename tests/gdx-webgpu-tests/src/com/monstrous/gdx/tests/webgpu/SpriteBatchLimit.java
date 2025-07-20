package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// Test behaviour above max sprites
// There should be error messages in the log

public class SpriteBatchLimit extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/webgpu.png", true);

        batch = new WgSpriteBatch(20);
        viewport = new ScreenViewport();
    }

    @Override
    public void render(  ){

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        MathUtils.random.setSeed(1234);

        // pass a clear color to batch begin
        batch.begin(Color.WHITE);
        for(int i = 0; i < 21; i++) {
            int x = MathUtils.random(Gdx.graphics.getWidth() - 32);
            int y = MathUtils.random(Gdx.graphics.getHeight() - 32);
            batch.draw(texture, x, y, 32, 32);
        }
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

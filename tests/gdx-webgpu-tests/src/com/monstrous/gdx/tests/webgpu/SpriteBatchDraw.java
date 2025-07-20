package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// Test different WgSpriteBatch draw methods, sprites and blending

public class SpriteBatchDraw extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;
    private Sprite sprite;
    private float angleDegrees;

    @Override
    public void create() {
        texture = new WgTexture("data/webgpu.png", true);

        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();

        // texture is 256x256
        // sprite will cover only the bottom 80 pixels
        sprite = new Sprite(texture, 0, 176, 256, 80);
    }

    @Override
    public void render(  ){
        float delta = Gdx.graphics.getDeltaTime();
        angleDegrees += 30 * delta;

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // pass a clear color to batch begin
        batch.begin(Color.WHITE);
        batch.draw(texture, 0, 0);  // bottom left, full texture size

        batch.draw(texture, 0, 320, 128, 32); // resized

        sprite.setPosition(200, 200);
        sprite.setRotation(angleDegrees);

        // set tint to use from now on
        batch.setColor(Color.YELLOW);
        // smaller size at right bottom corner
        batch.draw(texture, Gdx.graphics.getWidth()-64, 0, 64, 64);

        // set tint to white with 50% transparency
        // note: blending is on by default
        batch.setColor(1,1,1,0.5f);
        // smaller size at top right corner, should appear semi-transparent
        batch.draw(texture, Gdx.graphics.getWidth()-64, Gdx.graphics.getHeight()-64, 64, 64);

        // now disable blending. tint still has 50% alpha
        batch.disableBlending();
        // smaller size at top right, should appear opaque
        batch.draw(texture, Gdx.graphics.getWidth()-128, Gdx.graphics.getHeight()-64, 64, 64);



        sprite.draw(batch);

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

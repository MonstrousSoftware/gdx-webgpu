package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// Test WgSpriteBatch with blending disabled before begin()
// (begin() should not reset the blending status)

public class SpriteBatchPipeline extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/webgpu.png", true);

        batch = new WgSpriteBatch();
        // by default blending is enabled

        // disable blending outside the begin/end brackets
        batch.disableBlending();
        viewport = new ScreenViewport();

    }

    @Override
    public void render() {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // disable blending before begin()
        // batch.disableBlending();
        // pass a clear color to batch begin
        batch.begin(Color.WHITE);
        batch.draw(texture, 0, 0); // bottom left, full texture size

        // set tint to use from now on
        batch.setColor(Color.YELLOW);
        // right bottom corner
        batch.draw(texture, Gdx.graphics.getWidth() - 64, 0, 64, 64);

        // set tint to white with 50% transparency
        // note: blending is on by default
        batch.setColor(1, 1, 1, 0.5f);
        // smaller size at top right corner, should appear semi-transparent
        batch.draw(texture, Gdx.graphics.getWidth() - 64, Gdx.graphics.getHeight() - 64, 64, 64);

        // now disable blending. tint still has 50% alpha
        // batch.disableBlending();
        // smaller size at top right, should appear opaque
        batch.draw(texture, Gdx.graphics.getWidth() - 128, Gdx.graphics.getHeight() - 64, 64, 64);

        // enable blending again. tint still has 50% alpha
        // batch.enableBlending();
        // smaller size towards centre top, should be semi-transparent
        batch.draw(texture, Gdx.graphics.getWidth() - 256, Gdx.graphics.getHeight() - 64, 64, 64);
        // batch.disableBlending();
        batch.end();
        // System.out.println("pipelines: "+batch.pipelineCount+" flushes: "+batch.flushCount);
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
    }

}

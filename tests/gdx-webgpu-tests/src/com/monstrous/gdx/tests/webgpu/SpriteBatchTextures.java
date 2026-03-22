package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// Test behaviour with many different textures, as they cause batch flushes

public class SpriteBatchTextures extends GdxTest {
    public static int NUM_SPRITES = 200;     // sprites per texture, max is 16384
    public static int NUM_TEXTURES = 10;

    private WgSpriteBatch batch;
    private WgSpriteBatch textBatch;
    private WgTexture[] textures;
    private ScreenViewport viewport;
    private WgBitmapFont font;
    private Pixmap pm;

    @Override
    public void create() {
        font = new WgBitmapFont();
        pm = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        textures = new WgTexture[NUM_TEXTURES];
        MathUtils.random.setSeed(1234);
        for (int i = 0; i < NUM_TEXTURES; i++)
            textures[i] = genTexture();

        // set max sprites per flush to NUM_SPRITES and max number of flushes to NUM_TEXTURES
        //
        batch = new WgSpriteBatch(NUM_SPRITES, null, NUM_TEXTURES);
        viewport = new ScreenViewport();

        // use a separate batch for text info to not affect the batch testing
        textBatch = new WgSpriteBatch();

    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // same random sprite positions per frame
        MathUtils.random.setSeed(1234);


        // pass a clear color to batch begin
        batch.begin(Color.TEAL);

        // best practice is to group sprites per texture to minimize the number of texture switches, i.e. render calls
        // so we iterate over the textures and draw some random sprites per texture
        // This does mean that if we set NUM_SPRITES too high, only the last texture will be visible
        // (Even better practice is to use a texture atlas, but this test is to demonstrate use of multiple textures)

        for (int i = 0; i < NUM_TEXTURES; i++) {
            for (int j = 0; j < NUM_SPRITES; j++) {
                int x = MathUtils.random(Gdx.graphics.getWidth() - 32);
                int y = MathUtils.random(Gdx.graphics.getHeight() - 32);
                batch.draw(textures[i], x, y, 32, 32);
            }
        }

        batch.end();

        textBatch.setProjectionMatrix(viewport.getCamera().combined);
        textBatch.begin();
        font.draw(textBatch, "fps: " + Gdx.graphics.getFramesPerSecond(), 10, 100);
        font.draw(textBatch, "numSprites: " + batch.numSprites, 10, 80);
        font.draw(textBatch, "numFlushes: " + batch.flushCount, 10, 60);
        textBatch.end();

    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // cleanup
        for (int i = 0; i < NUM_TEXTURES; i++)
            textures[i].dispose();
        batch.dispose();
    }

    private WgTexture genTexture() {

        Color bg = new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1);
        Color fg = new Color(MathUtils.random(), MathUtils.random(), MathUtils.random(), 1);
        pm.setColor(bg);
        pm.fill();
        pm.setColor(fg);
        pm.fillCircle(16, 16, 12);
        return new WgTexture(pm);
    }

}

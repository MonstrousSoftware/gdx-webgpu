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

// Stress test: Many sprites
// Vanilla libgdx supports a maximum of 8K sprites
// gdx-webgpu supports 16384 sprites with 16 bit indexes and will automatically switch to 32 bit index values
// if there are more than 16384 sprites and can in theory support billions of sprites (until you run out of memory etc.)
//
public class SpriteBatchWideIndices extends GdxTest {
    public static int NUM_SPRITES = 100000;  // note: exceeds 16384,ie more than 64K vertices
    public static int NUM_TEXTURES = 1;     // use one texture only
    public static int MAX_SPRITES_PER_FLUSH = NUM_SPRITES;

    private WgSpriteBatch batch;
    private WgSpriteBatch textBatch;
    private WgTexture[] textures;
    private ScreenViewport viewport;
    private WgBitmapFont font;
    private Pixmap pm;

    @Override
    public void create() {
        font = new WgBitmapFont();
        textures = new WgTexture[NUM_TEXTURES];
        pm = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        MathUtils.random.setSeed(123);
        for (int i = 0; i < NUM_TEXTURES; i++)
            textures[i] = genTexture();

        batch = new WgSpriteBatch(MAX_SPRITES_PER_FLUSH, null, NUM_TEXTURES);
        viewport = new ScreenViewport();

        // use a separate batch for text info to not affect the batch testing
        textBatch = new WgSpriteBatch();
    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        MathUtils.random.setSeed(1234);

        int W = Gdx.graphics.getWidth();
        int SZ = 4;
        int spritesPerLine = W / SZ;

        // pass a clear color to batch begin
        batch.begin(Color.TEAL);
        for (int i = 0; i < NUM_SPRITES; i++) {
            int x = (i % spritesPerLine) * SZ;
            int y = (i / spritesPerLine) * SZ;
            WgTexture texture = textures[MathUtils.random(NUM_TEXTURES - 1)];
            batch.draw(texture, x, y, SZ, SZ);
        }
        batch.end();

        textBatch.setProjectionMatrix(viewport.getCamera().combined);
        textBatch.begin();
        font.setColor(Color.BLACK);
        font.draw(textBatch, "Demo of more than 16K sprites" , 10, 120);
        font.draw(textBatch, "fps: " + Gdx.graphics.getFramesPerSecond(), 10, 100);
        font.draw(textBatch, "numSprites: " + batch.numSprites, 10, 80);
        font.draw(textBatch, "numFlushes: " + batch.flushCount, 10, 60);
        textBatch.end();

    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // cleanup
        for (int i = 0; i < NUM_TEXTURES; i++)
            textures[i].dispose();
        batch.dispose();
        textBatch.dispose();
        pm.dispose();
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

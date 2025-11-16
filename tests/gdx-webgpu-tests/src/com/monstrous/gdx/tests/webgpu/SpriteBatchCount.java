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

// Stress test: Place lots of different textures on the screen.
// Almost every texture will cause a flush (texture swap)
//
public class SpriteBatchCount extends GdxTest {
    public static int NUM_SPRITES = 10000;
    public static int NUM_TEXTURES = 100;

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
        for (int i = 0; i < NUM_TEXTURES; i++)
            textures[i] = genTexture();

        // since every sprite will have a random texture, (almost) every sprite
        // will cause a batch flush (unless the texture is the same as the previous sprite)
        // So we need to allow for one flush per sprite.
        batch = new WgSpriteBatch(NUM_SPRITES, null, NUM_SPRITES);
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
        font.setColor(Color.YELLOW);
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

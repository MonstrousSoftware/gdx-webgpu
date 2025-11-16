
package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// demonstrates use of WebPUBitmapFont
//
public class FontTest extends GdxTest {
    private WgSpriteBatch batch;
    private BitmapFont font;

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();

        // font = new WgBitmapFont(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"));
    }

    @Override
    public void render() {

        batch.begin();
        font.draw(batch, "Hello, world!", 200, 200);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }

}

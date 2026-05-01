package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

/** Test processing of image file with 8 bits per pixel.
 * Note: the texture is rendered in red as the format is converted to R8Unorm (i.e. one byte red channel).
 * In OpenGL this was rendered instead as GL_ALPHA.
 *
 */
public class TextureGreyscale extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/greyscale.png", false);
        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        batch.begin(Color.WHITE);
        batch.draw(texture, 0, 0);
        batch.end();
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

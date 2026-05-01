package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

/** Test processing of image file with 8 bits per pixel.
 * Note: the texture stored in format R8Unorm (i.e. one byte red channel).
 * We use a dedicated shader to show the red channel as grey.
 *
 * In OpenGL libgdx, a pixmap with Alpha format
 * is rendered instead as GL_ALPHA.  This is deprecated in OpenGL and
 * not a format for WebGPU.
 *
 */
public class TextureGreyscale extends GdxTest {

    private WgSpriteBatch batch;
    private WgShaderProgram shader;
    private WgTexture texture;
    private ScreenViewport viewport;

    @Override
    public void create() {
        texture = new WgTexture("data/greyscale.png", false);
        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
        shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/sprite-red-to-grey.wgsl"));
    }

    @Override
    public void render() {

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        float x = (Gdx.graphics.getWidth() - texture.getWidth())/2f;
        float y = (Gdx.graphics.getHeight() - texture.getHeight())/2f;

        batch.begin(Color.WHITE);
        batch.setShader(shader);
        batch.draw(texture, x, y);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // cleanup
        texture.dispose();
        batch.dispose();
        shader.dispose();
    }

}

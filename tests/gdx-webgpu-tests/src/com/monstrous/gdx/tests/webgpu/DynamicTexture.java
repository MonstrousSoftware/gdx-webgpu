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

/** Demo of updating an existing texture within the render loop using
 * a pixmap.
 */
public class DynamicTexture extends GdxTest {

    private final static int SIZE = 256;

    private WgSpriteBatch batch;
    private WgTexture texture;
    private ScreenViewport viewport;
    private WgBitmapFont font;
    private Pixmap pm;
    private int counter;

    @Override
    public void create() {
        font = new WgBitmapFont();

        pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        texture = new WgTexture(pm);

        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render() {
        counter++;
        updatePixmap(pm, counter);
        texture.load(pm.getPixels(), SIZE, SIZE);   // modify texture with new pixmap contents

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        // pass a clear color to batch begin
        batch.begin(Color.TEAL);

        // draw centred texture
        batch.draw(texture, (Gdx.graphics.getWidth()-texture.getWidth())/2f, (Gdx.graphics.getHeight()-texture.getHeight())/2f);

        font.setColor(Color.BLACK);
        font.draw(batch, "Demo of a WgTexture being updated every frame from a Pixmap using WgTexture::load()", 10, 80);
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 10, 60);
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
        pm.dispose();
    }

    // update pixmap contents based on counter value
    private void updatePixmap(Pixmap pm, int counter){
        Color bg = Color.DARK_GRAY;
        Color fg = Color.GREEN;
        int cx = SIZE/2;
        int cy = SIZE/2;
        pm.setColor(bg);
        pm.fill();
        pm.setColor(fg);
        pm.fillCircle(cx, cy, counter % (SIZE/2));
        pm.setColor(Color.WHITE);
        int px = (int) (SIZE/2f * Math.sin(counter / 200f));
        int py = (int) (SIZE/2f * Math.cos(counter / 200f));
        pm.drawLine(cx, cy, cx+px, cy+py);
    }

}

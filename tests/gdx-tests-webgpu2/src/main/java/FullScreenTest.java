
package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.webgpu.application.WebGPUApplication2;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;


public class FullScreenTest extends ApplicationAdapter {
    private WgSpriteBatch batch;
    private BitmapFont font;
    private WgTexture texture;
    private Viewport viewport;
    private int savedWidth, savedHeight;

    public static void main(String[] argv) {
        new WgDesktopApplication(new FullScreenTest());
    }

    @Override
    public void create () {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        texture = new WgTexture("data/badlogic.jpg");
        viewport = new ScreenViewport();

        // since the content is static we could use non-continuous rendering
        Gdx.graphics.setContinuousRendering(false);
    }

    @Override
    public void render () {
        System.out.println("render");

        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit();
        }
        if(Gdx.input.isKeyJustPressed(Input.Keys.F11)){
            boolean fullScreen = Gdx.graphics.isFullscreen();
            WgGraphics.DisplayMode currentMode = Gdx.graphics.getDisplayMode();
            if (fullScreen)
                Gdx.graphics.setWindowedMode(savedWidth, savedHeight);
            else {
                savedWidth = Gdx.graphics.getWidth();
                savedHeight = Gdx.graphics.getHeight();
                Gdx.graphics.setFullscreenMode(currentMode);
            }
            return;
        }


        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        batch.draw(texture, 100, 100);
        font.draw(batch, "Press F11 to toggle between window and full screen mode", 10, 70);

        batch.end();
    }


    @Override
    public void resize (int width, int height) {
       Gdx.app.log("resize", "");
       if(width ==0 || height == 0)
           return;
       viewport.update(width, height, true);
    }

    @Override
    public void dispose () {
        batch.dispose();
        font.dispose();
        texture.dispose();
    }

}

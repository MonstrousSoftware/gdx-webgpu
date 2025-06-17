package com.badlogic.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.badlogic.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.badlogic.gdx.webgpu.graphics.WgTexture;

public class ViewportTest extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture background;
    private Viewport viewport;
    private Viewport[] viewports;
    private String[] names;
    private int index;
    private WgBitmapFont font;
    private boolean keyUp = true;

    // launcher
    public static void main (String[] argv) {
        WgApplicationConfiguration config = new WgApplicationConfiguration();
        config.setWindowedMode(1200, 480);

        new WgApplication(new ViewportTest(), config);
    }

    // demonstrate a custom viewport that shows the content only in a box at the centre of the screen
    // i.e. not using the full window.
    public static class WindowViewport extends Viewport {
        public WindowViewport(float worldWidth, float worldHeight ) {
            this(worldWidth, worldHeight, new OrthographicCamera());
        }

        public WindowViewport(float worldWidth, float worldHeight, Camera camera ) {
            setWorldSize(worldWidth, worldHeight);
            setCamera( camera );
        }

        @Override
        public void update (int screenWidth, int screenHeight, boolean centerCamera) {
            setScreenBounds(screenWidth/4, screenHeight/4, screenWidth/2, screenHeight/2);
            apply(centerCamera);
        }
    }

    @Override
    public void create() {

        background = new WgTexture("data/simplegame/background.png", true);

        batch = new WgSpriteBatch();

        getViewports();
        index = 0;
        viewport = viewports[index];

        font = new WgBitmapFont();
    }

    @Override
    public void render(  ){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){

            Gdx.app.exit();
            return;
        }
        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            if(keyUp) {
                index = (index + 1) % viewports.length;
                viewport = viewports[index];
                resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                System.out.println("Switch to " + names[index]);
            }
            keyUp = false;
        } else
            keyUp = true;


        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);


        batch.begin(Color.BLUE);
        batch.draw(background, 0,0);
        font.draw(batch, names[index], 50, 90);
        font.draw(batch, "Press SPACE to switch viewport. ESCAPE to quit.", 50, 30);
        batch.end();
    }

    @Override
    public void dispose(){
        // cleanup
        background.dispose();
        batch.dispose();
        font.dispose();
    }

    @Override
    public void resize(int width, int height) {

        viewport.update(width, height, true); // true centers the camera
    }

    private void getViewports(){
        viewports = new Viewport[4];

        viewports[0] = new StretchViewport(800, 500);
        viewports[1] = new ScreenViewport();
        viewports[2] = new FitViewport(800,500);
        viewports[3] = new WindowViewport(800,500);

        names = new String[]{ "StretchViewport", "ScreenViewport", "FitViewport", "WindowViewport"};
    }


}

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
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplicationConfiguration;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUSpriteBatch;
import com.badlogic.gdx.webgpu.wrappers.WebGPUTexture;

public class ScissorTest extends GdxTest {

    private WebGPUSpriteBatch batch;
    private WebGPUTexture background;
    private Viewport viewport;
    private Viewport[] viewports;
    private String[] names;
    private int index;
    private WebGPUBitmapFont font;
    private boolean keyUp = true;
    private int x,y;
    private int dx, dy;

    // launcher
    public static void main (String[] argv) {
        WebGPUApplicationConfiguration config = new WebGPUApplicationConfiguration();
        config.setWindowedMode(640, 480);

        new WebGPUApplication(new ScissorTest(), config);
    }


    @Override
    public void create() {

        background = new WebGPUTexture("data/simplegame/background.png", true);

        batch = new WebGPUSpriteBatch();

        font = new WebGPUBitmapFont();
        x = 100;
        y = 100;
        dx = 1;
        dy = 1;
    }

    @Override
    public void render(  ){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){

            Gdx.app.exit();
            return;
        }

        Gdx.gl.glScissor(x,y, 300, 200);

        x += dx;
        y += dy;
        if(x == 0){
            dx = 1;
        } else if (x +300 >= Gdx.graphics.getWidth() ){
            dx = -1;
        }
        if(y == 0){
            dy = 1;
        } else if (y + 200 >= Gdx.graphics.getHeight() ){
            dy = -1;
        }


        batch.begin(Color.BLUE);
        batch.draw(background, 0,0);

        font.draw(batch, "ESCAPE to quit.", 50, 30);
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

    }


}

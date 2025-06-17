package com.badlogic.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.badlogic.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.badlogic.gdx.webgpu.graphics.WgTexture;

public class ScissorTest extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture background;
    private WgBitmapFont font;
    private int x,y;
    private int dx, dy;

    // launcher
    public static void main (String[] argv) {
        new WgApplication(new ScissorTest());
    }


    @Override
    public void create() {

        background = new WgTexture("data/simplegame/background.png", true);

        batch = new WgSpriteBatch();

        font = new WgBitmapFont();
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

        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor(x,y, 300, 200);    // fake GL call
        //Gdx.gl.glViewport(x,y, 300, 200);    // fake GL call

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
        // todo
    }


}

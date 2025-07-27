package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class TestTexture6 extends GdxTest {

    private WgSpriteBatch batch;
    private WgTexture[] textures;
    private ScreenViewport viewport;

//    String[] sides = { "PX.png","NX.png", "PY.png", "NY.png", "PZ.png", "NZ.png"  };
//    //String prefix = "data/g3d/environment/debug_";
//    String prefix = "data/g3d/environment/environment_01_";

     String[] sides = {  "pos-x.jpg","neg-x.jpg", "pos-y.jpg","neg-y.jpg", "pos-z.jpg", "neg-z.jpg"   };
     String prefix = "data/g3d/environment/leadenhall/";

    @Override
    public void create() {
        textures = new WgTexture[6];

        for(int i = 0; i < 6; i++) {
            String fileName = prefix + sides[i];
            textures[i] = new WgTexture(fileName, false);
        }
        batch = new WgSpriteBatch();
        viewport = new ScreenViewport();
    }

    @Override
    public void render(  ){

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        batch.begin(Color.WHITE);
        for(int i = 0; i < 6; i++)
            batch.draw(textures[i], 64*i, 0, 64, 64);
        batch.end();
    }

    @Override
    public void resize (int width, int height) {
        Gdx.app.log("resize", "");
        viewport.update(width, height, true);
    }

    @Override
    public void dispose(){
        // cleanup
        for(int i = 0; i < 6; i++)
            textures[i].dispose();
        batch.dispose();
    }



}

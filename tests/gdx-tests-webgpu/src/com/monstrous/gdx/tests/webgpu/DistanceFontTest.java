
package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgDistanceFieldFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// demonstrates use of SDF font
//
public class DistanceFontTest extends GdxTest {
    private WgSpriteBatch batch;
    private BitmapFont font;
    private WgShaderProgram shader;

    @Override
    public void create () {

        shader = WgDistanceFieldFont.createDistanceFieldShader();
        batch = new WgSpriteBatch();
        font = new WgDistanceFieldFont(Gdx.files.classpath("lsans32-sdf.fnt"));
    }

    @Override
    public void render () {

        batch.begin();
        batch.setShader(shader);
        font.draw(batch, "Hello, world!", 200, 200);
        batch.setShader((WgShaderProgram) null);
        batch.end();
    }


    @Override
    public void resize (int width, int height) {
        Gdx.app.log("resize", "");
    }

    @Override
    public void dispose () {
        batch.dispose();
        font.dispose();
    }

}

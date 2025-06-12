
package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUSpriteBatch;

// demonstrates use of WebPUBitmapFont
//
public class FontTest extends GdxTest {
		private WebGPUSpriteBatch batch;
		private BitmapFont font;

    public static void main (String[] argv) {
        new WebGPUApplication(new FontTest());
    }

    @Override
    public void create () {
        batch = new WebGPUSpriteBatch();
        font = new WebGPUBitmapFont();

        //font = new WebGPUBitmapFont(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"));
    }

		@Override
		public void render () {

			batch.begin();
			font.draw(batch, "Hello, world!", 200, 200);
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

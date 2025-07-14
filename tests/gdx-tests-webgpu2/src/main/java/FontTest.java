
package main.java;


import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.github.xpenatan.webgpu.WGPUBlendFactor;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

// demonstrates use of WgBitmapFont
//
public class FontTest extends ApplicationAdapter {
		private WgSpriteBatch batch;
		private BitmapFont font;

    public static void main(String[] argv) {
        new WgDesktopApplication(new FontTest());
    }

    @Override
    public void create () {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();

        //font = new WgBitmapFont(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"));
    }

		@Override
		public void render () {

			batch.begin();
            batch.setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);

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

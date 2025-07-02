
package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.scene2d.WgStage;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

// demonstrates the use of Scene2d
//
public class WebGPUTestScene2d {

	// launcher
	public static void main (String[] argv) {

		WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WgDesktopApplication(new TestApp(), config);
	}

	// application
	static class TestApp extends ApplicationAdapter {
		private WgSpriteBatch batch;
		private WgTexture texture;
		private WgStage stage;
		private Image image;
		private TextureRegion region;
		private TextureRegionDrawable drawable;

		public void create () {
			batch = new WgSpriteBatch();
			texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));

			stage = new WgStage();

			region = new TextureRegion(texture, 0, 0, 0.5f, 0.5f);
			drawable = new TextureRegionDrawable(region);
			image = new Image(drawable);
			image.setPosition(10,10);	// position goes from bottom left of the screen and related to the bottom left of the image
			stage.addActor(image);

		}

		@Override
		public void render () {
			batch.begin(Color.FOREST);
			batch.draw(region, 300, 400, 100, 100);

			// this should show the top left quadrant, but upside down
			batch.draw(texture, 300, 10, 128, 128, 0, 0, 0.5f, 0.5f);

			// this should show the top left quadrant, and the right way round
			batch.draw(region, 300, 300);

			//image.getDrawable().draw(batch, 0,0,120, 120);
			batch.end();

			stage.act();
			stage.draw();
		}


		@Override
		public void resize (int width, int height) {
			batch.getProjectionMatrix().setToOrtho2D(0,0,Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}

		@Override
		public void dispose () {
			batch.dispose();
			texture.dispose();
			stage.dispose();
		}


	}
}

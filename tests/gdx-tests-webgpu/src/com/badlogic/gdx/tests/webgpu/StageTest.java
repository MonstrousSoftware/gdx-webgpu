
package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplicationConfiguration;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUSpriteBatch;
import com.badlogic.gdx.webgpu.graphics.viewport.WebGPUScreenViewport;
import com.badlogic.gdx.webgpu.scene2d.WebGPUSkin;
import com.badlogic.gdx.webgpu.scene2d.WebGPUStage;
import com.badlogic.gdx.webgpu.wrappers.WebGPUTexture;

// demonstrates the use of WebGPUSpriteBatch
// shows texture from file, texture from pixmap, texture region, sprite
//
public class StageTest extends GdxTest {
		private WebGPUSpriteBatch batch;
		private WebGPUScreenViewport viewport;
		private WebGPUStage stage;
		private WebGPUSkin skin;
		private WebGPUTexture texture;

		// launcher
		public static void main (String[] argv) {

			WebGPUApplicationConfiguration config = new WebGPUApplicationConfiguration();
			config.setWindowedMode(640, 480);
			config.setTitle("WebGPUTest");

			new WebGPUApplication(new StageTest(), config);


		}

		public void create () {
			Matrix4 mat = new Matrix4();
			int w = Gdx.graphics.getWidth();
			int h =  Gdx.graphics.getHeight();
			mat.setToOrtho(0, w, 0, h, 1, -1);
			System.out.println(mat.toString());

			viewport = new WebGPUScreenViewport();
			batch = new WebGPUSpriteBatch();

			stage = new WebGPUStage(viewport);
			//stage.enableDebug(false);
			stage.setDebugAll(true);
			Gdx.input.setInputProcessor(stage);

			skin = new WebGPUSkin(Gdx.files.internal("data/uiskin.json"));

			Button button = new Button(skin);


			Label label = new Label("Label text", skin);
			TextButton textButton = new TextButton("Text Button", skin);
			// Add a listener to the button. ChangeListener is fired when the button's checked state changes, eg when clicked,
			// Button#setChecked() is called, via a key press, etc. If the event.cancel() is called, the checked state will be reverted.
			// ClickListener could have been used, but would only fire when clicked. Also, canceling a ClickListener event won't
			// revert the checked state.
			textButton.addListener(new ChangeListener() {
				public void changed (ChangeEvent event, Actor actor) {
					System.out.println("Clicked! Is checked: " + button.isChecked());
					textButton.setText("Good job!");
				}
			});
			texture = new WebGPUTexture(Gdx.files.internal("data/badlogic.jpg"));
			Image image = new Image(texture);
			Slider slider = new Slider(0, 100, 20, false, skin);
			slider.debug();

			Table table = new Table();
			table.setFillParent(true);

			table.add(label);
			table.row();
			table.add(button).width(100);
			table.row();
			table.add(textButton);
			table.row();
			table.add(image);
			table.row();
			table.add(slider);
			table.debug();

			stage.addActor(table);


		}

		@Override
		public void render () {

			stage.act();
			stage.draw();
		}


		@Override
		public void resize (int width, int height) {
			Gdx.app.log("resize", "");
			stage.getViewport().update(width, height, true);

		}

		@Override
		public void dispose () {

			stage.dispose();
			texture.dispose();

		}

}

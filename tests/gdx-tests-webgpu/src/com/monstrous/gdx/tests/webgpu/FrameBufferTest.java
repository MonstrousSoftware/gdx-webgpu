/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgObjLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureUsage;

/** Demonstrate wgFrameBuffer use. */


public class FrameBufferTest extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
	PerspectiveCamController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	Model model;
	ModelInstance instance;
    WebGPUGraphicsBase gfx;
    WgFrameBuffer fbo;


	// launcher
	public static void main (String[] argv) {

		WgApplicationConfiguration config = new WgApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WgApplication(new FrameBufferTest(), config);
	}

	// application
	public void create () {
        gfx = (WebGPUGraphicsBase)Gdx.graphics;

		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 2, 4);
		cam.lookAt(0,0,0);
		cam.near = 0.1f;

		WgObjLoader loader = new WgObjLoader();
		// these assets need to be put in the class path...
		model = loader.loadModel(Gdx.files.internal("data/g3d/ducky.obj"), true);
		instance = new ModelInstance(model);


		controller = new PerspectiveCamController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        fbo = new WgFrameBuffer(WGPUTextureFormat.BGRA8Unorm,  640, 480, true);
	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		instance.transform.rotate(Vector3.Y, 15f*delta);


        fbo.begin();

		WgScreenUtils.clear(Color.TEAL);

		cam.update();
		modelBatch.begin(cam);

		modelBatch.render(instance);

		modelBatch.end();

        fbo.end();


		batch.begin();
        batch.draw(fbo.getColorBufferTexture(), 20, 100, 256, 180);
        batch.draw(fbo.getColorBufferTexture(), 320+20, 100, 256, 180);
		font.draw(batch, "Using Texture as Render Target" , 0, 20);
		batch.end();
	}

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();

	}

	@Override
	public void dispose () {
		batch.dispose();
		font.dispose();
		modelBatch.dispose();
		model.dispose();
        fbo.dispose();
	}


}

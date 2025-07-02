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
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.utils.WgShapeRenderer;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

public class ShapeRenderer2DTest extends GdxTest {

	WgShapeRenderer renderer;
	Camera cam;
	WgSpriteBatch batch;
	WgTexture texture;

	// launcher
	public static void main (String[] argv) {

		WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WgDesktopApplication(new ShapeRenderer2DTest(), config);
	}

	public void create () {
		renderer = new WgShapeRenderer();

		cam = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		// important to set near and far for WebGPU clip space
		cam.near = -1;
		cam.far = 1;
		cam.update();

		System.out.println(cam.combined.toString());

		batch = new WgSpriteBatch();
		texture = new WgTexture(Gdx.files.internal("data/badlogicsmall.jpg"));
	}

	public void render () {

		WgScreenUtils.clear(Color.TEAL);

		renderer.setProjectionMatrix(cam.combined);

		renderer.begin(WgShapeRenderer.ShapeType.Line);
		renderer.setColor(1, 0, 1, 0.5f);
		// note: shape renderer has (0,0) at centre screen, unlike SpriteBatch
		renderer.circle(0, 0, 50);
		renderer.end();

		// bug: using multiple begin/end pairs leads to errors

//		renderer.begin(WebGPUShapeRenderer.ShapeType.Line);
//		renderer.setColor(Color.RED);
//		// note: shape renderer has (0,0) at centre screen, unlike SpriteBatch
//		renderer.circle(0, 0, 75);
//		renderer.end();
//
//
//		batch.setProjectionMatrix(cam.combined);
//		batch.begin();
//		batch.draw(texture, 100, 100);
//		batch.end();
	}

	@Override
	public void dispose () {
		renderer.dispose();
		batch.dispose();
		texture.dispose();
	}
}

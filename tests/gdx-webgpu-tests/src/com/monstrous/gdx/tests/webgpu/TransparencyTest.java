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
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/** Test rendering of semi-transparent models
 *
 */
public class TransparencyTest extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
    CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
    Model box;
    Array<ModelInstance> instances;


	public void create () {
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 0, 0);
		cam.near = 0.1f;
        cam.far = 100;


		//controller = new PerspectiveCamController(cam);
        controller = new CameraInputController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont();

        instances = new Array<>();

		//
		// Create some model instances
		//
        ModelBuilder modelBuilder = new WgModelBuilder();
        ColorAttribute colorAttribute = new ColorAttribute(ColorAttribute.Diffuse, 0, 1, 0, 0.5f);
        BlendingAttribute blendingAttribute = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Material mat = new Material(colorAttribute, blendingAttribute);
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates | VertexAttributes.Usage.Normal ;
        box = modelBuilder.createBox(1, 1, 1, mat, attribs);

        blendingAttribute.opacity = 0.25f;

        instances.add( new ModelInstance(box, 0, 0, -2));
        instances.add( new ModelInstance(box, 0, 0, -5));
        instances.add( new ModelInstance(box, 0, 0, -10));


	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();

		WgScreenUtils.clear(Color.TEAL, true);
		cam.update();
		modelBatch.begin(cam);
		modelBatch.render(instances);
		modelBatch.end();


		batch.begin();
		font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, 20);
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
		box.dispose();
	}

}

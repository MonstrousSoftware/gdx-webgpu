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
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.GammaCorrection;

/** Test fog
 *  We can set the fog color via the environment, this should normally be the same as the background color.
 *  Fog strength is determined by distance to the camera.  At the camera's far plane the fog reaches 100%.
 * */
public class FogTest extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
    CameraInputController controller;
    Model box;
    ModelInstance instance;
    Environment environment;
	Color fogColor = Color.DARK_GRAY;

	public void create () {
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(30,10,30);
        cam.lookAt(0,0,0);
		cam.near = 0.1f;
        cam.far = 45f;

        controller = new CameraInputController(cam);
		Gdx.input.setInputProcessor(controller);

		//
		// Create some model instances
		//
        ModelBuilder modelBuilder = new WgModelBuilder();
        Material mat = new Material(ColorAttribute.createDiffuse(Color.GREEN));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal ;
        box = modelBuilder.createBox(5, 5, 5, mat, attribs);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1.f));
		environment.set(new ColorAttribute(ColorAttribute.Fog,fogColor));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -0.3f, -1f, -0.2f));

        instance = new ModelInstance(box, 0,0,0);
	}

	public void render () {
		animate();

		WgScreenUtils.clear(fogColor, true);
		cam.update();
		modelBatch.begin(cam);
		modelBatch.render(instance, environment);
		modelBatch.end();
	}

    float dir = 1;

    private void animate () {

        float delta = Gdx.graphics.getDeltaTime();

        instance.transform.val[14] += delta * 16 * dir;

        if (instance.transform.val[14] > 30 || instance.transform.val[14] < -5) {
            dir *= -1;
        }
    }

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();

	}

	@Override
	public void dispose () {
		modelBatch.dispose();
		box.dispose();
	}

}

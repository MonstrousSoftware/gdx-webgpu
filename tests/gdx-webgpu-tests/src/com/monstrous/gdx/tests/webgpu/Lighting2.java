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
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.UBJsonReader;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgG3dModelLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

/** Test lights in environment
 * - directional lights, point lights, ambient light
 * */


public class Lighting2 extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
	PerspectiveCamController controller;
	Model model;
	Array<ModelInstance> instances;
	Environment environment;
	ScreenViewport viewport;
    //WgStage stage;
    WgSkin skin;



	// application
	public void create () {

        System.out.println("Create");
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(50, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, -0.5f, 2.5f);
		cam.lookAt(0,0,0);
		cam.near = 0.1f;

		// create a model instance
		instances = new Array<>();
		WgG3dModelLoader loader = new WgG3dModelLoader(new UBJsonReader());
		model = loader.loadModel(Gdx.files.internal("data/g3d/head.g3db"));
		ModelInstance instance = new ModelInstance(model, 0, -1, 0);

		instances.add(instance);

		environment = new Environment();
//		ColorAttribute ambient =  ColorAttribute.createAmbientLight(0.0f, 0f, 0f, 1f);
//		environment.set(ambient);

        controller = new PerspectiveCamController(cam);

        viewport = new ScreenViewport();


        InputMultiplexer im = new InputMultiplexer();
        Gdx.input.setInputProcessor(im);
        im.addProcessor(controller);

        System.out.println("Loading skin");
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));
        System.out.println("Loaded skin");

	}



	public void render () {
        System.out.println("Render");

		WgScreenUtils.clear(Color.TEAL, true);

		modelBatch.begin(cam);
		modelBatch.render(instances, environment);
		modelBatch.end();
        System.out.println("end of Render");

	}

	@Override
	public void resize(int width, int height) {
        System.out.println("Resize");
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();

	}

	@Override
	public void dispose () {

		modelBatch.dispose();
		model.dispose();
	}


}

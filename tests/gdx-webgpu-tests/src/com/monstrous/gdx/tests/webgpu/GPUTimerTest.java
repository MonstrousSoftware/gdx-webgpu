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
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.assets.WgAssetManager;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgObjLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

/** Demonstrates gpu timer measurements.
 * */


public class GPUTimerTest extends GdxTest {
	final static int MAX_INSTANCES = 10000;

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
	CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	Model model;
	Array<ModelInstance> instances;
	Environment environment;
	ScreenViewport viewport;
	WgStage stage;
	WgSkin skin;
	WgAssetManager assets;
    WgGraphics gfx;
    WebGPUContext webgpu;



	// application
	public void create () {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        WgModelBatch.Config config = new WgModelBatch.Config();
		config.maxInstances = MAX_INSTANCES;
		modelBatch = new WgModelBatch(config);

		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 1.f, 2.5f);
		cam.lookAt(0,0,0);
		cam.near = 0.1f;

		// create a model instance
		instances = new Array<>();

		// queue for asynchronous loading
		assets = new WgAssetManager();

        // pass extra parameter to flip texture V
        assets.load("data/g3d/ducky.obj", Model.class, new WgObjLoader.ObjLoaderParameters(true));
		assets.finishLoading();

		model = assets.get("data/g3d/ducky.obj");
		ModelInstance instance = new ModelInstance(model, 0, -1, 0);

		instances.add(instance);

		for(float z = -3; z > -20; z-= 2) {
            for (float x = -25; x < 25; x += 1) {
                instance = new ModelInstance(model, x, -1, z);
				instance.transform.rotate(Vector3.Y, (float)Math.random() * 360f);
                instances.add(instance);
            }
		}

		// Create an environment with lights
		environment = new Environment();

		float ambientLevel = 0.7f;
		ColorAttribute ambient =  ColorAttribute.createAmbientLight(ambientLevel, ambientLevel, ambientLevel, 1f);
		environment.set(ambient);

		DirectionalLight dirLight1 = new DirectionalLight();
		dirLight1.setDirection(1f, -.2f, .2f);
		dirLight1.setColor(Color.BLUE);

		DirectionalLight dirLight2 = new DirectionalLight();
		dirLight2.setDirection(-1f, -.2f, .2f);
		dirLight2.setColor(Color.RED);

		DirectionalLight dirLight3 = new DirectionalLight();
		dirLight3.setDirection(-.2f, -.6f, -.2f);
		dirLight3.setColor(Color.GREEN);

		PointLight pointLight1 = new PointLight();
		pointLight1.setPosition(-1f, 2f, -1f);
		pointLight1.setColor(Color.PURPLE);
		pointLight1.setIntensity(1f);

		PointLight pointLight2 = new PointLight();
		pointLight2.setPosition(1f, 2f, 1f);
		pointLight2.setColor(Color.YELLOW);
		pointLight2.setIntensity(1f);




        //controller = new PerspectiveCamController(cam);
        controller = new CameraInputController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

		// Add some GUI
		//
		viewport = new ScreenViewport();
		stage = new WgStage(viewport);
		//stage.setDebugAll(true);

		InputMultiplexer im = new InputMultiplexer();
		Gdx.input.setInputProcessor(im);
		im.addProcessor(stage);
		im.addProcessor(controller);

		skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));


		Slider instancesSlider = new Slider(1, MAX_INSTANCES, 10, false, skin);
		instancesSlider.addListener(new ChangeListener() {
			public void changed (ChangeEvent event, Actor actor) {
				System.out.println("Instances: " + instancesSlider.getValue());
				float n = instancesSlider.getValue();
				int nn = (int) Math.sqrt(n);
				instances.clear();
				int rz = 3*nn;
				int rx = 3*nn/2;
				for(float z = -3; z > -(3+rz); z-= 3) {
					for (float x = -rx; x < rx; x += 3) {
						ModelInstance instance = new ModelInstance(model, x, -1, z);
						instance.transform.rotate(Vector3.Y, (float)Math.random() * 360f);
						instances.add(instance);
					}
				}

			}
		});

		Table screenTable = new Table();
		screenTable.setFillParent(true);
		Table controls = new Table();
		controls.add(new Label("numInstances:", skin)).align(Align.left).row();
		controls.add(instancesSlider).align(Align.left).row();
		screenTable.add(controls).left().top().expand();


		stage.addActor(screenTable);
	}



	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		for(ModelInstance instance : instances)
			instance.transform.rotate(Vector3.Y, 15f*delta);

        viewport.apply();
		WgScreenUtils.clear(Color.TEAL, true);

		cam.update();
		modelBatch.begin(cam);

		modelBatch.render(instances, environment);

		modelBatch.end();


        batch.setProjectionMatrix(viewport.getCamera().combined);
		batch.begin();
		int y = 250;
		font.draw(batch, "Draw calls: "+modelBatch.drawCalls+" shader switches: "+modelBatch.shaderSwitches,0, y -= 20);
		font.draw(batch, "numRenderables: "+modelBatch.numRenderables ,0, y -= 20);
		font.draw(batch, "Materials: "+modelBatch.numMaterials ,0, y -= 20);
		font.draw(batch, "FPS: "+Gdx.graphics.getFramesPerSecond() ,0, y -= 20);
        font.draw(batch, "delta time: "+(int)(1000000/(Gdx.graphics.getFramesPerSecond()+0.001f))+" microseconds",0, y -= 20);

        for(int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
            font.draw(batch, "GPU time (pass "+pass+" "+webgpu.getGPUTimer().getPassName(pass)+") : "+(int)webgpu.getAverageGPUtime(pass)+ " microseconds" ,0, y -= 20);
		batch.end();

		stage.act();
		stage.draw();
	}

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();
        viewport.update(width, height, true);

	}

	@Override
	public void dispose () {
		batch.dispose();
		font.dispose();
		modelBatch.dispose();
		model.dispose();
		stage.dispose();
		skin.dispose();
	}


}

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
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgDirectionalShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgMeshBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/** Shadow demo.
 * */
public class ShadowTest extends GdxTest {

	WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
	PerspectiveCamera cam;
	PerspectiveCamController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
    Model box;
    Model ground;
    Array<ModelInstance> instances;
    Environment environment;
    WgDirectionalShadowLight shadowLight;


	// launcher
	public static void main (String[] argv) {

		WgApplicationConfiguration config = new WgApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WgApplication(new ShadowTest(), config);
	}

	public void create () {
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 1, 5);
		cam.near = 0.1f;
        cam.lookAt(0,0,0);

        shadowBatch = new WgModelBatch();   // to do provider

		controller = new PerspectiveCamController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

		//
		// Create some renderables
		//
        instances = new Array<>();

        ModelBuilder modelBuilder = new WgModelBuilder();
        WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat = new Material(TextureAttribute.createDiffuse(texture2));
        Material mat2 = new Material(ColorAttribute.createDiffuse(Color.GREEN));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates | VertexAttributes.Usage.Normal ;
        box = modelBuilder.createBox(1, 1, 1, mat, attribs);

        long attribs2 = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked;
        ground = modelBuilder.createBox(10, 0.1f, 10, mat2, attribs2);

        instances.add(new ModelInstance(box,0,1.5f,0));
        instances.add(new ModelInstance(ground,0,0,0));

        environment = new Environment();

        float level = 0.2f;
        ColorAttribute ambient =  ColorAttribute.createAmbientLight(level, level, level, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(.2f, -.8f, .2f);
        dirLight1.setColor(Color.BLUE);
        environment.add(dirLight1);


        final int MAP = 256;
        final int VP = 5;
        shadowLight = new WgDirectionalShadowLight(MAP, MAP, VP, VP, 0.1f, 50f);
        shadowLight.set(dirLight1);
	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
        instances.get(0).transform.rotate(Vector3.Y, 15f*delta);



		cam.update();

        shadowLight.begin(new Vector3(0, 5, 0), Vector3.Zero);

		shadowBatch.begin(shadowLight.getCamera(), Color.RED);
        shadowBatch.render(instances);
        shadowBatch.end();
        shadowLight.end();

        WgScreenUtils.clear(Color.TEAL);
        modelBatch.begin(cam);
        modelBatch.render(instances, environment);
        modelBatch.end();

		batch.begin();
        batch.draw(shadowLight.getDepthMap(), 0, 0, 256, 256);
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
        ground.dispose();
	}

}

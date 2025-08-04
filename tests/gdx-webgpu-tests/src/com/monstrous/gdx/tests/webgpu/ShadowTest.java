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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;

import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

/** Shadow demo.
 * */
public class ShadowTest extends GdxTest {

	WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
	PerspectiveCamera cam;
	CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
    Model box;
    Model ground;
    Model lightModel;
    ModelInstance lightInstance;
    Array<ModelInstance> instances;
    Environment environment;
    Environment emptyEnvironment;
    com.monstrous.gdx.webgpu.graphics.g3d.attributes.environment.WgDirectionalShadowLight shadowLight;
    Vector3 lightPos;
    WebGPUContext webgpu;



	public void create () {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 1, 5);
		cam.near = 0.1f;
        cam.lookAt(0,0,0);

		controller = new CameraInputController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont();

		//
		// Create some renderables
		//
        instances = new Array<>();

        ModelBuilder modelBuilder = new WgModelBuilder();
        WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat = new Material(TextureAttribute.createDiffuse(texture2));
        Material mat2 = new Material(ColorAttribute.createDiffuse(Color.OLIVE));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates | VertexAttributes.Usage.Normal ;
        box = modelBuilder.createBox(1, 1, 1, mat, attribs);

        long attribs2 = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.ColorPacked;
        ground = modelBuilder.createBox(8, 0.1f, 9, mat2, attribs2);

        lightPos = new Vector3(-.75f, 2f, -0.25f);
        Vector3 vec = new Vector3(lightPos).nor();

        lightModel = modelBuilder.createArrow( vec, Vector3.Zero,
            new Material(ColorAttribute.createDiffuse(Color.BLUE)),  VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);


        lightInstance = new ModelInstance(lightModel,lightPos);

        instances.add(new ModelInstance(box,0,1.0f,0));
        instances.add(new ModelInstance(box,2,0.5f,0));
        instances.add(new ModelInstance(box,0,1.0f,-2));
        instances.add(new ModelInstance(box,-2,1.0f,0));
        instances.add(new ModelInstance(ground,0,0,0));


        environment = new Environment();
        emptyEnvironment = new Environment();

        float level = 0.3f;
        ColorAttribute ambient =  ColorAttribute.createAmbientLight(level, level, 0, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(-lightPos.x, -lightPos.y, -lightPos.z);
        dirLight1.setColor(2f, 2f, 4f, 1f);// color * intensity
        environment.add(dirLight1);


        final int MAP = 1024;   // resolution of shadow map texture (may affect frame rate)
        final int VIEWPORT = 8; // depth and width of shadow volume in world units
        final float DEPTH = 20f; // length of shadow volume in world units along light direction
        shadowLight = new com.monstrous.gdx.webgpu.graphics.g3d.attributes.environment.WgDirectionalShadowLight(MAP, MAP, VIEWPORT, VIEWPORT, 0f, DEPTH);
        shadowLight.setDirection(dirLight1.direction);
        shadowLight.set(dirLight1);
        environment.shadowMap = shadowLight;

        shadowBatch = new WgModelBatch(new WgDepthShaderProvider());
	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
        instances.get(0).transform.rotate(Vector3.Y, 15f*delta);
		cam.update();

        Vector3 focalPoint = new Vector3(0, 0, 0);  // central position for shadow volume

        shadowLight.begin(focalPoint, Vector3.Zero);
		shadowBatch.begin(shadowLight.getCamera(), Color.BLUE, true, RenderPassType.DEPTH_ONLY);
        shadowBatch.render(instances);
        shadowBatch.end();
        shadowLight.end();

        //WgScreenUtils.clear(Color.TEAL, true);
        modelBatch.begin(cam,Color.TEAL, true);
        modelBatch.render(instances, environment);
//        modelBatch.render(lightInstance, environment);
        modelBatch.end();

		batch.begin();
        float y = 200;
		font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, y -= 20);
        for(int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
            font.draw(batch, "GPU time (pass "+pass+" "+webgpu.getGPUTimer().getPassName(pass)+") : "+(int)webgpu.getAverageGPUtime(pass)+ " microseconds" ,0, y -= 20);

        batch.end();
	}

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();
        batch.getProjectionMatrix().setToOrtho2D(0,0, width, height);
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

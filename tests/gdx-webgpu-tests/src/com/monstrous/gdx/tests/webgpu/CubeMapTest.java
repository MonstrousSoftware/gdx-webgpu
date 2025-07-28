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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.EnvironmentMap;

public class CubeMapTest extends GdxTest {
	public PerspectiveCamera cam;
	public CameraInputController inputController;
	public WgModelBatch modelBatch;
	public Model model;
	public ModelInstance instance;
	public Environment environment;
    private WgCubemap cubemap;
    private WgTexture texture;

	@Override
	public void create () {
		modelBatch = new WgModelBatch();

		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(5f, 5f, 5f);
		cam.lookAt(0, 0, 0);
		cam.near = 0.1f;
		cam.far = 150f;
		cam.update();

        FileHandle file = Gdx.files.internal("data/webgpu.png");
//        Pixmap w = new Pixmap(file);
//
//        texture = new WgTexture(w);
//        w.dispose();

        texture = new WgTexture(file);


        Material mat = new Material(ColorAttribute.createDiffuse(Color.YELLOW));
        ModelBuilder modelBuilder = new WgModelBuilder();
		model = modelBuilder.createBox(5f, 5f, 5f, new Material(TextureAttribute.createDiffuse(texture)),
			VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal| VertexAttributes.Usage.TextureCoordinates);
		instance = new ModelInstance(model);

		Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));

//          String[] sides = { "PX.png","NX.png", "PY.png", "NY.png", "PZ.png", "NZ.png"  };
//       String prefix = "data/g3d/environment/environment_01_";
//        String prefix = "data/g3d/environment/debug_";
//
         String[] sides = {  "pos-x.jpg","neg-x.jpg", "pos-y.jpg","neg-y.jpg", "pos-z.jpg", "neg-z.jpg"   };
        String prefix = "data/g3d/environment/leadenhall/";

        FileHandle[] fileHandles = new FileHandle[6];
        for(int i = 0; i < sides.length; i++){
            fileHandles[i] = Gdx.files.internal(prefix + sides[i]);
        }

        cubemap = new WgCubemap(fileHandles[0], fileHandles[1], fileHandles[2], fileHandles[3], fileHandles[4], fileHandles[5], false);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
        environment.set(new WgCubemapAttribute(EnvironmentMap, cubemap));    // add cube map attribute

	}

	@Override
	public void render () {
		inputController.update();

        WgScreenUtils.clear(Color.TEAL, true);

		modelBatch.begin(cam);
		modelBatch.render(instance, environment);
		modelBatch.end();
	}

	@Override
	public void dispose () {
		modelBatch.dispose();
		model.dispose();
        cubemap.dispose();
	}

    @Override
	public void resize (int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
	}

}

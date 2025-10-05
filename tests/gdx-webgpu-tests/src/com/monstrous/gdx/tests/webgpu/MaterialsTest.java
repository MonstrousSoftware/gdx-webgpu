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
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

public class MaterialsTest extends GdxTest {
	public PerspectiveCamera cam;
	public CameraInputController inputController;
	public WgModelBatch modelBatch;
	public Model model;
	public Array<ModelInstance> instances;
	public Environment environment;
    private WgSpriteBatch batch;
    private BitmapFont font;
    private Texture texture1, texture2;


	@Override
	public void create () {
		modelBatch = new WgModelBatch();

		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1f));
		environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(10f, 10f, 10f);
		cam.lookAt(0, 0, 0);
		cam.near = 0.1f;
		cam.far = 150f;
		cam.update();

        instances = new Array<>();
        ModelBuilder modelBuilder = new WgModelBuilder();
		model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.GREEN)),
			VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
		instances.add( new ModelInstance(model));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.RED)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 1, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 2, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.ORANGE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 3, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.GREEN)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 4, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 5, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 6, 0));

        model = modelBuilder.createBox(1f, 1f, 1f, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instances.add( new ModelInstance(model, 0, 7, 0));

        texture1 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        texture1.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat1 = new Material(TextureAttribute.createDiffuse(texture1));

        model = modelBuilder.createBox(1f, 1f, 1f, mat1,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
        instances.add( new ModelInstance(model, 2, 0, 0));

        texture2 = new WgTexture(Gdx.files.internal("data/webgpu.png"), true);
        texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat2 = new Material(TextureAttribute.createDiffuse(texture2));

        model = modelBuilder.createBox(1f, 1f, 1f, mat2,
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal | VertexAttributes.Usage.TextureCoordinates);
        instances.add( new ModelInstance(model, 2, 1, 0));

		Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));

        batch = new WgSpriteBatch();
        font = new WgBitmapFont();

	}

	@Override
	public void render () {
		inputController.update();


        WgScreenUtils.clear(Color.TEAL, true);

		modelBatch.begin(cam);
		modelBatch.render(instances, environment);
		modelBatch.end();

        batch.begin();
        font.draw(batch, "FPS: "+Gdx.graphics.getFramesPerSecond(), 10, 110);
        font.draw(batch, "Materials: "+modelBatch.materials.count(), 10, 80);
        font.draw(batch, "Material bindings/frame: "+modelBatch.materials.materialBindings(), 10, 50);
        batch.end();
	}

	@Override
	public void dispose () {
		modelBatch.dispose();
		model.dispose();
        batch.dispose();
        font.dispose();
        texture1.dispose();
        texture2.dispose();
	}

}

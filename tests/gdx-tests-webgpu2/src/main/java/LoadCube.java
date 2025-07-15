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

package main.java;


import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.JsonReader;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModel;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

public class LoadCube extends ApplicationAdapter {
	public PerspectiveCamera cam;
	public CameraInputController inputController;
	public WgModelBatch modelBatch;
	public Model model;
	public ModelInstance instance;
	public Environment environment;

    public static void main(String[] argv) {
        new WgDesktopApplication(new LoadCube());
    }


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
		cam.far = 50f;
		cam.update();

        String modelFileName = "data/g3d/gltf/UnitCube/UnitCube.gltf";
		FileHandle file = Gdx.files.internal(modelFileName);
        WgGLTFModelLoader loader = new WgGLTFModelLoader();
		Model model2 =  loader.loadModel(file);

        //Material mat = model2.materials.get(0);
//        Material mat =new Material(ColorAttribute.createDiffuse(Color.GREEN));
//
//        ModelBuilder modelBuilder = new WgModelBuilder();
//		model = modelBuilder.createBox(5f, 5f, 5f, mat, //new Material(ColorAttribute.createDiffuse(Color.GREEN)),
//			VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates |VertexAttributes.Usage.Normal);

//        ModelData modelData = new ModelData();
//
//        ModelMesh modelMesh = new ModelMesh();
//
//        ModelMaterial modelMaterial = new ModelMaterial();
//        modelMaterial.id = "mat";
//        modelMaterial.diffuse = Color.YELLOW;
//
//        ModelNodePart part = new ModelNodePart();
//        part.materialId = "mat";
//        part.meshPartId = "part";
//        part.bones = null;
//
//        ModelNode modelNode = new ModelNode();
//        modelNode.scale = new Vector3(1,1,1);
//        modelNode.translation = new Vector3(0,0,0);
//        modelNode.rotation = new Quaternion();
//        modelNode.parts = new ModelNodePart[1];
//        modelNode.parts[0] = part;
//
//
//        modelData.addMesh(modelMesh);
//        modelData.materials.add(modelMaterial);
//        modelData.nodes.add(modelNode);




		instance = new ModelInstance(model2);

//		Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));
	}

	@Override
	public void render () {
//		inputController.update();


        WgScreenUtils.clear(Color.TEAL, true);

		modelBatch.begin(cam);
		modelBatch.render(instance, environment);
		modelBatch.end();
	}

	@Override
	public void dispose () {
		modelBatch.dispose();
		model.dispose();
	}


	public void resume () {
	}

	public void resize (int width, int height) {
	}

	public void pause () {
	}
}

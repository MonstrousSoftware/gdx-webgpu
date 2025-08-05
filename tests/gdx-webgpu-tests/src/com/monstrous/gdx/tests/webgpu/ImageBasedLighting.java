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
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.*;


/** Test IBL */


public class ImageBasedLighting extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
    CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	Model model;
	ModelInstance instance;
    String modelFileName;
    Environment environment;
    int numMeshes;
    int numVerts;
    int numIndices;
    WgGraphics gfx;
    WebGPUContext webgpu;
    private Viewport viewport;
    private SkyBox skybox;
    private WgCubemap envCubeMap;
    private WgCubemap diffuseCubeMap;
    private WgCubemap specularCubeMap;



	// application
	public void create () {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 0, 3f);
		cam.lookAt(0,0,0);
		cam.near = 0.001f;
		cam.far = 100f;


        environment = new Environment();
        float amb = 0.5f;
        ColorAttribute ambient =  ColorAttribute.createAmbientLight(amb, amb, amb, 1f);
        environment.set(ambient);

//        DirectionalLight dirLight1 = new DirectionalLight();
//        dirLight1.setDirection(1f, -.2f, .2f);
//        dirLight1.setColor(Color.WHITE);
//        environment.add(dirLight1);


        // Environment map also uses as Sky Box
        //
        String[] sides = {  "posx","negx", "posy","negy", "posz", "negz"   };
        String prefix = "data/IBL/Studio/Studio-envmap_";
        String extension = ".png";

        FileHandle[] fileHandles = new FileHandle[6];
        for(int i = 0; i < sides.length; i++){
            fileHandles[i] = Gdx.files.internal(prefix + sides[i] + extension);
        }

        envCubeMap = new WgCubemap(fileHandles[0], fileHandles[1], fileHandles[2], fileHandles[3], fileHandles[4], fileHandles[5], false);
        skybox = new SkyBox(envCubeMap);

        environment.set(new WgCubemapAttribute(EnvironmentMap, envCubeMap));    // add cube map attribute as environment,i.e. for reflections

        // Diffuse cube map (irradiance map)
        //
        prefix = "data/IBL/Studio/Studio-irradiance_";
        for(int i = 0; i < sides.length; i++){
            fileHandles[i] = Gdx.files.internal(prefix + sides[i] + extension);
        }
        diffuseCubeMap = new WgCubemap(fileHandles[0], fileHandles[1], fileHandles[2], fileHandles[3], fileHandles[4], fileHandles[5], false);

        environment.set(new WgCubemapAttribute(DiffuseCubeMap, diffuseCubeMap));    // irradiance map

        // Specular cube map (radiance map)
        //
        prefix = "data/IBL/Studio/Studio-radiance_";
        String level = "_0";
        for(int i = 0; i < sides.length; i++){
            fileHandles[i] = Gdx.files.internal(prefix + sides[i] + level + extension);
        }
        specularCubeMap = new WgCubemap(fileHandles[0], fileHandles[1], fileHandles[2], fileHandles[3], fileHandles[4], fileHandles[5], true);
        // todo needs proper convolution of mip levels

        environment.set(new WgCubemapAttribute(SpecularCubeMap, specularCubeMap));    // radiance map





        // Model
        //
        modelFileName = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";
        //modelFileName = "data/g3d/gltf/sphere.gltf";


        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        System.out.println("Start loading");
        long startLoad = System.currentTimeMillis();
        FileHandle file = Gdx.files.internal(modelFileName);
        if(file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if(file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: "+modelFileName);
        long endLoad = System.currentTimeMillis();
        System.out.println("Model loading time (ms): "+(endLoad - startLoad));

		instance = new ModelInstance(model);



        numMeshes = instance.model.meshes.size;
        for(int i = 0; i < numMeshes; i++){
            numVerts += instance.model.meshes.get(i).getNumVertices();
            numIndices += instance.model.meshes.get(i).getNumIndices();
        }

		controller = new CameraInputController(cam);

		Gdx.input.setInputProcessor(controller);
        viewport = new ScreenViewport();
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);



	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		instance.transform.rotate(Vector3.Y, 15f*delta);

		WgScreenUtils.clear(Color.DARK_GRAY, true);

		cam.update();
		modelBatch.begin(cam);

		modelBatch.render(instance, environment);

		modelBatch.end();

        skybox.renderPass(cam);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
		batch.begin();
        int y = 200;
		font.draw(batch, "Model loaded: "+modelFileName , 0, y-=20);
        font.draw(batch, "Meshes: "+numMeshes , 0, y-=20);
        font.draw(batch, "Vertices: "+numVerts , 0, y-=20);
        font.draw(batch, "Indices: "+numIndices , 0, y-=20);
        font.draw(batch, "FPS: "+Gdx.graphics.getFramesPerSecond() ,0, y -= 20);
        font.draw(batch, "delta time: "+(int)(1000000/(Gdx.graphics.getFramesPerSecond()+0.001f))+" microseconds",0, y -= 20);

        for(int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
            font.draw(batch, "GPU time (pass "+pass+" "+webgpu.getGPUTimer().getPassName(pass)+") : "+(int)webgpu.getAverageGPUtime(pass)+ " microseconds" ,0, y -= 20);
        batch.end();

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
	}


}

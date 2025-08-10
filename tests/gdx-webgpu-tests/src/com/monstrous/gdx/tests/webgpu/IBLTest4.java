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
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModel;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.HDRLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.DiffuseCubeMap;
import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.EnvironmentMap;


/** Test IBL
 * Generates environment cube map from equirectangular texture.
 *
 * */


public class IBLTest4 extends GdxTest {
    CameraInputController controller;
    PerspectiveCamera cam;
    SkyBox skyBox;

    WgTexture equiRectangular;
    Model model;
    ModelInstance instance;
    WgModelBatch modelBatch;
    Environment environment;


	public void create () {

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, -3f);
        cam.direction.set(0,0,1);
        cam.near = 0.01f;       // avoid zero
        cam.far = 100f;
        cam.update();


        controller = new CameraInputController(cam);
        controller.scrollFactor = -0.01f;
        Gdx.input.setInputProcessor(controller);

        // load equirectangular texture from HDR file format
        equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/leadenhall_market_2k.hdr"), true);

        // Generate environment map from equirectangular texture
        WgCubemap cubemap = IBLGenerator.buildCubeMapFromEquirectangularTexture(equiRectangular, 2048);

        WgCubemap irradianceMap = IBLGenerator.buildIrradianceMap(cubemap, 128);

        // use cube map as a sky box
        skyBox = new SkyBox(irradianceMap);

        modelBatch = new WgModelBatch();

        environment = new Environment();
        environment.set(new WgCubemapAttribute(DiffuseCubeMap, irradianceMap));    // add irradiance map

        // Model
        //

        String modelFileName = "data/g3d/gltf/sphere.gltf";

        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        System.out.println("Start loading");
        FileHandle file = Gdx.files.internal(modelFileName);
        if(file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if(file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: "+modelFileName);



        instance = new ModelInstance(model);

        //instance.materials.get(0).set(new ColorAttribute(ColorAttribute.Diffuse, Color.RED));

    }


    public void render () {
        controller.update();


        modelBatch.begin(cam, Color.BLACK, true);
        modelBatch.render(instance, environment);
        modelBatch.end();

        skyBox.renderPass(cam, false);
    }


	@Override
	public void dispose () {
        skyBox.dispose();
        equiRectangular.dispose();
        model.dispose();
	}

}

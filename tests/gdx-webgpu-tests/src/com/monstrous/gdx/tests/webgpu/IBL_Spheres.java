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
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.HDRLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.*;


/** Test IBL
 * Generates environment cube map from equirectangular texture.
 * Shows a number of spheres with different metallic/roughness values.
 *
 * */


public class IBL_Spheres extends GdxTest {
    CameraInputController controller;
    PerspectiveCamera cam;
    SkyBox skyBox;

    WgTexture equiRectangular;
    private Array<ModelInstance> instances;
    private Array<Disposable> disposables;
    WgModelBatch modelBatch;
    Environment environment;

    public void create() {

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, -10f);
        cam.direction.set(0, 0, 1);
        cam.near = 0.01f;
        cam.far = 100f;
        cam.update();


        controller = new CameraInputController(cam);
        controller.scrollFactor = -0.01f;
        Gdx.input.setInputProcessor(controller);

        // load equirectangular texture from HDR file format
        equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/leadenhall_market_2k.hdr"), true);
        //equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/brown_photostudio_02_1k.hdr"), true);

        // Generate environment map from equirectangular texture
        WgCubemap envMap = IBLGenerator.buildCubeMapFromEquirectangularTexture(equiRectangular, 1024);

        // Diffuse cube map (irradiance map)
        //
        WgCubemap irradianceMap = IBLGenerator.buildIrradianceMap(envMap, 64);  // higher values e.g. >=32 cause artifacts


        // Specular cube map (radiance map)
        //
        WgCubemap radianceMap = IBLGenerator.buildRadianceMap(envMap, 128);

        // use cube map as a sky box
        skyBox = new SkyBox(envMap);

        modelBatch = new WgModelBatch();

        environment = new Environment();
        environment.set(new WgCubemapAttribute(DiffuseCubeMap, irradianceMap));   // add irradiance map
        environment.set(new WgCubemapAttribute(SpecularCubeMap, radianceMap));    // add radiance map
        environment.set(new WgCubemapAttribute(EnvironmentMap, irradianceMap));    // add cube map attribute

        // Add lighting (a few point lights)
        float intensity = 250f;
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(-10f,10f,10).setIntensity(intensity));
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(10f,10f,10).setIntensity(intensity));
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(10f,-10f,10).setIntensity(intensity));

        // Models
        //
        instances = new Array<>();
        disposables = new Array<>();

        // create some spheres
        Model sphere;
        for(int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 5; x++) {
                sphere = buildSphere((y == 0 ? Color.RED : Color.GRAY), y, 0.2f*x);
                instances.add(new ModelInstance(sphere, 3 * (x - 2.5f), 3*y-1.5f, 0));
                disposables.add(sphere);
            }
        }
    }


    public void render() {
        controller.update();

        modelBatch.begin(cam, Color.BLACK, true);
        modelBatch.render(instances, environment);
        modelBatch.end();

        skyBox.renderPass(cam, false);
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }


    private Model buildSphere(Color albedo, float metallic, float roughness){
        long usage = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material mat = new Material(ColorAttribute.createDiffuse(albedo), PBRFloatAttribute.createMetallic(metallic), PBRFloatAttribute.createRoughness(roughness) );

        WgModelBuilder modelBuilder = new WgModelBuilder();
        return modelBuilder.createSphere(2f, 2f, 2f, 16, 16, mat, usage);
    }

	@Override
	public void dispose () {
        skyBox.dispose();
        equiRectangular.dispose();
        for(Disposable disposable : disposables)
            disposable.dispose();
	}

}

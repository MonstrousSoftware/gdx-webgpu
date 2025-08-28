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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.HDRLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.*;


/** Test IBL
 * Generates environment cube map from equirectangular texture.
 * Shows a model, control metallic & roughness with sliders
 *
 * */


public class IBL_Sliders extends GdxTest {
    CameraInputController controller;
    PerspectiveCamera cam;
    SkyBox skyBox;

    WgTexture equiRectangular;
    Model model;
    private ModelInstance instance;
    WgModelBatch modelBatch;
    Environment environment;
    private WgStage stage;
    private WgSkin skin;
    private float metallic = 0.0f;
    private float roughness = 0.0f;
    private WgSpriteBatch batch;


    public void create() {

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, -3f);
        cam.direction.set(0, 0, 1);
        cam.near = 0.01f;       // avoid zero
        cam.far = 100f;
        cam.update();


        controller = new CameraInputController(cam);
        controller.scrollFactor = -0.01f;

        // load equirectangular texture from HDR file format
        //equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/leadenhall_market_2k.hdr"), true);
        equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/brown_photostudio_02_1k.hdr"), true);

        // Generate environment map from equirectangular texture
        WgCubemap envMap = IBLGenerator.buildCubeMapFromEquirectangularTexture(equiRectangular, 1024);

        // Diffuse cube map (irradiance map)
        //
        WgCubemap irradianceMap = IBLGenerator.buildIrradianceMap(envMap, 16);

        // Specular cube map (radiance map)
        //
        WgCubemap radianceMap = IBLGenerator.buildRadianceMap(envMap, 128);

        // use cube map as a sky box
        skyBox = new SkyBox(envMap);

        modelBatch = new WgModelBatch();

        environment = new Environment();
        environment.set( WgCubemapAttribute.createDiffuseCubeMap(irradianceMap));   // add irradiance map
        environment.set( WgCubemapAttribute.createSpecularCubeMap(radianceMap));    // add radiance map

        // Add lighting
        float intensity = 25f;
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(-10f,10f,10).setIntensity(intensity));
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(10f,10f,10).setIntensity(intensity));
        environment.add( new PointLight().setColor(Color.WHITE).setPosition(10f,-10f,10).setIntensity(intensity));
        //environment.add( new PointLight().setColor(Color.WHITE).setPosition(-10f,-10f,10).setIntensity(intensity));

        // Model
        //

//        String modelFileName = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";
//
//        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
//        params.textureParameter.genMipMaps = true;
//
//        System.out.println("Start loading");
//        FileHandle file = Gdx.files.internal(modelFileName);
//        if (file.extension().contentEquals("gltf"))
//            model = new WgGLTFModelLoader().loadModel(file, params);
//        else if (file.extension().contentEquals("glb"))
//            model = new WgGLBModelLoader().loadModel(file, params);
//        else
//            System.out.println("File extension not supported: " + modelFileName);

        model = buildSphere(Color.RED, 0.5f, 0.5f);

        instance = new ModelInstance(model);

        instance.materials.get(0).set(new ColorAttribute(ColorAttribute.Diffuse, Color.RED));


        Gdx.input.setInputProcessor(controller);

        stage = new WgStage(new ScreenViewport());
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));
        rebuildStage();


        InputMultiplexer im = new InputMultiplexer();
        im.addProcessor(stage);
        im.addProcessor(controller);
        Gdx.input.setInputProcessor(im);

        batch = new WgSpriteBatch();

    }


    public void render() {
        controller.update();
        float delta = Gdx.graphics.getDeltaTime();
        instance.transform.rotate(Vector3.Y, 15f*delta);


        modelBatch.begin(cam, Color.BLACK, true);
        modelBatch.render(instance, environment);
        modelBatch.end();

        skyBox.renderPass(cam, false);

        stage.act();
        stage.draw();

        batch.begin();
        for(int i = 0; i < 6; i++){
            batch.draw(IBLGenerator.debugTextures[i], 128*i, 0);
        }
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        //viewport.update(width, height, true);
        stage.getViewport().update(width, height, true);
        rebuildStage();

    }

    private void rebuildStage() {
        stage.clear();

        Table screenTable = new Table();
        screenTable.setFillParent(true);

        Label metallicValue = new Label("", skin);
        metallicValue.setText(String.format("metallic: %.2f", metallic));
        instance.materials.get(0).set(new PBRFloatAttribute(PBRFloatAttribute.Metallic, metallic));
        Label roughnessValue = new Label("", skin);
        roughnessValue.setText(String.format("roughness: %.2f", roughness));
        instance.materials.get(0).set(new PBRFloatAttribute(PBRFloatAttribute.Roughness, roughness));


        Table sliderTable = new Table();
        Slider slider = new Slider(0, 1, 0.01f, false, skin);
        slider.setValue(metallic);
        slider.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                metallic = slider.getValue();
                metallicValue.setText(String.format("metallic: %.2f", metallic));
                instance.materials.get(0).set(new PBRFloatAttribute(PBRFloatAttribute.Metallic, metallic));
            }
        });

        Slider slider2 = new Slider(0, 1, 0.01f, false, skin);
        slider2.setValue(roughness);
        slider2.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                roughness = slider2.getValue();
                roughnessValue.setText(String.format("roughness: %.2f", roughness));
                instance.materials.get(0).set(new PBRFloatAttribute(PBRFloatAttribute.Roughness, roughness));
            }
        });


        sliderTable.add(slider);
        sliderTable.row();
        sliderTable.add(metallicValue);
        sliderTable.row();
        sliderTable.add(slider2);
        sliderTable.row();
        sliderTable.add(roughnessValue);
        sliderTable.row();

        screenTable.add(sliderTable).align(Align.topRight).expand();
        stage.addActor(screenTable);
    }
    private Model buildSphere(Color albedo, float metallic, float roughness){
        long usage = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material mat = new Material(ColorAttribute.createDiffuse(albedo), PBRFloatAttribute.createMetallic(metallic), PBRFloatAttribute.createRoughness(roughness) );

        WgModelBuilder modelBuilder = new WgModelBuilder();
        return modelBuilder.createSphere(2f, 2f, 2f, 32, 32, mat, usage);
    }

	@Override
	public void dispose () {
        skyBox.dispose();
        equiRectangular.dispose();
        model.dispose();

	}

}

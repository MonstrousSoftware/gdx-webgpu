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
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
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
import com.monstrous.gdx.webgpu.assets.WgAssetManager;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Test GLTF loading and ModelInstance rendering
 */

public class AssetManagerTest extends GdxTest {

    WgModelBatch modelBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model model;
    ModelInstance instance;
    Environment environment;
    int numMeshes;
    int numVerts;
    int numIndices;
    private Viewport viewport;

    AssetManager assetManager;
    String modelFileName;
    String fontFileName;

    // application
    public void create() {
        assetManager = new WgAssetManager();
        modelBatch = new WgModelBatch();
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, 3f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.001f;
        cam.far = 100f;

        modelFileName = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";
        // modelFileName = "data/g3d/gltf/Cube/Cube.gltf";
        // modelFileName = "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf";
        // modelFileName = "data/g3d/gltf/Cubes/cubes.gltf";
        // modelFileName = "data/g3d/gltf/AntiqueCamera/AntiqueCamera.gltf";
        // modelFileName = "data/g3d/gltf/torus.gltf";
        // modelFileName = "data/g3d/gltf/Sponza/Sponza.gltf";
        // modelFileName = "data/g3d/gltf/waterbottle/waterbottle.glb";
        // modelFileName = "data/g3d/gltf/Buggy/Buggy.gltf";
        // modelFileName = "data/g3d/gltf/triangle.gltf";
        // modelFileName = "data/g3d/gltf/Avocado.glb";

        fontFileName = "data/lsans-15.fnt";

        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        assetManager.load(modelFileName, Model.class, params);
        assetManager.load(fontFileName, WgBitmapFont.class);

        controller = new CameraInputController(cam);

        Gdx.input.setInputProcessor(controller);
        viewport = new ScreenViewport();
        batch = new WgSpriteBatch();

        environment = new Environment();
        float amb = 0.0f;
        ColorAttribute ambient = ColorAttribute.createAmbientLight(amb, amb, amb, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(1f, -.2f, .2f);
        dirLight1.setColor(Color.WHITE);
        environment.add(dirLight1);
    }

    public void render() {
        WgScreenUtils.clear(Color.DARK_GRAY, true);
        cam.update();

        float delta = Gdx.graphics.getDeltaTime();

        if (assetManager.update()) {
            loadModel();
            loadFont();
        }

        if (instance != null) {
            instance.transform.rotate(Vector3.Y, 15f * delta);
            modelBatch.begin(cam);
            modelBatch.render(instance, environment);
            modelBatch.end();
        }

        if (font != null) {
            batch.setProjectionMatrix(viewport.getCamera().combined);
            batch.begin();
            int y = 200;
            font.draw(batch, "Model loaded: " + modelFileName, 0, y -= 20);
            font.draw(batch, "Meshes: " + numMeshes, 0, y -= 20);
            font.draw(batch, "Vertices: " + numVerts, 0, y -= 20);
            font.draw(batch, "Indices: " + numIndices, 0, y -= 20);
            font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 0, y -= 20);
            font.draw(batch,
                    "delta time: " + (int) (1000000 / (Gdx.graphics.getFramesPerSecond() + 0.001f)) + " microseconds",
                    0, y -= 20);
            batch.end();
        }
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        modelBatch.dispose();
        model.dispose();
    }

    private void loadModel() {
        if (instance == null && assetManager.isLoaded(modelFileName)) {
            model = assetManager.get(modelFileName, Model.class);
            instance = new ModelInstance(model);
            numMeshes = instance.model.meshes.size;
            for (int i = 0; i < numMeshes; i++) {
                numVerts += instance.model.meshes.get(i).getNumVertices();
                numIndices += instance.model.meshes.get(i).getNumIndices();
            }
        }
    }

    private void loadFont() {
        if (font == null && assetManager.isLoaded(fontFileName)) {
            font = assetManager.get(fontFileName, WgBitmapFont.class);
        }
    }
}

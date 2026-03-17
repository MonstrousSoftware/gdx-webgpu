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
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.FirstPersonCameraController;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.HDRLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgImmediateModeRenderer;
import com.monstrous.gdx.webgpu.graphics.utils.WgShapeRenderer;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.*;

/**
 * Test that combines 3D models with debug lines (WgShapeRenderer) to verify
 * correct depth testing interaction between model rendering and line rendering.
 *
 * <p>Features:
 * <ul>
 *   <li>IBL-lit PBR scene with skybox for realistic visuals</li>
 *   <li>Multiple 3D models (GLTF helmet, procedural spheres, boxes, ground plane)</li>
 *   <li>Debug lines: grid, bounding boxes, axes, circles, connecting lines</li>
 *   <li>Toggle depth test on/off for debug lines with the T key</li>
 *   <li>First-person fly camera: WASD move, E/Q up/down, right-click drag to look, scroll to adjust speed</li>
 * </ul>
 *
 * <p>When depth testing is enabled (default), debug lines should be correctly
 * occluded by solid models. When disabled (press T), all lines render on top.
 */
public class DebugLinesDepthTest extends GdxTest {

    PerspectiveCamera cam;
    FirstPersonCameraController controller;
    SkyBox skyBox;

    // IBL textures
    WgTexture equiRectangular;
    WgCubemap envMap;
    WgCubemap irradianceMap;
    WgCubemap radianceMap;

    // Rendering
    WgModelBatch modelBatch;
    WgShapeRenderer shapeRenderer;
    WgSpriteBatch spriteBatch;
    WgBitmapFont font;
    Environment environment;

    // Models & instances
    Model helmetModel;
    Model groundModel;
    Model boxModel;
    Model sphereModel;
    Model cylinderModel;
    Array<ModelInstance> instances;
    Array<Disposable> disposables;

    boolean depthTestEnabled = true;

    @Override
    public void create() {
        // ---- Camera ----
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(5f, 4f, 8f);
        cam.lookAt(0, 1f, 0);
        cam.near = 0.01f;
        cam.far = 200f;
        cam.update();

        controller = new FirstPersonCameraController(cam);
        Gdx.input.setInputProcessor(controller);

        // ---- IBL setup ----
        equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/leadenhall_market_2k.hdr"), true);
        envMap = IBLGenerator.buildCubeMapFromEquirectangularTexture(equiRectangular, 1024);
        irradianceMap = IBLGenerator.buildIrradianceMap(envMap, 64);
        radianceMap = IBLGenerator.buildRadianceMap(envMap, 128);
        skyBox = new SkyBox(envMap);

        // ---- Environment ----
        environment = new Environment();
        environment.set(new WgCubemapAttribute(DiffuseCubeMap, irradianceMap));
        environment.set(new WgCubemapAttribute(SpecularCubeMap, radianceMap));
        float intensity = 30f;
        environment.add(new PointLight().setColor(Color.WHITE).setPosition(-8f, 10f, 8f).setIntensity(intensity));
        environment.add(new PointLight().setColor(Color.WHITE).setPosition(8f, 10f, 8f).setIntensity(intensity));
        environment.add(new PointLight().setColor(Color.WHITE).setPosition(0f, 10f, -8f).setIntensity(intensity));

        // ---- Model batch ----
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.maxDirectionalLights = 0;
        config.maxPointLights = 3;
        modelBatch = new WgModelBatch(config);

        // ---- Shape renderer ----
        shapeRenderer = new WgShapeRenderer(10000);

        // ---- Sprite batch / font for HUD ----
        spriteBatch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        // ---- Build scene ----
        instances = new Array<>();
        disposables = new Array<>();
        WgModelBuilder mb = new WgModelBuilder();
        long posNorm = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        // Ground plane
        Material groundMat = new Material(
                ColorAttribute.createDiffuse(new Color(0.35f, 0.35f, 0.35f, 1f)),
                PBRFloatAttribute.createMetallic(0f),
                PBRFloatAttribute.createRoughness(0.85f));
        groundModel = mb.createBox(20f, 0.1f, 20f, groundMat, posNorm);
        disposables.add(groundModel);
        instances.add(new ModelInstance(groundModel, 0, -0.05f, 0));

        // GLTF helmet
        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;
        helmetModel = new WgGLTFModelLoader().loadModel(
                Gdx.files.internal("data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf"), params);
        disposables.add(helmetModel);
        ModelInstance helmet1 = new ModelInstance(helmetModel, 0, 1.5f, 0);
        instances.add(helmet1);

        // A second helmet further away
        ModelInstance helmet2 = new ModelInstance(helmetModel, -4f, 1.5f, -3f);
        helmet2.transform.scale(0.7f, 0.7f, 0.7f);
        instances.add(helmet2);

        // Procedural spheres with varying metallic/roughness
        for (int i = 0; i < 5; i++) {
            float metallic = i / 4f;
            Material sphereMat = new Material(
                    ColorAttribute.createDiffuse(new Color(0.8f, 0.2f, 0.1f, 1f)),
                    PBRFloatAttribute.createMetallic(metallic),
                    PBRFloatAttribute.createRoughness(0.3f));
            Model s = mb.createSphere(0.8f, 0.8f, 0.8f, 16, 16, sphereMat, posNorm);
            disposables.add(s);
            instances.add(new ModelInstance(s, -4f + 2f * i, 0.4f, 4f));
        }

        // A few boxes scattered around
        for (int i = 0; i < 3; i++) {
            float hue = i / 3f;
            Color col = new Color();
            col.fromHsv(hue * 360f, 0.7f, 0.9f);
            col.a = 1f;
            Material boxMat = new Material(
                    ColorAttribute.createDiffuse(col),
                    PBRFloatAttribute.createMetallic(0.5f),
                    PBRFloatAttribute.createRoughness(0.4f));
            Model b = mb.createBox(1f, 1f + i * 0.5f, 1f, boxMat, posNorm);
            disposables.add(b);
            float angle = i * 120f;
            float x = 3.5f * MathUtils.cosDeg(angle);
            float z = 3.5f * MathUtils.sinDeg(angle);
            ModelInstance bi = new ModelInstance(b, x, (1f + i * 0.5f) / 2f, z);
            bi.transform.rotate(Vector3.Y, angle);
            instances.add(bi);
        }

        // A cylinder
        Material cylMat = new Material(
                ColorAttribute.createDiffuse(new Color(0.2f, 0.6f, 0.9f, 1f)),
                PBRFloatAttribute.createMetallic(0.8f),
                PBRFloatAttribute.createRoughness(0.15f));
        cylinderModel = mb.createCylinder(0.6f, 3f, 0.6f, 16, cylMat, posNorm);
        disposables.add(cylinderModel);
        instances.add(new ModelInstance(cylinderModel, 5f, 1.5f, -2f));
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Toggle depth test for debug lines
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            depthTestEnabled = !depthTestEnabled;
        }

        controller.update();

        // Slowly rotate the first helmet
        instances.get(1).transform.rotate(Vector3.Y, 15f * delta);

        // ---- Render 3D models with IBL ----
        modelBatch.begin(cam, Color.BLACK, true);
        modelBatch.render(instances, environment);
        modelBatch.end();

        // ---- Skybox ----
        skyBox.renderPass(cam, false);

        // ---- Debug lines ----
        // Set depth test on the underlying WgImmediateModeRenderer
        WgImmediateModeRenderer imr = (WgImmediateModeRenderer) shapeRenderer.getRenderer();
        if (depthTestEnabled) {
            imr.enableDepthTest();
        } else {
            imr.disableDepthTest();
        }


        shapeRenderer.setProjectionMatrix(cam.combined);
        shapeRenderer.identity();

        // -- Ground grid --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(0.5f, 0.5f, 0.5f, 1f);
        float gridSize = 10f;
        float gridStep = 1f;
        for (float g = -gridSize; g <= gridSize; g += gridStep) {
            shapeRenderer.line(g, 0.001f, -gridSize, g, 0.001f, gridSize);
            shapeRenderer.line(-gridSize, 0.001f, g, gridSize, 0.001f, g);
        }
        shapeRenderer.end();

        // -- World axes --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        float axisLen = 3f;
        // X axis - red
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.line(0, 0.01f, 0, axisLen, 0.01f, 0);
        // Y axis - green
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(0, 0.01f, 0, 0, axisLen, 0);
        // Z axis - blue
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.line(0, 0.01f, 0, 0, 0.01f, axisLen);
        shapeRenderer.end();

        // -- Bounding box around the central helmet (approximate) --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.YELLOW);
        drawWireBox(shapeRenderer, 0, 1.5f, 0, 1.2f, 1.2f, 1.2f);
        shapeRenderer.end();

        // -- Bounding box around the second helmet --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.CYAN);
        drawWireBox(shapeRenderer, -4f, 1.5f, -3f, 0.84f, 0.84f, 0.84f);
        shapeRenderer.end();

        // -- Circles around the spheres row --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.MAGENTA);
        for (int i = 0; i < 5; i++) {
            drawCircleXZ(shapeRenderer, -4f + 2f * i, 0.01f, 4f, 0.6f, 32);
        }
        shapeRenderer.end();

        // -- Connecting lines between objects --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.ORANGE);
        // Line from helmet 1 to helmet 2
        shapeRenderer.line(0, 1.5f, 0, -4f, 1.5f, -3f);
        // Lines from helmet 1 to each sphere
        for (int i = 0; i < 5; i++) {
            shapeRenderer.setColor(1f, 0.5f + 0.1f * i, 0f, 1f);
            shapeRenderer.line(0, 1.5f, 0, -4f + 2f * i, 0.4f, 4f);
        }
        shapeRenderer.end();

        // -- Vertical debug markers at box positions --
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            float angle = i * 120f;
            float x = 3.5f * MathUtils.cosDeg(angle);
            float z = 3.5f * MathUtils.sinDeg(angle);
            shapeRenderer.line(x, 0, z, x, 3f, z);
        }
        // Marker at cylinder
        shapeRenderer.setColor(Color.SKY);
        shapeRenderer.line(5f, 0, -2f, 5f, 4f, -2f);
        shapeRenderer.end();

        // ---- HUD ----
        spriteBatch.begin();
        int y = Gdx.graphics.getHeight() - 10;
        font.draw(spriteBatch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 10, y);
        y -= 18;
        font.draw(spriteBatch, "Depth test for debug lines: " + (depthTestEnabled ? "ON" : "OFF") + "  (press T to toggle)", 10, y);
        y -= 18;
        font.draw(spriteBatch, "WASD = move, E/Q = up/down, Right-click drag = look, Scroll = speed, Shift = fast", 10, y);
        spriteBatch.end();
    }

    /** Draw a wireframe axis-aligned box centered at (cx, cy, cz) with half-extents (hx, hy, hz). */
    private void drawWireBox(WgShapeRenderer sr, float cx, float cy, float cz, float hx, float hy, float hz) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;

        // Bottom face
        sr.line(x0, y0, z0, x1, y0, z0);
        sr.line(x1, y0, z0, x1, y0, z1);
        sr.line(x1, y0, z1, x0, y0, z1);
        sr.line(x0, y0, z1, x0, y0, z0);
        // Top face
        sr.line(x0, y1, z0, x1, y1, z0);
        sr.line(x1, y1, z0, x1, y1, z1);
        sr.line(x1, y1, z1, x0, y1, z1);
        sr.line(x0, y1, z1, x0, y1, z0);
        // Verticals
        sr.line(x0, y0, z0, x0, y1, z0);
        sr.line(x1, y0, z0, x1, y1, z0);
        sr.line(x1, y0, z1, x1, y1, z1);
        sr.line(x0, y0, z1, x0, y1, z1);
    }

    /** Draw a circle in the XZ plane at height y, centered at (cx, y, cz). */
    private void drawCircleXZ(WgShapeRenderer sr, float cx, float y, float cz, float radius, int segments) {
        float step = 360f / segments;
        float prevX = cx + radius;
        float prevZ = cz;
        for (int i = 1; i <= segments; i++) {
            float angle = i * step;
            float nx = cx + radius * MathUtils.cosDeg(angle);
            float nz = cz + radius * MathUtils.sinDeg(angle);
            sr.line(prevX, y, prevZ, nx, y, nz);
            prevX = nx;
            prevZ = nz;
        }
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        spriteBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        spriteBatch.dispose();
        font.dispose();
        modelBatch.dispose();
        skyBox.dispose();
        equiRectangular.dispose();
        envMap.dispose();
        irradianceMap.dispose();
        radianceMap.dispose();
        for (Disposable d : disposables)
            d.dispose();
    }
}




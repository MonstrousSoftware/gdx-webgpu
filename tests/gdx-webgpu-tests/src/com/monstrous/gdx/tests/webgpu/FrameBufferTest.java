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
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgObjLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgDirectionalShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

/**
 * Demonstrate stacked wgFrameBuffer use. Tests rendering to framebuffers of different sizes
 * and then rendering those framebuffers to other framebuffers.
 */

public class FrameBufferTest extends GdxTest {

    WgSpriteBatch batch;
    WgBitmapFont font;
    WgGraphics gfx;

    // 2-level stacking chain (2D colored shapes)
    WgFrameBuffer fbo2Level_Base;   // 512x512 - draws simple colored rectangles
    WgFrameBuffer fbo2Level_Top;    // 384x384 - reads fbo2Level_Base

    // 3D shadow rendering (single framebuffer)
    WgFrameBuffer fbo3Level_Base;   // 512x512 - 3D scene with shadows

    OrthographicCamera camera2D;
    PerspectiveCamera camera3D;
    float time = 0f;

    // Colored textures for 2D rendering
    WgTexture redTexture;
    WgTexture greenTexture;
    WgTexture blueTexture;
    WgTexture yellowTexture;

    // 3D rendering for shadow chain
    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
    Model boxModel;
    Model groundModel;
    Array<ModelInstance> instances;
    Environment environment;
    WgDirectionalShadowLight shadowLight;
    WebGPUContext webgpu;
    Vector3 lightPos;

    // Render mode flags
    boolean useFrameBuffers = true;      // Set to false to render scene directly without framebuffers
    boolean renderShadowScene = true;    // Set to false to disable shadow scene (only 2D framebuffers)

    // application
    public void create() {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        // Create 2D camera
        camera2D = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera2D.position.set(camera2D.viewportWidth / 2, camera2D.viewportHeight / 2, 0);
        camera2D.update();

        // Create 3D camera
        camera3D = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.position.set(4, 3, -4);
        camera3D.lookAt(0, 0.5f, 0);
        camera3D.near = 0.1f;

        // Create colored textures for 2D rendering
        redTexture = createColoredTexture(Color.RED);
        greenTexture = createColoredTexture(Color.GREEN);
        blueTexture = createColoredTexture(Color.BLUE);
        yellowTexture = createColoredTexture(Color.YELLOW);

        // Initialize 3D rendering for shadow scene
        initializeShadowScene();

        // 2-level stacking chain (2D)
        fbo2Level_Base = new WgFrameBuffer(512, 512, true);
        fbo2Level_Top = new WgFrameBuffer(384, 384, true);

        // 3D shadow rendering (single framebuffer)
        fbo3Level_Base = new WgFrameBuffer(512, 512, true);
    }

    private void initializeShadowScene() {
        modelBatch = new WgModelBatch();
        shadowBatch = new WgModelBatch(new WgDepthShaderProvider());
        instances = new Array<>();

        WgModelBuilder modelBuilder = new WgModelBuilder();
        WgTexture texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);

        Material boxMat = new Material(TextureAttribute.createDiffuse(texture));
        long boxAttribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates
                | VertexAttributes.Usage.Normal;
        boxModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f, boxMat, boxAttribs);

        Material groundMat = new Material(ColorAttribute.createDiffuse(0.2f, 0.5f, 0.2f, 1f));
        long groundAttribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.ColorPacked;
        groundModel = modelBuilder.createBox(5, 0.1f, 5, groundMat, groundAttribs);

        // Create instances
        instances.add(new ModelInstance(boxModel, 0, 0.4f, 0));
        instances.add(new ModelInstance(boxModel, 2, 0.4f, 0));
        instances.add(new ModelInstance(boxModel, -2, 0.4f, 0));
        instances.add(new ModelInstance(groundModel, 0, 0, 0));

        // Setup environment
        environment = new Environment();
        float level = 0.4f;
        ColorAttribute ambient = ColorAttribute.createAmbientLight(level, level, level, 1f);
        environment.set(ambient);

        DirectionalLight dirLight = new DirectionalLight();
        lightPos = new Vector3(2f, 3f, 2f);
        Vector3 lightDir = new Vector3(lightPos).scl(-1).nor();
        dirLight.setDirection(lightDir);
        dirLight.setColor(2f, 2f, 2.5f, 1f);
        environment.add(dirLight);

        // Setup shadow light
        final int SHADOW_MAP_SIZE = 1024;
        final int SHADOW_VIEWPORT = 8;
        final float SHADOW_DEPTH = 20f;
        shadowLight = new WgDirectionalShadowLight(SHADOW_MAP_SIZE, SHADOW_MAP_SIZE, SHADOW_VIEWPORT,
                SHADOW_VIEWPORT, 0f, SHADOW_DEPTH);
        shadowLight.setDirection(lightDir);
        shadowLight.set(dirLight);
        environment.shadowMap = shadowLight;
    }

    private WgTexture createColoredTexture(Color color) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.drawPixel(0, 0);
        WgTexture texture = new WgTexture(pixmap);
        pixmap.dispose();
        return texture;
    }

    public void render() {
        time += Gdx.graphics.getDeltaTime();

        // Animate 3D objects
        animateScene();
        camera3D.update();

        if (useFrameBuffers) {
            renderWithFrameBuffers();
        } else {
            renderDirectShadowScene();
        }
    }

    private void animateScene() {
        instances.get(0).transform.rotate(Vector3.Y, 30f * Gdx.graphics.getDeltaTime());
        instances.get(1).transform.rotate(Vector3.X, 20f * Gdx.graphics.getDeltaTime());
        instances.get(2).transform.rotate(Vector3.Z, 15f * Gdx.graphics.getDeltaTime());
    }

    private void renderWithFrameBuffers() {
        // Render 2-level stacking chain (2D)
        render2LevelChain();

        // Render 3-level stacking chain (3D with shadows)
        render3LevelChainWithShadows();

        // Display all framebuffer results on screen
        displayFrameBufferResults();
    }

    private void render2LevelChain() {
        // Base level: Draw simple colored rectangles
        fbo2Level_Base.begin();
        WgScreenUtils.clear(0.1f, 0.1f, 0.1f, 1f);
        batch.setProjectionMatrix(camera2D.combined);
        batch.begin();

        batch.setColor(Color.RED);
        batch.draw(redTexture, 80, 80, 150, 100);
        batch.setColor(Color.GREEN);
        batch.draw(greenTexture, 280, 180, 150, 100);
        batch.setColor(Color.BLUE);
        batch.draw(blueTexture, 180, 300, 150, 100);

        batch.setColor(Color.WHITE);
        batch.end();
        fbo2Level_Base.end();

        // Top level: Draw base with scaling effect
        fbo2Level_Top.begin();
        WgScreenUtils.clear(0.2f, 0.2f, 0.25f, 1f);
        batch.setProjectionMatrix(camera2D.combined);
        batch.begin();

        float scale2 = 0.9f + 0.1f * (float) Math.sin(time * 2f);
        batch.draw(fbo2Level_Base.getColorBufferTexture(),
                   192 - (192 * scale2)/2, 192 - (192 * scale2)/2,
                   192 * scale2, 192 * scale2);

        batch.end();
        fbo2Level_Top.end();
    }

    private void render3LevelChainWithShadows() {
        // Skip shadow rendering if disabled
        if (!renderShadowScene) {
            return;
        }

        // Single framebuffer: Render 3D scene with shadows
        fbo3Level_Base.begin();
        WgScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        renderShadowScene();
        fbo3Level_Base.end();
    }

    private void renderShadowScene() {
        // Render shadow map
        Vector3 focalPoint = new Vector3(0, 0.5f, 0);
        shadowLight.begin(focalPoint, Vector3.Zero);
        shadowBatch.begin(shadowLight.getCamera(), Color.WHITE, true, RenderPassType.DEPTH_ONLY);
        shadowBatch.render(instances);
        shadowBatch.end();
        shadowLight.end();

        // Render scene with shadows
        modelBatch.begin(camera3D, Color.TEAL, true);
        modelBatch.render(instances, environment);
        modelBatch.end();
    }

    private void renderDirectShadowScene() {
        // Direct rendering without framebuffers - just shadow scene on screen
        WgScreenUtils.clear(0.1f, 0.1f, 0.15f, 1f);
        renderShadowScene();

        // Draw help text
        batch.setProjectionMatrix(camera2D.combined);
        batch.begin();
        font.draw(batch, "Direct Shadow Rendering (No FrameBuffers)", 20, 50);
        font.draw(batch, "Press any key to toggle framebuffer mode", 20, 30);
        batch.end();
    }

    private void displayFrameBufferResults() {
        WgScreenUtils.clear(0.3f, 0.3f, 0.3f, 1f);
        batch.setProjectionMatrix(camera2D.combined);
        batch.begin();

        batch.setColor(Color.WHITE);

        if (renderShadowScene) {
            // Display 2-level chain (2D)
            batch.draw(fbo2Level_Base.getColorBufferTexture(), 20, 280, 180, 180);
            batch.draw(fbo2Level_Top.getColorBufferTexture(), 220, 280, 160, 160);

            // Display 3D shadow scene (single framebuffer)
            batch.draw(fbo3Level_Base.getColorBufferTexture(), 420, 280, 180, 180);

            // Draw section labels
            font.draw(batch, "2D - 2-LEVEL", 30, 270);
            font.draw(batch, "Base", 50, 250);
            font.draw(batch, "Top", 240, 250);

            font.draw(batch, "3D+SHADOW - SINGLE", 420, 270);
            font.draw(batch, "Base (3D)", 440, 250);

            font.draw(batch, "Stacked FrameBuffer Test with 3D Shadow Rendering", 20, 50);
            font.draw(batch, "2D: 2-Level Stacking | 3D: Single Framebuffer with Shadows", 20, 30);
        } else {
            // Display only 2-level chain (2D) - larger display
            batch.draw(fbo2Level_Base.getColorBufferTexture(), 50, 250, 250, 250);
            batch.draw(fbo2Level_Top.getColorBufferTexture(), 350, 250, 220, 220);

            // Draw section labels
            font.draw(batch, "2D FRAMEBUFFER TEST (Shadow Scene Disabled)", 50, 300);
            font.draw(batch, "2-Level Stacking", 50, 270);
            font.draw(batch, "Base (512x512)", 70, 240);
            font.draw(batch, "Top (384x384)", 370, 240);

            font.draw(batch, "renderShadowScene = false", 20, 50);
            font.draw(batch, "Only 2D colored shapes framebuffer chain", 20, 30);
        }

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera2D.viewportWidth = width;
        camera2D.viewportHeight = height;
        camera2D.position.set(width / 2, height / 2, 0);
        camera2D.update();

        camera3D.viewportWidth = width;
        camera3D.viewportHeight = height;
        camera3D.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        redTexture.dispose();
        greenTexture.dispose();
        blueTexture.dispose();
        yellowTexture.dispose();

        // Dispose 3D resources
        modelBatch.dispose();
        shadowBatch.dispose();
        boxModel.dispose();
        groundModel.dispose();

        // Dispose framebuffers
        fbo2Level_Base.dispose();
        fbo2Level_Top.dispose();
        fbo3Level_Base.dispose();
    }

}

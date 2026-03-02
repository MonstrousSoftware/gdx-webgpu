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
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgObjLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Demonstrate wgFrameBuffer use. todo support other formats todo handle viewport if fbo has other dimensions than
 * window
 */

public class FrameBufferTest extends GdxTest {

    WgModelBatch modelBatch1;
    WgModelBatch modelBatch2;
    WgSpriteBatch textureBatch1;
    WgSpriteBatch textureBatch2;
    PerspectiveCamera cam1;
    PerspectiveCamera cam2;
    OrthographicCamera cam3;
    OrthographicCamera cam4;
    PerspectiveCamController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model model;
    ModelInstance instance;
    WgGraphics gfx;
    WgFrameBuffer fbo1;
    WgFrameBuffer fbo2;
    WgFrameBuffer fbo3;
    WgFrameBuffer fbo4;
    Texture texture;

    boolean sharedBatch = true;

    // application
    public void create() {
        gfx = (WgGraphics) Gdx.graphics;

        modelBatch1 = new WgModelBatch();
        textureBatch1 = new WgSpriteBatch();
        if (sharedBatch) {
            modelBatch2 = modelBatch1;
            textureBatch2 = textureBatch1;
        } else {
            modelBatch2 = new WgModelBatch();
            textureBatch2 = new WgSpriteBatch();
        }
        cam1 = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam1.position.set(0, 2, 3);
        cam1.lookAt(0, 0, 0);
        cam1.near = 0.1f;

        cam2 = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam2.position.set(0, 1, -2);
        cam2.lookAt(0, 1, 0);
        cam2.near = 0.1f;

        cam3 = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam3.position.set(80, 80, 0);
        cam4 = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam4.position.set(-80, -80, 0);

        texture = new WgTexture(Gdx.files.internal("data/badlogicsmall.jpg"));

        WgObjLoader loader = new WgObjLoader();
        // these assets need to be put in the class path...
        model = loader.loadModel(Gdx.files.internal("data/g3d/ducky.obj"), true);
        instance = new ModelInstance(model);

        controller = new PerspectiveCamController(cam1);
        Gdx.input.setInputProcessor(controller);
        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        fbo1 = new WgFrameBuffer(640, 480, true);
        fbo2 = new WgFrameBuffer(640, 480, true);
        fbo3 = new WgFrameBuffer(640, 480, true);
        fbo4 = new WgFrameBuffer(640, 480, true);
    }

    public void render() {
        WgScreenUtils.clear(Color.ORANGE);
        float delta = Gdx.graphics.getDeltaTime();
        instance.transform.rotate(Vector3.Y, 15f * delta);

        // render into frame buffer
        fbo1.begin();
        WgScreenUtils.clear(Color.TEAL, true);
        cam1.update();
        modelBatch1.begin(cam1);
        modelBatch1.render(instance);
        modelBatch1.end();
        fbo1.end();

        fbo2.begin();
        WgScreenUtils.clear(Color.BLUE, true);
        cam2.update();
        modelBatch2.begin(cam2);
        modelBatch2.render(instance);
        modelBatch2.end();
        fbo2.end();

        fbo3.begin();
        WgScreenUtils.clear(Color.ROYAL, true);
        cam3.update();
        textureBatch1.setProjectionMatrix(cam3.combined);
        textureBatch1.begin();
        textureBatch1.draw(texture, 0, 0);
        textureBatch1.end();
        fbo3.end();

        fbo4.begin();
        WgScreenUtils.clear(Color.GREEN, true);
        cam4.update();
        textureBatch2.setProjectionMatrix(cam4.combined);
        textureBatch2.begin();
        textureBatch2.draw(texture, 0, 0);
        textureBatch2.end();
        fbo4.end();

        // display 4 copies of the frame buffer
        batch.begin();
        batch.draw(fbo1.getColorBufferTexture(), 20, 100, 256, 180);
        batch.draw(fbo2.getColorBufferTexture(), 320 + 20, 100, 256, 180);
        batch.draw(fbo3.getColorBufferTexture(), 20, 100 + 190, 256, 180);
        batch.draw(fbo4.getColorBufferTexture(), 320 + 20, 100 + 190, 256, 180);
        font.draw(batch, "Using FrameBuffer as Render Target", 0, 20);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam1.viewportWidth = width;
        cam1.viewportHeight = height;
        cam1.update();
        cam2.viewportWidth = width;
        cam2.viewportHeight = height;
        cam2.update();
        cam3.viewportWidth = width;
        cam3.viewportHeight = height;
        cam3.update();
        cam4.viewportWidth = width;
        cam4.viewportHeight = height;
        cam4.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        modelBatch1.dispose();
        textureBatch1.dispose();
        if (!sharedBatch) {
            modelBatch2.dispose();
            textureBatch2.dispose();
        }
        model.dispose();
        fbo1.dispose();
        fbo2.dispose();
        fbo3.dispose();
        fbo4.dispose();
    }

}

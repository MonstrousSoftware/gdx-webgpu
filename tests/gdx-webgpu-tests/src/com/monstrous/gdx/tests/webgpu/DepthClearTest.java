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
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Test preservation of depth buffer The small close by cube is rendered in pass 1 Then we clear the screen color but
 * not the depth buffer Then we draw the big far away cube in pass 2 There should be a "ghost" of the smaller cube
 * visible in background color as a gap in the bigger cube. This demonstrates the depth buffer from pass 1 is preserved.
 */
public class DepthClearTest extends GdxTest {

    WgModelBatch modelBatch;
    WgModelBatch modelBatch2;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model box;
    ModelInstance instanceClose, instanceFar;

    public void create() {
        modelBatch = new WgModelBatch();
        modelBatch2 = new WgModelBatch();
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, 0);
        cam.near = 0.1f;

        // controller = new PerspectiveCamController(cam);
        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);
        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        //
        // Create some model instances
        //
        ModelBuilder modelBuilder = new WgModelBuilder();
        WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat = new Material(TextureAttribute.createDiffuse(texture2));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates
                | VertexAttributes.Usage.Normal;
        box = modelBuilder.createBox(1, 1, 1, mat, attribs);

        instanceFar = new ModelInstance(box, 0, 0, -5);
        instanceClose = new ModelInstance(box, 0, 0, -2);
        instanceClose.transform.scale(0.1f, 2.0f, 0.1f);
        instanceFar.transform.scale(2f, 2f, 2f);
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        instanceClose.transform.rotate(Vector3.Y, delta * 15f);
        // instanceFar.transform.rotate(Vector3.Y, -delta*15f);

        WgScreenUtils.clear(Color.TEAL, true);
        cam.update();
        modelBatch.begin(cam);
        modelBatch.render(instanceClose);
        modelBatch.end();

        // note: we need a different model batch here
        WgScreenUtils.clear(Color.TEAL, false);
        modelBatch2.begin(cam);
        modelBatch2.render(instanceFar);
        modelBatch2.end();

        // You should see the far cube, but a gap where the close cube is. The close cube is invisible, but the depth of
        // it still
        // hides part of the far cube.

        batch.begin();
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, 20);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();

    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        modelBatch.dispose();
        box.dispose();

    }

}

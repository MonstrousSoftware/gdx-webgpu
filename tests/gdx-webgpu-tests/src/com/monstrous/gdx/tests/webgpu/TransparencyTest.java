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
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Test rendering of semi-transparent models Shows a number of cubes and 'window panes'. If you look from different
 * angles, you should see the cubes through the panes. and overlapping panes should get progressively more opaque.
 */
public class TransparencyTest extends GdxTest {

    WgModelBatch modelBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    Model boxTrans;
    Model boxOpaque;
    Array<ModelInstance> instances;

    public void create() {
        modelBatch = new WgModelBatch();
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(-6.5f, 1.2f, 6.1f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 100;

        // controller = new PerspectiveCamController(cam);
        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);

        instances = new Array<>();

        ColorAttribute colorAttribute = new ColorAttribute(ColorAttribute.Diffuse, 0, 0, 1, 0.3f);
        BlendingAttribute blendingAttribute = new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        blendingAttribute.opacity = 0.15f;
        blendingAttribute.blended = true;
        Material matTrans = new Material(colorAttribute, blendingAttribute);

        WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material matSolid = new Material(TextureAttribute.createDiffuse(texture2));

        //
        // Create some model instances
        //
        ModelBuilder modelBuilder = new WgModelBuilder();
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates
                | VertexAttributes.Usage.Normal;
        boxTrans = modelBuilder.createBox(3, 3, 0.2f, matTrans, attribs);
        boxOpaque = modelBuilder.createBox(1, 1, 1, matSolid, attribs);

        instances.add(new ModelInstance(boxTrans, 0, 1, -2));
        instances.add(new ModelInstance(boxOpaque, 0, 0, -5));
        instances.add(new ModelInstance(boxTrans, 0, 1, -8));
        instances.add(new ModelInstance(boxOpaque, 0, 0, -11));
        instances.add(new ModelInstance(boxTrans, 0, 1, -14));
    }

    public void render() {
        WgScreenUtils.clear(Color.WHITE, true);
        cam.update();
        // System.out.println("cam: "+cam.position);
        modelBatch.begin(cam);
        modelBatch.render(instances);
        modelBatch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();

    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        boxOpaque.dispose();
        boxTrans.dispose();
    }

}

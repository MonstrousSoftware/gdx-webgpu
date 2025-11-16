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
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.HDRLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.wrappers.*;

/**
 * Test IBL Generates environment cube map from equirectangular texture.
 *
 */

public class IBL_EquirectangularMap extends GdxTest {
    CameraInputController controller;
    PerspectiveCamera cam;
    SkyBox skyBox;
    WgTexture equiRectangular;

    public void create() {

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, 0f);
        cam.direction.set(0, 0, 1);
        cam.near = 1f;
        cam.far = 100f;
        cam.update();

        controller = new CameraInputController(cam);
        controller.scrollFactor = -0.01f;
        Gdx.input.setInputProcessor(controller);

        // load equirectangular texture from HDR file format
        equiRectangular = HDRLoader.loadHDR(Gdx.files.internal("data/hdr/leadenhall_market_2k.hdr"), true);

        // Generate environment map from equirectangular texture
        WgCubemap envMap = IBLGenerator.buildCubeMapFromEquirectangularTexture(equiRectangular, 2048);

        // use cube map as a sky box
        skyBox = new SkyBox(envMap);
    }

    public void render() {
        controller.update();
        skyBox.renderPass(cam, true);
    }

    @Override
    public void dispose() {
        skyBox.dispose();
        equiRectangular.dispose();
    }

}

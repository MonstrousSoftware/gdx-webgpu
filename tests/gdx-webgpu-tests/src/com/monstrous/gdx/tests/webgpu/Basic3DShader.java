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
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/** Demonstration of using a custom shader for model instance rendering.
 *  In this case a single alternative shader is used for all renderables via the configuration parameter
 *  that is passed to the model batch.
 *  If you need more flexibility in applying different shaders to different renderables, you can pass a shader provider
 *  to model batch and override `getShader()` to return a specific WgShader appropriate for each renderable.
 *
 */
public class Basic3DShader extends GdxTest {
    public PerspectiveCamera cam;
    public CameraInputController inputController;
    public WgModelBatch modelBatch;
    public Model model;
    public ModelInstance instance;

    @Override
    public void create() {
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.shaderSource = getShaderSource();    // use alternative shader source for all 3d rendering

        modelBatch = new WgModelBatch(config);

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 150f;
        cam.update();

        ModelBuilder modelBuilder = new WgModelBuilder();
        model = modelBuilder.createBox(5f, 5f, 5f, new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        instance = new ModelInstance(model);

        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));
    }

    @Override
    public void render() {

        inputController.update();

        WgScreenUtils.clear(Color.TEAL, true);

        modelBatch.begin(cam);
        modelBatch.render(instance);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        model.dispose();
    }

    private String getShaderSource(){
        return Gdx.files.classpath("data/wgsl/modelbatchNormal.wgsl").readString();
    }
}

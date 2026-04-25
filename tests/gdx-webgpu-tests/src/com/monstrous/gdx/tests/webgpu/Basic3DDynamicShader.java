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
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;


/** Demonstration of switching shaders for different renderables.
 *  This demo applies different shaders to different renderables by using a shader provider that determines
 *  which shader to use for each renderable.
 *  We use WgDefaultShaderProvider and override `getShader()` to return a specific WgShader.
 *  In this simple example we identify renderables that need an alternative shader by means of a new material attribute type.  This also makes
 *  sure these renderables are not passed to the default shader.
 *  (See https://xoppa.github.io/blog/using-materials-with-libgdx/)
 */
public class Basic3DDynamicShader extends GdxTest {
    public PerspectiveCamera cam;
    public CameraInputController inputController;
    public WgModelBatch modelBatch;
    public Model modelA, modelB;
    public Array<ModelInstance> instances;
    public Environment environment;

    static class SpecialShader extends WgDefaultShader {
        // create a new attribute type to flag renderables that require a special shader
        public static class ShaderAttribute extends IntAttribute {
            public final static String NormalsAlias = "renderNormals";
            public final static long Normals = register(NormalsAlias);

            public ShaderAttribute(long type, int value) {
                super(type, value);
            }
        }

        public SpecialShader(Renderable renderable) {
            super(renderable);
        }

        @Override
        protected String getShaderSource() {
            return Gdx.files.classpath("data/wgsl/modelbatchNormal.wgsl").readString();
        }

        @Override
        public boolean canRender(Renderable renderable) {
            return renderable.material.has(ShaderAttribute.Normals);
        }
    }

    @Override
    public void create() {

        ShaderProvider shaderProvider = new WgDefaultShaderProvider() {
            @Override
            protected Shader createShader(Renderable renderable) {
                if(renderable.material.has(SpecialShader.ShaderAttribute.Normals))
                    return new SpecialShader(renderable);
                return super.createShader(renderable);  // use default
            }
        };
        modelBatch = new WgModelBatch(shaderProvider);

        environment = new Environment();
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 150f;
        cam.update();

        // create some model instances for default rendering and some for special rendering
        instances = new Array<>();
        ModelBuilder modelBuilder = new WgModelBuilder();
        modelA = modelBuilder.createSphere(5f, 5f, 5f, 16, 16,
                new Material(ColorAttribute.createDiffuse(Color.CYAN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        modelB = modelBuilder.createSphere(5f, 5f, 5f, 16, 16,
            new Material(new SpecialShader.ShaderAttribute(SpecialShader.ShaderAttribute.Normals, 1)),  // trigger special shader
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        instances.add(new ModelInstance(modelA, 6, 0, 6));
        instances.add(new ModelInstance(modelA, -6, 0, -6));
        instances.add(new ModelInstance(modelB, -6, 0, 6));
        instances.add(new ModelInstance(modelB, 6, 0, -6));

        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));
    }

    @Override
    public void render() {
        inputController.update();

        WgScreenUtils.clear(Color.TEAL, true);

        modelBatch.begin(cam);
        modelBatch.render(instances, environment);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        modelA.dispose();
        modelB.dispose();
    }

}

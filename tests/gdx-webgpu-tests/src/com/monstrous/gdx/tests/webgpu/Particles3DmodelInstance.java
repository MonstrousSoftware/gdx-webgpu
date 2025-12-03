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
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleShader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleSystem;
import com.badlogic.gdx.graphics.g3d.particles.batches.ModelInstanceParticleBatch;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.assets.WgAssetManager;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.particles.WgParticleShader;
import com.monstrous.gdx.webgpu.graphics.g3d.particles.batches.WgBillboardParticleBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.particles.batches.WgPointSpriteParticleBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Test of loading 3d particle effects from a file. Demonstrates model instance particles. Note: the Flame editor
 * doesn't support glb/gltf models, but you can edit the pfx file to replace the model file name from a g3db file.
 */

public class Particles3DmodelInstance extends GdxTest {

    public final static String FX1 = "data/g3d/particle/ducks.pfx";

    public PerspectiveCamera cam;
    public WgModelBatch modelBatch;
    public WgAssetManager assets;
    private ParticleEffect currentEffects;
    private ParticleSystem particleSystem;
    private Color bgColor;
    private CameraInputController inputController;
    private Environment environment;

    @Override
    public void create() {
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 1500f;
        cam.update();

        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));

        modelBatch = new WgModelBatch();
        bgColor = new Color(0x201D80FF);

        assets = new WgAssetManager();

        // create a particle system
        particleSystem = new ParticleSystem();

        ModelInstanceParticleBatch instanceParticleBatch = new ModelInstanceParticleBatch();
        particleSystem.add(instanceParticleBatch);

        ParticleEffectLoader.ParticleEffectLoadParameter loadParam = new ParticleEffectLoader.ParticleEffectLoadParameter(
                particleSystem.getBatches());

        assets.load(FX1, ParticleEffect.class, loadParam);
        // halt the main thread until assets are loaded.
        // this is bad for actual games, but okay for demonstration purposes.
        assets.finishLoading();
        currentEffects = assets.get(FX1, ParticleEffect.class).copy();

        currentEffects.init();
        particleSystem.add(currentEffects);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

    }

    @Override
    public void render() {
        inputController.update();

        WgScreenUtils.clear(bgColor, true);

        modelBatch.begin(cam);
        particleSystem.update();
        particleSystem.begin();
        particleSystem.draw();
        particleSystem.end();
        modelBatch.render(particleSystem, environment);
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
        if (currentEffects != null)
            currentEffects.dispose();
        modelBatch.dispose();
        assets.dispose();
    }
}

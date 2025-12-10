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
 * Test of loading 3d particle effects from a file. Demonstrates texture rotation.
 */

public class Particles3DSnow extends GdxTest {

    public final static String FX1 = "data/g3d/particle/snow.pfx";

    public PerspectiveCamera cam;
    public WgModelBatch modelBatch;
    public WgAssetManager assets;
    private ParticleEffect currentEffects;
    private ParticleSystem particleSystem;
    private Color bgColor;
    private CameraInputController inputController;

    @Override
    public void create() {
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 150f;
        cam.update();

        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));

        modelBatch = new WgModelBatch();
        bgColor = new Color(0x201D80FF);

        // cam = new OrthographicCamera(18.0f, 18.0f);
        assets = new WgAssetManager();

        // create a particle system
        particleSystem = new ParticleSystem();

        // WgPointSpriteParticleBatch pointSpriteBatch = new WgPointSpriteParticleBatch();

        // create a point sprite batch and add it to the particle system
        WgPointSpriteParticleBatch pointSpriteBatch = new WgPointSpriteParticleBatch(1000,
                new WgParticleShader.Config(WgParticleShader.ParticleType.Point),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f), null);
        pointSpriteBatch.setCamera(cam);
        particleSystem.add(pointSpriteBatch);

        ParticleEffectLoader.ParticleEffectLoadParameter loadParam = new ParticleEffectLoader.ParticleEffectLoadParameter(
                particleSystem.getBatches());
        ParticleEffectLoader loader = new ParticleEffectLoader(new InternalFileHandleResolver());
        assets.setLoader(ParticleEffect.class, loader);

        assets.load(FX1, ParticleEffect.class, loadParam);
    }

    @Override
    public void render() {
        inputController.update();

        if (assets.update(5)) {

            currentEffects = assets.get(FX1, ParticleEffect.class).copy();
            currentEffects.init();
            particleSystem.add(currentEffects);
        }

        WgScreenUtils.clear(bgColor, true);

        modelBatch.begin(cam);
        particleSystem.update();
        particleSystem.begin();
        particleSystem.draw();
        particleSystem.end();
        modelBatch.render(particleSystem);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        if (currentEffects != null)
            currentEffects.dispose();
        modelBatch.dispose();
        assets.dispose();
    }
}

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
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffect;
import com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleShader;
import com.badlogic.gdx.graphics.g3d.particles.ParticleSystem;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
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
 * Test of loading 3d particle effects from a file. Supports only BillBoardParticles for now. Blending and coloring
 * seems to be incorrect.
 */
public class Particles3D extends GdxTest {

    public final static String SMOKE = "fire-and-smoke-pointsprite.pfx";

    public PerspectiveCamera cam;
    public Color bgColor;
    public CameraInputController inputController;
    public WgModelBatch modelBatch;
    public Model model;
    public ModelInstance instance;
    public Environment environment;
    public ParticleEffects particleEffects;
    public Model axesModel;
    public ModelInstance axesInstance;
    public float countDown;

    public static class ParticleEffects implements Disposable {

        private final ParticleSystem particleSystem;
        private final Array<ParticleEffect> activeEffects;
        private final Array<ParticleEffect> deleteList;

        private final ParticleEffect smokeEffect;
        private ParticleEffect ringEffect; // requires point sprites
        private ParticleEffect exhaustFumesEffect;

        public ParticleEffects(Camera cam) {
            // create a particle system
            particleSystem = new ParticleSystem();

            // create a point sprite batch and add it to the particle system
            WgPointSpriteParticleBatch pointSpriteBatch = new WgPointSpriteParticleBatch(1000,
                    new WgParticleShader.Config(WgParticleShader.ParticleType.Point),
                    new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f), null);
            pointSpriteBatch.setCamera(cam);
            particleSystem.add(pointSpriteBatch);

            // NB don't set useGPU to true for now.
            // requires shader implementation

            WgBillboardParticleBatch billboardParticleBatch = new WgBillboardParticleBatch(
                    ParticleShader.AlignMode.Screen, false, 1000,
                    new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.15f), null);
            billboardParticleBatch.setCamera(cam);
            particleSystem.add(billboardParticleBatch);

            // load particle effect from file
            // via the asset manager
            WgAssetManager assets = new WgAssetManager();
            ParticleEffectLoader.ParticleEffectLoadParameter loadParam = new ParticleEffectLoader.ParticleEffectLoadParameter(
                    particleSystem.getBatches());
            assets.load("data/g3d/particle/" + SMOKE, ParticleEffect.class, loadParam);
            assets.load("data/g3d/particle/explosion-ring.pfx", ParticleEffect.class, loadParam);

            assets.finishLoading();
            smokeEffect = assets.get("data/g3d/particle/" + SMOKE);
            ringEffect = assets.get("data/g3d/particle/explosion-ring.pfx");
            // exhaustFumesEffect = assets.get("data/g3d/particle/green-scatter.pfx");

            activeEffects = new Array<>();
            deleteList = new Array<>();
        }

        // puff of smoke effect where character lands
        public void addExhaustFumes(Matrix4 transform) {
            // we cannot use the originalEffect, we must make a copy each time we create new particle effect
            addEffect(exhaustFumesEffect.copy(), transform);
        }

        // add effect
        // we use a transform rather than only a position because some effects may need to be oriented
        // e.g. dust trail behind the player
        private void addEffect(ParticleEffect effect, Matrix4 transform) {
            // add loaded effect to particle system
            effect.setTransform(transform);
            effect.init();
            effect.start(); // optional: particle will begin playing immediately
            particleSystem.add(effect);
            activeEffects.add(effect);
        }

        public void addFire(Vector3 position) {
            // add loaded effect to particle system

            // we cannot use the originalEffect, we must make a copy each time we create new particle effect
            ParticleEffect effect = smokeEffect.copy();
            effect.translate(position);
            effect.init();
            effect.start(); // optional: particle will begin playing immediately
            particleSystem.add(effect);
            activeEffects.add(effect);
        }

        public void addExplosion(Vector3 position) {
            // add loaded effect to particle system

            // we cannot use the originalEffect, we must make a copy each time we create new particle effect
            ParticleEffect effect = ringEffect.copy();
            effect.translate(position);
            effect.init();
            effect.start(); // optional: particle will begin playing immediately
            particleSystem.add(effect);
            activeEffects.add(effect);
        }

        public void update(float deltaTime) {
            particleSystem.update(deltaTime);

            // remove effects that have finished
            deleteList.clear();
            for (ParticleEffect effect : activeEffects) {
                if (effect.isComplete()) {
                    // Gdx.app.debug("particle effect completed", "");
                    particleSystem.remove(effect);
                    effect.dispose();
                    deleteList.add(effect);
                }
            }
            activeEffects.removeAll(deleteList, true);
        }

        /** modelBatch must be between begin() and end() */
        public void render(WgModelBatch modelBatch) {
            particleSystem.begin();
            particleSystem.draw();
            particleSystem.end();
            modelBatch.render(particleSystem);
        }

        @Override
        public void dispose() {
            particleSystem.removeAll();
            for (ParticleEffect effect : activeEffects)
                effect.dispose();
        }
    }

    @Override
    public void create() {
        modelBatch = new WgModelBatch();
        bgColor = new Color(0x878787FF);

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10f, 10f, 10f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 150f;
        cam.update();

        Material mat = new Material(ColorAttribute.createDiffuse(Color.GREEN),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 0.15f));
        ModelBuilder modelBuilder = new WgModelBuilder();
        model = modelBuilder.createBox(1f, 1f, 1f, mat, VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates);
        instance = new ModelInstance(model);

        createAxes();

        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController = new CameraInputController(cam)));

        particleEffects = new ParticleEffects(cam);
        spawnFire(Vector3.Zero);
        spawnExplosion(Vector3.Zero);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        countDown -= delta;
        if (countDown < 0) {
            countDown = 3;
            spawnExplosion(Vector3.Zero);
            spawnFire(Vector3.Zero);
        }
        inputController.update();

        particleEffects.update(delta);

        WgScreenUtils.clear(bgColor, true);

        modelBatch.begin(cam);
        modelBatch.render(axesInstance);
        particleEffects.render(modelBatch);
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
        model.dispose();
        particleEffects.dispose();
    }

    public void spawnFire(Vector3 position) {
        particleEffects.addFire(position);
    }

    public void spawnExplosion(Vector3 position) {

        particleEffects.addExplosion(position);
    }

    public void spawnSmokeTrail(Matrix4 transform) {

        particleEffects.addExhaustFumes(transform);
    }

    final float GRID_MIN = -5f;
    final float GRID_MAX = 5f;
    final float GRID_STEP = 1f;

    private void createAxes() {
        WgModelBuilder modelBuilder = new WgModelBuilder();
        modelBuilder.begin();
        MeshPartBuilder builder = modelBuilder.part("grid", GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, new Material());
        builder.setColor(Color.LIGHT_GRAY);
        for (float t = GRID_MIN; t <= GRID_MAX; t += GRID_STEP) {
            builder.line(t, 0, GRID_MIN, t, 0, GRID_MAX);
            builder.line(GRID_MIN, 0, t, GRID_MAX, 0, t);
        }
        builder = modelBuilder.part("axes", GL20.GL_LINES,
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorUnpacked, new Material());
        builder.setColor(Color.RED);
        builder.line(0, 0, 0, 100, 0, 0);
        builder.setColor(Color.GREEN);
        builder.line(0, 0, 0, 0, 100, 0);
        builder.setColor(Color.BLUE);
        builder.line(0, 0, 0, 0, 0, 100);
        axesModel = modelBuilder.end();
        axesInstance = new ModelInstance(axesModel);
    }

}

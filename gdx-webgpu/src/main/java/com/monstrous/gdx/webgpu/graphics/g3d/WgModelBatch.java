/*******************************************************************************
 * Copyright 2025 Monstrous Software.
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

package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderableSorter;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.FlushablePool;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.MaterialsCache;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgShader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgDefaultRenderableSorter;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

/**
 * Class for 3d rendering, e.g. to render model instances. Use instead of ModelBatch.
 */
public class WgModelBatch implements Disposable {

    private final Config config;
    private boolean drawing;
    private WebGPURenderPass renderPass;
    private final ShaderProvider shaderProvider;
    private final boolean ownsShaderProvider;
    private final Array<Renderable> renderables;
    protected final RenderablePool renderablesPool = new RenderablePool();
    private Camera camera;
    private final RenderableSorter sorter;
    public int numRenderables;
    public int drawCalls;
    public int shaderSwitches;
    // public int numMaterials;
    public MaterialsCache materials;

    public static class Config {
        public int maxInstances;
        public int maxMaterials;
        public int maxDirectionalLights;
        public int maxPointLights;
        public int numBones; // max bone count per rigged instance
        public int maxRigged; // max number of instances that are rigged
        public boolean usePBR; // use physics based rendering
        public MaterialsCache materials;

        public Config() {
            this.maxInstances = 1024;
            this.maxMaterials = 512;
            this.maxDirectionalLights = 3;
            this.maxPointLights = 3;
            this.numBones = 48; // todo
            this.maxRigged = 20;
            this.usePBR = true;
            this.materials = new MaterialsCache(256);
        }
    }

    protected static class RenderablePool extends FlushablePool<Renderable> {
        @Override
        protected Renderable newObject() {
            return new Renderable();
        }

        @Override
        public Renderable obtain() {
            Renderable renderable = super.obtain();
            renderable.environment = null;
            renderable.material = null;
            renderable.meshPart.set("", null, 0, 0, 0);
            renderable.shader = null;
            renderable.userData = null;
            return renderable;
        }
    }

    /**
     * Create a ModelBatch.
     *
     */
    public WgModelBatch() {
        this(new Config());
    }

    public WgModelBatch(Config config) {
        this(null, config);
    }

    public WgModelBatch(ShaderProvider shaderProvider) {
        this(shaderProvider, null);
    }

    public WgModelBatch(ShaderProvider shaderProvider, Config config) {
        this.config = config == null ? new Config() : config;
        this.shaderProvider = shaderProvider == null ? new WgDefaultShaderProvider(this.config) : shaderProvider;
        ownsShaderProvider = shaderProvider == null;
        renderables = new Array<>();
        materials = new MaterialsCache(this.config.maxMaterials);
        this.config.materials = materials;
        this.sorter = new WgDefaultRenderableSorter();
        drawing = false;
    }

    public boolean isDrawing() {
        return drawing;
    }

    public void begin(final Camera camera) {
        begin(camera, null, false, RenderPassType.COLOR_AND_DEPTH);
    }

    public void begin(final Camera camera, final Color clearColor) {
        begin(camera, clearColor, false, RenderPassType.COLOR_AND_DEPTH);
    }

    public void begin(final Camera camera, final Color clearColor, boolean clearDepth) {
        begin(camera, clearColor, clearDepth, RenderPassType.COLOR_AND_DEPTH);
    }

    public void begin(final Camera camera, final Color clearColor, boolean clearDepth, int numSamples) {
        begin(camera, clearColor, clearDepth, RenderPassType.COLOR_AND_DEPTH, numSamples);
    }

    // extra param for now to identify depth pass so that render pass is created without color attachment
    // can we detect this another way?
    public void begin(final Camera camera, final Color clearColor, boolean clearDepth, RenderPassType passType) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        int numSamples = gfx.getContext().getSamples();
        begin(camera, clearColor, clearDepth, passType, numSamples);
    }

    public void begin(final Camera camera, final Color clearColor, boolean clearDepth, RenderPassType passType,
            int numSamples) {
        if (drawing)
            throw new RuntimeException("Must end() before begin()");
        drawing = true;
        this.camera = camera;

        renderPass = RenderPassBuilder.create("ModelBatch", clearColor, clearDepth, numSamples, passType);

        renderables.clear();
        shaderSwitches = 0;
        drawCalls = 0;
        materials.start();
    }

    public void render(final Renderable renderable) {
        renderable.shader = shaderProvider.getShader(renderable);
        renderables.add(renderable);
    }

    public void render(final RenderableProvider renderableProvider) {

        int offset = renderables.size;
        renderableProvider.getRenderables(renderables, renderablesPool);
        for (int i = offset; i < renderables.size; i++) {
            Renderable renderable = renderables.get(i);
            renderable.shader = shaderProvider.getShader(renderable);
        }
    }

    public void render(final RenderableProvider renderableProvider, final Environment environment) {
        final int offset = renderables.size;
        renderableProvider.getRenderables(renderables, renderablesPool);
        for (int i = offset; i < renderables.size; i++) {
            Renderable renderable = renderables.get(i);
            renderable.environment = environment;
            renderable.shader = shaderProvider.getShader(renderable);

        }
    }

    public <T extends RenderableProvider> void render(final Iterable<T> renderableProviders) {
        for (final RenderableProvider renderableProvider : renderableProviders)
            render(renderableProvider);
    }

    public <T extends RenderableProvider> void render(final Iterable<T> renderableProviders,
            final Environment environment) {
        for (final RenderableProvider renderableProvider : renderableProviders)
            render(renderableProvider, environment);
    }

    // todo add other render() combinations

    public void flush() {
        if (renderables.size > config.maxInstances)
            throw new ArrayIndexOutOfBoundsException(
                    "Too many renderables (> " + renderables.size + "). Increase config.maxInstances.");
        sorter.sort(camera, renderables);

        WgShader currentShader = null;
        for (Renderable renderable : renderables) {
            if (currentShader != renderable.shader) {
                if (currentShader != null) {
                    currentShader.end();
                    drawCalls += currentShader.drawCalls;
                    shaderSwitches++;
                }
                currentShader = (WgShader) renderable.shader;
                currentShader.begin(camera, renderable, renderPass);
            }
            currentShader.render(renderable);
        }
        if (currentShader != null) {
            currentShader.end();
            drawCalls += currentShader.drawCalls;
        }
        renderablesPool.flush();
        numRenderables = renderables.size;
        renderables.clear();
    }

    public void end() {
        if (!drawing) // catch incorrect usage
            throw new RuntimeException("Cannot end() without begin()");
        drawing = false;
        flush();
        renderPass.end();
        renderPass = null;
    }

    @Override
    public void dispose() {
        if (ownsShaderProvider)
            shaderProvider.dispose();
        materials.dispose();
    }

    public int getMaterialCount() {
        return materials.count();
    }

}

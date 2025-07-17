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
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgShader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgDefaultRenderableSorter;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

/**
 * Class for 3d rendering, e.g. to render model instances.
 */
public class WgModelBatch implements Disposable {

    private WgDefaultShader.Config config;
    private boolean drawing;
    private WebGPURenderPass renderPass;
    private ShaderProvider shaderProvider;
    private boolean ownsShaderProvider;
    private final Array<Renderable> renderables;
    protected final RenderablePool renderablesPool = new RenderablePool();
    private Camera camera;
    private RenderableSorter sorter;
    public int numRenderables;
    public int drawCalls;
    public int shaderSwitches;
    public int numMaterials;


    protected static class RenderablePool extends FlushablePool<Renderable> {
        @Override
        protected Renderable newObject () {
            return new Renderable();
        }

        @Override
        public Renderable obtain () {
            Renderable renderable = super.obtain();
            renderable.environment = null;
            renderable.material = null;
            renderable.meshPart.set("", null, 0, 0, 0);
            renderable.shader = null;
            renderable.userData = null;
            return renderable;
        }
    }

    /** Create a ModelBatch.
     *
     */
    public WgModelBatch() {
        this(new WgDefaultShader.Config());
    }

    public WgModelBatch(WgDefaultShader.Config config) {
        this.config = config;
        drawing = false;

        shaderProvider = new WgDefaultShaderProvider(config);
        ownsShaderProvider = true;
        renderables = new Array<>();
        this.sorter = new WgDefaultRenderableSorter();
    }

    public WgModelBatch(ShaderProvider shaderProvider) {
        this(shaderProvider, null);
    }

    public WgModelBatch(ShaderProvider shaderProvider, WgDefaultShader.Config config) {
        this.config = config == null ? new WgDefaultShader.Config() : config;
        drawing = false;

        this.shaderProvider = shaderProvider;
        ownsShaderProvider = false;
        renderables = new Array<>();
        this.sorter = new WgDefaultRenderableSorter();
    }


    public boolean isDrawing () {
        return drawing;
    }

    public void begin(final Camera camera){
        begin(camera, null, false, RenderPassType.COLOR_AND_DEPTH);
    }

    public void begin(final Camera camera, final Color clearColor) {
        begin(camera, clearColor, false, RenderPassType.COLOR_AND_DEPTH);
    }

    public void begin(final Camera camera, final Color clearColor, boolean clearDepth){
        begin(camera, clearColor, clearDepth, RenderPassType.COLOR_AND_DEPTH);
    }

    // extra param for now to identify depth pass so that render pass is created without color attachment
    // can we detect this another way?
    public void begin(final Camera camera, final Color clearColor, boolean clearDepth, RenderPassType passType) {
        if (drawing)
            throw new RuntimeException("Must end() before begin()");
        drawing = true;
        this.camera = camera;

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        renderPass = RenderPassBuilder.create("ModelBatch", clearColor, clearDepth, gfx.getContext().getSamples(), passType);

        renderables.clear();
        shaderSwitches = 0;
        drawCalls = 0;
        numMaterials = 0;
    }


    public void render(final Renderable renderable){
        renderable.shader = shaderProvider.getShader(renderable);
        renderables.add(renderable);
    }

    public void render (final RenderableProvider renderableProvider) {

        int offset = renderables.size;
        renderableProvider.getRenderables(renderables, renderablesPool);
        for(int i = offset; i < renderables.size; i++){
            Renderable renderable = renderables.get(i);
            renderable.shader = shaderProvider.getShader(renderable);
        }
    }

    public void render (final RenderableProvider renderableProvider, final Environment environment) {
        final int offset = renderables.size;
        renderableProvider.getRenderables(renderables, renderablesPool);
        for (int i = offset; i < renderables.size; i++) {
            Renderable renderable = renderables.get(i);
            renderable.environment = environment;
            renderable.shader = shaderProvider.getShader(renderable);
        }
    }

    public <T extends RenderableProvider> void render (final Iterable<T> renderableProviders) {
        for (final RenderableProvider renderableProvider : renderableProviders)
            render(renderableProvider);
    }

    public <T extends RenderableProvider> void render (final Iterable<T> renderableProviders, final Environment environment) {
        for (final RenderableProvider renderableProvider : renderableProviders)
            render(renderableProvider, environment);
    }

    // todo add other render() combinations

    public void flush() {
        if(renderables.size > config.maxInstances)
            throw new ArrayIndexOutOfBoundsException("Too many renderables");
        sorter.sort(camera, renderables);

        WgShader currentShader = null;
        for(Renderable renderable : renderables) {
            if (currentShader != renderable.shader) {
                if (currentShader != null) {
                    numMaterials += currentShader.numMaterials;

                    currentShader.end();
                    drawCalls += currentShader.drawCalls;
                    shaderSwitches++;
                }
                currentShader = (WgShader) renderable.shader;
                currentShader.begin(camera, renderable, renderPass);
            }
            currentShader.render(renderable);
        }
        if (currentShader != null){
            numMaterials += currentShader.numMaterials;
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
    public void dispose(){
        if(ownsShaderProvider)
            shaderProvider.dispose();
    }



}

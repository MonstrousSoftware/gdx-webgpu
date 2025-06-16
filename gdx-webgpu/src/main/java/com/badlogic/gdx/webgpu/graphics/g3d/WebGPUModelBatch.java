package com.badlogic.gdx.webgpu.graphics.g3d;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.utils.DefaultRenderableSorter;
import com.badlogic.gdx.graphics.g3d.utils.RenderableSorter;
import com.badlogic.gdx.graphics.g3d.utils.ShaderProvider;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.graphics.g3d.shaders.WebGPUDefaultShader;
import com.badlogic.gdx.webgpu.graphics.g3d.shaders.WebGPUDefaultShaderProvider;
import com.badlogic.gdx.graphics.*;

import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.webgpu.graphics.g3d.utils.WebGPUDefaultRenderableSorter;
import com.badlogic.gdx.webgpu.wrappers.RenderPassBuilder;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;


/**
 * Class for 3d rendering, e.g. to render model instances.
 */
public class WebGPUModelBatch implements Disposable {

    private WebGPUDefaultShader.Config config;
    private boolean drawing;
    private WebGPURenderPass renderPass;
    private WebGPUDefaultShaderProvider shaderProvider;
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
    public WebGPUModelBatch() {
        this(new WebGPUDefaultShader.Config());
    }

    public WebGPUModelBatch(WebGPUDefaultShader.Config config) {
        this.config = config;
        drawing = false;

        shaderProvider = new WebGPUDefaultShaderProvider(config);
        renderables = new Array<>();
        this.sorter = new WebGPUDefaultRenderableSorter();
    }


    public boolean isDrawing () {
        return drawing;
    }


    public void begin(final Camera camera) {
        if (drawing)
            throw new RuntimeException("Must end() before begin()");
        drawing = true;
        this.camera = camera;

        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        renderPass = RenderPassBuilder.create(null, gfx.getSamples());

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


        WebGPUDefaultShader currentShader = null;
        for(Renderable renderable : renderables) {
            if (currentShader != renderable.shader) {
                if (currentShader != null) {
                    numMaterials += currentShader.numMaterials;

                    currentShader.end();
                    drawCalls += currentShader.drawCalls;
                    shaderSwitches++;
                }
                currentShader = (WebGPUDefaultShader) renderable.shader;
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
        shaderProvider.dispose();
    }



}

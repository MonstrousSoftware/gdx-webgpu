package com.monstrous.gdx.webgpu.graphics.g3d;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.utils.RenderableSorter;
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.badlogic.gdx.graphics.*;

import com.badlogic.gdx.utils.*;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgDefaultRenderableSorter;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;


/**
 * Class for 3d rendering, e.g. to render model instances.
 */
public class WgModelBatch implements Disposable {

    private WgDefaultShader.Config config;
    private boolean drawing;
    private WebGPURenderPass renderPass;
    private WgDefaultShaderProvider shaderProvider;
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
        renderables = new Array<>();
        this.sorter = new WgDefaultRenderableSorter();
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


        WgDefaultShader currentShader = null;
        for(Renderable renderable : renderables) {
            if (currentShader != renderable.shader) {
                if (currentShader != null) {
                    numMaterials += currentShader.numMaterials;

                    currentShader.end();
                    drawCalls += currentShader.drawCalls;
                    shaderSwitches++;
                }
                currentShader = (WgDefaultShader) renderable.shader;
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

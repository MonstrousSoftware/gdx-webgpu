
package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;

public class WgDepthShaderProvider extends BaseShaderProvider {
    public final WgModelBatch.Config config;

    public WgDepthShaderProvider(final WgModelBatch.Config config) {
        this.config = (config == null) ? new WgModelBatch.Config() : config;
    }

    public WgDepthShaderProvider() {
        this(null);
    }

    @Override
    protected Shader createShader(final Renderable renderable) {
        return new WgDepthShader(renderable, config);
    }

}

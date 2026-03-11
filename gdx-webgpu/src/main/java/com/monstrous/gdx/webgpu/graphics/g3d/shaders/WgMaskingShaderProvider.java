package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;

/**
 * Shader provider for depth masking operations. Creates depth shaders that output constant depth values for masking.
 */
public class WgMaskingShaderProvider extends BaseShaderProvider {
    public final WgModelBatch.Config config;

    public WgMaskingShaderProvider(final WgModelBatch.Config config) {
        this.config = (config == null) ? new WgModelBatch.Config() : config;
    }

    public WgMaskingShaderProvider() {
        this(null);
    }

    @Override
    protected Shader createShader(final Renderable renderable) {
        // Use the default depth shader as a depth-only shader (no fragment outputs). The
        // vertex stage already writes clip-space depth, so a fragment stage is unnecessary
        // for depth masking; keep the pipeline depth-only to avoid color output validation.
        return new WgDepthShader(renderable, config);
    }
}

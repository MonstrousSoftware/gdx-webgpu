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

    // Fragment shader for masking - outputs constant depth to create blocking mask
    private static final String MASKING_FRAGMENT_SHADER = "\n// Fragment shader for depth masking\n" + "@fragment\n"
            + "fn fs_main(in: VertexOutput) -> @builtin(frag_depth) f32 {\n"
            + "    // Output depth of 0.0 (nearest possible) to create a blocking mask\n" + "    return 0.0;\n" + "}\n";

    public WgMaskingShaderProvider(final WgModelBatch.Config config) {
        this.config = (config == null) ? new WgModelBatch.Config() : config;
    }

    public WgMaskingShaderProvider() {
        this(null);
    }

    @Override
    protected Shader createShader(final Renderable renderable) {
        // Get the default depth shader source and append masking fragment shader
        String shaderSource = WgDepthShader.getDefaultShaderSource();
        shaderSource = shaderSource + MASKING_FRAGMENT_SHADER;

        // Create depth shader with modified source and fragment entry point for masking
        return new WgDepthShader(renderable, config, shaderSource, "fs_main");
    }
}

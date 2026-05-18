package com.monstrous.gdx.webgpu.graphics.shader.modular;

import com.monstrous.gdx.webgpu.graphics.shader.modular.layout.ShaderLayoutBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderDefines;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.WgShaderTemplate;

public interface WgShaderModule {
    default String getName() {
        return getClass().getSimpleName();
    }

    default String getSignature(ShaderModuleContext context) {
        return getClass().getName();
    }

    default void configureDefines(ShaderDefines defines, ShaderModuleContext context) {
    }

    default void configureLayout(ShaderLayoutBuilder layout, ShaderModuleContext context) {
    }

    default void contribute(WgShaderTemplate template, ShaderModuleContext context) {
    }
}

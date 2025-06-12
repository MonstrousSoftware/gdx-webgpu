package com.badlogic.gdx.webgpu.graphics.g3d.shaders;


import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;

public class WebGPUDefaultShaderProvider extends BaseShaderProvider {
        public final WebGPUDefaultShader.Config config;

        public WebGPUDefaultShaderProvider (final WebGPUDefaultShader.Config config) {
            this.config = (config == null) ? new WebGPUDefaultShader.Config() : config;
        }

        public WebGPUDefaultShaderProvider () {
            this(null);
        }

        @Override
        protected Shader createShader (final Renderable renderable) {
            return new WebGPUDefaultShader(renderable, config);
        }

}

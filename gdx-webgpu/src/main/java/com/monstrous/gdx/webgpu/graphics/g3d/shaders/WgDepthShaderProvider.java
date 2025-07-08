
package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;

public class WgDepthShaderProvider extends BaseShaderProvider {
        public final WgDefaultShader.Config config;

        public WgDepthShaderProvider(final WgDefaultShader.Config config) {
            this.config = (config == null) ? new WgDefaultShader.Config() : config;
        }

        public WgDepthShaderProvider() {
            this(null);
        }

        @Override
        protected Shader createShader (final Renderable renderable) {
            return new WgDepthShader(renderable, config);
        }

}

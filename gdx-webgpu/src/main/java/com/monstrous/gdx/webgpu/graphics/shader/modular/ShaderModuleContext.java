package com.monstrous.gdx.webgpu.graphics.shader.modular;

import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Renderable;

public final class ShaderModuleContext {
    public final Renderable renderable;
    public final VertexAttributes attributes;
    public final Environment environment;

    public ShaderModuleContext(Renderable renderable, VertexAttributes attributes, Environment environment) {
        this.renderable = renderable;
        this.attributes = attributes;
        this.environment = environment;
    }
}

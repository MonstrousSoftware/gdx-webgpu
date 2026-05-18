package com.monstrous.gdx.webgpu.graphics.shader.modular.layout;

public final class UniformField {
    public final String scope;
    public final String name;
    public final UniformType type;

    UniformField(String scope, String name, UniformType type) {
        this.scope = scope;
        this.name = name;
        this.type = type;
    }
}

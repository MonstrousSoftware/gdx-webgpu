package com.monstrous.gdx.webgpu.graphics.shader.template;

public final class TextureBinding {
    public final String scope;
    public final String textureName;
    public final String samplerName;
    public final int textureBindingId;
    public final int samplerBindingId;

    TextureBinding(String scope, String textureName, String samplerName, int textureBindingId, int samplerBindingId) {
        this.scope = scope;
        this.textureName = textureName;
        this.samplerName = samplerName;
        this.textureBindingId = textureBindingId;
        this.samplerBindingId = samplerBindingId;
    }
}

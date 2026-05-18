package com.monstrous.gdx.webgpu.graphics.shader.modular.layout;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.MaterialUniformLayout;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderTemplateException;

public final class ShaderLayoutBuilder {
    public static final String SCOPE_MATERIAL = "material";

    private final ObjectSet<String> availableScopes = new ObjectSet<>();
    private final Array<MaterialUniform> materialUniforms = new Array<>();
    private final Array<MaterialTexture> materialTextures = new Array<>();
    private MaterialUniformLayout materialLayout;

    public ShaderLayoutBuilder allowScope(String scope) {
        availableScopes.add(scope);
        return this;
    }

    public ShaderLayoutBuilder setMaterialLayout(MaterialUniformLayout materialLayout) {
        this.materialLayout = materialLayout;
        allowScope(SCOPE_MATERIAL);
        return this;
    }

    public UniformField addUniform(String scope, String name, UniformType type) {
        return addUniform(scope, name, type, null);
    }

    public UniformField addUniform(String scope, String name, UniformType type,
                                   MaterialUniformLayout.UniformResolver resolver) {
        validateScope(scope);
        if (type == null)
            throw new ShaderTemplateException("Uniform '" + name + "' has no type.");
        if (SCOPE_MATERIAL.equals(scope)) {
            ensureUniqueMaterialUniform(name);
            materialUniforms.add(new MaterialUniform(name, type, resolver));
        } else {
            throw new ShaderTemplateException("Scope '" + scope + "' is not applied by this shader owner.");
        }
        return new UniformField(scope, name, type);
    }

    public TextureBinding addTexture(String scope, String textureName, String samplerName) {
        return addTexture(scope, textureName, samplerName, null);
    }

    public TextureBinding addTexture(String scope, String textureName, String samplerName,
                                     MaterialUniformLayout.TextureResolver resolver) {
        validateScope(scope);
        if (SCOPE_MATERIAL.equals(scope)) {
            ensureUniqueMaterialTexture(textureName, samplerName);
            int textureBindingId = nextMaterialTextureBindingId();
            int samplerBindingId = textureBindingId < 0 ? -1 : textureBindingId + 1;
            materialTextures.add(new MaterialTexture(textureName, samplerName, resolver,
                    textureBindingId, samplerBindingId));
            return new TextureBinding(scope, textureName, samplerName, textureBindingId, samplerBindingId);
        } else {
            throw new ShaderTemplateException("Scope '" + scope + "' is not applied by this shader owner.");
        }
    }

    public UniformField addUniformAt(String scope, int group, int binding, String name, UniformType type) {
        throw new ShaderTemplateException("Explicit uniform binding placement is not supported by this shader owner: "
                + scope + " group=" + group + " binding=" + binding + " name=" + name);
    }

    public TextureBinding addTextureAt(String scope, int group, int binding, String textureName, String samplerName) {
        throw new ShaderTemplateException("Explicit texture binding placement is not supported by this shader owner: "
                + scope + " group=" + group + " binding=" + binding + " texture=" + textureName);
    }

    public void apply() {
        if (materialLayout == null && (materialUniforms.size > 0 || materialTextures.size > 0))
            throw new ShaderTemplateException("Material layout registrations exist but no material layout was set.");
        if (materialLayout != null) {
            for (MaterialUniform uniform : materialUniforms)
                materialLayout.registerUniform(uniform.name, uniform.type.materialLayoutType, uniform.resolver);
            for (MaterialTexture texture : materialTextures)
                materialLayout.registerTexture(texture.textureName, texture.samplerName, texture.resolver);
        }
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("scopes=");
        for (String scope : availableScopes)
            sb.append(scope).append(';');
        sb.append('\n');
        for (MaterialUniform uniform : materialUniforms)
            sb.append("material.uniform ").append(uniform.name).append(' ').append(uniform.type).append('\n');
        for (MaterialTexture texture : materialTextures)
            sb.append("material.texture ").append(texture.textureName).append(' ')
                    .append(texture.samplerName).append('\n');
        return sb.toString();
    }

    private void validateScope(String scope) {
        if (scope == null || !availableScopes.contains(scope))
            throw new ShaderTemplateException("Shader layout scope is not available: " + scope);
    }

    private void ensureUniqueMaterialUniform(String name) {
        if (materialLayout != null && materialLayout.hasUniform(name))
            throw new ShaderTemplateException("Duplicate material uniform: " + name);
        for (MaterialUniform uniform : materialUniforms) {
            if (uniform.name.equals(name))
                throw new ShaderTemplateException("Duplicate material uniform: " + name);
        }
    }

    private void ensureUniqueMaterialTexture(String textureName, String samplerName) {
        if (materialLayout != null && (materialLayout.hasTexture(textureName) || materialLayout.hasSampler(samplerName)))
            throw new ShaderTemplateException("Duplicate material texture or sampler: "
                    + textureName + " / " + samplerName);
        for (MaterialTexture texture : materialTextures) {
            if (texture.textureName.equals(textureName) || texture.samplerName.equals(samplerName))
                throw new ShaderTemplateException("Duplicate material texture or sampler: "
                        + textureName + " / " + samplerName);
        }
    }

    private int nextMaterialTextureBindingId() {
        if (materialLayout == null)
            return -1;
        return materialLayout.getNextBindingId() + materialTextures.size * 2;
    }

    private static final class MaterialUniform {
        final String name;
        final UniformType type;
        final MaterialUniformLayout.UniformResolver resolver;

        MaterialUniform(String name, UniformType type, MaterialUniformLayout.UniformResolver resolver) {
            this.name = name;
            this.type = type;
            this.resolver = resolver;
        }
    }

    private static final class MaterialTexture {
        final String textureName;
        final String samplerName;
        final MaterialUniformLayout.TextureResolver resolver;
        final int textureBindingId;
        final int samplerBindingId;

        MaterialTexture(String textureName, String samplerName, MaterialUniformLayout.TextureResolver resolver,
                        int textureBindingId, int samplerBindingId) {
            this.textureName = textureName;
            this.samplerName = samplerName;
            this.resolver = resolver;
            this.textureBindingId = textureBindingId;
            this.samplerBindingId = samplerBindingId;
        }
    }
}

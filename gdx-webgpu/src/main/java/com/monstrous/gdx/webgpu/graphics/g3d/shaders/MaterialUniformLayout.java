package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/**
 * Describes the structure of the material uniform group (group 1) for a shader.
 *
 * Each uniform and texture slot carries a resolver — a small lambda that knows how to extract
 * the value from a libGDX {@link Material}. MaterialsCache iterates these resolvers automatically;
 * no hardcoded attribute names exist anywhere in the framework code.
 *
 * Example — adding a custom "tintColor" uniform and "detailTexture":
 *
 *   super.createMaterialLayout()
 *       .registerUniform("tintColor", TYPE_VEC4,
 *           mat -> {
 *               MyTintAttribute a = mat.get(MyTintAttribute.class, MyTintAttribute.Type);
 *               return a != null ? a.color : Color.WHITE;
 *           })
 *       .registerTexture("detailTexture", "detailSampler",
 *           mat -> (WgTexture) ((TextureAttribute) mat.get(MyDetailAttribute.Type))
 *                      .textureDescription.texture,
 *           myDefaultDetailTexture);
 */
public class MaterialUniformLayout {

    // ------------------------------------------------------------------
    // Type constants
    // ------------------------------------------------------------------
    public static final int TYPE_FLOAT = 1;
    public static final int TYPE_VEC2  = 2;
    public static final int TYPE_VEC3  = 3;
    public static final int TYPE_VEC4  = 4;
    public static final int TYPE_MAT4  = 5;

    // ------------------------------------------------------------------
    // Resolver interfaces
    // ------------------------------------------------------------------

    /**
     * Reads a uniform value from a Material and writes it into the binder.
     * Receives the Binder so it can call the right setUniform() overload.
     */
    public interface UniformResolver {
        /** Write the value for this uniform into the binder at the given uniform name. */
        void resolve(Material material, com.monstrous.gdx.webgpu.graphics.Binder binder, String uniformName);
    }

    /**
     * Reads a WgTexture from a Material for a texture slot.
     * Returns the fallback texture when the attribute is absent.
     */
    public interface TextureResolver {
        WgTexture resolve(Material material);
    }

    // ------------------------------------------------------------------
    // Entry types
    // ------------------------------------------------------------------

    public static class UniformEntry {
        public final String name;
        public final int type;
        public final int byteOffset;
        /** May be null — entry was registered without a resolver (manual override required). */
        public final UniformResolver resolver;

        UniformEntry(String name, int type, int byteOffset, UniformResolver resolver) {
            this.name       = name;
            this.type       = type;
            this.byteOffset = byteOffset;
            this.resolver   = resolver;
        }
    }

    public static class TextureEntry {
        public final String textureName;
        public final String samplerName;
        public final int textureBindingId;
        public final int samplerBindingId;
        /** May be null — entry was registered without a resolver (manual override required). */
        public final TextureResolver resolver;

        TextureEntry(String textureName, String samplerName,
                     int textureBindingId, int samplerBindingId,
                     TextureResolver resolver) {
            this.textureName      = textureName;
            this.samplerName      = samplerName;
            this.textureBindingId = textureBindingId;
            this.samplerBindingId = samplerBindingId;
            this.resolver         = resolver;
        }
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    final Array<UniformEntry> uniforms = new Array<>();
    final Array<TextureEntry> textures = new Array<>();

    private int nextBindingId    = 1;
    private int currentByteOffset = 0;
    private boolean sealed       = false;

    // ------------------------------------------------------------------
    // Registration — with resolver (preferred)
    // ------------------------------------------------------------------

    /**
     * Register a uniform with an automatic resolver that reads its value from a Material.
     *
     * @param name     WGSL struct field name (must match the binder definition)
     * @param type     one of the TYPE_* constants
     * @param resolver lambda that writes the value into the binder
     */
    public MaterialUniformLayout registerUniform(String name, int type, UniformResolver resolver) {
        if (sealed) throw new IllegalStateException("MaterialUniformLayout is already sealed.");
        currentByteOffset = align(currentByteOffset, alignmentOf(type));
        uniforms.add(new UniformEntry(name, type, currentByteOffset, resolver));
        currentByteOffset += byteSize(type);
        return this;
    }

    /**
     * Register a texture slot with an automatic resolver.
     *
     * @param textureName  binder name for the texture view
     * @param samplerName  binder name for the sampler
     * @param resolver     lambda that returns the WgTexture for a given Material
     */
    public MaterialUniformLayout registerTexture(String textureName, String samplerName,
                                                  TextureResolver resolver) {
        if (sealed) throw new IllegalStateException("MaterialUniformLayout is already sealed.");
        int texId     = nextBindingId++;
        int samplerId = nextBindingId++;
        textures.add(new TextureEntry(textureName, samplerName, texId, samplerId, resolver));
        return this;
    }

    // ------------------------------------------------------------------
    // Registration — without resolver (backward compat / manual override)
    // ------------------------------------------------------------------

    /** Register a uniform without a resolver. You must handle it in a MaterialsCache subclass. */
    public MaterialUniformLayout registerUniform(String name, int type) {
        return registerUniform(name, type, null);
    }

    /** Register a texture slot without a resolver. You must handle it in a MaterialsCache subclass. */
    public MaterialUniformLayout registerTexture(String textureName, String samplerName) {
        return registerTexture(textureName, samplerName, null);
    }

    // ------------------------------------------------------------------
    // Seal
    // ------------------------------------------------------------------

    public MaterialUniformLayout seal() {
        currentByteOffset = align(currentByteOffset, 16);
        sealed = true;
        return this;
    }

    public int getTotalUniformBytes() {
        if (!sealed) throw new IllegalStateException("Call seal() first.");
        return currentByteOffset == 0 ? 16 : currentByteOffset;
    }

    public boolean isSealed() { return sealed; }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int byteSize(int type) {
        switch (type) {
            case TYPE_FLOAT: return 4;
            case TYPE_VEC2:  return 8;
            case TYPE_VEC3:  return 12;
            case TYPE_VEC4:  return 16;
            case TYPE_MAT4:  return 64;
            default: throw new IllegalArgumentException("Unknown uniform type: " + type);
        }
    }

    private static int alignmentOf(int type) {
        switch (type) {
            case TYPE_FLOAT: return 4;
            case TYPE_VEC2:  return 8;
            case TYPE_VEC3:  return 16;
            case TYPE_VEC4:  return 16;
            case TYPE_MAT4:  return 16;
            default: throw new IllegalArgumentException("Unknown uniform type: " + type);
        }
    }

    private static int align(int value, int alignment) {
        return (value + alignment - 1) & -alignment;
    }
}


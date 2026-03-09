package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.utils.Array;

/**
 * Describes the structure of the material uniform group (group 1) for a shader.
 *
 * The shader calls registerUniform(), registerTexture(), and registerSampler() once during
 * construction. MaterialsCache reads this descriptor to build the BindGroupLayout, the Binder
 * definitions, and the GPU uniform buffer — all automatically.
 *
 * No manual layout work is needed in MaterialsCache or WgDefaultShader when you add a new
 * attribute; you only edit your shader subclass and the matching WGSL source.
 *
 * Example usage inside a WgDefaultShader subclass constructor:
 *
 *   MaterialUniformLayout layout = new MaterialUniformLayout();
 *   // built-in uniforms
 *   layout.registerUniform("diffuseColor",  MaterialUniformLayout.TYPE_VEC4);
 *   layout.registerUniform("shininess",     MaterialUniformLayout.TYPE_FLOAT);
 *   // built-in textures
 *   layout.registerTexture("diffuseTexture", "diffuseSampler");
 *   // custom extra texture
 *   layout.registerTexture("detailTexture",  "detailSampler");
 *   // custom extra uniform
 *   layout.registerUniform("detailStrength", MaterialUniformLayout.TYPE_FLOAT);
 *
 *   MaterialsCache cache = new MaterialsCache(config.maxMaterials, layout);
 */
public class MaterialUniformLayout {

    // Uniform types — used to compute byte size and WebGPU alignment
    public static final int TYPE_FLOAT  = 1;   //  4 bytes
    public static final int TYPE_VEC2   = 2;   //  8 bytes
    public static final int TYPE_VEC3   = 3;   // 12 bytes  (note: 16-byte aligned in WGSL structs)
    public static final int TYPE_VEC4   = 4;   // 16 bytes
    public static final int TYPE_MAT4   = 5;   // 64 bytes

    /** A declared float uniform inside the MaterialUniforms struct. */
    public static class UniformEntry {
        public final String name;
        public final int type;
        public final int byteOffset; // computed by MaterialUniformLayout

        UniformEntry(String name, int type, int byteOffset) {
            this.name = name;
            this.type = type;
            this.byteOffset = byteOffset;
        }
    }

    /** A declared texture + sampler pair. */
    public static class TextureEntry {
        public final String textureName;
        public final String samplerName;
        public final int textureBindingId;  // computed by MaterialUniformLayout
        public final int samplerBindingId;  // computed by MaterialUniformLayout

        TextureEntry(String textureName, String samplerName, int textureBindingId, int samplerBindingId) {
            this.textureName = textureName;
            this.samplerName = samplerName;
            this.textureBindingId = textureBindingId;
            this.samplerBindingId = samplerBindingId;
        }
    }

    final Array<UniformEntry> uniforms  = new Array<>();
    final Array<TextureEntry> textures  = new Array<>();

    /** Binding id 0 is reserved for the uniform buffer. Textures start at 1. */
    private int nextBindingId = 1;
    private int currentByteOffset = 0;
    private boolean sealed = false;

    /**
     * Register a scalar or vector uniform that will live inside the MaterialUniforms struct.
     * Must be called before {@link #seal()}.
     *
     * @param name  name used in the Binder (must match the WGSL struct field)
     * @param type  one of the TYPE_* constants
     */
    public MaterialUniformLayout registerUniform(String name, int type) {
        if (sealed) throw new IllegalStateException("MaterialUniformLayout is already sealed.");
        int size = byteSize(type);
        // WGSL alignment: vec3 needs 16-byte alignment, vec4/mat4 need 16-byte alignment
        currentByteOffset = align(currentByteOffset, alignmentOf(type));
        uniforms.add(new UniformEntry(name, type, currentByteOffset));
        currentByteOffset += size;
        return this;
    }

    /**
     * Register a texture2d + sampler pair.
     * Binding ids are assigned sequentially starting after the uniform buffer slot (binding 0).
     * Must be called before {@link #seal()}.
     *
     * @param textureName name for the texture view binding
     * @param samplerName name for the sampler binding
     */
    public MaterialUniformLayout registerTexture(String textureName, String samplerName) {
        if (sealed) throw new IllegalStateException("MaterialUniformLayout is already sealed.");
        int texId     = nextBindingId++;
        int samplerId = nextBindingId++;
        textures.add(new TextureEntry(textureName, samplerName, texId, samplerId));
        return this;
    }

    /**
     * Finalise the layout. After sealing, {@link #getTotalUniformBytes()} is stable.
     * Called automatically by MaterialsCache — you don't have to call it yourself.
     */
    public MaterialUniformLayout seal() {
        // Pad the struct to a 16-byte boundary (WGSL requirement for uniform buffers)
        currentByteOffset = align(currentByteOffset, 16);
        sealed = true;
        return this;
    }

    /** Total byte size of the MaterialUniforms struct (valid after seal()). */
    public int getTotalUniformBytes() {
        if (!sealed) throw new IllegalStateException("Call seal() first.");
        return currentByteOffset == 0 ? 16 : currentByteOffset; // minimum 16 bytes
    }

    public boolean isSealed() { return sealed; }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
            case TYPE_VEC3:  return 16; // WGSL aligns vec3 to 16 bytes
            case TYPE_VEC4:  return 16;
            case TYPE_MAT4:  return 16;
            default: throw new IllegalArgumentException("Unknown uniform type: " + type);
        }
    }

    /** Round up `value` to the nearest multiple of `alignment`. */
    private static int align(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }
}


package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Null;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRTextureAttribute;
import com.monstrous.gdx.webgpu.wrappers.WebGPUBindGroupLayout;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;
import com.monstrous.gdx.webgpu.wrappers.WebGPUUniformBuffer;

import static com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888;

/**
 * GPU-side material cache.
 *
 * Instead of hardcoding every uniform and texture slot, this class is now driven by a
 * {@link MaterialUniformLayout} that the shader registers once at construction time.
 * The BindGroupLayout, the Binder definitions and the uniform buffer size are all derived
 * automatically from that descriptor.
 *
 * To customise the material group, you only have to:
 *   1. Build your MaterialUniformLayout in your shader subclass.
 *   2. Pass it to the MaterialsCache constructor.
 *   3. Override {@link #applyMaterialAttributes(Material, MaterialEntry)} to write your custom values.
 *   4. Update your WGSL to match the new bindings.
 *
 * That is it â€” no changes to this class are needed.
 */
public class MaterialsCache implements Disposable {

    /** Per-material GPU entry: dynamic offset + one WgTexture per registered texture slot. */
    public static class MaterialEntry {
        int dynamicOffset;
        WgTexture[] textures; // indexed by registration order in MaterialUniformLayout

        MaterialEntry(int numTextures) {
            textures = new WgTexture[numTextures];
        }
    }

    // -----------------------------------------------------------------------
    // Per-instance fallback textures â€” recreated with each cache instance
    // so they always belong to the current WebGPU device.
    // -----------------------------------------------------------------------

    private final WgTexture defaultTexture;
    private final WgTexture defaultNormalTexture;
    private final WgTexture defaultBlackTexture;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final int maxMaterials;
    private int numMaterials;
    private WebGPUUniformBuffer materialBuffer;
    Binder binder;
    private final int group = 1;
    private int materialSize;
    private WebGPUBindGroupLayout bindGroupLayout;
    private IntMap<MaterialEntry> cache;
    private MaterialEntry boundEntry;
    private int bindCount;

    protected final MaterialUniformLayout uniformLayout;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Create a cache using the default PBR material layout. */
    public MaterialsCache(int maxMaterials) {
        // Create fallback textures first, then build the default layout whose
        // resolvers close over these per-instance references.
        this.maxMaterials = maxMaterials;

        Pixmap pixmap = new Pixmap(1, 1, RGBA8888);
        pixmap.setColor(Color.WHITE);  pixmap.fill();
        defaultTexture = new WgTexture(pixmap, "default (white)");
        pixmap.setColor(Color.GREEN);  pixmap.fill();
        defaultNormalTexture = new WgTexture(pixmap, "default normal texture");
        pixmap.setColor(Color.BLACK);  pixmap.fill();
        defaultBlackTexture = new WgTexture(pixmap, "default (black)");
        pixmap.dispose();

        this.uniformLayout = buildDefaultLayout(defaultTexture, defaultNormalTexture, defaultBlackTexture);
        init();
    }

    /**
     * Create a cache driven by a custom {@link MaterialUniformLayout}.
     * The layout must already contain all resolvers.
     * If not yet sealed it will be sealed inside this constructor.
     *
     * NOTE: if your resolvers reference fallback textures, create them before building
     * the layout and manage their lifecycle yourself.
     */
    public MaterialsCache(int maxMaterials, MaterialUniformLayout layout) {
        this.maxMaterials = maxMaterials;
        this.uniformLayout = layout;
        // No built-in fallback textures for custom layouts â€” the caller owns them.
        defaultTexture = null;
        defaultNormalTexture = null;
        defaultBlackTexture = null;
        init();
    }

    /** Shared initialisation after fields are set. */
    private void init() {
        if (!uniformLayout.isSealed()) uniformLayout.seal();

        numMaterials = 0;
        cache        = new IntMap<>();
        boundEntry   = null;

        materialSize = uniformLayout.getTotalUniformBytes();

        materialBuffer = new WebGPUUniformBuffer(materialSize,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform), maxMaterials);
        materialBuffer.beginSlices();

        binder = new Binder();
        bindGroupLayout = createMaterialBindGroupLayout();
        binder.defineGroup(group, bindGroupLayout);

        binder.defineBinding("materialUniforms", group, 0);
        binder.setBuffer("materialUniforms", materialBuffer, 0, materialSize);

        for (MaterialUniformLayout.UniformEntry u : uniformLayout.uniforms)
            binder.defineUniform(u.name, group, 0, u.byteOffset);

        for (MaterialUniformLayout.TextureEntry t : uniformLayout.textures) {
            binder.defineBinding(t.textureName, group, t.textureBindingId);
            binder.defineBinding(t.samplerName, group, t.samplerBindingId);
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public MaterialEntry addMaterial(Material mat) {
        MaterialEntry entry = findMaterial(mat);
        if (entry != null) return entry;

        if (numMaterials >= maxMaterials)
            throw new GdxRuntimeException(
                    "Too many materials for MaterialsCache (" + numMaterials + "). Increase maxMaterials.");

        entry = buildEntry(mat);
        numMaterials++;
        cache.put(hashCode(mat), entry);
        return entry;
    }

    public int count()            { return numMaterials; }
    public int materialBindings() { return bindCount; }

    public @Null MaterialEntry findMaterial(Material mat) {
        return cache.get(hashCode(mat));
    }

    public void start() {
        boundEntry = null;
        bindCount  = 0;
    }

    public void bindMaterial(WebGPURenderPass renderPass, Material mat) {
        MaterialEntry entry = addMaterial(mat);
        if (entry == boundEntry) return;

        // Bind each texture/sampler pair listed in the layout
        Array<MaterialUniformLayout.TextureEntry> texEntries = uniformLayout.textures;
        for (int i = 0; i < texEntries.size; i++) {
            MaterialUniformLayout.TextureEntry te = texEntries.get(i);
            WgTexture tex = entry.textures[i];
            binder.setTexture(te.textureName, tex.getTextureView());
            binder.setSampler(te.samplerName, tex.getSampler());
        }

        binder.bindGroup(renderPass, group, entry.dynamicOffset);
        boundEntry = entry;
        bindCount++;
    }

    /**
     * Returns the BindGroupLayout that was built during construction.
     * WgDefaultShader registers this with its own Binder for group 1.
     * Always use this getter â€” never call createMaterialBindGroupLayout() again,
     * as that would produce a second, inconsistent layout object.
     */
    public WebGPUBindGroupLayout getBindGroupLayout() {
        return bindGroupLayout;
    }

    /**
     * Builds the BindGroupLayout for group 1 from the registered descriptor.
     * Called once during construction; result is stored and exposed via {@link #getBindGroupLayout()}.
     */
    public WebGPUBindGroupLayout createMaterialBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (material)");
        layout.begin();

        // Binding 0: uniform buffer (dynamic offset)
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment),
                WGPUBufferBindingType.Uniform, materialSize, true);

        // One texture + sampler entry per registered pair
        for (MaterialUniformLayout.TextureEntry te : uniformLayout.textures) {
            layout.addTexture(te.textureBindingId, WGPUShaderStage.Fragment,
                    WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
            layout.addSampler(te.samplerBindingId, WGPUShaderStage.Fragment,
                    WGPUSamplerBindingType.Filtering);
        }

        layout.end();
        return layout;
    }

    // -----------------------------------------------------------------------
    // Override point
    // -----------------------------------------------------------------------

    /**
     * Write uniform values and texture references for a single material into {@code entry}.
     *
     * The default implementation simply iterates every resolver registered in the
     * {@link MaterialUniformLayout} â€” no attribute names are hardcoded here.
     *
     * Override this only if you registered a slot <em>without</em> a resolver (the
     * no-resolver overloads exist for advanced cases). Otherwise you never need to touch
     * this method.
     *
     * @param material the libGDX Material being processed
     * @param entry    the GPU entry to populate (entry.dynamicOffset is already set)
     */
    protected void applyMaterialAttributes(Material material, MaterialEntry entry) {
        // uniforms â€” each resolver writes its value into the binder
        for (MaterialUniformLayout.UniformEntry u : uniformLayout.uniforms) {
            if (u.resolver != null)
                u.resolver.resolve(material, binder, u.name);
        }
        // textures â€” each resolver returns the WgTexture to bind for this slot
        for (int i = 0; i < uniformLayout.textures.size; i++) {
            MaterialUniformLayout.TextureEntry t = uniformLayout.textures.get(i);
            if (t.resolver != null)
                entry.textures[i] = t.resolver.resolve(material);
        }
    }

    // -----------------------------------------------------------------------
    // Default layout builder
    // -----------------------------------------------------------------------

    /**
     * Builds the complete standard PBR material layout.
     * Resolvers close over the provided per-instance fallback textures.
     */
    public static MaterialUniformLayout buildDefaultLayout(WgTexture defaultTex,
                                                            WgTexture defaultNormalTex,
                                                            WgTexture defaultBlackTex) {
        return new MaterialUniformLayout()
            .registerUniform("diffuseColor", MaterialUniformLayout.TYPE_VEC4,
                (mat, binder, name) -> {
                    ColorAttribute a = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                    binder.setUniform(name, a == null ? Color.WHITE : a.color);
                })
            .registerUniform("shininess", MaterialUniformLayout.TYPE_FLOAT,
                (mat, binder, name) -> {
                    FloatAttribute a = mat.get(FloatAttribute.class, FloatAttribute.Shininess);
                    binder.setUniform(name, a == null ? 20f : a.value);
                })
            .registerUniform("roughnessFactor", MaterialUniformLayout.TYPE_FLOAT,
                (mat, binder, name) -> {
                    FloatAttribute a = mat.get(PBRFloatAttribute.class, PBRFloatAttribute.Roughness);
                    binder.setUniform(name, a == null ? 1f : a.value);
                })
            .registerUniform("metallicFactor", MaterialUniformLayout.TYPE_FLOAT,
                (mat, binder, name) -> {
                    FloatAttribute a = mat.get(PBRFloatAttribute.class, PBRFloatAttribute.Metallic);
                    binder.setUniform(name, a == null ? 1f : a.value);
                })
            .registerTexture("diffuseTexture", "diffuseSampler",
                mat -> {
                    if (mat.has(TextureAttribute.Diffuse)) {
                        TextureAttribute ta = (TextureAttribute) mat.get(TextureAttribute.Diffuse);
                        WgTexture tex = (WgTexture) ta.textureDescription.texture;
                        tex.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
                        tex.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);
                        return tex;
                    }
                    return defaultTex;
                })
            .registerTexture("normalTexture", "normalSampler",
                mat -> {
                    if (mat.has(TextureAttribute.Normal)) {
                        TextureAttribute ta = (TextureAttribute) mat.get(TextureAttribute.Normal);
                        return (WgTexture) ta.textureDescription.texture;
                    }
                    return defaultNormalTex;
                })
            .registerTexture("metallicRoughnessTexture", "metallicRoughnessSampler",
                mat -> {
                    if (mat.has(PBRTextureAttribute.MetallicRoughness)) {
                        TextureAttribute ta = (TextureAttribute) mat.get(PBRTextureAttribute.MetallicRoughness);
                        return (WgTexture) ta.textureDescription.texture;
                    }
                    return defaultTex;
                })
            .registerTexture("emissiveTexture", "emissiveSampler",
                mat -> {
                    if (mat.has(TextureAttribute.Emissive)) {
                        TextureAttribute ta = (TextureAttribute) mat.get(TextureAttribute.Emissive);
                        return (WgTexture) ta.textureDescription.texture;
                    }
                    return defaultBlackTex;
                });
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private MaterialEntry buildEntry(Material material) {
        MaterialEntry entry = new MaterialEntry(uniformLayout.textures.size);
        entry.dynamicOffset = materialBuffer.nextSlice();
        applyMaterialAttributes(material, entry);
        materialBuffer.flush();
        return entry;
    }

    private int hashCode(Material material) {
        int code = material.attributesHash();
        if (material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            code += 31 * ta.textureDescription.texture.hashCode();
        }
        return code;
    }

    @Override
    public void dispose() {
        cache.clear();
        binder.dispose();
        materialBuffer.dispose();
        // Dispose per-instance fallback textures (null when using custom layout constructor).
        if (defaultTexture      != null) defaultTexture.dispose();
        if (defaultNormalTexture!= null) defaultNormalTexture.dispose();
        if (defaultBlackTexture != null) defaultBlackTexture.dispose();
    }

}

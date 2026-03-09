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
 * That is it — no changes to this class are needed.
 */
public class MaterialsCache implements Disposable {

    /** Per-material GPU entry: dynamic offset + one WgTexture per registered texture slot. */
    public static class MaterialEntry {
        int dynamicOffset;
        WgTexture[] textures; // indexed by position in MaterialUniformLayout.textures

        MaterialEntry(int numTextures) {
            textures = new WgTexture[numTextures];
        }

        // --- convenience accessors kept for code that still references them by name ---
        public WgTexture getDiffuseTexture()          { return textures.length > 0 ? textures[0] : null; }
        public WgTexture getNormalTexture()           { return textures.length > 1 ? textures[1] : null; }
        public WgTexture getMetallicRoughnessTexture(){ return textures.length > 2 ? textures[2] : null; }
        public WgTexture getEmissiveTexture()         { return textures.length > 3 ? textures[3] : null; }
    }

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------

    private final int maxMaterials;
    private int numMaterials;
    private final WebGPUUniformBuffer materialBuffer;
    final Binder binder;          // package-private so subclasses can call setUniform() on it
    private final int group = 1;
    private final int materialSize;
    private WebGPUBindGroupLayout bindGroupLayout; // built once, shared with WgDefaultShader
    private WgTexture defaultTexture;
    private WgTexture defaultNormalTexture;
    private WgTexture defaultBlackTexture;
    private final IntMap<MaterialEntry> cache;
    private MaterialEntry boundEntry;
    private int bindCount;

    protected final MaterialUniformLayout uniformLayout;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /**
     * Create a cache using the default material layout (diffuseColor, shininess, roughnessFactor,
     * metallicFactor + 4 texture pairs). This matches the original hard-coded behaviour so existing
     * code continues to work without any changes.
     */
    public MaterialsCache(int maxMaterials) {
        this(maxMaterials, buildDefaultLayout());
    }

    /**
     * Create a cache driven by a custom {@link MaterialUniformLayout}.
     * The layout is sealed inside this constructor if it has not been sealed already.
     *
     * @param maxMaterials maximum number of distinct materials
     * @param layout       descriptor built by the shader
     */
    public MaterialsCache(int maxMaterials, MaterialUniformLayout layout) {
        this.maxMaterials = maxMaterials;
        this.uniformLayout = layout;

        if (!layout.isSealed()) layout.seal();

        numMaterials = 0;
        cache        = new IntMap<>();
        boundEntry   = null;

        defineDefaultTextures();

        materialSize = layout.getTotalUniformBytes();

        // GPU uniform buffer with one dynamic-offset slice per material
        materialBuffer = new WebGPUUniformBuffer(materialSize,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform), maxMaterials);
        materialBuffer.beginSlices();

        // Build Binder from the descriptor — this is the only place bindings are declared
        binder = new Binder();
        bindGroupLayout = createMaterialBindGroupLayout(); // built once, reused by the shader
        binder.defineGroup(group, bindGroupLayout);

        // Binding 0 = uniform buffer
        binder.defineBinding("materialUniforms", group, 0);
        binder.setBuffer("materialUniforms", materialBuffer, 0, materialSize);

        // Register each uniform's byte offset inside binding 0
        for (MaterialUniformLayout.UniformEntry u : layout.uniforms) {
            binder.defineUniform(u.name, group, 0, u.byteOffset);
        }

        // Register texture + sampler bindings
        for (MaterialUniformLayout.TextureEntry t : layout.textures) {
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
     * Always use this getter — never call createMaterialBindGroupLayout() again,
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
     * The default implementation handles the standard PBR attributes (diffuseColor, shininess,
     * roughnessFactor, metallicFactor, diffuseTexture, normalTexture, metallicRoughnessTexture,
     * emissiveTexture).
     *
     * Override this in a subclass when you have registered additional uniforms or textures via a
     * custom {@link MaterialUniformLayout}.  Call {@code super.applyMaterialAttributes(material)}
     * first to keep the standard behaviour, then write your own extras via {@code binder}.
     *
     * @param material the libGDX Material being processed
     * @param entry    the GPU entry to populate (entry.dynamicOffset is already set)
     */
    protected void applyMaterialAttributes(Material material, MaterialEntry entry) {

        // --- uniforms (written only if registered) ---

        if (hasUniform("diffuseColor")) {
            ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
            binder.setUniform("diffuseColor", diffuse == null ? Color.WHITE : diffuse.color);
        }
        if (hasUniform("shininess")) {
            FloatAttribute shiny = material.get(FloatAttribute.class, FloatAttribute.Shininess);
            binder.setUniform("shininess", shiny == null ? 20f : shiny.value);
        }
        if (hasUniform("roughnessFactor")) {
            FloatAttribute roughness = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Roughness);
            binder.setUniform("roughnessFactor", roughness == null ? 1f : roughness.value);
        }
        if (hasUniform("metallicFactor")) {
            FloatAttribute metallic = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Metallic);
            binder.setUniform("metallicFactor", metallic == null ? 1f : metallic.value);
        }

        // --- textures (assigned only if the slot name was registered) ---

        setTextureSlot(entry, "diffuseTexture",
                getTextureOrDefault(material, TextureAttribute.Diffuse, defaultTexture, true));

        setTextureSlot(entry, "normalTexture",
                getTextureOrDefault(material, TextureAttribute.Normal, defaultNormalTexture, false));

        setTextureSlot(entry, "metallicRoughnessTexture",
                getTextureOrDefault(material, PBRTextureAttribute.MetallicRoughness, defaultTexture, false));

        setTextureSlot(entry, "emissiveTexture",
                getTextureOrDefault(material, TextureAttribute.Emissive, defaultBlackTexture, false));
    }

    // -----------------------------------------------------------------------
    // Default layout builder (mirrors the old hard-coded behaviour)
    // -----------------------------------------------------------------------

    /**
     * Builds the standard PBR material layout that was previously hardcoded.
     * Used by the no-argument constructor so existing code is unaffected.
     */
    public static MaterialUniformLayout buildDefaultLayout() {
        return new MaterialUniformLayout()
                .registerUniform("diffuseColor",       MaterialUniformLayout.TYPE_VEC4)
                .registerUniform("shininess",          MaterialUniformLayout.TYPE_FLOAT)
                .registerUniform("roughnessFactor",    MaterialUniformLayout.TYPE_FLOAT)
                .registerUniform("metallicFactor",     MaterialUniformLayout.TYPE_FLOAT)
                .registerTexture("diffuseTexture",             "diffuseSampler")
                .registerTexture("normalTexture",              "normalSampler")
                .registerTexture("metallicRoughnessTexture",   "metallicRoughnessSampler")
                .registerTexture("emissiveTexture",            "emissiveSampler");
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

    /** Returns true if a uniform with the given name was registered in the layout. */
    protected boolean hasUniform(String name) {
        for (MaterialUniformLayout.UniformEntry u : uniformLayout.uniforms)
            if (u.name.equals(name)) return true;
        return false;
    }

    /** Returns the index of a texture slot by its textureName, or -1 if not registered. */
    protected int textureSlotIndex(String textureName) {
        Array<MaterialUniformLayout.TextureEntry> entries = uniformLayout.textures;
        for (int i = 0; i < entries.size; i++)
            if (entries.get(i).textureName.equals(textureName)) return i;
        return -1;
    }

    /** Assigns a WgTexture to the named slot if it was registered. */
    protected void setTextureSlot(MaterialEntry entry, String textureName, WgTexture texture) {
        int idx = textureSlotIndex(textureName);
        if (idx >= 0) entry.textures[idx] = texture;
    }

    private WgTexture getTextureOrDefault(Material material, long attributeType,
                                           WgTexture fallback, boolean applyWrapFilter) {
        if (material.has(attributeType)) {
            TextureAttribute ta = (TextureAttribute) material.get(attributeType);
            assert ta != null;
            WgTexture tex = (WgTexture) ta.textureDescription.texture;
            if (applyWrapFilter) {
                tex.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
                tex.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);
            }
            return tex;
        }
        return fallback;
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

    private void defineDefaultTextures() {
        Pixmap pixmap = new Pixmap(1, 1, RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        defaultTexture = new WgTexture(pixmap, "default (white)");
        pixmap.setColor(Color.GREEN);
        pixmap.fill();
        defaultNormalTexture = new WgTexture(pixmap, "default normal texture");
        pixmap.setColor(Color.BLACK);
        pixmap.fill();
        defaultBlackTexture = new WgTexture(pixmap, "default (black)");
    }

    @Override
    public void dispose() {
        defaultTexture.dispose();
        defaultNormalTexture.dispose();
        defaultBlackTexture.dispose();
        cache.clear();
        binder.dispose();
        materialBuffer.dispose();
    }

}

package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
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

public class MaterialsCache implements Disposable {

    public static class MaterialEntry {
        int dynamicOffset; // offset in uniform buffer
        WgTexture diffuseTexture;
        WgTexture normalTexture;
        WgTexture metallicRoughnessTexture;
        WgTexture emissiveTexture;
    }

    private final int maxMaterials;
    private int numMaterials;
    private final WebGPUUniformBuffer materialBuffer;
    private final Binder binder;
    private final int group;
    private final int materialSize;
    private WgTexture defaultTexture;
    private WgTexture defaultNormalTexture;
    private WgTexture defaultBlackTexture;
    private final IntMap<MaterialEntry> cache;
    private MaterialEntry boundEntry;
    private int bindCount;

    public MaterialsCache(int maxMaterials) {
        this.maxMaterials = maxMaterials;
        numMaterials = 0;

        group = 1;

        cache = new IntMap<>();
        boundEntry = null;

        defineDefaultTextures();

        materialSize = 8 * Float.BYTES; // data size per material (should be enough for now)

        // buffer for uniforms per material, e.g. color, shininess, ...
        // this does not include textures
        // todo when a texture is missing we can use a place holder texture, or flag via uniforms that it is not present
        // to save a binding call.

        // allocate a uniform buffer with dynamic offsets
        materialBuffer = new WebGPUUniformBuffer(materialSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform),
                maxMaterials);
        materialBuffer.beginSlices();

        binder = new Binder();
        // define group
        binder.defineGroup(group, createMaterialBindGroupLayout());

        // define bindings in the group
        binder.defineBinding("materialUniforms", group, 0);
        binder.defineBinding("diffuseTexture", group, 1);
        binder.defineBinding("diffuseSampler", group, 2);
        binder.defineBinding("normalTexture", group, 3);
        binder.defineBinding("normalSampler", group, 4);
        binder.defineBinding("metallicRoughnessTexture", group, 5);
        binder.defineBinding("metallicRoughnessSampler", group, 6);
        binder.defineBinding("emissiveTexture", group, 7);
        binder.defineBinding("emissiveSampler", group, 8);

        // define uniforms
        int offset = 0;
        binder.defineUniform("diffuseColor", group, 0, offset);
        offset += 4 * 4;
        binder.defineUniform("shininess", group, 0, offset);
        offset += 4;
        binder.defineUniform("roughnessFactor", group, 0, offset);
        offset += 4;
        binder.defineUniform("metallicFactor", group, 0, offset);
        offset += 4;

        binder.setBuffer("materialUniforms", materialBuffer, 0, materialSize);
    }

    public MaterialEntry addMaterial(Material mat) {
        MaterialEntry entry = findMaterial(mat); // duplicate?
        if (entry != null)
            return entry;

        if (numMaterials >= maxMaterials)
            throw new GdxRuntimeException(
                    "Too many materials for MaterialsCache (" + numMaterials + "). Increase maxMaterials.");
        entry = applyMaterial(mat);
        numMaterials++;
        cache.put(hashCode(mat), entry);
        // System.out.println("Add material: "+numMaterials+" : "+mat.id+ " "+mat.attributesHash());
        return entry;
    }

    /** returns actual number of materials in the cache. */
    public int count() {
        return numMaterials;
    }

    /** returns number of material bindings done since start() was called */
    public int materialBindings() {
        return bindCount;
    }

    public @Null MaterialEntry findMaterial(Material mat) {
        // use attributesHash as key so that we look only at the material contents, not the material's id
        return cache.get(hashCode(mat));
    }

    private int hashCode(Material material) {
        // ignore material.id
        int code = material.attributesHash();
        // just the attributes hash is not sufficient, check if texture is different
        if (material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            code += 31 * tex.hashCode();
        }
        // assume the other textures are not relevant to distinguish materials
        return code;
    }

    public void start() {
        // System.out.println("===== start");
        boundEntry = null;
        bindCount = 0;
    }

    public void bindMaterial(WebGPURenderPass renderPass, Material mat) {

        MaterialEntry entry = addMaterial(mat);
        if (entry == boundEntry)
            return;

        // System.out.println("bind "+bindCount+ ": "+mat.attributesHash());
        binder.setTexture("diffuseTexture", entry.diffuseTexture.getTextureView());
        binder.setSampler("diffuseSampler", entry.diffuseTexture.getSampler());
        binder.setTexture("normalTexture", entry.normalTexture.getTextureView());
        binder.setSampler("normalSampler", entry.normalTexture.getSampler());
        binder.setTexture("metallicRoughnessTexture", entry.metallicRoughnessTexture.getTextureView());
        binder.setSampler("metallicRoughnessSampler", entry.metallicRoughnessTexture.getSampler());
        binder.setTexture("emissiveTexture", entry.emissiveTexture.getTextureView());
        binder.setSampler("emissiveSampler", entry.emissiveTexture.getSampler());
        binder.bindGroup(renderPass, group, entry.dynamicOffset);
        boundEntry = entry;
        bindCount++;
    }

    private MaterialEntry applyMaterial(Material material) {
        MaterialEntry entry = new MaterialEntry();

        // move to new buffer offset for this new material (flushes previous one).
        entry.dynamicOffset = materialBuffer.nextSlice();

        // diffuse color
        ColorAttribute diffuse = (ColorAttribute) material.get(ColorAttribute.Diffuse);
        binder.setUniform("diffuseColor", diffuse == null ? Color.WHITE : diffuse.color);

        final FloatAttribute shiny = material.get(FloatAttribute.class, FloatAttribute.Shininess);
        binder.setUniform("shininess", shiny == null ? 20f : shiny.value);

        // the following are multiplication factors for the MR texture. If not provided, use 1.0.
        final FloatAttribute roughness = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Roughness);
        binder.setUniform("roughnessFactor", roughness == null ? 1f : roughness.value);

        final FloatAttribute metallic = material.get(PBRFloatAttribute.class, PBRFloatAttribute.Metallic);
        binder.setUniform("metallicFactor", metallic == null ? 1f : metallic.value);

        // diffuse texture
        WgTexture diffuseTexture;
        if (material.has(TextureAttribute.Diffuse)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Diffuse);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            diffuseTexture = (WgTexture) tex;
            diffuseTexture.setWrap(ta.textureDescription.uWrap, ta.textureDescription.vWrap);
            diffuseTexture.setFilter(ta.textureDescription.minFilter, ta.textureDescription.magFilter);
        } else {
            diffuseTexture = defaultTexture;
        }
        entry.diffuseTexture = diffuseTexture;

        // System.out.println("binding diffuse texture:
        // "+diffuseTexture.getLabel()+diffuseTexture.getUWrap()+diffuseTexture.getVWrap());

        // normal texture
        WgTexture normalTexture;
        if (material.has(TextureAttribute.Normal)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Normal);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            normalTexture = (WgTexture) tex;
        } else {
            normalTexture = defaultNormalTexture; // green texture, ie. Y = 1
        }
        entry.normalTexture = normalTexture;

        // metallic roughness texture
        WgTexture metallicRoughnessTexture;
        if (material.has(PBRTextureAttribute.MetallicRoughness)) {
            TextureAttribute ta = (TextureAttribute) material.get(PBRTextureAttribute.MetallicRoughness);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            metallicRoughnessTexture = (WgTexture) tex;
        } else {
            metallicRoughnessTexture = defaultTexture; // suitable?
        }
        entry.metallicRoughnessTexture = metallicRoughnessTexture;

        // metallic roughness texture
        WgTexture emissiveTexture;
        if (material.has(TextureAttribute.Emissive)) {
            TextureAttribute ta = (TextureAttribute) material.get(TextureAttribute.Emissive);
            assert ta != null;
            Texture tex = ta.textureDescription.texture;
            emissiveTexture = (WgTexture) tex;
        } else {
            emissiveTexture = defaultBlackTexture;
        }
        entry.emissiveTexture = emissiveTexture;

        // todo not needed except for last one
        materialBuffer.flush(); // write to GPU

        return entry;
    }

    public WebGPUBindGroupLayout createMaterialBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("ModelBatch bind group layout (material)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                materialSize, true);
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.addTexture(3, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(4, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.addTexture(5, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(6, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.addTexture(7, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D,
                false);
        layout.addSampler(8, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.end();
        return layout;
    }

    private void defineDefaultTextures() {
        // fallback texture
        // todo these could be static?
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
        // more??
    }

}

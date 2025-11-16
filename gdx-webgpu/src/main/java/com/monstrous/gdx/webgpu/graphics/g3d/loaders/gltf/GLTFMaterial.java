package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

public class GLTFMaterial {
    public String name;
    public GLTFMaterialPBR pbrMetallicRoughness;
    public int normalTexture;
    public int occlusionTexture;
    public int emissiveTexture;
    public String alphaMode;
    public float alphaCutoff;
    public boolean doubleSide;

    public GLTFMaterial() {
        alphaMode = "OPAQUE";
        alphaCutoff = 0.5f;
        doubleSide = false;
        normalTexture = -1;
        occlusionTexture = -1;
        emissiveTexture = -1;
    }
}

package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;


import com.badlogic.gdx.graphics.Color;

public class GLTFMaterialPBR {
    public Color baseColorFactor;
    public int baseColorTexture;
    public float metallicFactor;
    public float roughnessFactor;
    public int metallicRoughnessTexture;

    public GLTFMaterialPBR() {
        baseColorFactor = new Color(1,1,1,1);
        metallicFactor = -1;
        roughnessFactor = -1;
        baseColorTexture = -1;
        metallicRoughnessTexture = -1;
    }
}

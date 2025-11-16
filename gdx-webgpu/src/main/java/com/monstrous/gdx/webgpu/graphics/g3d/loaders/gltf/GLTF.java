package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import java.util.ArrayList;

// class to store the contents of a gltf file

public class GLTF {

    public static int SBYTE8 = 5120;
    public static int UBYTE8 = 5121;
    public static int USHORT16 = 5123;
    public static int UINT32 = 5125;
    public static int FLOAT32 = 5126;

    public int scene;
    public ArrayList<GLTFTexture> textures;
    public ArrayList<GLTFMaterial> materials;
    public ArrayList<GLTFImage> images;
    public ArrayList<GLTFSampler> samplers;
    public ArrayList<GLTFMesh> meshes;
    public ArrayList<GLTFBuffer> buffers;
    public ArrayList<GLTFBufferView> bufferViews;
    public ArrayList<GLTFAccessor> accessors;
    public ArrayList<GLTFNode> nodes;
    public ArrayList<GLTFAnimation> animations;
    public ArrayList<GLTFSkin> skins;
    public ArrayList<GLTFScene> scenes;
    public ArrayList<GLTFRawBuffer> rawBuffers; // binary data either from a .bin file or from second chunk in .glb file

    public GLTF() {
        textures = new ArrayList<>();
        materials = new ArrayList<>();
        images = new ArrayList<>();
        samplers = new ArrayList<>();
        meshes = new ArrayList<>();
        buffers = new ArrayList<>();
        bufferViews = new ArrayList<>();
        accessors = new ArrayList<>();
        nodes = new ArrayList<>();
        animations = new ArrayList<>();
        skins = new ArrayList<>();
        scenes = new ArrayList<>();
        rawBuffers = new ArrayList<>();
    }
}

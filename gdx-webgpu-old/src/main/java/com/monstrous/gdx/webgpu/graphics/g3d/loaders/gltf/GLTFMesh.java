package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import java.util.ArrayList;

public class GLTFMesh {
    public String name;
    public ArrayList<GLTFPrimitive> primitives;

    public GLTFMesh() {
        primitives = new ArrayList<>();
    }
}

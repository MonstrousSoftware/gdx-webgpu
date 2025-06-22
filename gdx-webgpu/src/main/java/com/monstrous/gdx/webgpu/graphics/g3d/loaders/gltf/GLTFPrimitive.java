package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import java.util.ArrayList;

public class GLTFPrimitive {
    public ArrayList<GLTFAttribute> attributes;
    public int indices;     // index
    public int material;    // index
    public int mode;        // topology

    public GLTFPrimitive() {
        attributes = new ArrayList<>();
        indices = 0;
        material = 0;
        mode = 4;
    }
}

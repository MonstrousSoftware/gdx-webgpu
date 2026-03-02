package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import java.util.ArrayList;

public class GLTFPrimitive {
    public ArrayList<GLTFAttribute> attributes; // vertex attributes
    public int indices; // index
    public int material; // material
    public int mode; // topology
    public ArrayList<ArrayList<GLTFAttribute>> targets; // morph targets

    public GLTFPrimitive() {
        attributes = new ArrayList<>();
        targets = new ArrayList<>();
        indices = -1;
        material = -1;
        mode = 4;
    }
}

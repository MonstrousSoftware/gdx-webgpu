package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import java.util.ArrayList;

public class GLTFAnimation {
    public String name;
    public ArrayList<GLTFAnimationChannel> channels;
    public ArrayList<GLTFAnimationSampler> samplers;

    public GLTFAnimation() {
        channels = new ArrayList<>();
        samplers = new ArrayList<>();
    }
}

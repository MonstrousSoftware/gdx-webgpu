package com.monstrous.gdx.webgpu.graphics.shader.modular.layout;

import com.monstrous.gdx.webgpu.graphics.g3d.shaders.MaterialUniformLayout;

public enum UniformType {
    FLOAT(MaterialUniformLayout.TYPE_FLOAT),
    VEC2(MaterialUniformLayout.TYPE_VEC2),
    VEC3(MaterialUniformLayout.TYPE_VEC3),
    VEC4(MaterialUniformLayout.TYPE_VEC4),
    MAT4(MaterialUniformLayout.TYPE_MAT4);

    public final int materialLayoutType;

    UniformType(int materialLayoutType) {
        this.materialLayoutType = materialLayoutType;
    }
}

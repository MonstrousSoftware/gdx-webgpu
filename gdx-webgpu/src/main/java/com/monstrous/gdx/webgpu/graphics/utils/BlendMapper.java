package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.graphics.GL20;
import com.github.xpenatan.webgpu.WGPUBlendFactor;

import java.util.HashMap;
import java.util.Map;

/** Utility class to map blend constants between OpenGL and WebGPU. */
public class BlendMapper {
    private static Map<Integer, WGPUBlendFactor> blendConstantMap; // mapping GL vs WebGPU constants
    private static Map<WGPUBlendFactor, Integer> blendGLConstantMap; // vice versa

    private static void initBlendMap() { // lazy init
        blendConstantMap = new HashMap<>();
        blendGLConstantMap = new HashMap<>();

        blendConstantMap.put(GL20.GL_ZERO, WGPUBlendFactor.Zero);
        blendConstantMap.put(GL20.GL_ONE, WGPUBlendFactor.One);
        blendConstantMap.put(GL20.GL_SRC_ALPHA, WGPUBlendFactor.SrcAlpha);
        blendConstantMap.put(GL20.GL_ONE_MINUS_SRC_ALPHA, WGPUBlendFactor.OneMinusSrcAlpha);
        blendConstantMap.put(GL20.GL_DST_ALPHA, WGPUBlendFactor.DstAlpha);
        blendConstantMap.put(GL20.GL_ONE_MINUS_DST_ALPHA, WGPUBlendFactor.OneMinusDstAlpha);
        blendConstantMap.put(GL20.GL_SRC_COLOR, WGPUBlendFactor.Src);
        blendConstantMap.put(GL20.GL_ONE_MINUS_SRC_COLOR, WGPUBlendFactor.OneMinusSrc);
        blendConstantMap.put(GL20.GL_DST_COLOR, WGPUBlendFactor.Dst);
        blendConstantMap.put(GL20.GL_ONE_MINUS_DST_COLOR, WGPUBlendFactor.OneMinusDst);
        blendConstantMap.put(GL20.GL_SRC_ALPHA_SATURATE, WGPUBlendFactor.SrcAlphaSaturated);

        // and build the inverse mapping
        for (int key : blendConstantMap.keySet()) {
            WGPUBlendFactor factor = blendConstantMap.get(key);
            blendGLConstantMap.put(factor, key);
        }
    }

    /** maps GL blend functions, e.g. GL_SRC_ALPHA to WebGPU constants, e.g. WGPUBlendFactor.SrcAlpha */
    public static WGPUBlendFactor blendFactor(int GL_blend_function) {
        if (blendConstantMap == null)
            initBlendMap();
        return blendConstantMap.get(GL_blend_function);
    }

    public static int blendFunction(WGPUBlendFactor factor) {
        if (blendGLConstantMap == null)
            initBlendMap();
        return blendGLConstantMap.get(factor);
    }
}

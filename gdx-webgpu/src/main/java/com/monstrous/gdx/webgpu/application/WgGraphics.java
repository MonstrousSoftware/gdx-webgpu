package com.monstrous.gdx.webgpu.application;


import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Graphics;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;

public interface WgGraphics extends Graphics {

    WebGPU_JNI getWebGPU ();        // to be phased out
    WebGPUContext getContext ();

}

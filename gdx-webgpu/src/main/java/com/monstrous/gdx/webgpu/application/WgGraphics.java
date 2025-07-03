package com.monstrous.gdx.webgpu.application;


import com.badlogic.gdx.Graphics;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;

public abstract class WgGraphics implements Graphics {

    public WebGPUContext webgpu;

    public abstract WebGPU_JNI getWebGPU ();        // to be phased out
    public abstract WebGPUApplication getContext ();

}

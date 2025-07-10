package com.monstrous.gdx.webgpu.application;


import com.badlogic.gdx.Graphics;


public abstract class WgGraphics implements Graphics {

    public WebGPUContext webgpu;
    public abstract WebGPUContext getContext ();

}

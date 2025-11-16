package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.Graphics;

public interface WgGraphics extends Graphics {

    WebGPUContext getContext();

}

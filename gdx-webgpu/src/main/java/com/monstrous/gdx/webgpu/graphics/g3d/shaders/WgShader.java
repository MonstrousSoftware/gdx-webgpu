package com.monstrous.gdx.webgpu.graphics.g3d.shaders;


import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Attributes;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;

public abstract class WgShader implements Shader {
    public int numRenderables;
    public int drawCalls;

    public abstract void init();
    public abstract void begin(Camera camera, RenderContext context);
    public abstract void begin(Camera camera, Renderable renderable, WebGPURenderPass renderPass);
    public abstract int compareTo(Shader other);
    public abstract boolean canRender(Renderable instance);
    public abstract void render (Renderable renderable);
    public abstract void render (Renderable renderable, Attributes attributes);
    public abstract void end();
}

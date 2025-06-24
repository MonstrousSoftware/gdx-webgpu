package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.webgpu.WGPUTextureUsage;
import jnr.ffi.Pointer;

/** This frame buffer is nestable */
public class WgFrameBuffer implements Disposable {

    private final WebGPUGraphicsBase gfx;
    private boolean hasDepth;
    private final WgTexture colorTexture;
    private final WgTexture depthTexture;
    private Pointer originalView;
    private WgTexture originalDepthTexture;

    /** note: requires WGPUTextureFormat instead of Pixmap.Format */
    public WgFrameBuffer(WGPUTextureFormat format, int width, int height, boolean hasDepth) {
        this.hasDepth = hasDepth;
        gfx = (WebGPUGraphicsBase) Gdx.graphics;

        final int textureUsage = WGPUTextureUsage.TextureBinding | WGPUTextureUsage.CopyDst|WGPUTextureUsage.RenderAttachment | WGPUTextureUsage.CopySrc;
        colorTexture = new WgTexture("fbo color", width, height, 1, textureUsage, format, 1, format);
        if(hasDepth)
            depthTexture = new WgTexture("fbo depth", width, height, 1, textureUsage, WGPUTextureFormat.Depth24Plus, 1, WGPUTextureFormat.Depth24Plus);
        else
            depthTexture = null;
    }

    public void begin(){
        originalView = gfx.pushTargetView(colorTexture.getTextureView());
        if(hasDepth)
            originalDepthTexture = gfx.pushDepthTexture(depthTexture);
    }

    public void end() {
        gfx.popTargetView(originalView);
        if(hasDepth)
            gfx.popDepthTexture(originalDepthTexture);
    }

    public Texture getColorBufferTexture(){
        return colorTexture;
    }

    public Texture getDepthTexture(){
        return depthTexture;
    }

    @Override
    public void dispose() {
        colorTexture.dispose();
    }
}

package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
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
    private Rectangle originalViewportRectangle;
    private WgTexture originalDepthTexture;
    private ScreenViewport viewport;

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
        originalViewportRectangle = new Rectangle();
        viewport = new ScreenViewport();
        //viewport.update(width, height, true);
    }

    public void begin(){
        originalView = gfx.pushTargetView(colorTexture, originalViewportRectangle);
        if(hasDepth)
            originalDepthTexture = gfx.pushDepthTexture(depthTexture);
        //viewport.apply();
    }

    public void end() {
        gfx.popTargetView(originalView, originalViewportRectangle);
        if(hasDepth)
            gfx.popDepthTexture(originalDepthTexture);
    }

    public Texture getColorBufferTexture(){
        return colorTexture;
    }

    public Texture getDepthTexture(){
        return depthTexture;
    }

    public int getWidth(){
        return colorTexture.getWidth();
    }

    public int getHeight(){
        return colorTexture.getHeight();
    }

    @Override
    public void dispose() {
        colorTexture.dispose();
    }
}

/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** This frame buffer is nestable */
public class WgFrameBuffer implements Disposable {

    private final WebGPUContext webgpu;
    private final WgTexture colorTexture;
    private final WgTexture[] colorTextures;
    private final WgTexture depthTexture;
    // Per-FBO pre-allocated view/format arrays used in begin().
    private final WGPUTextureView[] ownedTargetViews;
    private final WGPUTextureFormat[] ownedTargetFormats;
    private final WebGPUContext.RenderOutputState prevState = new WebGPUContext.RenderOutputState();

    public WgFrameBuffer(int width, int height, boolean hasDepth) {
        this(WGPUTextureFormat.RGBA8UnormSrgb, width, height, hasDepth);
    }

    /** note: requires WGPUTextureFormat instead of Pixmap.Format. */
    public WgFrameBuffer(WGPUTextureFormat format, int width, int height, boolean hasDepth) {
        this(new WGPUTextureFormat[] {format}, width, height, hasDepth);
    }

    public WgFrameBuffer(WGPUTextureFormat[] formats, int width, int height, boolean hasDepth) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        final WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst)
                .or(WGPUTextureUsage.RenderAttachment).or(WGPUTextureUsage.CopySrc);

        colorTextures = new WgTexture[formats.length];
        for (int i = 0; i < formats.length; i++) {
            colorTextures[i] = new WgTexture("fbo color " + i, width, height, false, textureUsage, formats[i], 1,
                    formats[i]);
        }
        colorTexture = colorTextures[0];

        // Allocate per-FBO arrays now so begin() never touches the shared context scratch.
        ownedTargetViews = new WGPUTextureView[formats.length];
        ownedTargetFormats = new WGPUTextureFormat[formats.length];

        if (hasDepth)
            depthTexture = new WgTexture("fbo depth", width, height, false, textureUsage, WGPUTextureFormat.Depth24Plus,
                    1, WGPUTextureFormat.Depth24Plus);
        else
            depthTexture = null;
    }

    public void begin() {
        // Fill this FBO's own view/format arrays from the current texture views.
        // Using per-FBO arrays (not the shared WebGPUContext scratch) guarantees that when
        // prevState saves targetViews it saves a reference to THIS FBO's array.  A subsequent
        // nested FBO push therefore cannot overwrite the scratch and corrupt the saved state.
        for (int i = 0; i < colorTextures.length; i++) {
            ownedTargetViews[i] = colorTextures[i].getTextureView();
            ownedTargetFormats[i] = colorTextures[i].getFormat();
        }
        webgpu.pushTargetView(prevState, ownedTargetViews, ownedTargetFormats,
                colorTexture.getWidth(), colorTexture.getHeight(), depthTexture);
    }

    public void end() {
        webgpu.popTargetView(prevState);
    }

    public Texture getColorBufferTexture() {
        return colorTexture;
    }

    public Texture getColorBufferTexture(int index) {
        return colorTextures[index];
    }

    public Texture getDepthTexture() {
        return depthTexture;
    }

    public int getWidth() {
        return colorTexture.getWidth();
    }

    public int getHeight() {
        return colorTexture.getHeight();
    }

    @Override
    public void dispose() {
        for (WgTexture tex : colorTextures) {
            tex.dispose();
        }
        if (depthTexture != null)
            depthTexture.dispose();
    }
}

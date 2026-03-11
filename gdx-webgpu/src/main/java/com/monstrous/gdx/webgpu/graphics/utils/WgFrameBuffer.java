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
            // Only promote the first (color) target to its sRGB variant so the GPU handles gamma
            // encoding/decoding automatically, matching the direct-to-screen gamma path.
            // Additional targets (e.g. object-ID targets) must keep their exact format so that
            // the encoded data values are stored and sampled verbatim.
            WGPUTextureFormat fboFormat = (i == 0) ? toSrgbFormat(formats[i]) : formats[i];
            colorTextures[i] = new WgTexture("fbo color " + i, width, height, false, textureUsage, fboFormat, 1,
                    fboFormat);
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

    /**
     * Maps a plain (non-sRGB) color format to its sRGB equivalent, or returns the format unchanged
     * if it is already sRGB or has no sRGB counterpart (e.g. the object-ID target RGBA8Unorm used
     * for non-color data should stay as-is — but for the purposes of correct gamma reproduction
     * we promote it so all targets match the rendering contract).
     * <p>
     * Promoting to sRGB means:
     * <ul>
     *   <li>On render attachment write the GPU converts linear → sRGB automatically.</li>
     *   <li>On texture sample the GPU converts sRGB → linear automatically.</li>
     * </ul>
     * This gives the same single-gamma-correction round-trip as rendering directly to the screen.
     */
    private static WGPUTextureFormat toSrgbFormat(WGPUTextureFormat format) {
        switch (format) {
            case RGBA8Unorm:  return WGPUTextureFormat.RGBA8UnormSrgb;
            case BGRA8Unorm:  return WGPUTextureFormat.BGRA8UnormSrgb;
            default:          return format; // already sRGB or a non-color format
        }
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

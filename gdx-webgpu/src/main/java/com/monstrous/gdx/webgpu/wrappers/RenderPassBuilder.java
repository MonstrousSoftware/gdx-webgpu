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

package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/**
 * Factory class to create WebGPURenderPass objects. use setCommandEncoder() before creating passes. use create() to
 * create a pass (at least once per frame)
 */
public class RenderPassBuilder {

    private static final WGPUTextureView[] scratchViews = new WGPUTextureView[16];
    private static final WGPUTextureFormat[] scratchFormats = new WGPUTextureFormat[16];
    // Pre-allocated single-element array to avoid per-call allocation in the single-texture overload
    private static final WgTexture[] singleTextureArray = new WgTexture[1];

    public static WebGPURenderPass create(String name) {
        return create(name, null);
    }

    public static WebGPURenderPass create(String name, Color clearColor) {
        return create(name, clearColor, ((WgGraphics) Gdx.graphics).getContext().getSamples());
    }

    public static WebGPURenderPass create(String name, Color clearColor, int sampleCount) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        return create(name, clearColor, false, null, gfx.getContext().getDepthTexture(), sampleCount);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        return create(name, clearColor, clearDepth, null, gfx.getContext().getDepthTexture(), 1);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, int sampleCount) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        return create(name, clearColor, clearDepth, null, gfx.getContext().getDepthTexture(), sampleCount);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, int sampleCount,
            RenderPassType passType) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        return create(name, clearColor, clearDepth, (WgTexture[]) null, gfx.getContext().getDepthTexture(), sampleCount,
                passType);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, WgTexture colorTexture,
            WgTexture depthTexture, int sampleCount) {
        return create(name, clearColor, clearDepth, colorTexture, depthTexture, sampleCount,
                RenderPassType.COLOR_AND_DEPTH);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, WgTexture outTexture,
            WgTexture depthTexture, int sampleCount, RenderPassType passType) {
        if (outTexture == null) {
            return create(name, clearColor, clearDepth, (WgTexture[]) null, depthTexture, sampleCount, passType);
        }
        singleTextureArray[0] = outTexture;
        return create(name, clearColor, clearDepth, singleTextureArray, depthTexture, sampleCount, passType);
    }

    /**
     * Create a render pass targeting only the first color attachment of the current context (ignoring additional MRT
     * targets). This is used e.g. by the skybox which only writes to one color target even when inside an MRT FBO.
     * The pipeline used with this pass must declare exactly one color format (the first context surface format).
     *
     * @param name pass name
     * @param clearDepth whether to clear the depth buffer
     * @param sampleCount samples per pixel
     * @return the created WebGPURenderPass
     */
    public static WebGPURenderPass createFirstTargetOnly(String name, boolean clearDepth, int sampleCount) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        if (webgpu.encoder == null)
            throw new RuntimeException("Encoder must be set before calling RenderPassBuilder.createFirstTargetOnly()");
        if (!webgpu.encoder.isValid())
            throw new RuntimeException("Encoder not valid for call of RenderPassBuilder.createFirstTargetOnly()");

        WGPURenderPassDescriptor renderPassDescriptor = WGPURenderPassDescriptor.obtain();
        renderPassDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        renderPassDescriptor.setOcclusionQuerySet(WGPUQuerySet.NULL);
        renderPassDescriptor.setLabel(name);

        WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();

        // Only use the first color target
        WGPURenderPassColorAttachment renderPassColorAttachment = WGPURenderPassColorAttachment.obtain();
        renderPassColorAttachment.setNextInChain(WGPUChainedStruct.NULL);
        renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
        renderPassColorAttachment.setDepthSlice(-1);
        renderPassColorAttachment.setLoadOp(WGPULoadOp.Load); // preserve existing scene color

        if (sampleCount > 1) {
            renderPassColorAttachment.setView(webgpu.getMultiSamplingTexture().getTextureView());
            renderPassColorAttachment.setResolveTarget(webgpu.getTargetViews()[0]);
        } else {
            renderPassColorAttachment.setView(webgpu.targetViews[0]);
            renderPassColorAttachment.setResolveTarget(WGPUTextureView.NULL);
        }
        colorAttachments.push_back(renderPassColorAttachment);

        renderPassDescriptor.setColorAttachments(colorAttachments);

        WgTexture depthTexture = webgpu.getDepthTexture();
        WGPURenderPassDepthStencilAttachment depthStencilAttachment = WGPURenderPassDepthStencilAttachment.obtain();
        depthStencilAttachment.setDepthClearValue(1.0f);
        depthStencilAttachment.setDepthLoadOp(clearDepth ? WGPULoadOp.Clear : WGPULoadOp.Load);
        depthStencilAttachment.setDepthStoreOp(WGPUStoreOp.Store);
        depthStencilAttachment.setDepthReadOnly(false);
        depthStencilAttachment.setStencilClearValue(0);
        depthStencilAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
        depthStencilAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
        depthStencilAttachment.setStencilReadOnly(true);
        depthStencilAttachment.setView(depthTexture.getTextureView());
        renderPassDescriptor.setDepthStencilAttachment(depthStencilAttachment);

        GPUTimer timer = webgpu.getGPUTimer();
        if (timer.isEnabled()) {
            timer.addPass(name);
            WGPURenderPassTimestampWrites query = WGPURenderPassTimestampWrites.obtain();
            query.setBeginningOfPassWriteIndex(timer.getStartIndex());
            query.setEndOfPassWriteIndex(timer.getStopIndex());
            query.setQuerySet(timer.getQuerySet());
            renderPassDescriptor.setTimestampWrites(query);
        }

        WebGPURenderPass pass = WebGPURenderPass.obtain();

        Rectangle view = webgpu.getViewportRectangle();
        int width = (int) view.width;
        int height = (int) view.height;

        WGPUTextureFormat[] singleFormat = new WGPUTextureFormat[] { webgpu.surfaceFormats[0] };
        pass.begin(webgpu.encoder, renderPassDescriptor, RenderPassType.COLOR_AND_DEPTH, singleFormat, 1,
                depthTexture.getFormat(), sampleCount, width, height);

        pass.setViewport(view.x, view.y, view.width, view.height, 0, 1);

        if (webgpu.isScissorEnabled()) {
            Rectangle scissor = webgpu.getScissor();
            pass.setScissorRect((int) scissor.x, (int) scissor.y, (int) scissor.width, (int) scissor.height);
        }

        return pass;
    }

    /**
     * Create a render pass
     *
     * @param clearColor background color, null to not clear the screen, e.g. for a UI
     * @param clearDepth clear depth buffer?
     * @param outTextures output textures, null to render to the screen
     * @param depthTexture output depth texture, can be null
     * @param sampleCount samples per pixel: 1 or 4
     * @param passType render pass type
     * @return the created WebGPURenderPass
     */
    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, WgTexture[] outTextures,
            WgTexture depthTexture, int sampleCount, RenderPassType passType) {
        // System.out.println("RenderPassBuilder: create");
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        if (webgpu.encoder == null)
            throw new RuntimeException("Encoder must be set before calling WebGPURenderPass.create()");
        if (!webgpu.encoder.isValid())
            throw new RuntimeException("Encoder not valid for call of WebGPURenderPass.create()");

        WGPURenderPassDescriptor renderPassDescriptor = WGPURenderPassDescriptor.obtain();
        renderPassDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        renderPassDescriptor.setOcclusionQuerySet(WGPUQuerySet.NULL);
        renderPassDescriptor.setLabel(name);

        WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();

        // determine the targets
        WGPUTextureView[] targetViews = null;
        WGPUTextureFormat[] targetFormats = null;
        int targetCount = 0;

        if (outTextures == null) {
            targetViews = webgpu.targetViews;
            targetFormats = webgpu.surfaceFormats;
            targetCount = targetViews.length;
        } else {
            targetCount = outTextures.length;
            if (targetCount > scratchViews.length) {
                throw new RuntimeException("Too many render targets: " + targetCount);
            }
            for (int i = 0; i < targetCount; i++) {
                scratchViews[i] = outTextures[i].getTextureView();
                scratchFormats[i] = outTextures[i].getFormat();
            }
            targetViews = scratchViews;
            targetFormats = scratchFormats;
        }

        if (passType == RenderPassType.COLOR_AND_DEPTH || passType == RenderPassType.COLOR_PASS
                || passType == RenderPassType.COLOR_PASS_AFTER_DEPTH_PREPASS || passType == RenderPassType.SHADOW_PASS
                || passType == RenderPassType.NO_DEPTH) {

            for (int i = 0; i < targetCount; i++) {
                WGPURenderPassColorAttachment renderPassColorAttachment = WGPURenderPassColorAttachment.obtain();
                renderPassColorAttachment.setNextInChain(WGPUChainedStruct.NULL);
                renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
                renderPassColorAttachment.setDepthSlice(-1);

                if (clearColor != null) {
                    if (!webgpu.hasLinearOutput())
                        GammaCorrection.fromLinear(clearColor); // inverse gamma correction
                    renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);

                    renderPassColorAttachment.getClearValue().setR(clearColor.r);
                    renderPassColorAttachment.getClearValue().setG(clearColor.g);
                    renderPassColorAttachment.getClearValue().setB(clearColor.b);
                    renderPassColorAttachment.getClearValue().setA(clearColor.a);
                } else {
                    renderPassColorAttachment.setLoadOp(WGPULoadOp.Load);
                }

                if (sampleCount > 1 && i == 0) { // Keep MSAA logic restricted to first attachment/screen if needed?
                    // Assuming MSAA only for main screen for now or if supported by logic
                    // existing logic:
                    if (outTextures == null) {
                        renderPassColorAttachment.setView(webgpu.getMultiSamplingTexture().getTextureView());
                        renderPassColorAttachment.setResolveTarget(webgpu.getTargetViews()[0]);
                    } else {
                        // MSAA to texture not fully implemented in this logic block for MRT?
                        // defaulting to simple logic for custom texture
                        renderPassColorAttachment.setView(targetViews[i]);
                        renderPassColorAttachment.setResolveTarget(WGPUTextureView.NULL);
                    }
                } else {
                    renderPassColorAttachment.setView(targetViews[i]);
                    renderPassColorAttachment.setResolveTarget(WGPUTextureView.NULL);
                }

                colorAttachments.push_back(renderPassColorAttachment);
            }

        } else {
            sampleCount = 1;
        }
        renderPassDescriptor.setColorAttachments(colorAttachments);

        if (passType != RenderPassType.NO_DEPTH) {
            WGPURenderPassDepthStencilAttachment depthStencilAttachment = WGPURenderPassDepthStencilAttachment.obtain();

            depthStencilAttachment.setDepthClearValue(1.0f);
            depthStencilAttachment.setDepthLoadOp(clearDepth ? WGPULoadOp.Clear : WGPULoadOp.Load);
            depthStencilAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthStencilAttachment.setDepthReadOnly(false);
            depthStencilAttachment.setStencilClearValue(0);
            depthStencilAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
            depthStencilAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
            depthStencilAttachment.setStencilReadOnly(true);

            depthStencilAttachment.setView(depthTexture.getTextureView());

            renderPassDescriptor.setDepthStencilAttachment(depthStencilAttachment);
        }

        GPUTimer timer = gfx.getContext().getGPUTimer();
        if (timer.isEnabled()) {
            timer.addPass(name); // announce a new render pass for this frame (this determines the index values)
            // create a query
            // System.out.println("Timer for "+name+ " indices: "+timer.getStartIndex()+" "+timer.getStopIndex());
            WGPURenderPassTimestampWrites query = WGPURenderPassTimestampWrites.obtain();
            query.setBeginningOfPassWriteIndex(timer.getStartIndex()); // get offset for this render pass's start time
            query.setEndOfPassWriteIndex(timer.getStopIndex());
            query.setQuerySet(timer.getQuerySet());
            renderPassDescriptor.setTimestampWrites(query);
        }

        WebGPURenderPass pass = WebGPURenderPass.obtain(); // Reuse render pass

        Rectangle view = webgpu.getViewportRectangle(); // todo may change over time

        int width, height;
        if (outTextures != null && outTextures.length > 0) {
            width = outTextures[0].getWidth();
            height = outTextures[0].getHeight();
        } else {
            width = (int) view.width;
            height = (int) view.height;
        }

        pass.begin(webgpu.encoder, renderPassDescriptor, passType, targetFormats, targetCount, depthTexture.getFormat(),
                sampleCount, width, height);

        pass.setViewport(view.x, view.y, view.width, view.height, 0, 1);

        if (webgpu.isScissorEnabled()) {
            Rectangle scissor = webgpu.getScissor();
            pass.setScissorRect((int) scissor.x, (int) scissor.y, (int) scissor.width, (int) scissor.height);
        }

        return pass;
    }

}

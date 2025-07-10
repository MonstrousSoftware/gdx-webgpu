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
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** Factory class to create WebGPURenderPass objects.
 *  use setCommandEncoder() before creating passes.
 *  use create() to create a pass (at least once per frame)
 */
public class RenderPassBuilder {

    // storage for descriptor elements to quickly rebuild it per frame
    public static class DescriptorComponents {
        WGPURenderPassEncoder renderPass;
        WGPURenderPassDescriptor renderPassDescriptor;
        WGPURenderPassColorAttachment renderPassColorAttachment;
        WGPUVectorRenderPassColorAttachment colorAttachments;
        WGPURenderPassDepthStencilAttachment depthStencilAttachment;
    }
//    private static WGPURenderPassDescriptor renderPassDescriptor;
//    private static WGPURenderPassColorAttachment renderPassColorAttachment;
//    private static WGPURenderPassDepthStencilAttachment depthStencilAttachment;

    public static WebGPURenderPass create(String name) {
        return create( name, null);
    }

    public static WebGPURenderPass create(String name, Color clearColor) {
        return create(name,clearColor,  1);
    }

    public static WebGPURenderPass create(String name, Color clearColor, int sampleCount) {
        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        return create(name,clearColor, false, null, gfx.getContext().getDepthTexture(), sampleCount);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, int sampleCount) {
        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        return create(name,clearColor, clearDepth,null,  gfx.getContext().getDepthTexture(), sampleCount);
    }

    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, int sampleCount, RenderPassType passType) {
        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        return create(name,clearColor, clearDepth,null,  gfx.getContext().getDepthTexture(), sampleCount, passType);
    }


    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, WgTexture colorTexture, WgTexture depthTexture, int sampleCount){
        return create(name, clearColor, clearDepth, colorTexture, depthTexture, sampleCount, RenderPassType.COLOR_AND_DEPTH);
    }


    /**
     * Create a render pass
     *
     * @param clearColor    background color, null to not clear the screen, e.g. for a UI
     * @param clearDepth    clear depth buffer?
     * @param outTexture    output texture, null to render to the screen
     * @param depthTexture   output depth texture, can be null
     * @param sampleCount       samples per pixel: 1 or 4
     * @param passType
     * @return
     */
    public static WebGPURenderPass create(String name, Color clearColor, boolean clearDepth, WgTexture outTexture,
                                          WgTexture depthTexture, int sampleCount, RenderPassType passType) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        DescriptorComponents components = new DescriptorComponents();

        if (webgpu.encoder == null)
            throw new RuntimeException("Encoder must be set before calling WebGPURenderPass.create()");

        WGPUTextureFormat colorFormat = WGPUTextureFormat.Undefined;


        components.renderPassDescriptor = new WGPURenderPassDescriptor();
        Gdx.app.log("RenderPassBuilder", "creating descriptors");

        components.renderPassDescriptor.setNextInChain(null);
        components.renderPassDescriptor.setOcclusionQuerySet(null);
        components.renderPassDescriptor.setLabel(name);

        // note: assuming there is only one color attachment
        components.renderPassColorAttachment = new WGPURenderPassColorAttachment();
        components.renderPassColorAttachment.setNextInChain(null);

        components.depthStencilAttachment = new WGPURenderPassDepthStencilAttachment();

        components.colorAttachments = new WGPUVectorRenderPassColorAttachment();

        if (passType == RenderPassType.COLOR_AND_DEPTH ||
            passType == RenderPassType.COLOR_PASS ||
            passType == RenderPassType.COLOR_PASS_AFTER_DEPTH_PREPASS ||
            passType == RenderPassType.SHADOW_PASS ||
            passType == RenderPassType.NO_DEPTH) {

            components.renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);

            components.renderPassColorAttachment.setDepthSlice(-1);


            if (clearColor != null) {
                components.renderPassColorAttachment.setLoadOp( WGPULoadOp.Clear);

                components.renderPassColorAttachment.getClearValue().setR(clearColor.r);
                components.renderPassColorAttachment.getClearValue().setG(clearColor.g);
                components.renderPassColorAttachment.getClearValue().setB(clearColor.b);
                components.renderPassColorAttachment.getClearValue().setA(clearColor.a);
            } else {
                components.renderPassColorAttachment.setLoadOp(WGPULoadOp.Load);
            }

            if (outTexture == null) {
                if (sampleCount > 1) {
                    components.renderPassColorAttachment.setView(webgpu.getMultiSamplingTexture().getTextureView());
                    components.renderPassColorAttachment.setResolveTarget(webgpu.getTargetView());
                } else {
                    components.renderPassColorAttachment.setView(webgpu.getTargetView());
                    components.renderPassColorAttachment.setResolveTarget(null);
                }
                colorFormat = webgpu.getSurfaceFormat();

            } else {
                components.renderPassColorAttachment.setView(outTexture.getTextureView());
                components.renderPassColorAttachment.setResolveTarget(null);
                colorFormat = outTexture.getFormat();
                sampleCount = 1;
            }

            components.colorAttachments.push_back(components.renderPassColorAttachment);
        } else {
            sampleCount = 1;
        }
        components.renderPassDescriptor.setColorAttachments(components.colorAttachments);

        if (passType != RenderPassType.NO_DEPTH) {
            components.depthStencilAttachment.setDepthClearValue(1.0f);
            components.depthStencilAttachment.setDepthLoadOp( clearDepth ? WGPULoadOp.Clear : WGPULoadOp.Load);
            //depthStencilAttachment.setDepthLoadOp(passType == RenderPassType.COLOR_PASS_AFTER_DEPTH_PREPASS ? WGPULoadOp.Load : WGPULoadOp.Clear);
            components.depthStencilAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            components.depthStencilAttachment.setDepthReadOnly(false);
            components.depthStencilAttachment.setStencilClearValue(0);
            components.depthStencilAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
            components.depthStencilAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
            components.depthStencilAttachment.setStencilReadOnly(true);

            components.depthStencilAttachment.setView(depthTexture.getTextureView());

            components.renderPassDescriptor.setDepthStencilAttachment(components.depthStencilAttachment);
        }

        GPUTimer timer = gfx.getContext().getGPUTimer();
        if (timer.isEnabled()) {
            timer.addPass(name);  // announce a new render pass for this frame (this determines the index values)
            // create a query
            //System.out.println("Timer for "+name+ " indices: "+timer.getStartIndex()+" "+timer.getStopIndex());
            WGPURenderPassTimestampWrites query = WGPURenderPassTimestampWrites.obtain();
            query.setBeginningOfPassWriteIndex(timer.getStartIndex());  // get offset for this render pass's start time
            query.setEndOfPassWriteIndex(timer.getStopIndex());
            query.setQuerySet(timer.getQuerySet());
            components.renderPassDescriptor.setTimestampWrites(query);
        }

//      //  WGPURenderPassEncoder renderPass = new WGPURenderPassEncoder();
//

        components.renderPass = new WGPURenderPassEncoder();
      //  webgpu.encoder.beginRenderPass(components.renderPassDescriptor, components.renderPass);

        WebGPURenderPass pass = new WebGPURenderPass(components, passType, colorFormat, depthTexture.getFormat(), sampleCount,
                outTexture == null ? Gdx.graphics.getWidth() : outTexture.getWidth(),
                outTexture == null ? Gdx.graphics.getHeight() : outTexture.getHeight());

        // todo may change over time
//        Rectangle view = webgpu.getViewportRectangle();
//        pass.setViewport(view.x, view.y, view.width, view.height, 0, 1);
//
//        if(webgpu.isScissorEnabled()) {
//            Rectangle scissor = webgpu.getScissor();
//            pass.setScissorRect((int) scissor.x, (int) scissor.y, (int) scissor.width, (int) scissor.height);
//        }

        return pass;
    }
}

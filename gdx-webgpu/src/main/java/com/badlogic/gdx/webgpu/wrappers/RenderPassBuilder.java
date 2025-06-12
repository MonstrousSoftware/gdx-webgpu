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

package com.badlogic.gdx.webgpu.wrappers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

/** Factory class to create WebGPURenderPass objects.
 *  use setCommandEncoder() before creating passes.
 *  use create() to create a pass (at least once per frame)
 *  //use setViewport() to apply a viewport on the next render pass.
 */
public class RenderPassBuilder {

//    private static Viewport viewport = null;
    private static WGPURenderPassDescriptor renderPassDescriptor;
    private static WGPURenderPassColorAttachment renderPassColorAttachment;
    private static WGPURenderPassDepthStencilAttachment depthStencilAttachment;

    public static WebGPURenderPass create() {
        return create( null);
    }

    public static WebGPURenderPass create(Color clearColor) {
        return create(clearColor,  1);
    }

    public static WebGPURenderPass create(Color clearColor, int sampleCount) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase)Gdx.graphics;
        return create(clearColor, null, gfx.getDepthTexture(), sampleCount);
    }

    public static WebGPURenderPass create( Color clearColor, WebGPUTexture colorTexture, WebGPUTexture depthTexture, int sampleCount){
        return create("color pass", clearColor, colorTexture, depthTexture, sampleCount, RenderPassType.COLOR_PASS);
    }


    /**
     * Create a render pass
     *
     * @param clearColor    background color, null to not clear the screen, e.g. for a UI
     * @param outTexture    output texture, null to render to the screen
     * @param depthTexture   output depth texture, can be null
     * @param sampleCount       samples per pixel: 1 or 4
     * @param passType
     * @return
     */
    public static WebGPURenderPass create(String name, Color clearColor, WebGPUTexture outTexture,
                                          WebGPUTexture depthTexture, int sampleCount, RenderPassType passType) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase)Gdx.graphics;
        if(gfx.getCommandEncoder() == null)
            throw new RuntimeException("Encoder must be set before calling WebGPURenderPass.create()");

        WGPUTextureFormat colorFormat = WGPUTextureFormat.Undefined;

        // create reusable helper objects on first call
        if(renderPassDescriptor == null){
            Gdx.app.log("RenderPassBuilder", "creating descriptors");
            renderPassDescriptor = WGPURenderPassDescriptor.createDirect();
            renderPassDescriptor.setNextInChain().setOcclusionQuerySet(JavaWebGPU.createNullPointer());

            renderPassColorAttachment = WGPURenderPassColorAttachment.createDirect();
            renderPassColorAttachment.setNextInChain();

            depthStencilAttachment = WGPURenderPassDepthStencilAttachment.createDirect();
        }
        renderPassDescriptor.setLabel(name);


        if(  passType == RenderPassType.COLOR_PASS ||
                passType == RenderPassType.COLOR_PASS_AFTER_DEPTH_PREPASS ||
                passType == RenderPassType.SHADOW_PASS ||
                passType == RenderPassType.NO_DEPTH){

            renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);

            renderPassColorAttachment.setDepthSlice(-1L);

            renderPassColorAttachment.setLoadOp((clearColor != null) ? WGPULoadOp.Clear : WGPULoadOp.Load);

            if (clearColor != null) {
                renderPassColorAttachment.getClearValue().setR(clearColor.r);
                renderPassColorAttachment.getClearValue().setG(clearColor.g);
                renderPassColorAttachment.getClearValue().setB(clearColor.b);
                renderPassColorAttachment.getClearValue().setA(clearColor.a);
            }

            if (outTexture == null) {
                if ( sampleCount > 1) {
                    renderPassColorAttachment.setView(gfx.getMultiSamplingTexture().getTextureView().getHandle());
                    renderPassColorAttachment.setResolveTarget(gfx.getTargetView());
                } else {
                    renderPassColorAttachment.setView(gfx.getTargetView());
                    renderPassColorAttachment.setResolveTarget(JavaWebGPU.createNullPointer());
                }
                colorFormat = gfx.getSurfaceFormat();

            } else {
                renderPassColorAttachment.setView(outTexture.getTextureView().getHandle());
                renderPassColorAttachment.setResolveTarget(JavaWebGPU.createNullPointer());
                colorFormat = outTexture.getFormat();
                sampleCount = 1;
            }

            renderPassDescriptor.setColorAttachmentCount(1);
            renderPassDescriptor.setColorAttachments(renderPassColorAttachment);
        } else {
            sampleCount = 1;
            renderPassDescriptor.setColorAttachmentCount(0);
        }

        if(passType != RenderPassType.NO_DEPTH) {
            depthStencilAttachment.setDepthClearValue(1.0f);
            // if we just did a depth prepass, don't clear the depth buffer
            depthStencilAttachment.setDepthLoadOp(passType == RenderPassType.COLOR_PASS_AFTER_DEPTH_PREPASS ? WGPULoadOp.Load : WGPULoadOp.Clear);
            depthStencilAttachment.setDepthStoreOp(WGPUStoreOp.Store);
            depthStencilAttachment.setDepthReadOnly(0L);
            depthStencilAttachment.setStencilClearValue(0);
            depthStencilAttachment.setStencilLoadOp(WGPULoadOp.Undefined);
            depthStencilAttachment.setStencilStoreOp(WGPUStoreOp.Undefined);
            depthStencilAttachment.setStencilReadOnly(1L);

            depthStencilAttachment.setView(depthTexture.getTextureView().getHandle());

            renderPassDescriptor.setDepthStencilAttachment(depthStencilAttachment);
        }

        // todo
        //app.gpuTiming.configureWebGPURenderPassDescriptor(renderPassDescriptor);



        Pointer renderPassPtr = gfx.getWebGPU().wgpuCommandEncoderBeginRenderPass(gfx.getCommandEncoder().getHandle(), renderPassDescriptor);
        WebGPURenderPass pass = new WebGPURenderPass(renderPassPtr, passType, colorFormat, depthTexture.getFormat(), sampleCount,
                outTexture == null ? Gdx.graphics.getWidth() : outTexture.getWidth(),
                outTexture == null ? Gdx.graphics.getHeight() : outTexture.getHeight());


//        if(viewport != null) {
//            viewport.apply(pass);
//            viewport = null;        // apply only once after setViewport() is called
//        }


        return pass;
    }


    // set viewport on future render passes created, set to null to not apply a viewport.
//    public static void setViewport(Viewport vp){
//        viewport = vp;
//    }

}

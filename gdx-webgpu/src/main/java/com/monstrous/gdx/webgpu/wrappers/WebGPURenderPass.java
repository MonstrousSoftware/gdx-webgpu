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
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;


public class WebGPURenderPass  {
    private final WGPURenderPassEncoder renderPass;                   // handle used by WebGPU
    public final RenderPassType type;
    private final WGPUTextureFormat textureFormat;
    private final WGPUTextureFormat depthFormat;
    public int targetWidth, targetHeight;
    private int sampleCount;
    WebGPUContext webgpu;

    // don't call this directly, use RenderPassBuilder.create()
    WebGPURenderPass(WGPURenderPassEncoder renderPass, RenderPassType type, WGPUTextureFormat textureFormat, WGPUTextureFormat depthFormat, int sampleCount, int targetWidth, int targetHeight) {
        super();

        this.renderPass = renderPass;
        this.type = type;
        this.textureFormat = textureFormat;
        this.depthFormat = depthFormat;
        this.sampleCount = sampleCount;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
    }


    public void end() {
        renderPass.end();
        renderPass.release();
        renderPass.dispose();
    }

    public WGPURenderPassEncoder getRenderPassEncoder() {
        return renderPass;
    }

    public WGPUTextureFormat getColorFormat(){
        return textureFormat;
    }

    public WGPUTextureFormat getDepthFormat(){
        return depthFormat;
    }

    public void setSampleCount(int n){
        sampleCount = n;
    }

    public int getSampleCount(){
        return sampleCount;
    }

    public void setPipeline(WGPURenderPipeline pipeline) {
        renderPass.setPipeline(pipeline);
    }

    public void setPipeline(WebGPUPipeline pipeline) {
        renderPass.setPipeline(pipeline.getPipeline());
    }

    public void setBindGroup(int groupIndex, WGPUBindGroup bindGroup) {
        WGPUVectorInt dynamicOffsets = WGPUVectorInt.obtain();
        renderPass.setBindGroup(groupIndex, bindGroup, dynamicOffsets); // can we pass null instead?
    }

    /** set bind group with one dynamic offset */
    public void setBindGroup(int groupIndex, WGPUBindGroup bindGroup, int dynamicOffset) {
        WGPUVectorInt dynamicOffsets = WGPUVectorInt.obtain();
        dynamicOffsets.push_back(dynamicOffset);
        renderPass.setBindGroup(groupIndex, bindGroup, dynamicOffsets);
    }


    /** set bind group with dynamic offsets */
    public void setBindGroup(int groupIndex, WGPUBindGroup bindGroup, WGPUVectorInt dynamicOffsets) {
        renderPass.setBindGroup(groupIndex, bindGroup, dynamicOffsets);
    }


    public void setVertexBuffer(int slot, WGPUBuffer vertexBuffer, int offset, int size) {
        renderPass.setVertexBuffer(slot, vertexBuffer, offset, size);
    }

    public void setIndexBuffer(WGPUBuffer indexBuffer, WGPUIndexFormat wgpuIndexFormat, int offset, int size) {
        renderPass.setIndexBuffer(indexBuffer, wgpuIndexFormat, offset, size);
    }

    public void setViewport(float x, float y, float width, float height, float minDepth, float maxDepth){
        renderPass.setViewport(x, y, width, height, minDepth, maxDepth);
    }

    public void setScissorRect(int x, int y, int width, int height){
        renderPass.setScissorRect( x,  y,  width,  height);
    }

    public void drawIndexed(int indexCount, int numInstances, int firstIndex, int baseVertex, int firstInstance) {
        renderPass.drawIndexed( indexCount,  numInstances,  firstIndex,  baseVertex,  firstInstance);
    }

    public void draw(int numVertices, int numInstances, int firstVertex, int firstInstance){
        renderPass.draw(numVertices, numInstances, firstVertex, firstInstance);
    }

    public void draw(int numVertices){
        draw(numVertices, 1, 0, 0);
    }


}

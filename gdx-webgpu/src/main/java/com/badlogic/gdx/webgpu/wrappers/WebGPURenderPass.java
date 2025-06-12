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
import com.badlogic.gdx.webgpu.webgpu.WGPUIndexFormat;
import com.badlogic.gdx.webgpu.webgpu.WGPUTextureFormat;
import com.badlogic.gdx.webgpu.webgpu.WebGPU_JNI;
import jnr.ffi.Pointer;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.system.MemoryStack.stackPush;

public class WebGPURenderPass  {
    private final WebGPU_JNI webGPU;
    private final Pointer renderPass;                   // handle used by WebGPU
    public final RenderPassType type;
    private final WGPUTextureFormat textureFormat;
    private final WGPUTextureFormat depthFormat;
    public int targetWidth, targetHeight;
    private int sampleCount;
    //private int[] dynamicOffsetBuffer;

    // don't call this directly, use RenderPassBuilder.create()
    WebGPURenderPass(Pointer renderPass, RenderPassType type, WGPUTextureFormat textureFormat, WGPUTextureFormat depthFormat, int sampleCount, int targetWidth, int targetHeight) {
        super();
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        this.renderPass = renderPass;
        this.type = type;
        this.textureFormat = textureFormat;
        this.depthFormat = depthFormat;
        this.sampleCount = sampleCount;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;

        //dynamicOffsetBuffer = new int[2];
    }

    public void end() {
        webGPU.wgpuRenderPassEncoderEnd(renderPass);
        webGPU.wgpuRenderPassEncoderRelease(renderPass);
    }

    public Pointer getHandle() {
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

    public void setPipeline(Pointer pipeline) {
        webGPU.wgpuRenderPassEncoderSetPipeline(renderPass, pipeline);
    }

    public void setPipeline(WebGPUPipeline pipeline) {
        webGPU.wgpuRenderPassEncoderSetPipeline(renderPass, pipeline.getHandle());
    }

    public void setBindGroup(int groupIndex, Pointer bindGroup) {
        setBindGroup(groupIndex, bindGroup, 0, null);
    }

    /** set bind group with one dynamic offset */
    public void setBindGroup(int groupIndex, Pointer bindGroup, int dynamicOffset) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer pDynamicOffsets = stack.malloc(Integer.BYTES);
            pDynamicOffsets.putInt(0, dynamicOffset);
            Pointer dynamicOffsets = JavaWebGPU.createByteBufferPointer(pDynamicOffsets);
            webGPU.wgpuRenderPassEncoderSetBindGroup(renderPass, groupIndex, bindGroup, 1, dynamicOffsets);
        }
    }

    /** set bind group with two dynamic offsets */
    public void setBindGroup(int groupIndex, Pointer bindGroup, int dynamicOffset1, int dynamicOffset2) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer pDynamicOffsets = stack.malloc(Integer.BYTES);
            pDynamicOffsets.putInt(0, dynamicOffset1);
            pDynamicOffsets.putInt(Integer.BYTES, dynamicOffset2);
            Pointer dynamicOffsets = JavaWebGPU.createByteBufferPointer(pDynamicOffsets);
            webGPU.wgpuRenderPassEncoderSetBindGroup(renderPass, groupIndex, bindGroup, 2, dynamicOffsets);
        }
    }

    /** set bind group with one dynamic offset */
    public void setBindGroup(int groupIndex, Pointer bindGroup, int dynamicOffsetCount, Pointer dynamicOffsets) {
        webGPU.wgpuRenderPassEncoderSetBindGroup(renderPass, groupIndex, bindGroup, dynamicOffsetCount, dynamicOffsets);
    }


    public void setVertexBuffer(int slot, Pointer vertexBuffer, long offset, long size) {
        webGPU.wgpuRenderPassEncoderSetVertexBuffer(renderPass,slot ,vertexBuffer, offset, size);
    }

    public void setIndexBuffer(Pointer indexBuffer, WGPUIndexFormat wgpuIndexFormat, int offset, long size) {
        webGPU.wgpuRenderPassEncoderSetIndexBuffer(renderPass, indexBuffer, wgpuIndexFormat, offset, size);
    }

    public void setViewport(float x, float y, float width, float height, float minDepth, float maxDepth){
        webGPU.wgpuRenderPassEncoderSetViewport(renderPass, x, y, width, height, minDepth, maxDepth);
    }

    public void setScissorRect(int x, int y, int width, int height){
        webGPU.wgpuRenderPassEncoderSetScissorRect(renderPass,  x,  y,  width,  height);
    }

    public void drawIndexed(int indexCount, int numInstances, int firstIndex, int baseVertex, int firstInstance) {
        webGPU.wgpuRenderPassEncoderDrawIndexed (renderPass, indexCount,  numInstances,  firstIndex,  baseVertex,  firstInstance);
    }

    public void draw(int numVertices, int numInstances, int firstVertex, int firstInstance){
        webGPU.wgpuRenderPassEncoderDraw(renderPass, numVertices, numInstances, firstVertex, firstInstance);
    }

    public void draw(int numVertices){
        draw(numVertices, 1, 0, 0);
    }
}

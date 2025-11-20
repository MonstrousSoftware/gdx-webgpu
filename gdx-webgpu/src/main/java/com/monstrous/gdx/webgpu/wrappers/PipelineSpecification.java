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
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;

import java.util.Objects;

// todo Environment

public class PipelineSpecification {
    public String name;
    public VertexAttributes vertexAttributes;
    public WGPUIndexFormat indexFormat;
    public WGPUPrimitiveTopology topology;
    public Environment environment;
    public String shaderSource; // shader source code
    public String vertexShaderEntryPoint;
    public String fragmentShaderEntryPoint;
    public WgShaderProgram shader;
    public boolean useDepthTest;
    public boolean noDepthAttachment; // use only when not rendering to the screen, removes the depth attachment
    public boolean isSkyBox;
    public boolean isDepthPass;
    public boolean afterDepthPrepass;
    public int numSamples;

    public boolean blendingEnabled;
    public WGPUBlendFactor blendSrcColor;
    public WGPUBlendFactor blendDstColor;
    public WGPUBlendOperation blendOpColor;
    public WGPUBlendFactor blendSrcAlpha;
    public WGPUBlendFactor blendDstAlpha;
    public WGPUBlendOperation blendOpAlpha;
    public WGPUCullMode cullMode;

    public WGPUTextureFormat colorFormat;
    public WGPUTextureFormat depthFormat;
    public int maxDirLights;
    public int maxPointLights;
    public boolean usePBR;
    private int hash;
    private boolean dirty; // does hash need to be recalculated?

    public PipelineSpecification() {
        this.name = "pipeline";
        dirty = true;
        enableDepthTest();
        noDepthAttachment = false;
        vertexShaderEntryPoint = "vs_main";
        fragmentShaderEntryPoint = "fs_main";
        blendOpColor = WGPUBlendOperation.Add;
        blendOpAlpha = WGPUBlendOperation.Add;
        setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        disableBlending();
        setCullMode(WGPUCullMode.None);
        indexFormat = WGPUIndexFormat.Uint16;
        topology = WGPUPrimitiveTopology.TriangleList;
        isDepthPass = false;
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        colorFormat = gfx.getContext().getSurfaceFormat();
        depthFormat = WGPUTextureFormat.Depth24Plus; // todo get from adapter?
        numSamples = gfx.getContext().getSamples(); // take from window
        isSkyBox = false;
        afterDepthPrepass = false;
        maxDirLights = 0;
        maxPointLights = 0;
        usePBR = true;
    }

    public PipelineSpecification(VertexAttributes vertexAttributes, String shaderSource) {
        this();
        this.vertexAttributes = vertexAttributes;
        this.shaderSource = shaderSource;
    }

    public PipelineSpecification(VertexAttributes vertexAttributes, WgShaderProgram shader) {
        this();
        this.vertexAttributes = vertexAttributes;
        this.shader = shader;
    }

    public PipelineSpecification(PipelineSpecification spec) {
        this.name = spec.name;
        this.vertexAttributes = spec.vertexAttributes; // should be deep copy
        this.environment = spec.environment;
        this.shaderSource = spec.shaderSource;
        this.shader = spec.shader;
        this.useDepthTest = spec.useDepthTest;
        this.noDepthAttachment = spec.noDepthAttachment;
        this.isDepthPass = spec.isDepthPass;
        this.blendingEnabled = spec.blendingEnabled;
        this.blendSrcColor = spec.blendSrcColor;
        this.blendDstColor = spec.blendDstColor;
        this.blendOpColor = spec.blendOpColor;
        this.blendSrcAlpha = spec.blendSrcAlpha;
        this.blendDstAlpha = spec.blendDstAlpha;
        this.blendOpAlpha = spec.blendOpAlpha;
        this.cullMode = spec.cullMode;
        this.topology = spec.topology;
        this.indexFormat = spec.indexFormat;
        this.isSkyBox = spec.isSkyBox;
        this.afterDepthPrepass = spec.afterDepthPrepass;

        this.colorFormat = spec.colorFormat;
        this.depthFormat = spec.depthFormat;
        this.numSamples = spec.numSamples;
        this.maxDirLights = spec.maxDirLights;
        this.maxPointLights = spec.maxPointLights;
        this.usePBR = spec.usePBR;
        this.fragmentShaderEntryPoint = spec.fragmentShaderEntryPoint;
        this.vertexShaderEntryPoint = spec.vertexShaderEntryPoint;
        this.dirty = true;
    }

    /** call this whenever changing a field directly to force a recalculation of the hash code. */
    public void invalidateHashCode() {
        dirty = true;
    }

    public void enableDepthTest() {
        useDepthTest = true;
        dirty = true;
    }

    public void disableDepthTest() {
        useDepthTest = false;
        dirty = true;
    }

    public void setCullMode(WGPUCullMode cullMode) {
        this.cullMode = cullMode;
        dirty = true;
    }

    public void enableBlending() {
        blendingEnabled = true;
        dirty = true;
    }

    public void disableBlending() {
        blendingEnabled = false;
        dirty = true;
    }

    public boolean isBlendingEnabled() {
        return blendingEnabled;
    }

    public void setBlendFactor(WGPUBlendFactor srcFunc, WGPUBlendFactor dstFunc) {
        setBlendFactorSeparate(srcFunc, dstFunc, srcFunc, dstFunc);
    }

    /** note:is only effective if blendingEnabled */
    public void setBlendFactorSeparate(WGPUBlendFactor srcFuncColor, WGPUBlendFactor dstFuncColor,
            WGPUBlendFactor srcFuncAlpha, WGPUBlendFactor dstFuncAlpha) {
        blendSrcColor = srcFuncColor;
        blendDstColor = dstFuncColor;
        blendSrcAlpha = srcFuncAlpha;
        blendDstAlpha = dstFuncAlpha;
        dirty = true;
    }

    public WGPUBlendFactor getBlendSrcFactor() {
        return blendSrcColor;
    }

    public WGPUBlendFactor getBlendDstFactor() {
        return blendDstColor;
    }

    public WGPUBlendFactor getBlendSrcFactorAlpha() {
        return blendSrcAlpha;
    }

    public WGPUBlendFactor getBlendDstFactorAlpha() {
        return blendDstAlpha;
    }

    // used?
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        return hash == ((PipelineSpecification) o).hash;
    }

    @Override
    public int hashCode() {
        if (dirty)
            recalcHash();
        return hash;
    }

    /**
     * to be called whenever relevant content changes (to avoid doing this in hashCode which is called a lot). Also:
     * avoid Objects.hash() to avoid lots of little allocations.
     */
    private void recalcHash() {
        // System.out.println("Recalc pipe spec hash "+((WgGraphics)Gdx.graphics).getFrameId());
        hash = 1;
        hash = 31 * hash + (vertexAttributes == null ? 0 : vertexAttributes.hashCode());
        hash = 31 * hash + (shaderSource == null ? 0 : shaderSource.hashCode());
        hash = 31 * hash + (isDepthPass ? 1231 : 1237);
        hash = 31 * hash + (afterDepthPrepass ? 1231 : 1237);
        hash = 31 * hash + (useDepthTest ? 1231 : 1237);
        hash = 31 * hash + (noDepthAttachment ? 1231 : 1237);
        hash = 31 * hash + (topology == null ? 0 : topology.hashCode());
        hash = 31 * hash + (indexFormat == null ? 0 : indexFormat.hashCode());
        hash = 31 * hash + (blendingEnabled ? 1231 : 1237);
        if (blendingEnabled) {
            hash = 31 * hash + (blendSrcColor == null ? 0 : blendSrcColor.hashCode());
            hash = 31 * hash + (blendDstColor == null ? 0 : blendDstColor.hashCode());
            hash = 31 * hash + (blendOpColor == null ? 0 : blendOpColor.hashCode());
            hash = 31 * hash + (blendSrcAlpha == null ? 0 : blendSrcAlpha.hashCode());
            hash = 31 * hash + (blendDstAlpha == null ? 0 : blendDstAlpha.hashCode());
            hash = 31 * hash + (blendOpAlpha == null ? 0 : blendOpAlpha.hashCode());
        }
        hash = 31 * hash + numSamples;
        hash = 31 * hash + (cullMode == null ? 0 : cullMode.hashCode());
        hash = 31 * hash + (isSkyBox ? 1231 : 1237);
        hash = 31 * hash + (depthFormat == null ? 0 : depthFormat.hashCode());
        hash = 31 * hash + numSamples;
        hash = 31 * hash + maxDirLights;
        hash = 31 * hash + maxPointLights;
        hash = 31 * hash + (usePBR ? 1231 : 1237);

        //
        // hash = Objects.hash(
        // vertexAttributes == null ? 0 : vertexAttributes.hashCode(),
        // shaderSource,
        // isDepthPass, afterDepthPrepass,
        // useDepthTest, noDepthAttachment,
        // topology, indexFormat,
        // blendingEnabled,
        // // blend factors should be ignored when !blendingEnabled
        // blendingEnabled ? blendSrcColor : 0,
        // blendingEnabled ? blendDstColor : 0,
        // blendingEnabled ? blendOpColor : 0,
        // blendingEnabled ? blendSrcAlpha: 0,
        // blendingEnabled ? blendDstAlpha: 0,
        // blendingEnabled ? blendOpAlpha : 0,
        // numSamples, cullMode, isSkyBox, depthFormat, numSamples
        // );
    }

    // note: don't include compiled shader in the hash because this would force new compiles every frame since a spec of
    // an uncompiled shader <> compiled shader

}

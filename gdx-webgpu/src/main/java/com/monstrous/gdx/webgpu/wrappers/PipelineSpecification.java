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

import java.util.Arrays;
import java.util.Objects;

// todo Environment

public class PipelineSpecification {
    public String name;
    public VertexAttributes vertexAttributes;
    public WebGPUVertexLayout vertexLayout;
    public WGPUVertexStepMode vertexStepMode;
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

    public WGPUTextureFormat[] colorFormats; // MRT support

    public boolean blendingEnabled;
    public WGPUBlendFactor blendSrcColor;
    public WGPUBlendFactor blendDstColor;
    public WGPUBlendOperation blendOpColor;
    public WGPUBlendFactor blendSrcAlpha;
    public WGPUBlendFactor blendDstAlpha;
    public WGPUBlendOperation blendOpAlpha;
    public WGPUCullMode cullMode;

    public WGPUTextureFormat depthFormat;
    public int maxDirLights;
    public int maxPointLights;
    public boolean usePBR;
    public String customPrefix; // To add extra defines e.g. for MRT
    private int hash;
    private boolean dirty; // does hash need to be recalculated?

    public PipelineSpecification() {
        this.name = "pipeline";
        dirty = true;
        enableDepthTest();
        noDepthAttachment = false;
        vertexShaderEntryPoint = "vs_main";
        fragmentShaderEntryPoint = "fs_main";
        vertexStepMode = WGPUVertexStepMode.Vertex;
        blendOpColor = WGPUBlendOperation.Add;
        blendOpAlpha = WGPUBlendOperation.Add;
        setBlendFactor(WGPUBlendFactor.SrcAlpha, WGPUBlendFactor.OneMinusSrcAlpha);
        disableBlending();
        setCullMode(WGPUCullMode.None);
        indexFormat = WGPUIndexFormat.Uint16;
        topology = WGPUPrimitiveTopology.TriangleList;
        isDepthPass = false;
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        colorFormats = new WGPUTextureFormat[] {gfx.getContext().getSurfaceFormat()};
        depthFormat = WGPUTextureFormat.Depth24Plus; // todo get from adapter?
        numSamples = gfx.getContext().getSamples(); // take from window
        isSkyBox = false;
        afterDepthPrepass = false;
        maxDirLights = 0;
        maxPointLights = 0;
        usePBR = true;
    }

    public PipelineSpecification(String name, VertexAttributes vertexAttributes, String shaderSource) {
        this();
        this.name = name;
        setVertexAttributes(vertexAttributes);
        this.shaderSource = shaderSource;
    }

    public PipelineSpecification(String name, VertexAttributes vertexAttributes, WgShaderProgram shader) {
        this();
        this.name = name;
        setVertexAttributes(vertexAttributes);
        this.shader = shader;
    }

    public PipelineSpecification(PipelineSpecification spec) {
        this.name = spec.name;
        this.vertexAttributes = spec.vertexAttributes;
        this.vertexLayout = spec.vertexLayout; // share the same layout instance (value-equal by equals/hashCode)
        this.vertexStepMode = spec.vertexStepMode;
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

        if (spec.colorFormats != null)
            this.colorFormats = spec.colorFormats.clone();
        else
            this.colorFormats = null;

        this.depthFormat = spec.depthFormat;
        this.numSamples = spec.numSamples;
        this.maxDirLights = spec.maxDirLights;
        this.maxPointLights = spec.maxPointLights;
        this.usePBR = spec.usePBR;
        this.fragmentShaderEntryPoint = spec.fragmentShaderEntryPoint;
        this.vertexShaderEntryPoint = spec.vertexShaderEntryPoint;
        this.customPrefix = spec.customPrefix;
        this.dirty = true;
    }

    public void setVertexAttributes(VertexAttributes vertexAttributes) {
        this.vertexAttributes = vertexAttributes;
        this.vertexLayout = new WebGPUVertexLayout(vertexAttributes);
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
        PipelineSpecification that = (PipelineSpecification) o;
        return useDepthTest == that.useDepthTest && noDepthAttachment == that.noDepthAttachment
                && isSkyBox == that.isSkyBox && isDepthPass == that.isDepthPass
                && afterDepthPrepass == that.afterDepthPrepass && numSamples == that.numSamples
                && blendingEnabled == that.blendingEnabled && Objects.equals(name, that.name)
                && Objects.equals(vertexAttributes, that.vertexAttributes)
                && Objects.equals(vertexLayout, that.vertexLayout) && vertexStepMode == that.vertexStepMode
                && indexFormat == that.indexFormat && topology == that.topology
                && Objects.equals(environment, that.environment) && Objects.equals(shaderSource, that.shaderSource)
                && Objects.equals(vertexShaderEntryPoint, that.vertexShaderEntryPoint)
                && Objects.equals(fragmentShaderEntryPoint, that.fragmentShaderEntryPoint)
                && Arrays.equals(colorFormats, that.colorFormats)
                && blendSrcColor == that.blendSrcColor && blendDstColor == that.blendDstColor
                && blendOpColor == that.blendOpColor && blendSrcAlpha == that.blendSrcAlpha
                && blendDstAlpha == that.blendDstAlpha && blendOpAlpha == that.blendOpAlpha && cullMode == that.cullMode
                && depthFormat == that.depthFormat && maxDirLights == that.maxDirLights
                && maxPointLights == that.maxPointLights && usePBR == that.usePBR
                && Objects.equals(customPrefix, that.customPrefix);
    }

    @Override
    public int hashCode() {
        if (!dirty)
            return hash;
        int result = Objects.hash(name, vertexAttributes, vertexLayout, vertexStepMode, indexFormat, topology,
                environment, shaderSource, vertexShaderEntryPoint, fragmentShaderEntryPoint, useDepthTest,
                noDepthAttachment, isSkyBox, isDepthPass, afterDepthPrepass, numSamples, blendingEnabled, blendSrcColor,
                blendDstColor, blendOpColor, blendSrcAlpha, blendDstAlpha, blendOpAlpha, cullMode, depthFormat,
                maxDirLights, maxPointLights, usePBR, customPrefix);
        result = 31 * result + Arrays.hashCode(colorFormats);
        hash = result;
        dirty = false;
        return hash;
    }
}

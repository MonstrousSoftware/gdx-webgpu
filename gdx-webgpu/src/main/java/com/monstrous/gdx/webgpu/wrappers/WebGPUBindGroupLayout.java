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

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulated bind group layout.
 * Example:
 *      WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("my layout");
 *      layout.begin();
 *      layout.addBuffer(0, ...);
 *      layout.addTexture(1, .... );
 *      layout.addSampler(2, ...);
 *      layout.end();
 */
public class WebGPUBindGroupLayout implements Disposable {
    private final WgGraphics gfx = (WgGraphics) Gdx.graphics;
    private final WebGPUContext webgpu = gfx.getContext();
    private WGPUBindGroupLayout layout = null;
    private final String label;
    private final Map<Integer, WGPUBindGroupLayoutEntry> entries;   // map from bindingId


    public WebGPUBindGroupLayout() {
        this("bind group layout");
    }

    public WebGPUBindGroupLayout(String label ) {
        this.label = label;
        entries = new HashMap<>();
    }

    public void begin(){
        entries.clear();
        layout = null;
    }

    /**
     * Add binding layout for a buffer.
     *
     * @param bindingId integer as in the shader, 0, 1, 2, ...  they don't have to be sequential or in order!
     * @param visibility e.g. WGPUShaderStage.Fragment (or combination using OR operator)
     * @param bufferBindingType e.g. WGPUBufferBindingType.ReadOnlyStorage
     */
    public void addBuffer(int bindingId, WGPUShaderStage visibility, WGPUBufferBindingType bufferBindingType, int minBindingSize, boolean hasDynamicOffset ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getBuffer().setType(bufferBindingType);
        bindingLayout.getBuffer().setMinBindingSize(minBindingSize);
        bindingLayout.getBuffer().setHasDynamicOffset(hasDynamicOffset? 1 : 0);

        entries.put(bindingId, bindingLayout);
    }

    public void addTexture(int bindingId, WGPUShaderStage visibility, WGPUTextureSampleType sampleType, WGPUTextureViewDimension viewDimension, boolean multiSampled ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getTexture().setMultisampled(multiSampled? 1 : 0);
        bindingLayout.getTexture().setSampleType(sampleType);
        bindingLayout.getTexture().setViewDimension(viewDimension);
        //bindingLayout.getTexture().setViewDimension(WGPUTextureViewDimension._2D);
        entries.put(bindingId, bindingLayout);
    }
    public void addStorageTexture(int bindingId, WGPUShaderStage visibility, WGPUStorageTextureAccess access, WGPUTextureFormat format, WGPUTextureViewDimension viewDimension ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getStorageTexture().setAccess(access);
        bindingLayout.getStorageTexture().setFormat(format);
        bindingLayout.getStorageTexture().setViewDimension(viewDimension);
        entries.put(bindingId, bindingLayout);
    }

    public void addSampler(int bindingId, WGPUShaderStage visibility, WGPUSamplerBindingType samplerType ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getSampler().setType(samplerType);
        entries.put(bindingId, bindingLayout);
    }


    /** addBinding
     *  common part of binding layouts
     */
    private WGPUBindGroupLayoutEntry addBinding(int bindingId, WGPUShaderStage visibility ){
        WGPUBindGroupLayoutEntry bindingLayout =  new WGPUBindGroupLayoutEntry();
        bindingLayout.setNextInChain(null);
        setDefaultLayout(bindingLayout);
        bindingLayout.setBinding(bindingId);
        bindingLayout.setVisibility(visibility);
        return bindingLayout;
    }

    public void end(){
        // Create a bind group layout
        WGPUBindGroupLayoutDescriptor bindGroupLayoutDesc = WGPUBindGroupLayoutDescriptor.obtain();
        bindGroupLayoutDesc.setNextInChain(null);
        bindGroupLayoutDesc.setLabel(label);
        WGPUVectorBindGroupLayoutEntry entryVector = WGPUVectorBindGroupLayoutEntry.obtain();
        for(WGPUBindGroupLayoutEntry entry : entries.values())
            entryVector.push_back(entry);
        bindGroupLayoutDesc.setEntries( entryVector );

        System.out.println("Create binding layout : "+entries.size() + "  "+label);
        layout = new WGPUBindGroupLayout();
        webgpu.device.createBindGroupLayout(bindGroupLayoutDesc, layout);
    }

    public int getEntryCount(){
        if(layout == null) throw new RuntimeException("Call after end()");
        return entries.size();
    }

    public WGPUBindGroupLayout getLayout(){
        if(layout == null)
            throw new RuntimeException("BindGroupLayout not defined, did you forget to call end()?");
        return layout;
    }


    private void setDefaultLayout(WGPUBindGroupLayoutEntry bindingLayout) {
        bindingLayout.setNextInChain(null);



//        bindingLayout.getBuffer().setNextInChain(null);
//        bindingLayout.getBuffer().setType(WGPUBufferBindingType.Undefined);
//        bindingLayout.getBuffer().setHasDynamicOffset(0);
//
//        bindingLayout.getSampler().setNextInChain(null);
//        bindingLayout.getSampler().setType(WGPUSamplerBindingType.Undefined);
//
//        bindingLayout.getStorageTexture().setNextInChain(null);
//        bindingLayout.getStorageTexture().setAccess(WGPUStorageTextureAccess.Undefined);
//        bindingLayout.getStorageTexture().setFormat(WGPUTextureFormat.Undefined);
//        bindingLayout.getStorageTexture().setViewDimension(WGPUTextureViewDimension.Undefined);
//
//        bindingLayout.getTexture().setNextInChain(null);
//        bindingLayout.getTexture().setMultisampled(0);
//        bindingLayout.getTexture().setSampleType(WGPUTextureSampleType.Undefined);
//        bindingLayout.getTexture().setViewDimension(WGPUTextureViewDimension.Undefined);

    }

    @Override
    public void dispose() {
        layout.release();
        layout.dispose();
        layout = null;
    }
}




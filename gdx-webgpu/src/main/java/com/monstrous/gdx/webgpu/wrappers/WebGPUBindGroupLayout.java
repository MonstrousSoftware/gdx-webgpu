package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulated bind group layout.  Use begin(), addXXX(), end() to define a layout.
 */
public class WebGPUBindGroupLayout implements Disposable {
    private final WgGraphics gfx = (WgGraphics) Gdx.graphics;
    private final WebGPU_JNI webGPU = gfx.getWebGPU();
    private final WebGPUApplication webgpu = gfx.getContext();
    private Pointer handle = null;
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
        handle = null;
    }

    /**
     * Add binding layout for a buffer.
     *
     * @param bindingId integer as in the shader, 0, 1, 2, ...  they don't have to be sequential or in order!
     * @param visibility e.g. WGPUShaderStage.Fragment (or combination using OR operator)
     * @param bufferBindingType e.g. WGPUBufferBindingType.ReadOnlyStorage
     */
    public void addBuffer(int bindingId, long visibility, WGPUBufferBindingType bufferBindingType, long minBindingSize, boolean hasDynamicOffset ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getBuffer().setType(bufferBindingType);
        bindingLayout.getBuffer().setMinBindingSize(minBindingSize);
        bindingLayout.getBuffer().setHasDynamicOffset(hasDynamicOffset? 1L : 0L);

        entries.put(bindingId, bindingLayout);
    }

    public void addTexture(int bindingId, long visibility, WGPUTextureSampleType sampleType, WGPUTextureViewDimension viewDimension, boolean multiSampled ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getTexture().setMultisampled(multiSampled? 1L : 0L);
        bindingLayout.getTexture().setSampleType(sampleType);
        bindingLayout.getTexture().setViewDimension(viewDimension);
        entries.put(bindingId, bindingLayout);
    }
    public void addStorageTexture(int bindingId, long visibility, WGPUStorageTextureAccess access, WGPUTextureFormat format, WGPUTextureViewDimension viewDimension ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getStorageTexture().setAccess(access);
        bindingLayout.getStorageTexture().setFormat(format);
        bindingLayout.getStorageTexture().setViewDimension(viewDimension);
        entries.put(bindingId, bindingLayout);
    }

    public void addSampler(int bindingId, long visibility, WGPUSamplerBindingType samplerType ){
        WGPUBindGroupLayoutEntry bindingLayout = addBinding(bindingId, visibility);
        bindingLayout.getSampler().setType(samplerType);
        entries.put(bindingId, bindingLayout);
    }


    /** addBinding
     *  common part of binding layouts
     */
    private WGPUBindGroupLayoutEntry addBinding(int bindingId, long visibility ){
        WGPUBindGroupLayoutEntry bindingLayout = WGPUBindGroupLayoutEntry.createDirect();
        setDefaultLayout(bindingLayout);
        bindingLayout.setBinding(bindingId);
        bindingLayout.setVisibility(visibility);
        return bindingLayout;
    }

    public void end(){
        // Create a bind group layout
        WGPUBindGroupLayoutDescriptor bindGroupLayoutDesc = WGPUBindGroupLayoutDescriptor.createDirect();
        bindGroupLayoutDesc.setNextInChain();
        bindGroupLayoutDesc.setLabel(label);
        bindGroupLayoutDesc.setEntryCount(entries.size());
        WGPUBindGroupLayoutEntry[] entryArray = new WGPUBindGroupLayoutEntry[entries.size()];
        int i = 0;
        for(WGPUBindGroupLayoutEntry entry : entries.values())
            entryArray[i++] = entry;
        bindGroupLayoutDesc.setEntries( entryArray );

        handle = webGPU.wgpuDeviceCreateBindGroupLayout(webgpu.device.getHandle(), bindGroupLayoutDesc);
    }

    public int getEntryCount(){
        if(handle == null) throw new RuntimeException("Call after end()");
        return entries.size();
    }

    public Pointer getHandle(){
        if(handle == null)
            throw new RuntimeException("BindGroupLayout not defined, did you forget to call end()?");
        return handle;
    }


    private void setDefaultLayout(WGPUBindGroupLayoutEntry bindingLayout) {

        bindingLayout.getBuffer().setNextInChain();
        bindingLayout.getBuffer().setType(WGPUBufferBindingType.Undefined);
        bindingLayout.getBuffer().setHasDynamicOffset(0L);

        bindingLayout.getSampler().setNextInChain();
        bindingLayout.getSampler().setType(WGPUSamplerBindingType.Undefined);

        bindingLayout.getStorageTexture().setNextInChain();
        bindingLayout.getStorageTexture().setAccess(WGPUStorageTextureAccess.Undefined);
        bindingLayout.getStorageTexture().setFormat(WGPUTextureFormat.Undefined);
        bindingLayout.getStorageTexture().setViewDimension(WGPUTextureViewDimension.Undefined);

        bindingLayout.getTexture().setNextInChain();
        bindingLayout.getTexture().setMultisampled(0L);
        bindingLayout.getTexture().setSampleType(WGPUTextureSampleType.Undefined);
        bindingLayout.getTexture().setViewDimension(WGPUTextureViewDimension.Undefined);

    }

    @Override
    public void dispose() {
        webGPU.wgpuBindGroupLayoutRelease(handle);
    }
}




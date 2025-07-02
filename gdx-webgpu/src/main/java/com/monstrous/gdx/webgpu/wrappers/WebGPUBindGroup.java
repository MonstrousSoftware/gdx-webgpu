package com.monstrous.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.webgpu.WGPUBindGroupDescriptor;
import com.monstrous.gdx.webgpu.webgpu.WGPUBindGroupEntry;
import com.monstrous.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulated bind group.  Used to bind values to a shader.
 *
 * Example:
 *      WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
 *      bg.begin();
 *      bg.addBuffer(buffer);
 *      bg.addTexture(textureView);
 *      bg.addSampler(sampler);
 *      bg.end();
 *
 *      Note the sequence and types must correspond to what is defined in the
 *      BindGroupLayout.
 *
 *      Alternatively:
 *      bg.setBuffer(0, buffer);
 *      bg.setTexture(1, textureView);
 *      bg.setSampler(2, sampler);
 *      bg.create();
 *
 *      This allows also to only update specific bindings.
 *      create() is implied by getHandle().
 */
public class WebGPUBindGroup implements Disposable {
    private final WebGPU_JNI webGPU;
    private Pointer handle = null;
    private final WgGraphics gfx;
    private final WebGPUContext webgpu;

    private final WebGPUBindGroupLayout layout;
    private final WGPUBindGroupDescriptor bindGroupDescriptor;
    private final WGPUBindGroupEntry[] entryArray;
    private final Map<Integer, Integer> bindingIndex;       // array index per bindingId (bindingId's can skip numbers)
    private final int numEntries;
    private boolean dirty;  // has an entry changed?


    public WebGPUBindGroup(WebGPUBindGroupLayout layout) {
        gfx = (WgGraphics) Gdx.graphics;
        webGPU = gfx.getWebGPU();
        webgpu = gfx.getContext();


        this.layout = layout;
        numEntries = layout.getEntryCount();

        // Create a bind group descriptor and an array of BindGroupEntry
        //
        bindGroupDescriptor = WGPUBindGroupDescriptor.createDirect();
        bindGroupDescriptor.setNextInChain()
                .setLayout(layout.getHandle())
                .setEntryCount(numEntries);

        bindingIndex = new HashMap<>();
        entryArray = new WGPUBindGroupEntry[numEntries];
        for (int i = 0; i < numEntries; i++) {
            entryArray[i] = WGPUBindGroupEntry.createDirect();
            setDefault(entryArray[i]);
        }

    }

    public void begin() {
        handle = null;
    }

//    /** bind a (subrange of a) buffer. */
//    public void addBuffer(int bindingId, WebGPUBuffer buffer, int offset, long size) {
//        setBuffer(bindingId, buffer, offset, size);
//    }
//
//    /** bind a buffer */
//    public void addBuffer(int bindingId, WebGPUBuffer buffer) {
//        setBuffer(bindingId, buffer);
//    }
//
//    /** bind a texture view */
//    public void addTexture(int bindingId, WebGPUTextureView textureView) {
//        setTexture( bindingId, textureView);
//    }
//
//    /** bind a sampler */
//    public void addSampler(int bindingId, Pointer sampler) {
//        setSampler(bindingId, sampler);
//    }

    /** creates the bind group */
    public Pointer end() {
        return create();
    }

    /** bind a buffer. */
    public void setBuffer( int bindingId, WebGPUBuffer buffer) {
        setBuffer( bindingId, buffer, 0, buffer.getSize());
    }

    /** find index of bindingId or create a new index of this is a new bindingId */
    private int findIndex(int bindingId){
        // should we check against the binding id's from the layout?
        Integer index = bindingIndex.get(bindingId);
        if(index == null){
            index = bindingIndex.size();
            if(index >= numEntries) throw new ArrayIndexOutOfBoundsException("Too many entries. See BindGroupLayout");
            bindingIndex.put(bindingId, index);
        }
        return index;
    }

    /** bind a (subrange of a) buffer. */
    public void setBuffer(int bindingId, WebGPUBuffer buffer, int offset, long size) {
        int index = findIndex(bindingId);
        WGPUBindGroupEntry entry = entryArray[index];
        entry.setBinding(bindingId);
        entry.setBuffer(buffer.getHandle());
        entry.setOffset(offset);
        entry.setSize(size);
        dirty = true;
    }

    public void setTexture(int bindingId, WebGPUTextureView textureView) {
        int index = findIndex(bindingId);
        WGPUBindGroupEntry entry = entryArray[index];
        entry.setBinding(bindingId);
        entry.setTextureView(textureView.getHandle());
        dirty = true;
    }

    /** bind a sampler */
    public void setSampler(int bindingId, Pointer sampler) {
        int index = findIndex(bindingId);
        WGPUBindGroupEntry entry = entryArray[index];
        entry.setBinding(bindingId);
        entry.setSampler(sampler);
        dirty = true;
    }

    private void setDefault(WGPUBindGroupEntry entry){
        entry.setBuffer(null);
        entry.setSampler(null);
        entry.setTextureView(null);
    }


    /** creates the bind group. (also implicitly called by getHandle()) */
    public Pointer create() {
        if(dirty) {
            if(handle != null) {
                //System.out.println("Releasing bind group");
                webGPU.wgpuBindGroupRelease(handle);
            }
            //System.out.println("Creating bind group");
            bindGroupDescriptor.setEntries(entryArray);
            handle = webGPU.wgpuDeviceCreateBindGroup(webgpu.device.getHandle(), bindGroupDescriptor);
            dirty = false;
        }
        return handle;
    }

    public Pointer getHandle() {
        if(dirty)
            create();
        return handle;
    }

    @Override
    public void dispose() {
        if(handle != null) {
            //System.out.println("Releasing bind group");
            webGPU.wgpuBindGroupRelease(handle);
            handle = null;
        }
    }

}




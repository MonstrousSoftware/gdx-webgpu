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
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import java.util.HashMap;
import java.util.Map;

/**
 * Encapsulated bind group.  Used to bind values to a shader.
 *
 * Example:
 *      WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
 *      bg.setBuffer(0, buffer);
 *      bg.setTexture(1, textureView);
 *      bg.setSampler(2, sampler);
 *      bg.create();
 *
 *      Note the sequence and types must correspond to what is defined in the
 *      BindGroupLayout.
 *
 *      This allows also to only update specific bindings.
 *      create() is implied by getHandle().
 */
public class WebGPUBindGroup implements Disposable {
    private WGPUBindGroup bindGroup = null;
    private final WebGPUContext webgpu;

    private final WGPUBindGroupDescriptor bindGroupDescriptor;
    private final Map<Integer, Integer> bindingIndex;       // array index per bindingId (bindingId's can skip numbers)
    private final WGPUBindGroupEntry[] entryArray;
    private final int numEntries;
    private boolean dirty;  // has an entry changed?


    public WebGPUBindGroup(WebGPUBindGroupLayout layout) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        numEntries = layout.getEntryCount();
        entryArray = new WGPUBindGroupEntry[numEntries];

        // Create a bind group descriptor and an array of BindGroupEntry
        //
        bindGroupDescriptor = WGPUBindGroupDescriptor.obtain();
        bindGroupDescriptor.setNextInChain(null);
        bindGroupDescriptor.setLayout(layout.getLayout());

        bindingIndex = new HashMap<>();

        for (int i = 0; i < numEntries; i++) {
            // don't use obtain because the entries will overwrite each other
            WGPUBindGroupEntry entry = new WGPUBindGroupEntry();
            setDefault(entry);
            entryArray[i] = entry;

        }
    }

    public void begin() {
        bindGroup = null;
    }


    /** creates the bind group */
    public WGPUBindGroup end() {
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
    public void setBuffer(int bindingId, WebGPUBuffer buffer, int offset, int size) {
        int index = findIndex(bindingId);
        WGPUBindGroupEntry entry = entryArray[index];
        entry.setBinding(bindingId);
        entry.setBuffer(buffer.getBuffer());
        entry.setOffset(offset);
        entry.setSize(size);
        dirty = true;
    }

    public void setTexture(int bindingId, WGPUTextureView textureView) {
        int index = findIndex(bindingId);
        WGPUBindGroupEntry entry = entryArray[index];
        entry.setBinding(bindingId);
        entry.setTextureView(textureView);
        dirty = true;
    }

    /** bind a sampler */
    public void setSampler(int bindingId, WGPUSampler sampler) {
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
    public WGPUBindGroup create() {
        if(dirty) {
            if(bindGroup != null) {
                //System.out.println("Releasing bind group");
                bindGroup.release();
                bindGroup.dispose();
            }
            WGPUVectorBindGroupEntry entryVector = WGPUVectorBindGroupEntry.obtain();
            for (int i = 0; i < numEntries; i++) {
                entryVector.push_back(entryArray[i]);
            }
            //System.out.println("Creating bind group");
            bindGroupDescriptor.setEntries(entryVector);
            bindGroup = new WGPUBindGroup();
            webgpu.device.createBindGroup(bindGroupDescriptor, bindGroup);
            dirty = false;
        }
        return bindGroup;
    }

    public WGPUBindGroup getBindGroup() {
        if(dirty)
            create();
        return bindGroup;
    }

    @Override
    public void dispose() {
        if(bindGroup != null) {
            //System.out.println("Releasing bind group");
            bindGroup.release();
            bindGroup.dispose();
            bindGroup = null;
        }
    }

}




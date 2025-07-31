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
package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.IntMap;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.wrappers.*;


import java.util.HashMap;
import java.util.Map;

/** Manages bind groups and provides methods for binding by uniform name.
 */
public class Binder implements Disposable {
    private final BindingDictionary bindMap;
    private final Map<Integer, WebGPUBindGroupLayout> groupLayouts;
    private final IntMap<WebGPUBindGroup> groups;
    private final IntMap<BufferInfo> buffers;
    private WGPUPipelineLayout pipelineLayout;
    private WebGPUContext webgpu;

    public static class BufferInfo {
        WebGPUUniformBuffer buffer;
        int offset;
        long size;

        public BufferInfo(WebGPUUniformBuffer buffer, int offset, long size) {
            this.buffer = buffer;
            this.offset = offset;
            this.size = size;
        }
    }

    public Binder() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
        bindMap = new BindingDictionary();
        groupLayouts = new HashMap<>(4);
        groups = new IntMap<>(4);
        buffers = new IntMap<>(4);
    }

    public void defineGroup(int groupId, WebGPUBindGroupLayout layout){
        groupLayouts.put(groupId, layout);
    }

    /** Associates a name with a groupId + bindingId. */
    public void defineBinding(String name, int groupId, int bindingId){
        bindMap.defineUniform(name, groupId, bindingId);
    }

    /** Associates a name with a groupId + bindingId + offset.
     *  This is for a uniform in a uniform buffer.
     *  */
    public void defineUniform(String name, int groupId, int bindingId, int offset){
        bindMap.defineUniform(name, groupId, bindingId, offset);
    }

    // to do specific  for uniform buffer
    public void setBuffer(String name, WebGPUUniformBuffer buffer, int offset, int size ){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        WebGPUBindGroup bindGroup = getBindGroup(mapping.groupId);
        bindGroup.setBuffer( mapping.bindingId, buffer, offset, size);
        // keep hold of the buffer information, we may need it for uniforms.
        buffers.put(combine(mapping.groupId, mapping.bindingId), new BufferInfo(buffer, offset, size));
    }


    public WebGPUBuffer getBuffer(String name){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        return bufferInfo.buffer;
    }

    // todo allow more generic buffers?
    public void setBuffer(int groupId, int bindingId, WebGPUUniformBuffer buffer, int offset, int size ){
        WebGPUBindGroup bindGroup = getBindGroup(groupId);
        bindGroup.setBuffer(bindingId, buffer, offset, size);

        // keep hold of the buffer information, we may need it for uniforms.
        buffers.put(combine(groupId, bindingId), new BufferInfo(buffer, offset, size));
    }

    public void setTexture(String name, WGPUTextureView textureView ){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        WebGPUBindGroup bindGroup = getBindGroup(mapping.groupId);

        bindGroup.setTexture(mapping.bindingId, textureView);
    }

    public void setSampler(String name, WGPUSampler sampler){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        WebGPUBindGroup bindGroup = getBindGroup(mapping.groupId);

        bindGroup.setSampler(mapping.bindingId, sampler);
    }

    // hack to use tuple as single key
    private int combine(int groupId, int bindingId){
        return groupId << 16 + bindingId;
    }

    /** note that buffer.flush() is needed to write the uniform values to the GPU. */
    public void setUniform(String name, float value){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");

        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));

        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset , value);
    }

    /** Add an additional offset to the uniform's offset. This may be handy for array uniforms. */
    public void setUniform(String name, int offset, float value){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset+offset , value);
    }


    public void setUniform(String name, Vector2 vec){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset, vec);
    }

    public void setUniform(String name, Vector3 vec){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset, vec);
    }

    public void setUniform(String name, int offset, Vector3 vec){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset+offset, vec);
    }

    public void setUniform(String name, Vector4 vec){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset , vec);
    }

    public void setUniform(String name, Matrix4 matrix){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset , matrix);
    }

    public void setUniform(String name, Color col){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset, col);
    }

    public void setUniform(String name, int offset, Color col){
        BindingDictionary.BindingMap mapping = bindMap.findUniform(name);
        if(mapping == null) throw new RuntimeException("Uniform name "+name+" not defined.");
        if(mapping.offset < 0) throw new RuntimeException("Uniform name "+name+" is not defined in a uniform buffer.");
        BufferInfo bufferInfo = buffers.get(combine(mapping.groupId, mapping.bindingId));
        WebGPUUniformBuffer buffer = bufferInfo.buffer;
        buffer.set(bufferInfo.offset + mapping.offset+offset, col);
    }


    /** find or create bind group */
    public WebGPUBindGroup getBindGroup(int groupId){
        WebGPUBindGroup bindGroup = groups.get(groupId);
        if(bindGroup == null){
            WebGPUBindGroupLayout layout = groupLayouts.get(groupId);
            if(layout == null) throw new RuntimeException("Group "+groupId+" not defined. Use defineGroup()");
            bindGroup = new WebGPUBindGroup(layout);
            groups.put(groupId, bindGroup);
        }
        return bindGroup;
    }


    public WGPUPipelineLayout getPipelineLayout(String label){
        // note: if label changes, this does not invalidate an existing pipeline layout
        // the method will return the cached layout with the original label.
        if(pipelineLayout == null){

            WGPUVectorBindGroupLayout layouts = WGPUVectorBindGroupLayout.obtain();

            // does this need to be in sequential order of group id? Can group id's skip numbers?
            for(WebGPUBindGroupLayout layout : groupLayouts.values())
                layouts.push_back(layout.getLayout());

            WGPUPipelineLayoutDescriptor pipelineLayoutDesc = WGPUPipelineLayoutDescriptor.obtain();
            pipelineLayoutDesc.setNextInChain(null);
            pipelineLayoutDesc.setLabel(label);
            pipelineLayoutDesc.setBindGroupLayouts(layouts);

            pipelineLayout = new WGPUPipelineLayout();
            webgpu.device.createPipelineLayout(pipelineLayoutDesc, pipelineLayout);
        }
        return pipelineLayout;
    }

    /** bind the bind group related to groupId to the render pass */
    public void bindGroup(WebGPURenderPass renderPass, int groupId ){
        WebGPUBindGroup bindGroup = groups.get(groupId);
        renderPass.setBindGroup( groupId, bindGroup.getBindGroup());
    }

    /** bind the bind group related to groupId to the render pass and use a dynamic offset for one of the bindings */
    public void bindGroup(WebGPURenderPass renderPass, int groupId, int dynamicOffset ){
        WebGPUBindGroup bindGroup = groups.get(groupId);
        renderPass.setBindGroup( groupId, bindGroup.getBindGroup(), dynamicOffset);
    }

    @Override
    public void dispose() {
        for(WebGPUBindGroup bg : groups.values())
            bg.dispose();
        if(pipelineLayout != null)
            pipelineLayout.dispose();
    }
}

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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;
import jnr.ffi.Pointer;

/** Uniform buffer, optionally to be used with dynamic offsets
 *
 * Usage:
 *  buffer.setDynamicOffsetIndex(0);    // indicate which slice we are at, can be omitted if not using dynamic offsets
 *  buffer.set(0, transformMatrix);     // set uniform value
 *  buffer.set(64, diffuseColor);       // set uniform value, offset in bytes, relative to this slice
 *  buffer.flush();                     // write content to GPU!
 *
 *  buffer.setDynamicOffsetIndex(1);    // next slice
 *  buffer.set(0, transformMatrix);     //
 *  buffer.set(64, diffuseColor);       //
 *  buffer.flush();                     // write content to GPU!
 *
 *  Beware of required padding between uniforms!
 */

public class WebGPUUniformBuffer extends WebGPUBuffer {

    private final int contentSize;
    private final int uniformStride;
    private final int maxSlices;
    private final Pointer floatData;
    private int dynamicOffsetIndex;
    private boolean dirty;


    /** Construct a Uniform Buffer without using dynamic offsets.
     *
     * @param contentSize size of data in bytes
     * @param usage flags of WGPUBufferUsage
     */
    public WebGPUUniformBuffer(int contentSize, long usage){
        this(contentSize, usage, 1);
    }

    /** Construct a Uniform Buffer. To use dynamic offsets, set maxSlices to the number of segments needed.
     *
     * @param contentSize size of data per slice in bytes (will be padded to a valid uniform stride size)
     * @param usage flags of WGPUBufferUsage
     * @param maxSlices minimum 1
     */
    public WebGPUUniformBuffer(int contentSize, long usage, int maxSlices){
        this("uniform buffer", contentSize, usage, maxSlices);
    }

    /** Construct a Uniform Buffer. To use dynamic offsets, set maxSlices to the number of segments needed.
     *
     * @param contentSize size of data per slice in bytes (will be padded to a valid uniform stride size)
     * @param usage flags of WGPUBufferUsage
     * @param maxSlices minimum 1
     */
    public WebGPUUniformBuffer(String label, int contentSize, long usage, int maxSlices){
        super(label, usage, calculateBufferSize(contentSize, maxSlices));
        this.contentSize = contentSize;
        this.maxSlices = maxSlices;

        this.uniformStride = calculateStride(contentSize, maxSlices);
        dynamicOffsetIndex = 0;
        dirty = false;

        // working buffer in native memory to use as input to WriteBuffer
        floatData = JavaWebGPU.createDirectPointer(contentSize);       // native memory buffer for one slice to aid write buffer
    }



    private static long calculateBufferSize(int contentSize, int maxSlices){
        // round up buffer size to 16 byte alignment
        long bufferSize = ceilToNextMultiple(contentSize, 16);

        // if we use dynamics offsets, there is a minimum stride to apply between "slices"
        if(maxSlices > 1) { // do we use dynamic offsets?
            int uniformStride = calculateStride(contentSize, maxSlices);
            bufferSize += (long) uniformStride * (maxSlices - 1);
        }
        return bufferSize;
    }

    private static int calculateStride(int contentSize, int maxSlices){
        int stride = 0;
        if(maxSlices > 1) { // do we use dynamic offsets?
            WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
            int uniformAlignment = (int) gfx.getDevice().getSupportedLimits().getLimits().getMinUniformBufferOffsetAlignment();
            stride = ceilToNextMultiple(contentSize, uniformAlignment);
        }
        return stride;
    }

    private static int ceilToNextMultiple(int value, int step){
        int d = value / step + (value % step == 0 ? 0 : 1);
        return step * d;
    }

    /** When using dynamic offsets, they need to be a multiple of this value. */
    public int getUniformStride(){
        return uniformStride;
    }

    /* call this after any set or a sequence of sets to write the floatData to the GPU buffer */
    public void flush(){
        if(dirty) {
            write(dynamicOffsetIndex * uniformStride, floatData, contentSize);
            dirty = false;
        }
    }

    public Pointer getFloatData() {
        return floatData;
    }

    /** For a uniform buffer with dynamic offset, set the index of the uniform buffer slice to use [0 .. maxSlices-1].
     *  Index will be translated to an offset of uniformStride * index */
    public void setDynamicOffsetIndex(int index){
        if(index < 0 || index >= maxSlices)
            throw new IllegalArgumentException("setDynamicOffsetIndex: index out of range, maxSlices = "+maxSlices);
        if(index != dynamicOffsetIndex)
            flush();
        dynamicOffsetIndex = index;
    }

    /** offset in bytes */
    public void set(int offset, float value ){
        floatData.putFloat(offset, value);
        dirty = true;
    }

    public void set( int offset, Vector2 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
        dirty = true;
    }

    public void set( int offset, Vector3 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
        floatData.putFloat(offset +2*Float.BYTES, vec.z);
        dirty = true;
    }

    public void set( int offset, Vector4 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
        floatData.putFloat(offset +2*Float.BYTES, vec.z);
        floatData.putFloat(offset +3*Float.BYTES, vec.w);
        dirty = true;
    }

    public void set(int offset, Matrix4 mat ){
        floatData.put(offset, mat.val, 0, 16);
        dirty = true;
    }

    public void set( int offset, Color col ){
        floatData.putFloat(offset, col.r);
        floatData.putFloat(offset +Float.BYTES, col.g);
        floatData.putFloat(offset +2*Float.BYTES,col.b);
        floatData.putFloat(offset +3*Float.BYTES, col.a);
        dirty = true;
    }


}

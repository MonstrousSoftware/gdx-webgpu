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
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


/** Uniform buffer, optionally to be used with dynamic offsets
 *
 * Usage without dynamic offsets:
 *   buffer.set(0, transformMatrix);     // set uniform value (a matrix) at offset 0
 *   buffer.set(64, diffuseColor);       // set uniform value, offset in bytes
 *   buffer.flush();
 *
 * Usage with dynamic offset:
 *  buffer.beginSlices();               // reset dynamic offset
 *  int dynamicOffset = buffer.nextSlice(); // start a new slice
 *  buffer.set(0, transformMatrix);     // set uniform value
 *  buffer.set(64, diffuseColor);       // set uniform value, offset in bytes, relative to this slice
 *  binder.bindGroup(renderPass, 3, dynamicOffset);     // pass dynamic offset to the render pass
 *
 *  dynamicOffset = buffer.nextSlice();   // next slice
 *  buffer.set(0, transformMatrix);     //
 *  buffer.set(64, diffuseColor);       //
 *  buffer.endSlices();                 // write content to GPU!
 *  binder.bindGroup(renderPass, 3, dynamicOffset);
 *
 *  Beware of required padding between uniforms!
 */

public class WebGPUUniformBuffer extends WebGPUBuffer {
    private final ByteBuffer dataBuf;
    private final FloatBuffer floatData;
    private int dynamicOffset;
    private int bytesFilled;
    private boolean dirty;


    /** Construct a Uniform Buffer without using dynamic offsets.
     *
     * @param contentSize size of data in bytes
     * @param usage flags of WGPUBufferUsage
     */
    public WebGPUUniformBuffer(int contentSize, WGPUBufferUsage usage){
        this(contentSize, usage, 1);
    }

    /** Construct a Uniform Buffer. To use dynamic offsets, set maxSlices to the number of segments needed.
     *
     * @param contentSize size of data per slice in bytes (will be padded to a valid uniform stride size)
     *                    Note: data for each slice doesn't have to be the same size. Provide the average or worst case
     *                    size to allow calculating the buffer size.
     * @param usage flags of WGPUBufferUsage
     * @param maxSlices minimum 1
     */
    public WebGPUUniformBuffer(int contentSize, WGPUBufferUsage usage, int maxSlices){
        this("uniform buffer", contentSize, usage, maxSlices);
    }

    /** Construct a Uniform Buffer. To use dynamic offsets, set maxSlices to the number of segments needed.
     *
     * @param contentSize size of data per slice in bytes (will be padded to a valid uniform stride size)
     * @param usage flags of WGPUBufferUsage
     * @param maxSlices minimum 1
     */
    public WebGPUUniformBuffer(String label, int contentSize, WGPUBufferUsage usage, int maxSlices){
        super(label, usage, calculateBufferSize(contentSize, maxSlices));
        dynamicOffset = 0;
        dirty = false;

        // working buffer in native memory to use as input to WriteBuffer
        dataBuf = BufferUtils.newUnsafeByteBuffer(contentSize);
        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
        floatData = dataBuf.asFloatBuffer();
    }



    private static int calculateBufferSize(int contentSize, int maxSlices){
        // round up buffer size to 16 byte alignment
        int bufferSize = ceilToNextMultiple(contentSize, 16);

        // if we use dynamics offsets, there is a minimum stride to apply between "slices"
        if(maxSlices > 1) { // do we use dynamic offsets?
            int uniformStride = calculateStride(contentSize, maxSlices);
            bufferSize += uniformStride * (maxSlices - 1);
        }
        return bufferSize;
    }

    private static int calculateStride(int contentSize, int maxSlices){
        int stride = 0;
        if(maxSlices > 1) { // do we use dynamic offsets?
            WebGPUContext webgpu = ((WgGraphics) Gdx.graphics).getContext();
            // todo
            int uniformAlignment =  256; //(int) webgpu.device.getSupportedLimits().getLimits().getMinUniformBufferOffsetAlignment();
            stride = ceilToNextMultiple(contentSize, uniformAlignment);
        }
        return stride;
    }

    private static int ceilToNextMultiple(int value, int step){
        int d = value / step + (value % step == 0 ? 0 : 1);
        return step * d;
    }

    /* call this after any set or a sequence of sets to write the floatData to the GPU buffer */
    public void flush(){
        if(dirty) {
            write(dynamicOffset, dataBuf, bytesFilled);
            dirty = false;
        }
    }

    public FloatBuffer getFloatData() {
        return floatData;
    }


    public void beginSlices(){
        dynamicOffset = 0;
        dataBuf.position(0);
        bytesFilled = 0;
    }

    /** Flush data and start a new set of data. The data can be set using the set() methods.
     * Returns dynamic offset for this set of data. */
    public int nextSlice(){
        if(bytesFilled > 0) {
            int uniformAlignment = 256; //(int) webgpu.device.getSupportedLimits().getLimits().getMinUniformBufferOffsetAlignment();
            int sliceLength = ceilToNextMultiple(bytesFilled, uniformAlignment); // round up
            flush();
            dynamicOffset += sliceLength;
            if (dynamicOffset > getSize())
                throw new GdxRuntimeException("Uniform buffer overflow");
            dataBuf.position(0);
            bytesFilled = 0;
        }
        return dynamicOffset;
    }

    /** Returns dynamic offset for this set of data. */
    public int getSliceOffset(){
        return dynamicOffset;
    }

    /** Ensure the last slice is written to the GPU buffer. */
    public void endSlices(){
        flush();
    }


    /** offset in bytes */
    public void set(int offset, float value ){
        dataBuf.putFloat(offset, value);
        bytesFilled = Math.max(bytesFilled, offset + Float.BYTES);
        dirty = true;
    }

    public void set( int offset, Vector2 vec ){
        dataBuf.putFloat(offset, vec.x);
        dataBuf.putFloat(offset +Float.BYTES, vec.y);
        bytesFilled = Math.max(bytesFilled, offset + 2*Float.BYTES);
        dirty = true;
    }

    public void set( int offset, Vector3 vec ){
        dataBuf.putFloat(offset, vec.x);
        dataBuf.putFloat(offset +Float.BYTES, vec.y);
        dataBuf.putFloat(offset +2*Float.BYTES, vec.z);
        bytesFilled = Math.max(bytesFilled, offset + 3*Float.BYTES);
        dirty = true;
    }

    public void set( int offset, Vector4 vec ){
        dataBuf.putFloat(offset, vec.x);
        dataBuf.putFloat(offset +Float.BYTES, vec.y);
        dataBuf.putFloat(offset +2*Float.BYTES, vec.z);
        dataBuf.putFloat(offset +3*Float.BYTES, vec.w);
        bytesFilled = Math.max(bytesFilled, offset + 4*Float.BYTES);
        dirty = true;
    }

    public void set(int offset, Matrix4 mat ){
        for(int i = 0; i < 16; i++) {
            float f =  mat.val[i];
            int index = offset + i*Float.BYTES;
            dataBuf.putFloat(index,f);
        }
        bytesFilled = Math.max(bytesFilled, offset + 16*Float.BYTES);
        dirty = true;
    }

    public void set(int offset, Matrix4[] matArray ){
        for(int j = 0; j < matArray.length; j++) {
            Matrix4 mat = matArray[j];
            for (int i = 0; i < 16; i++) {
                float f = mat.val[i];
                int index = offset + j * 16*Float.BYTES + i * Float.BYTES;
                dataBuf.putFloat(index, f);
            }
        }
        bytesFilled = Math.max(bytesFilled, offset + matArray.length * 16*Float.BYTES);
        dirty = true;
    }

    public void set( int offset, Color col ){
        dataBuf.putFloat(offset, col.r);
        dataBuf.putFloat(offset +Float.BYTES, col.g);
        dataBuf.putFloat(offset +2*Float.BYTES,col.b);
        dataBuf.putFloat(offset +3*Float.BYTES, col.a);
        bytesFilled = Math.max(bytesFilled, offset + 4*Float.BYTES);
        dirty = true;
    }

    @Override
    public void dispose() {
        BufferUtils.disposeUnsafeByteBuffer(dataBuf);
        super.dispose();
    }
}

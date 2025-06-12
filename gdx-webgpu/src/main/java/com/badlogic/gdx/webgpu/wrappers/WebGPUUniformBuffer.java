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

// todo auto padding between elements

public class WebGPUUniformBuffer extends WebGPUBuffer {

    private final int contentSize;
    private final int uniformStride;
    private final int maxSlices;
    private final Pointer floatData;
    private int appendOffset;


    public WebGPUUniformBuffer(int contentSize, long usage){
        this(contentSize, usage, 1);
    }

    /** Construct a Uniform Buffer. To use dynamic offsets, set maxSlices to the number of segments needed. */
    public WebGPUUniformBuffer(int contentSize, long usage, int maxSlices){
        super("uniform buffer", usage, calculateBufferSize(contentSize, maxSlices));
        this.contentSize = contentSize;
        this.maxSlices = maxSlices;

        this.uniformStride = calculateStride(contentSize, maxSlices);

        // working buffer in native memory to use as input to WriteBuffer
        floatData = JavaWebGPU.createDirectPointer(contentSize);       // native memory buffer for one instance to aid write buffer
    }



    private static long calculateBufferSize(int contentSize, int maxSlices){
        // round up buffer size to 16 byte alignment
        long bufferSize = ceilToNextMultiple(contentSize, 16);

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

    public void beginFill(){
        appendOffset = 0;
    }

    public void pad(int bytes){
        appendOffset += bytes;
    }

    public int getOffset(){
        return appendOffset;
    }

    public void setOffset(int offset){
        this.appendOffset = offset;
    }

    public void append( int value ){
        floatData.putInt(appendOffset, value);
        appendOffset += Integer.BYTES;
    }

    public void append( float f ){
        floatData.putFloat(appendOffset, f);
        appendOffset += Float.BYTES;
        //offset += 4*Float.BYTES;           // with padding!
    }

    public void append( Matrix4 mat ){
        set(appendOffset, mat);
        appendOffset += 16*Float.BYTES;
    }

    public void append( Vector3 vec ){
        set(appendOffset, vec);
        appendOffset += 4*Float.BYTES;           // with padding!
    }



    public void append( Color color ){
        floatData.putFloat(appendOffset +0*Float.BYTES, color.r);
        floatData.putFloat(appendOffset +1*Float.BYTES, color.g);
        floatData.putFloat(appendOffset +2*Float.BYTES, color.b);
        floatData.putFloat(appendOffset +3*Float.BYTES, color.a);
        appendOffset += 4*Float.BYTES;
    }

    public void append( float r, float g, float b, float a ){
        floatData.putFloat(appendOffset +0*Float.BYTES, r);
        floatData.putFloat(appendOffset +1*Float.BYTES, g);
        floatData.putFloat(appendOffset +2*Float.BYTES, b);
        floatData.putFloat(appendOffset +3*Float.BYTES, a);
        appendOffset += 4*Float.BYTES;
    }

    /** Write buffer data to the GPU */
    public void endFill(){
        endFill(0);
    }

    /** Fill the given slice of the uniform buffer. Writes data to the GPU. destOffset should be a multiple of uniformStride. */
    public void endFill(int destOffset){
        int dataSize = appendOffset;
        if(dataSize > contentSize) throw new RuntimeException("Overflow in UniformBuffer: content ("+dataSize+") > size ("+contentSize+").");
        if(destOffset > getSize()-dataSize) throw new IllegalArgumentException("UniformBuffer: offset too large.");
        write(destOffset, floatData, dataSize);
    }

    // to be trimmed
    /* call this after any set or a sequence of sets to write the floatData to the GPU buffer */
    public void flush(){
        write(0, floatData, contentSize);
    }

    public Pointer getFloatData() {
        return floatData;
    }

    public void set(int offset, float value ){
        floatData.putFloat(offset, value);
    }

    public void set( int offset, Vector2 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
    }

    public void set( int offset, Vector3 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
        floatData.putFloat(offset +2*Float.BYTES, vec.z);
    }

    public void set( int offset, Vector4 vec ){
        floatData.putFloat(offset, vec.x);
        floatData.putFloat(offset +Float.BYTES, vec.y);
        floatData.putFloat(offset +2*Float.BYTES, vec.z);
        floatData.putFloat(offset +3*Float.BYTES, vec.w);
    }

    public void set(int offset, Matrix4 mat ){
        floatData.put(offset, mat.val, 0, 16);
    }

    public void set( int offset, Color col ){
        floatData.putFloat(offset, col.r);
        floatData.putFloat(offset +Float.BYTES, col.g);
        floatData.putFloat(offset +2*Float.BYTES,col.b);
        floatData.putFloat(offset +3*Float.BYTES, col.a);
    }


}

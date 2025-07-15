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
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUByteBuffer;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import java.nio.ByteBuffer;

// Formerly WebGPUBuffer
// Is there still an added value wrt WebGPUBuffer?

/**
 * Encapsulation of WebGPU Buffer
 *
 * label: for debug/error messages, no functional value
 * bufferSize: in bytes, to be aligned if necessary
 * usage: one or more flags in combination, e.g. WGPUBufferUsage.CopyDst | WGPUBufferUsage.Uniform
 */
public class WebGPUBuffer implements Disposable {
    protected WGPUBuffer buffer;
    private final int bufferSize;
    private WgGraphics gfx;
    private WebGPUContext webgpu;

    public WebGPUBuffer(String label, WGPUBufferUsage usage, int bufferSize){
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        this.bufferSize = bufferSize;

        // Create uniform buffer
        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.obtain();
        bufferDesc.setLabel( label );
        bufferDesc.setUsage( usage );
        bufferDesc.setSize( bufferSize );
        bufferDesc.setMappedAtCreation(false);
        buffer = new WGPUBuffer();
        webgpu.device.createBuffer(bufferDesc, buffer);
    }

    public WGPUBuffer getBuffer(){
        return buffer;
    }

    public int getSize(){
        return bufferSize;
    }

    @Deprecated
    public void write(int destOffset, WGPUByteBuffer data){
        if(destOffset + data.getLimit() > bufferSize) throw new RuntimeException("Overflow in Buffer.write().");
        webgpu.queue.writeBuffer(buffer, destOffset, data);
        log(data.getLimit());
    }

    public void write(int destOffset, ByteBuffer data, int sizeInBytes){
        if(destOffset + sizeInBytes > bufferSize) throw new RuntimeException("Overflow in Buffer.write().");
        webgpu.queue.writeBuffer(buffer, destOffset, data, sizeInBytes);
        log(data.limit());
    }

    public void write(int destOffset, ByteBuffer data){
        if(destOffset + data.limit() > bufferSize) throw new RuntimeException("Overflow in Buffer.write().");
        webgpu.queue.writeBuffer(buffer, destOffset, data, data.limit());
        log(data.limit());
    }

    @Override
    public void dispose() {
        buffer.release();
        //buffer.destroy();
        // todo buffer.destroy();
        buffer = null;
    }

    private void log(int bytesWritten){
        //System.out.println("buffer write: "+bytesWritten);
    }
}

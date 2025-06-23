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
import com.monstrous.gdx.webgpu.WebGPUGraphicsBase;
import com.monstrous.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

/** Class to facilitate GPU benchmarking of render passes.
 * Beware: if you have multiple render passes you will receive only the average timing (or maybe the last pass?).
 * todo We should collect duration per render pass.
 */

public class GPUTimer implements Disposable {
    private final static int BUF_SIZE = 16;     // size for two 64-bit integer values

    private final boolean enabled;
    private Pointer timestampQuerySet;
    private Pointer timeStampResolveBuffer;
    private Pointer timeStampMapBuffer;
    private boolean timeStampMapOngoing = false;
    private WGPURenderPassTimestampWrites query = null;
    private WebGPU_JNI webGPU;
    private WebGPUDevice device;

    public GPUTimer(WebGPUDevice device, boolean enabled) {
        this.device = device;
        this.enabled = enabled;
        if(!enabled)
            return;

        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        // Create timestamp queries
        WGPUQuerySetDescriptor querySetDescriptor =  WGPUQuerySetDescriptor.createDirect();
        querySetDescriptor.setNextInChain();
        querySetDescriptor.setLabel("Timestamp Query Set");
        querySetDescriptor.setType(WGPUQueryType.Timestamp);
        querySetDescriptor.setCount(2); // start and end time


        timestampQuerySet = webGPU.wgpuDeviceCreateQuerySet(device.getHandle(), querySetDescriptor);

        // Create buffer
        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.createDirect();
        bufferDesc.setLabel("timestamp resolve buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopySrc | WGPUBufferUsage.QueryResolve );
        bufferDesc.setSize(BUF_SIZE);     // space for 2 uint64's
        bufferDesc.setMappedAtCreation(0L);
        timeStampResolveBuffer = webGPU.wgpuDeviceCreateBuffer(device.getHandle(), bufferDesc);

        bufferDesc.setLabel("timestamp map buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopyDst | WGPUBufferUsage.MapRead );
        bufferDesc.setSize(BUF_SIZE);
        timeStampMapBuffer = webGPU.wgpuDeviceCreateBuffer(device.getHandle(), bufferDesc);

        query = WGPURenderPassTimestampWrites.createDirect();
        query.setBeginningOfPassWriteIndex(0);
        query.setEndOfPassWriteIndex(1);
        query.setQuerySet(timestampQuerySet);
    }


    /** get a query that can be passed into a render pass descriptor using renderPassDescriptor.setTimestampWrites(query);
     *  Will be null if timing is not enabled. */
    public WGPURenderPassTimestampWrites getRenderPassQuery(){
        return query;
    }

    public void resolveTimeStamps(WebGPUCommandEncoder encoder){
        if( !enabled || timeStampMapOngoing)
            return;

        // Resolve the timestamp queries (write their result to the resolve buffer)
        webGPU.wgpuCommandEncoderResolveQuerySet(encoder.getHandle(), timestampQuerySet, 0, 2, timeStampResolveBuffer, 0);

        // Copy to the map buffer
        webGPU.wgpuCommandEncoderCopyBufferToBuffer(encoder.getHandle(), timeStampResolveBuffer, 0,  timeStampMapBuffer, 0,BUF_SIZE);
    }


    // a lambda expression to define a callback function
    WGPUBufferMapCallback onTimestampBufferMapped = (WGPUBufferMapAsyncStatus status, Pointer userData) -> {
        if(status == WGPUBufferMapAsyncStatus.Success) {
            Pointer ram =  webGPU.wgpuBufferGetConstMappedRange(timeStampMapBuffer, 0, BUF_SIZE);
            long start = ram.getLong(0);
            long end = ram.getLong(Long.BYTES);
            webGPU.wgpuBufferUnmap(timeStampMapBuffer);
            long ns = end - start;
            addTimeSample(ns);
        }
        timeStampMapOngoing = false;
    };

    public void fetchTimestamps(){
        if( !enabled || timeStampMapOngoing)
            return;

        timeStampMapOngoing = true;
        webGPU.wgpuBufferMapAsync(timeStampMapBuffer, WGPUMapMode.Read, 0, BUF_SIZE, onTimestampBufferMapped, null);
    }

    @Override
    public void dispose() {
        if(!enabled)
            return;
        System.out.println("GPUTimer.dispose()");
        while(timeStampMapOngoing) {
            System.out.println("Waiting for time stamp map before disposing... ");
            device.tick();
        }

        webGPU.wgpuQuerySetRelease(timestampQuerySet);
        // the following causes a crash when uncommented
        //webGPU.wgpuQuerySetDestroy(timestampQuerySet);

        webGPU.wgpuBufferDestroy(timeStampMapBuffer);
        webGPU.wgpuBufferRelease(timeStampMapBuffer);
        webGPU.wgpuBufferDestroy(timeStampResolveBuffer);
        webGPU.wgpuBufferRelease(timeStampResolveBuffer);
    }

    private long cumulative = 0;
    private int numSamples = 0;

    private void addTimeSample(long ns){
        numSamples++;
        cumulative += ns;
    }

    // returns average time per frame spent by GPU (in microseconds).
    public float getAverageGPUtime(){
        if(numSamples == 0)
            return 0;
        float avg = (float) (cumulative/1000) / (float)numSamples;
        //resetGPUsamples();
        return avg;
    }

    public void logAverageGPUtime(){
        if(numSamples > 0)
            System.out.println("average: "+(float)cumulative / (float)numSamples + " numSamples: "+numSamples);
        resetGPUsamples();
    }

    public void resetGPUsamples(){
        numSamples = 0;
        cumulative = 0;
    }


}

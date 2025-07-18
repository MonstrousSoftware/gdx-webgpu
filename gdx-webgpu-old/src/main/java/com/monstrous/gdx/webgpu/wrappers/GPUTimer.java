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
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

/** Class to facilitate GPU benchmarking of render passes.
 * Beware: if you have multiple render passes you will receive only the average timing (or maybe the last pass?).
 * todo We should collect duration per render pass.
 */

public class GPUTimer implements Disposable {
    public final static int MAX_PASSES = 20;   // max number of passes that we can support

    private final boolean enabled;
    private Pointer timestampQuerySet;
    private Pointer timeStampResolveBuffer;
    private Pointer timeStampMapBuffer;
    private boolean timeStampMapOngoing = false;
    private WebGPU_JNI webGPU;
    private final WebGPUDevice device;
    private int passNumber;
    private int numPasses;

    public GPUTimer(WebGPUDevice device, boolean enabled) {
        this.device = device;
        this.enabled = enabled;
        if(!enabled)
            return;

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        // Create timestamp queries
        WGPUQuerySetDescriptor querySetDescriptor =  WGPUQuerySetDescriptor.createDirect();
        querySetDescriptor.setNextInChain();
        querySetDescriptor.setLabel("Timestamp Query Set");
        querySetDescriptor.setType(WGPUQueryType.Timestamp);
        querySetDescriptor.setCount(2*MAX_PASSES); // start and end time

        timestampQuerySet = webGPU.wgpuDeviceCreateQuerySet(device.getHandle(), querySetDescriptor);

        // Create buffer
        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.createDirect();
        bufferDesc.setLabel("timestamp resolve buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopySrc | WGPUBufferUsage.QueryResolve );
        bufferDesc.setSize(8*2*MAX_PASSES);     // space for 2 uint64's per pass
        bufferDesc.setMappedAtCreation(0L);
        timeStampResolveBuffer = webGPU.wgpuDeviceCreateBuffer(device.getHandle(), bufferDesc);

        bufferDesc.setLabel("timestamp map buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopyDst | WGPUBufferUsage.MapRead );
        bufferDesc.setSize(8*2*MAX_PASSES);
        timeStampMapBuffer = webGPU.wgpuDeviceCreateBuffer(device.getHandle(), bufferDesc);

        passNumber = -1;

    }


    public boolean isEnabled(){
        return enabled;
    }

    /** get a query set that can be made into a query for renderPassDescriptor.setTimestampWrites(query);
     *  Will be null if timing is not enabled. */
    public Pointer getQuerySet(){
        return timestampQuerySet;
    }

    public int addPass(String name){
        if(passNumber == MAX_PASSES) {
            Gdx.app.error("GPUTimer", "Timing too many passes: "+passNumber);
            names[passNumber] = name;
            return passNumber;  // overwrite last slot instead of overflowing
        }
        else {
            names[passNumber+1] = name;
            return passNumber++;
        }
    }

    public int getStartIndex(){
        return 2 * passNumber;
    }

    public int getStopIndex(){
        return 2* passNumber + 1;
    }

    /** number of passes in this frame so far. */
    public int getNumPasses(){
        return numPasses;
    }

    public void resolveTimeStamps(WebGPUCommandEncoder encoder){
        // reset pass counter as we are at the end of the frame
        numPasses = passNumber+1;
        passNumber = -1;

        if( !enabled || timeStampMapOngoing)
            return;

        // Resolve the timestamp queries (write their result to the resolve buffer)
        webGPU.wgpuCommandEncoderResolveQuerySet(encoder.getHandle(), timestampQuerySet, 0, 2*numPasses, timeStampResolveBuffer, 0);

        // Copy to the map buffer
        webGPU.wgpuCommandEncoderCopyBufferToBuffer(encoder.getHandle(), timeStampResolveBuffer, 0,  timeStampMapBuffer, 0,8*2*numPasses);


    }



    // a lambda expression to define a callback function
    WGPUBufferMapCallback onTimestampBufferMapped = (WGPUBufferMapAsyncStatus status, Pointer userData) -> {
        if(status == WGPUBufferMapAsyncStatus.Success) {
            Pointer ram =  webGPU.wgpuBufferGetConstMappedRange(timeStampMapBuffer, 0, 8*2*MAX_PASSES);
            for(int pass = 0; pass < numPasses; pass++) {
                long start = ram.getLong(8L *2*pass);
                long end = ram.getLong(8L *2*pass + 8);
                long ns = end - start;
                addTimeSample(pass, ns);
            }
            webGPU.wgpuBufferUnmap(timeStampMapBuffer);

        }
        timeStampMapOngoing = false;
    };

    public void fetchTimestamps(){
        if( !enabled || timeStampMapOngoing)
            return;

        timeStampMapOngoing = true;
        webGPU.wgpuBufferMapAsync(timeStampMapBuffer, WGPUMapMode.Read, 0, 8*2*MAX_PASSES, onTimestampBufferMapped, null);
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

    private final String[] names = new String[MAX_PASSES];
    private final long[] cumulative = new long[MAX_PASSES];
    private final int[] numSamples = new int[MAX_PASSES];

    private void addTimeSample(int pass, long ns){
        numSamples[pass]++;
        cumulative[pass] += ns;
    }

    // returns average time per frame spent by GPU (in microseconds).
    public float getAverageGPUtime(int pass){
        if(numSamples[pass] == 0)
            return 0;
        float avg = (float) (cumulative[pass]/1000) / (float)numSamples[pass];  // average time converted from nano to microseconds
        resetGPUsamples(pass);
        return avg;
    }

    public String getPassName(int pass){
        return names[pass];
    }


    public void resetGPUsamples(int pass){
        numSamples[pass] = 0;
        cumulative[pass] = 0L;
    }


}

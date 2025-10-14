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
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


/** Class to facilitate GPU benchmarking of render passes.
 */

public class GPUTimer implements Disposable {
    public final static int MAX_PASSES = 20;   // max number of passes that we can support

    private boolean enabled;
    private WGPUQuerySet timestampQuerySet;
    private WGPUBuffer timeStampResolveBuffer;
    private WGPUBuffer timeStampMapBuffer;
    private boolean timeStampMapOngoing = false;
    private int passNumber;
    private int numPasses;
    private ByteBuffer ram;
    private WGPUBufferMapCallback callback;

    public GPUTimer(WGPUDevice device, boolean enabled) {
        this.enabled = enabled;
        if(!enabled)
            return;

        // Create timestamp queries
        WGPUQuerySetDescriptor querySetDescriptor =  WGPUQuerySetDescriptor.obtain();
        querySetDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        querySetDescriptor.setLabel("Timestamp Query Set");
        querySetDescriptor.setType(WGPUQueryType.Timestamp);
        querySetDescriptor.setCount(2*MAX_PASSES); // start and end time

        timestampQuerySet = new WGPUQuerySet();
        device.createQuerySet(querySetDescriptor, timestampQuerySet);

        // Create buffer
        WGPUBufferDescriptor bufferDesc = WGPUBufferDescriptor.obtain();
        bufferDesc.setLabel("timestamp resolve buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopySrc.or(WGPUBufferUsage.QueryResolve) );
        bufferDesc.setSize(8*2*MAX_PASSES);     // space for 2 uint64's per pass
        bufferDesc.setMappedAtCreation(false);
        timeStampResolveBuffer = device.createBuffer(bufferDesc);

        bufferDesc.setLabel("timestamp map buffer");
        bufferDesc.setUsage( WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead) );
        bufferDesc.setSize(8*2*MAX_PASSES);
        timeStampMapBuffer = device.createBuffer(bufferDesc);

        passNumber = -1;

        ram = BufferUtils.newUnsafeByteBuffer(8 * 2 * MAX_PASSES);
        ram.order(ByteOrder.LITTLE_ENDIAN);
    }


    public boolean isEnabled(){
        return enabled;
    }

    public boolean setEnabled(boolean enabled){
        boolean old = this.enabled;
        this.enabled = enabled;
        return old;
    }

    /** get a query set that can be made into a query for renderPassDescriptor.setTimestampWrites(query);
     *  Will be null if timing is not enabled. */
    public WGPUQuerySet getQuerySet(){
        return timestampQuerySet;
    }

    public int addPass(String name){
        if(!enabled)
            return 0;
        if(passNumber >= MAX_PASSES-1) {
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

    public void resolveTimeStamps(WGPUCommandEncoder encoder){
        // reset pass counter as we are at the end of the frame
        numPasses = passNumber+1;
        passNumber = -1;

        if( !enabled || timeStampMapOngoing)
            return;

        // Resolve the timestamp queries (write their result to the resolve buffer)
        encoder.resolveQuerySet(timestampQuerySet, 0, 2*numPasses, timeStampResolveBuffer, 0);

        // Copy to the map buffer
        encoder.copyBufferToBuffer(timeStampResolveBuffer, 0,  timeStampMapBuffer, 0,8*2*numPasses);
    }

    public void fetchTimestamps(){
        if( !enabled || timeStampMapOngoing)
            return;

        if(callback == null) {
            callback = new WGPUBufferMapCallback() {
                @Override
                protected void onCallback(WGPUMapAsyncStatus status, String message) {

                    if(status == WGPUMapAsyncStatus.Success) {
                        ram.position(0);
                        timeStampMapBuffer.getConstMappedRange(0, 8 * 2 * MAX_PASSES, ram);
                        for (int pass = 0; pass < numPasses; pass++) {
                            long start = ram.getLong();
                            long end = ram.getLong();
                            long nanoseconds = end - start;
                            addTimeSample(pass, nanoseconds);
                        }
                        timeStampMapBuffer.unmap();
                    }
                    timeStampMapOngoing = false;
                }
            };
        }

        timeStampMapOngoing = true;
        timeStampMapBuffer.mapAsync(WGPUMapMode.Read, 0, 8 * 2 * MAX_PASSES, WGPUCallbackMode.AllowProcessEvents, callback);
    }

    @Override
    public void dispose() {
        if(!enabled)
            return;
        //System.out.println("GPUTimer.dispose()");
//        while(timeStampMapOngoing) {
//            System.out.println("Waiting for time stamp map before disposing... ");
//            // todo device.tick();
//        }

        BufferUtils.disposeUnsafeByteBuffer(ram);

        timestampQuerySet.destroy();

        timeStampMapBuffer.destroy();
        timeStampResolveBuffer.destroy();
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

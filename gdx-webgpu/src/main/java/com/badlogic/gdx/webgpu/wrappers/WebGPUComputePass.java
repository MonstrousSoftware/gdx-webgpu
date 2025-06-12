package com.badlogic.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.WGPUComputePassDescriptor;
import com.badlogic.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.utils.Disposable;
import jnr.ffi.Pointer;

public class WebGPUComputePass implements Disposable {
    private final WebGPU_JNI webGPU;
    private final Pointer computePass;

    public WebGPUComputePass(WebGPUCommandEncoder commandEncoder) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        // Create a compute pass
        WGPUComputePassDescriptor passDesc = WGPUComputePassDescriptor.createDirect();
        passDesc.setNextInChain();
        passDesc.setTimestampWrites();
        computePass = webGPU.wgpuCommandEncoderBeginComputePass(commandEncoder.getHandle(), passDesc);
    }

    public void setBindGroup(int groupId, WebGPUBindGroup bindGroup) {
        webGPU.wgpuComputePassEncoderSetBindGroup(computePass, groupId, bindGroup.getHandle(), 0, JavaWebGPU.createNullPointer());
    }

    public void setPipeline(Pointer pipeline) {
        webGPU.wgpuComputePassEncoderSetPipeline(computePass, pipeline);
    }

    public void dispatchWorkGroups(int workgroupCountX, int workgroupCountY, int workgroupCountZ) {
        webGPU.wgpuComputePassEncoderDispatchWorkgroups(computePass,workgroupCountX,workgroupCountY,workgroupCountZ);
    }

    public void end(){
        webGPU.wgpuComputePassEncoderEnd(computePass);
    }


    public Pointer getHandle(){
        return computePass;
    }

    @Override
    public void dispose() {
        //webGPU.wgpuComputePassEncoderRelease(computePass);  // this causes crash
    }
}

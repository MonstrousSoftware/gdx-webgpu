package com.badlogic.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

import static com.badlogic.gdx.webgpu.utils.JavaWebGPU.createIntegerArrayPointer;


public class WebGPUDevice implements Disposable {
    private final Pointer device;
    private final WebGPU_JNI webGPU;
    private final WGPUSupportedLimits supportedLimits;

    public WebGPUDevice(WebGPUAdapter adapter, boolean gpuTimingEnabled) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase) Gdx.graphics;
        webGPU = gfx.getWebGPU();

        // set required limits for device
        // todo these values are rather random
        WGPURequiredLimits requiredLimits = WGPURequiredLimits.createDirect();
        setDefault(requiredLimits.getLimits());
        requiredLimits.getLimits().setMaxVertexAttributes(8);
        requiredLimits.getLimits().setMaxVertexBuffers(2);
        requiredLimits.getLimits().setMaxInterStageShaderComponents(20); //

        // from vert to frag
        requiredLimits.getLimits().setMaxBufferSize(300);
        requiredLimits.getLimits().setMaxVertexBufferArrayStride(11*Float.BYTES);
        requiredLimits.getLimits().setMaxDynamicUniformBuffersPerPipelineLayout(1);
        requiredLimits.getLimits().setMaxTextureDimension1D(2048);
        requiredLimits.getLimits().setMaxTextureDimension2D(2048);
        requiredLimits.getLimits().setMaxTextureArrayLayers(6);
        requiredLimits.getLimits().setMaxSampledTexturesPerShaderStage(1);
        requiredLimits.getLimits().setMaxSamplersPerShaderStage(1);

        requiredLimits.getLimits().setMaxBindGroups(4);
        requiredLimits.getLimits().setMaxUniformBuffersPerShaderStage(4);// We use at most 1 uniform buffer per stage
        requiredLimits.getLimits().setMaxUniformBufferBindingSize(16*4*Float.BYTES);

        // Get Device
        WGPUDeviceDescriptor deviceDescriptor = WGPUDeviceDescriptor.createDirect();
        deviceDescriptor.setNextInChain();
        deviceDescriptor.setLabel("My Device");
        deviceDescriptor.setRequiredLimits(requiredLimits);


        // required feature to do timestamp queries
        if(gpuTimingEnabled){
            int[] featureValues = new int[1];
            featureValues[0] = WGPUFeatureName.TimestampQuery;
            Pointer requiredFeatures = createIntegerArrayPointer(featureValues);

            deviceDescriptor.setRequiredFeatureCount(1);
            deviceDescriptor.setRequiredFeatures( requiredFeatures );
        } else {
            deviceDescriptor.setRequiredFeatureCount(0);
            deviceDescriptor.setRequiredFeatures(null);
        }

        device = getDeviceSync(adapter, deviceDescriptor);

        // use a lambda expression to define a callback function
        WGPUErrorCallback deviceCallback = (WGPUErrorType type, String message, Pointer userdata) -> {
            System.out.println("*** Device error: " + type + " : " + message);
            System.exit(-1);
        };
        webGPU.wgpuDeviceSetUncapturedErrorCallback(device, deviceCallback, null);

        // Collect the device limits which may be more constrained than the adapter limits
        // e.g. getMinUniformBufferOffsetAlignment maybe becomes 256 on the device instead of 64 on the adapter.
        supportedLimits = WGPUSupportedLimits.createDirect();
        webGPU.wgpuDeviceGetLimits(device, supportedLimits);
        System.out.println("device maxVertexAttributes " + supportedLimits.getLimits().getMaxVertexAttributes());

        System.out.println("maxTextureDimension1D " + supportedLimits.getLimits().getMaxTextureDimension1D());
        System.out.println("maxTextureDimension2D " + supportedLimits.getLimits().getMaxTextureDimension2D());
        System.out.println("maxTextureDimension3D " + supportedLimits.getLimits().getMaxTextureDimension3D());
        System.out.println("maxTextureArrayLayers " + supportedLimits.getLimits().getMaxTextureArrayLayers());


        if (gpuTimingEnabled && !webGPU.wgpuDeviceHasFeature(device, WGPUFeatureName.TimestampQuery)) {
            System.out.println("** Requested timestamp queries are not supported!");
        }
    }

    public WGPUSupportedLimits getSupportedLimits(){
        return supportedLimits;
    }

    private Pointer getDeviceSync(WebGPUAdapter adapter, WGPUDeviceDescriptor deviceDescriptor){

        Pointer userBuf = JavaWebGPU.createLongArrayPointer(new long[1]);
        WGPURequestDeviceCallback callback = (WGPURequestDeviceStatus status, Pointer device, String message, Pointer userdata) -> {
            if(status == WGPURequestDeviceStatus.Success)
                userdata.putPointer(0, device);
            else
                System.out.println("Could not get device: "+message);
        };
        webGPU.wgpuAdapterRequestDevice(adapter.getHandle(), deviceDescriptor, callback, userBuf);
        // on native implementations, we don't have to wait for asynchronous operation. It returns result immediately.
        return  userBuf.getPointer(0);
    }

    public Pointer getHandle(){
        return device;
    }

    public void tick() {
        webGPU.wgpuDeviceTick(device);
    }

    @Override
    public void dispose() {
        webGPU.wgpuDeviceRelease(device);
    }

    final static long WGPU_LIMIT_U32_UNDEFINED = -1L;
    final static long WGPU_LIMIT_U64_UNDEFINED = -1L;//.   18446744073709551615L;
    // should be 18446744073709551615L but Java longs are signed so it is half that, will it work?

    public void setDefault(WGPULimits limits) {
        limits.setMaxTextureDimension1D(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxTextureDimension2D(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxTextureDimension3D(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxTextureArrayLayers(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxBindGroups(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxBindGroupsPlusVertexBuffers(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxBindingsPerBindGroup(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxDynamicUniformBuffersPerPipelineLayout(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxDynamicStorageBuffersPerPipelineLayout(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxSampledTexturesPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxSamplersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxStorageBuffersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxStorageTexturesPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxUniformBuffersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxUniformBufferBindingSize(WGPU_LIMIT_U64_UNDEFINED);
        limits.setMaxStorageBufferBindingSize(WGPU_LIMIT_U64_UNDEFINED);
        limits.setMinUniformBufferOffsetAlignment(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMinStorageBufferOffsetAlignment(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxVertexBuffers(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxBufferSize(WGPU_LIMIT_U64_UNDEFINED);
        limits.setMaxVertexAttributes(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxVertexBufferArrayStride(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxInterStageShaderComponents(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxInterStageShaderVariables(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxColorAttachments(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxColorAttachmentBytesPerSample(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeWorkgroupStorageSize(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeInvocationsPerWorkgroup(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeWorkgroupSizeX(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeWorkgroupSizeY(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeWorkgroupSizeZ(WGPU_LIMIT_U32_UNDEFINED);
        limits.setMaxComputeWorkgroupsPerDimension(WGPU_LIMIT_U32_UNDEFINED);
    }
}

package com.monstrous.gdx.webgpu.application;

import com.github.xpenatan.webgpu.WGPUAdapter;
import com.github.xpenatan.webgpu.WGPUBackendType;
import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUDevice;
import com.github.xpenatan.webgpu.WGPUDeviceDescriptor;
import com.github.xpenatan.webgpu.WGPUErrorType;
import com.github.xpenatan.webgpu.WGPUFeatureName;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPULimits;
import com.github.xpenatan.webgpu.WGPUPowerPreference;
import com.github.xpenatan.webgpu.WGPURequestAdapterCallback;
import com.github.xpenatan.webgpu.WGPURequestAdapterOptions;
import com.github.xpenatan.webgpu.WGPURequestAdapterStatus;
import com.github.xpenatan.webgpu.WGPURequestDeviceCallback;
import com.github.xpenatan.webgpu.WGPURequestDeviceStatus;
import com.github.xpenatan.webgpu.WGPUUncapturedErrorCallback;
import com.github.xpenatan.webgpu.WGPUVectorFeatureName;

public class WebGPUInitialization {

    private final static int WGPU_LIMIT_U32_UNDEFINED = -1;
    private final static int WGPU_LIMIT_U64_UNDEFINED = -1;

    private WebGPUInitialization() {}

    public static void setup(WGPUInstance instance, WGPUPowerPreference powerPreference, WGPUBackendType backendType, OnSetupCallback initCallback) {
        if(!instance.isValid()) {
            initCallback.onInit(WebGPUInitState.INSTANCE_NOT_VALID, null, null);
            return;
        }

        WGPURequestAdapterOptions op = WGPURequestAdapterOptions.obtain();
        op.setPowerPreference(powerPreference);
        op.setBackendType(backendType);
        WGPURequestAdapterCallback callback = new WGPURequestAdapterCallback() {
            @Override
            protected void onCallback(WGPURequestAdapterStatus status, WGPUAdapter adapter, String message) {
                if (status == WGPURequestAdapterStatus.Success) {
                    onRequestDeviceSuccess(adapter, initCallback);
                } else {
                    initCallback.onInit(WebGPUInitState.ADAPTER_NOT_VALID, null, null);
                }
            }
        };
        instance.requestAdapter(op, WGPUCallbackMode.AllowProcessEvents, callback);
    }

    private static void onRequestDeviceSuccess(WGPUAdapter adapter, OnSetupCallback initCallback) {
        // WGPUAdapterInfo info = WGPUAdapterInfo.obtain();
        // if(adapter.getInfo(info)) {
        // WGPUBackendType backendType = info.getBackendType();
        // System.out.println("BackendType: " + backendType);
        // WGPUAdapterType adapterType = info.getAdapterType();
        // System.out.println("AdapterType: " + adapterType);
        // String vendor = info.getVendor().c_str();
        // System.out.println("Vendor: " + vendor);
        // String architecture = info.getArchitecture().c_str();
        // System.out.println("Architecture: " + architecture);
        // String description = info.getDescription().c_str();
        // System.out.println("Description: " + description);
        // String device = info.getDevice().c_str();
        // System.out.println("Device: " + device);
        // //System.out.println("Has Feature DepthClipControl: " +
        // adapter.hasFeature(WGPUFeatureName.DepthClipControl));
        // }

        WGPUDeviceDescriptor deviceDescriptor = WGPUDeviceDescriptor.obtain();
        WGPULimits limits = WGPULimits.obtain();
        setDefaultLimits(limits);
        deviceDescriptor.setRequiredLimits(limits);
        deviceDescriptor.setLabel("My Device");

        WGPUVectorFeatureName features = WGPUVectorFeatureName.obtain();
        features.push_back(WGPUFeatureName.DepthClipControl);
        features.push_back(WGPUFeatureName.TimestampQuery);
        deviceDescriptor.setRequiredFeatures(features);

        deviceDescriptor.getDefaultQueue().setLabel("The default queue");

        adapter.requestDevice(deviceDescriptor, WGPUCallbackMode.AllowProcessEvents, new WGPURequestDeviceCallback() {
            @Override
            protected void onCallback(WGPURequestDeviceStatus status, WGPUDevice device, String message) {
                WebGPUInitState initState;
                if (status == WGPURequestDeviceStatus.Success) {
                    initState = WebGPUInitState.DEVICE_VALID;
                } else {
                    initState = WebGPUInitState.DEVICE_NOT_VALID;
                }
                initCallback.onInit(initState, adapter, device);
            }
        }, new WGPUUncapturedErrorCallback() {
            @Override
            protected void onCallback(WGPUErrorType errorType, String message) {
                initCallback.onError(errorType, message);
            }
        });
    }

    private static void setDefaultLimits(WGPULimits limits) {
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
        // limits.setMaxInterStageShaderComponents(WGPU_LIMIT_U32_UNDEFINED);
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

    public interface OnSetupCallback {
        void onInit(WebGPUInitState initState, WGPUAdapter adapter, WGPUDevice device);
        void onError(WGPUErrorType errorType, String message);
    }
}

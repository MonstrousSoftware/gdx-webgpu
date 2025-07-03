package com.github.xpenatan.webgpu.demo.app;

import com.github.xpenatan.webgpu.*;

public class WGPUApp {
    public int width;
    public int height;
    public WebGPUInstance instance;
    public WebGPUAdapter adapter;
    public WebGPUDevice device;
    public WebGPUSurface surface;
    public WebGPUQueue queue;

    private InitState initState = InitState.NOT_INITIALIZED;

    public void init() {
        WebGPUInstance instance = WGPU.createInstance();
        if(instance.isValid()) {
            initState = InitState.INSTANCE_VALID;
            this.instance = instance;
            requestAdapter();
        }
        else {
            initState = InitState.INSTANCE_NOT_VALID;
            instance.dispose();
        }
    }

    private void requestAdapter() {
        WebGPURequestAdapterOptions op = WebGPURequestAdapterOptions.obtain();
        WGPUBackendType backend = WGPUBackendType.Vulkan;
        op.setBackendType(backend);
        RequestAdapterCallback callback = new RequestAdapterCallback() {
            @Override
            protected void onCallback(WGPURequestAdapterStatus status, WebGPUAdapter adapter) {
                System.out.println("Adapter Status: " + status);
                if(status == WGPURequestAdapterStatus.Success) {
                    initState = InitState.ADAPTER_VALID;
                    WGPUApp.this.adapter = adapter;
                    requestDevice();
                }
                else {
                    initState = InitState.ADAPTER_NOT_VALID;
                }
            }
        };
        instance.requestAdapter(op, WGPUCallbackMode.AllowProcessEvents, callback);
    }

    private void requestDevice() {
        WebGPUAdapterInfo info = WebGPUAdapterInfo.obtain();
        if(adapter.getInfo(info)) {
            WGPUBackendType backendType = info.getBackendType();
            System.out.println("BackendType: " + backendType);
            WGPUAdapterType adapterType = info.getAdapterType();
            System.out.println("AdapterType: " + adapterType);
            String vendor = info.getVendor().c_str();
            System.out.println("Vendor: " + vendor);
            String architecture = info.getArchitecture().c_str();
            System.out.println("Architecture: " + architecture);
            String description = info.getDescription().c_str();
            System.out.println("Description: " + description);
            String device = info.getDevice().c_str();
            System.out.println("Device: " + device);
            System.out.println("Has Feature DepthClipControl: " + adapter.hasFeature(WGPUFeatureName.DepthClipControl));
        }

        WebGPUDeviceDescriptor deviceDescriptor = WebGPUDeviceDescriptor.obtain();
        WebGPULimits limits = WebGPULimits.obtain();
        setDefaultLimits(limits);
        deviceDescriptor.setRequiredLimits(limits);
        deviceDescriptor.setLabel("My Device");

        WGPUVectorFeatureName features = WGPUVectorFeatureName.obtain();
        features.push_back(WGPUFeatureName.DepthClipControl);
        deviceDescriptor.setRequiredFeatures(features);

        deviceDescriptor.getDefaultQueue().setLabel("The default queue");

        adapter.requestDevice(deviceDescriptor, WGPUCallbackMode.AllowProcessEvents, new RequestDeviceCallback() {
            @Override
            protected void onCallback(WGPURequestDeviceStatus status, WebGPUDevice device) {
                System.out.println("Device Status: " + status);
                if(status == WGPURequestDeviceStatus.Success) {
                    initState = InitState.DEVICE_VALID;
                    WGPUApp.this.device = device;
                    queue = device.getQueue();
                    System.out.println("Platform: " + WGPU.getPlatformType());

                    WebGPUSupportedFeatures features = WebGPUSupportedFeatures.obtain();
                    device.getFeatures(features);
                    int featureCount = features.getFeatureCount();
                    System.out.println("Total Features: " + featureCount);
                    for(int i = 0; i < featureCount; i++) {
                        WGPUFeatureName featureName = features.getFeatureAt(i);
                        System.out.println("Feature name: " + featureName);
                    }
                    features.dispose();

                    WebGPULimits limits = WebGPULimits.obtain();
                    device.getLimits(limits);
                    System.out.println("Device limits: " + featureCount);
                    System.out.println("MaxTextureDimension1D: " + limits.getMaxTextureDimension1D());
                    System.out.println("MaxTextureDimension2D: " + limits.getMaxTextureDimension2D());
                    System.out.println("MaxTextureDimension3D: " + limits.getMaxTextureDimension3D());
                    System.out.println("MaxTextureArrayLayers: " + limits.getMaxTextureArrayLayers());
                }
                else {
                    initState = InitState.DEVICE_NOT_VALID;
                }
            }
        }, new UncapturedErrorCallback() {
            @Override
            protected void onCallback(WGPUErrorType errorType, String message) {
                System.err.println("ErrorType: " + errorType);
                System.err.println("Error Message: " + message);
                initState = InitState.ERROR;
            }
        });
    }

    public void update() {
        if(instance != null) {
            instance.processEvents();
        }
        if(initState == InitState.ERROR) {
            throw new RuntimeException("WebGPU Error");
        }
    }

    public boolean isReady() {
        return initState == InitState.DEVICE_VALID;
    }

    public boolean isNotSupport() {
        return initState.status < 0;
    }

    final static int WGPU_LIMIT_U32_UNDEFINED = -1;
    final static int WGPU_LIMIT_U64_UNDEFINED = -1;

    public void setDefaultLimits (WebGPULimits limits) {
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
//        limits.setMaxInterStageShaderComponents(WGPU_LIMIT_U32_UNDEFINED);
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

    enum InitState {
        NOT_INITIALIZED(0),
        ERROR(1),
        INSTANCE_VALID(2),
        ADAPTER_VALID(3),
        DEVICE_VALID(4),
        INSTANCE_NOT_VALID(-1),
        ADAPTER_NOT_VALID(-2),
        DEVICE_NOT_VALID(-3);

        int status;

        InitState(int status) {
            this.status = status;
        }
    }
}

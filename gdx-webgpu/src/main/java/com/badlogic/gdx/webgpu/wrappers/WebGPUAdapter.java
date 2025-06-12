package com.badlogic.gdx.webgpu.wrappers;


import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

public class WebGPUAdapter implements Disposable {
    private final WebGPU_JNI webGPU;
    private Pointer adapter;
    private final WGPUSupportedLimits supportedLimits;

    public WebGPUAdapter(Pointer instance, Pointer surface, WGPUBackendType backendType, WGPUPowerPreference powerPreference) {
        WebGPUGraphicsBase gfx = (WebGPUGraphicsBase)Gdx.graphics;
        webGPU = gfx.getWebGPU();

        WGPURequestAdapterOptions options = WGPURequestAdapterOptions.createDirect();
        options.setNextInChain();
        options.setCompatibleSurface(surface);
        options.setBackendType(backendType);
        options.setPowerPreference(powerPreference);

        if(backendType == WGPUBackendType.Null)
            throw new IllegalStateException("Request Adapter: Back end 'Null' only valid if config.noWindow is true");

        // Get Adapter
        adapter = getAdapterSync(instance, options);
        if(adapter == null){
            System.out.println("Configured adapter back end ("+ gfx.getRequestedBackendType()+") not available, requesting fallback");
            options.setBackendType(WGPUBackendType.Undefined);
            options.setPowerPreference(powerPreference);
            adapter = getAdapterSync(instance, options);
        }

        supportedLimits = WGPUSupportedLimits.createDirect();
        gfx.getWebGPU().wgpuAdapterGetLimits(adapter, supportedLimits);
//        System.out.println("adapter maxVertexAttributes " + supportedLimits.getLimits().getMaxVertexAttributes());
//        System.out.println("adapter maxBindGroups " + supportedLimits.getLimits().getMaxBindGroups());
//
//        System.out.println("maxTextureDimension1D " + supportedLimits.getLimits().getMaxTextureDimension1D());
//        System.out.println("maxTextureDimension2D " + supportedLimits.getLimits().getMaxTextureDimension2D());
//        System.out.println("maxTextureDimension3D " + supportedLimits.getLimits().getMaxTextureDimension3D());
//        System.out.println("maxTextureArrayLayers " + supportedLimits.getLimits().getMaxTextureArrayLayers());


        WGPUAdapterProperties adapterProperties = WGPUAdapterProperties.createDirect();
        adapterProperties.setNextInChain();

        webGPU.wgpuAdapterGetProperties(adapter, adapterProperties);
        System.out.println("VendorID: " + adapterProperties.getVendorID());
        System.out.println("Vendor name: " + adapterProperties.getVendorName());
        System.out.println("Device ID: " + adapterProperties.getDeviceID());
        System.out.println("Back end: " + adapterProperties.getBackendType());
        System.out.println("Description: " + adapterProperties.getDriverDescription());
    }

    public WGPUSupportedLimits getSupportedLimits(){
        return supportedLimits;
    }

    public Pointer getHandle(){
        return adapter;
    }

    @Override
    public void dispose() {
        webGPU.wgpuAdapterRelease(adapter);       // we can release our adapter as soon as we have a device
    }

    private Pointer getAdapterSync(Pointer instance, WGPURequestAdapterOptions options){

        Pointer userBuf = JavaWebGPU.createLongArrayPointer(new long[1]);
        userBuf.putPointer(0, null);

        WGPURequestAdapterCallback callback = (WGPURequestAdapterStatus status, Pointer adapter, String message, Pointer userdata) -> {
            if(status == WGPURequestAdapterStatus.Success)
                userdata.putPointer(0, adapter);
            else
                System.out.println("Could not get adapter: "+message);
        };
        webGPU.wgpuInstanceRequestAdapter(instance, options, callback, userBuf);
        // on native implementations, we don't have to wait for asynchronous operation. It returns result immediately.
        return  userBuf.getPointer(0);
    }
}

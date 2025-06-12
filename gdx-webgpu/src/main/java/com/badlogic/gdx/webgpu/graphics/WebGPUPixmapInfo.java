package com.badlogic.gdx.webgpu.graphics;



import com.badlogic.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;


public class WebGPUPixmapInfo extends WgpuJavaStruct {

    public final Struct.Unsigned32 width = new Struct.Unsigned32();
    public final Struct.Unsigned32 height = new Struct.Unsigned32();
    public final Struct.Unsigned32 format = new Struct.Unsigned32();
    public final Struct.Unsigned32 blend = new Struct.Unsigned32();
    public final Struct.Unsigned32 scale = new Struct.Unsigned32();
    public final Struct.Pointer pixels = new Struct.Pointer();

    private WebGPUPixmapInfo(){}

    @Deprecated
    public WebGPUPixmapInfo(Runtime runtime){
        super(runtime);
    }

    public static WebGPUPixmapInfo createAt(jnr.ffi.Pointer address){
        WebGPUPixmapInfo struct = new WebGPUPixmapInfo();
        struct.useMemory(address);
        return struct;
    }
}
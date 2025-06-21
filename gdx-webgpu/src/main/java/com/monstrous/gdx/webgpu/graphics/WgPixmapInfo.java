package com.monstrous.gdx.webgpu.graphics;



import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;


public class WgPixmapInfo extends WgpuJavaStruct {

    public final Struct.Unsigned32 width = new Struct.Unsigned32();
    public final Struct.Unsigned32 height = new Struct.Unsigned32();
    public final Struct.Unsigned32 format = new Struct.Unsigned32();
    public final Struct.Unsigned32 blend = new Struct.Unsigned32();
    public final Struct.Unsigned32 scale = new Struct.Unsigned32();
    public final Struct.Pointer pixels = new Struct.Pointer();

    private WgPixmapInfo(){}

    @Deprecated
    public WgPixmapInfo(Runtime runtime){
        super(runtime);
    }

    public static WgPixmapInfo createAt(jnr.ffi.Pointer address){
        WgPixmapInfo struct = new WgPixmapInfo();
        struct.useMemory(address);
        return struct;
    }
}

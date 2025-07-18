
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUAdapterPropertiesD3D extends WgpuJavaStruct {

	private final WGPUChainedStructOut chain = inner(WGPUChainedStructOut.createHeap());
	private final Unsigned32 shaderModel = new Unsigned32();

	private WGPUAdapterPropertiesD3D () {
	}

	@Deprecated
	public WGPUAdapterPropertiesD3D (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUAdapterPropertiesD3D createHeap () {
		return new WGPUAdapterPropertiesD3D();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUAdapterPropertiesD3D createDirect () {
		WGPUAdapterPropertiesD3D struct = new WGPUAdapterPropertiesD3D();
		struct.useDirectMemory();
		return struct;
	}

	public WGPUChainedStructOut getChain () {
		return chain;
	}

	public long getShaderModel () {
		return shaderModel.get();
	}

	public WGPUAdapterPropertiesD3D setShaderModel (long val) {
		this.shaderModel.set(val);
		return this;
	}

}

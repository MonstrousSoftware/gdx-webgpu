
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUDawnAdapterPropertiesPowerPreference extends WgpuJavaStruct {

	private final WGPUChainedStructOut chain = inner(WGPUChainedStructOut.createHeap());
	private final Enum<WGPUPowerPreference> powerPreference = new Enum<>(WGPUPowerPreference.class);

	private WGPUDawnAdapterPropertiesPowerPreference () {
	}

	@Deprecated
	public WGPUDawnAdapterPropertiesPowerPreference (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUDawnAdapterPropertiesPowerPreference createHeap () {
		return new WGPUDawnAdapterPropertiesPowerPreference();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUDawnAdapterPropertiesPowerPreference createDirect () {
		WGPUDawnAdapterPropertiesPowerPreference struct = new WGPUDawnAdapterPropertiesPowerPreference();
		struct.useDirectMemory();
		return struct;
	}

	public WGPUChainedStructOut getChain () {
		return chain;
	}

	public WGPUPowerPreference getPowerPreference () {
		return powerPreference.get();
	}

	public WGPUDawnAdapterPropertiesPowerPreference setPowerPreference (WGPUPowerPreference val) {
		this.powerPreference.set(val);
		return this;
	}

}

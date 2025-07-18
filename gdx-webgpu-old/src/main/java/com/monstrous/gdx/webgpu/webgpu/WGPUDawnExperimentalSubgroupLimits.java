
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUDawnExperimentalSubgroupLimits extends WgpuJavaStruct {

	private final WGPUChainedStructOut chain = inner(WGPUChainedStructOut.createHeap());
	private final Unsigned32 minSubgroupSize = new Unsigned32();
	private final Unsigned32 maxSubgroupSize = new Unsigned32();

	private WGPUDawnExperimentalSubgroupLimits () {
	}

	@Deprecated
	public WGPUDawnExperimentalSubgroupLimits (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUDawnExperimentalSubgroupLimits createHeap () {
		return new WGPUDawnExperimentalSubgroupLimits();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUDawnExperimentalSubgroupLimits createDirect () {
		WGPUDawnExperimentalSubgroupLimits struct = new WGPUDawnExperimentalSubgroupLimits();
		struct.useDirectMemory();
		return struct;
	}

	public WGPUChainedStructOut getChain () {
		return chain;
	}

	public long getMinSubgroupSize () {
		return minSubgroupSize.get();
	}

	public WGPUDawnExperimentalSubgroupLimits setMinSubgroupSize (long val) {
		this.minSubgroupSize.set(val);
		return this;
	}

	public long getMaxSubgroupSize () {
		return maxSubgroupSize.get();
	}

	public WGPUDawnExperimentalSubgroupLimits setMaxSubgroupSize (long val) {
		this.maxSubgroupSize.set(val);
		return this;
	}

}

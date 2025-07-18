
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUDepthStencilStateDepthWriteDefinedDawn extends WgpuJavaStruct {

	private final WGPUChainedStruct chain = inner(WGPUChainedStruct.createHeap());
	private final Unsigned32 depthWriteDefined = new Unsigned32();

	private WGPUDepthStencilStateDepthWriteDefinedDawn () {
	}

	@Deprecated
	public WGPUDepthStencilStateDepthWriteDefinedDawn (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUDepthStencilStateDepthWriteDefinedDawn createHeap () {
		return new WGPUDepthStencilStateDepthWriteDefinedDawn();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUDepthStencilStateDepthWriteDefinedDawn createDirect () {
		WGPUDepthStencilStateDepthWriteDefinedDawn struct = new WGPUDepthStencilStateDepthWriteDefinedDawn();
		struct.useDirectMemory();
		return struct;
	}

	public WGPUChainedStruct getChain () {
		return chain;
	}

	public long getDepthWriteDefined () {
		return depthWriteDefined.get();
	}

	public WGPUDepthStencilStateDepthWriteDefinedDawn setDepthWriteDefined (long val) {
		this.depthWriteDefined.set(val);
		return this;
	}

}

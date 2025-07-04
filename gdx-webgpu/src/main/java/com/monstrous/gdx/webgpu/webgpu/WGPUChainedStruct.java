
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUChainedStruct extends WgpuJavaStruct {

	private final DynamicStructRef<WGPUChainedStruct> next = new DynamicStructRef<>(WGPUChainedStruct.class);
	private final Unsigned32 sType = new Unsigned32();

	private WGPUChainedStruct () {
	}

	@Deprecated
	public WGPUChainedStruct (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUChainedStruct createHeap () {
		return new WGPUChainedStruct();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUChainedStruct createDirect () {
		WGPUChainedStruct struct = new WGPUChainedStruct();
		struct.useDirectMemory();
		return struct;
	}

	public DynamicStructRef<WGPUChainedStruct> getNext () {
		return next;
	}

	public WGPUChainedStruct setNext (WGPUChainedStruct... x) {
		if (x.length == 0 || x[0] == null) {
			this.next.set(JavaWebGPU.createNullPointer());
		} else {
			this.next.set(x);
		}
		return this;
	}

	public long getSType () {
		return sType.get();
	}

	public WGPUChainedStruct setSType (long val) {
		this.sType.set(val);
		return this;
	}

}

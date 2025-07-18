
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUSharedBufferMemoryProperties extends WgpuJavaStruct {

	private final DynamicStructRef<WGPUChainedStructOut> nextInChain = new DynamicStructRef<>(WGPUChainedStructOut.class);
	private final Unsigned32 usage = new Unsigned32();
	private final Unsigned64 size = new Unsigned64();

	private WGPUSharedBufferMemoryProperties () {
	}

	@Deprecated
	public WGPUSharedBufferMemoryProperties (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUSharedBufferMemoryProperties createHeap () {
		return new WGPUSharedBufferMemoryProperties();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUSharedBufferMemoryProperties createDirect () {
		WGPUSharedBufferMemoryProperties struct = new WGPUSharedBufferMemoryProperties();
		struct.useDirectMemory();
		return struct;
	}

	public DynamicStructRef<WGPUChainedStructOut> getNextInChain () {
		return nextInChain;
	}

	public WGPUSharedBufferMemoryProperties setNextInChain (WGPUChainedStructOut... x) {
		if (x.length == 0 || x[0] == null) {
			this.nextInChain.set(JavaWebGPU.createNullPointer());
		} else {
			this.nextInChain.set(x);
		}
		return this;
	}

	public long getUsage () {
		return usage.get();
	}

	public WGPUSharedBufferMemoryProperties setUsage (long val) {
		this.usage.set(val);
		return this;
	}

	public @jnr.ffi.types.u_int64_t long getSize () {
		return size.get();
	}

	public WGPUSharedBufferMemoryProperties setSize (@jnr.ffi.types.u_int64_t long val) {
		this.size.set(val);
		return this;
	}

}

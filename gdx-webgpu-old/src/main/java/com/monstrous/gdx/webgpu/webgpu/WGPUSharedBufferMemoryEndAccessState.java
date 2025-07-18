
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUSharedBufferMemoryEndAccessState extends WgpuJavaStruct {

	private final DynamicStructRef<WGPUChainedStructOut> nextInChain = new DynamicStructRef<>(WGPUChainedStructOut.class);
	private final Unsigned32 initialized = new Unsigned32();
	private final size_t fenceCount = new size_t();
	private final Pointer fences = new Pointer();
	private final Pointer signaledValues = new Pointer();

	private WGPUSharedBufferMemoryEndAccessState () {
	}

	@Deprecated
	public WGPUSharedBufferMemoryEndAccessState (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUSharedBufferMemoryEndAccessState createHeap () {
		return new WGPUSharedBufferMemoryEndAccessState();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUSharedBufferMemoryEndAccessState createDirect () {
		WGPUSharedBufferMemoryEndAccessState struct = new WGPUSharedBufferMemoryEndAccessState();
		struct.useDirectMemory();
		return struct;
	}

	public DynamicStructRef<WGPUChainedStructOut> getNextInChain () {
		return nextInChain;
	}

	public WGPUSharedBufferMemoryEndAccessState setNextInChain (WGPUChainedStructOut... x) {
		if (x.length == 0 || x[0] == null) {
			this.nextInChain.set(JavaWebGPU.createNullPointer());
		} else {
			this.nextInChain.set(x);
		}
		return this;
	}

	public long getInitialized () {
		return initialized.get();
	}

	public WGPUSharedBufferMemoryEndAccessState setInitialized (long val) {
		this.initialized.set(val);
		return this;
	}

	public long getFenceCount () {
		return fenceCount.get();
	}

	public WGPUSharedBufferMemoryEndAccessState setFenceCount (long val) {
		this.fenceCount.set(val);
		return this;
	}

	public jnr.ffi.Pointer getFences () {
		return fences.get();
	}

	public WGPUSharedBufferMemoryEndAccessState setFences (jnr.ffi.Pointer val) {
		this.fences.set(val);
		return this;
	}

	public jnr.ffi.Pointer getSignaledValues () {
		return signaledValues.get();
	}

	public WGPUSharedBufferMemoryEndAccessState setSignaledValues (jnr.ffi.Pointer val) {
		this.signaledValues.set(val);
		return this;
	}

}

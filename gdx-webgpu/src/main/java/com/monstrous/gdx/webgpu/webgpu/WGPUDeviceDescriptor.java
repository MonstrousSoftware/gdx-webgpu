
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.CStrPointer;
import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.utils.RustCString;
import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import org.jetbrains.annotations.Nullable;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUDeviceDescriptor extends WgpuJavaStruct {

	private final DynamicStructRef<WGPUChainedStruct> nextInChain = new DynamicStructRef<>(WGPUChainedStruct.class);
	private final @Nullable @CStrPointer Struct.Pointer label = new Pointer();
	private final size_t requiredFeatureCount = new size_t();
	private final Pointer requiredFeatures = new Pointer();
	private final @Nullable DynamicStructRef<WGPURequiredLimits> requiredLimits = new DynamicStructRef<>(WGPURequiredLimits.class);
	private final WGPUQueueDescriptor defaultQueue = inner(WGPUQueueDescriptor.createHeap());
	private final Pointer deviceLostCallback = new Pointer();
	private final Pointer deviceLostUserdata = new Pointer();
	private final WGPUDeviceLostCallbackInfo deviceLostCallbackInfo = inner(WGPUDeviceLostCallbackInfo.createHeap());
	private final WGPUUncapturedErrorCallbackInfo uncapturedErrorCallbackInfo = inner(
		WGPUUncapturedErrorCallbackInfo.createHeap());

	private WGPUDeviceDescriptor () {
	}

	@Deprecated
	public WGPUDeviceDescriptor (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUDeviceDescriptor createHeap () {
		return new WGPUDeviceDescriptor();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUDeviceDescriptor createDirect () {
		WGPUDeviceDescriptor struct = new WGPUDeviceDescriptor();
		struct.useDirectMemory();
		return struct;
	}

	public DynamicStructRef<WGPUChainedStruct> getNextInChain () {
		return nextInChain;
	}

	public WGPUDeviceDescriptor setNextInChain (WGPUChainedStruct... x) {
		if (x.length == 0 || x[0] == null) {
			this.nextInChain.set(JavaWebGPU.createNullPointer());
		} else {
			this.nextInChain.set(x);
		}
		return this;
	}

	public java.lang.String getLabel () {
		return RustCString.fromPointer(label.get());
	}

	public WGPUDeviceDescriptor setLabel (java.lang.String str) {
		this.label.set(RustCString.toPointer(str));
		return this;
	}

	public long getRequiredFeatureCount () {
		return requiredFeatureCount.get();
	}

	public WGPUDeviceDescriptor setRequiredFeatureCount (long val) {
		this.requiredFeatureCount.set(val);
		return this;
	}

	public jnr.ffi.Pointer getRequiredFeatures () {
		return requiredFeatures.get();
	}

	public WGPUDeviceDescriptor setRequiredFeatures (jnr.ffi.Pointer val) {
		this.requiredFeatures.set(val);
		return this;
	}

	public DynamicStructRef<WGPURequiredLimits> getRequiredLimits () {
		return requiredLimits;
	}

	public WGPUDeviceDescriptor setRequiredLimits (WGPURequiredLimits... x) {
		if (x.length == 0 || x[0] == null) {
			this.requiredLimits.set(JavaWebGPU.createNullPointer());
		} else {
			this.requiredLimits.set(x);
		}
		return this;
	}

	public WGPUQueueDescriptor getDefaultQueue () {
		return defaultQueue;
	}

	public jnr.ffi.Pointer getDeviceLostCallback () {
		return deviceLostCallback.get();
	}

	public WGPUDeviceDescriptor setDeviceLostCallback (jnr.ffi.Pointer val) {
		this.deviceLostCallback.set(val);
		return this;
	}

	public jnr.ffi.Pointer getDeviceLostUserdata () {
		return deviceLostUserdata.get();
	}

	public WGPUDeviceDescriptor setDeviceLostUserdata (jnr.ffi.Pointer val) {
		this.deviceLostUserdata.set(val);
		return this;
	}

	public WGPUDeviceLostCallbackInfo getDeviceLostCallbackInfo () {
		return deviceLostCallbackInfo;
	}

	public WGPUUncapturedErrorCallbackInfo getUncapturedErrorCallbackInfo () {
		return uncapturedErrorCallbackInfo;
	}

}

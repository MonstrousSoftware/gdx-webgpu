
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;
import jnr.ffi.Struct;
import org.jetbrains.annotations.Nullable;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPUCopyTextureForBrowserOptions extends WgpuJavaStruct {

	private final DynamicStructRef<WGPUChainedStruct> nextInChain = new DynamicStructRef<>(WGPUChainedStruct.class);
	private final Unsigned32 flipY = new Unsigned32();
	private final Unsigned32 needsColorSpaceConversion = new Unsigned32();
	private final Enum<WGPUAlphaMode> srcAlphaMode = new Enum<>(WGPUAlphaMode.class);
	private final @Nullable Struct.Pointer srcTransferFunctionParameters = new Pointer();
	private final @Nullable Struct.Pointer conversionMatrix = new Pointer();
	private final @Nullable Struct.Pointer dstTransferFunctionParameters = new Pointer();
	private final Enum<WGPUAlphaMode> dstAlphaMode = new Enum<>(WGPUAlphaMode.class);
	private final Unsigned32 internalUsage = new Unsigned32();

	private WGPUCopyTextureForBrowserOptions () {
	}

	@Deprecated
	public WGPUCopyTextureForBrowserOptions (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPUCopyTextureForBrowserOptions createHeap () {
		return new WGPUCopyTextureForBrowserOptions();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPUCopyTextureForBrowserOptions createDirect () {
		WGPUCopyTextureForBrowserOptions struct = new WGPUCopyTextureForBrowserOptions();
		struct.useDirectMemory();
		return struct;
	}

	public DynamicStructRef<WGPUChainedStruct> getNextInChain () {
		return nextInChain;
	}

	public WGPUCopyTextureForBrowserOptions setNextInChain (WGPUChainedStruct... x) {
		if (x.length == 0 || x[0] == null) {
			this.nextInChain.set(JavaWebGPU.createNullPointer());
		} else {
			this.nextInChain.set(x);
		}
		return this;
	}

	public long getFlipY () {
		return flipY.get();
	}

	public WGPUCopyTextureForBrowserOptions setFlipY (long val) {
		this.flipY.set(val);
		return this;
	}

	public long getNeedsColorSpaceConversion () {
		return needsColorSpaceConversion.get();
	}

	public WGPUCopyTextureForBrowserOptions setNeedsColorSpaceConversion (long val) {
		this.needsColorSpaceConversion.set(val);
		return this;
	}

	public WGPUAlphaMode getSrcAlphaMode () {
		return srcAlphaMode.get();
	}

	public WGPUCopyTextureForBrowserOptions setSrcAlphaMode (WGPUAlphaMode val) {
		this.srcAlphaMode.set(val);
		return this;
	}

	public jnr.ffi.Pointer getSrcTransferFunctionParameters () {
		return srcTransferFunctionParameters.get();
	}

	public WGPUCopyTextureForBrowserOptions setSrcTransferFunctionParameters (jnr.ffi.Pointer val) {
		this.srcTransferFunctionParameters.set(val);
		return this;
	}

	public jnr.ffi.Pointer getConversionMatrix () {
		return conversionMatrix.get();
	}

	public WGPUCopyTextureForBrowserOptions setConversionMatrix (jnr.ffi.Pointer val) {
		this.conversionMatrix.set(val);
		return this;
	}

	public jnr.ffi.Pointer getDstTransferFunctionParameters () {
		return dstTransferFunctionParameters.get();
	}

	public WGPUCopyTextureForBrowserOptions setDstTransferFunctionParameters (jnr.ffi.Pointer val) {
		this.dstTransferFunctionParameters.set(val);
		return this;
	}

	public WGPUAlphaMode getDstAlphaMode () {
		return dstAlphaMode.get();
	}

	public WGPUCopyTextureForBrowserOptions setDstAlphaMode (WGPUAlphaMode val) {
		this.dstAlphaMode.set(val);
		return this;
	}

	public long getInternalUsage () {
		return internalUsage.get();
	}

	public WGPUCopyTextureForBrowserOptions setInternalUsage (long val) {
		this.internalUsage.set(val);
		return this;
	}

}

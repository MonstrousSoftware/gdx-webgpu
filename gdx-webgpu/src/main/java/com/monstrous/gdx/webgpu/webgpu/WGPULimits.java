
package com.monstrous.gdx.webgpu.webgpu;

import com.monstrous.gdx.webgpu.utils.WgpuJavaStruct;
import jnr.ffi.Runtime;

/** NOTE: THIS FILE WAS PRE-GENERATED BY JNR_GEN! */
public class WGPULimits extends WgpuJavaStruct {

	private final Unsigned32 maxTextureDimension1D = new Unsigned32();
	private final Unsigned32 maxTextureDimension2D = new Unsigned32();
	private final Unsigned32 maxTextureDimension3D = new Unsigned32();
	private final Unsigned32 maxTextureArrayLayers = new Unsigned32();
	private final Unsigned32 maxBindGroups = new Unsigned32();
	private final Unsigned32 maxBindGroupsPlusVertexBuffers = new Unsigned32();
	private final Unsigned32 maxBindingsPerBindGroup = new Unsigned32();
	private final Unsigned32 maxDynamicUniformBuffersPerPipelineLayout = new Unsigned32();
	private final Unsigned32 maxDynamicStorageBuffersPerPipelineLayout = new Unsigned32();
	private final Unsigned32 maxSampledTexturesPerShaderStage = new Unsigned32();
	private final Unsigned32 maxSamplersPerShaderStage = new Unsigned32();
	private final Unsigned32 maxStorageBuffersPerShaderStage = new Unsigned32();
	private final Unsigned32 maxStorageTexturesPerShaderStage = new Unsigned32();
	private final Unsigned32 maxUniformBuffersPerShaderStage = new Unsigned32();
	private final Unsigned64 maxUniformBufferBindingSize = new Unsigned64();
	private final Unsigned64 maxStorageBufferBindingSize = new Unsigned64();
	private final Unsigned32 minUniformBufferOffsetAlignment = new Unsigned32();
	private final Unsigned32 minStorageBufferOffsetAlignment = new Unsigned32();
	private final Unsigned32 maxVertexBuffers = new Unsigned32();
	private final Unsigned64 maxBufferSize = new Unsigned64();
	private final Unsigned32 maxVertexAttributes = new Unsigned32();
	private final Unsigned32 maxVertexBufferArrayStride = new Unsigned32();
	private final Unsigned32 maxInterStageShaderComponents = new Unsigned32();
	private final Unsigned32 maxInterStageShaderVariables = new Unsigned32();
	private final Unsigned32 maxColorAttachments = new Unsigned32();
	private final Unsigned32 maxColorAttachmentBytesPerSample = new Unsigned32();
	private final Unsigned32 maxComputeWorkgroupStorageSize = new Unsigned32();
	private final Unsigned32 maxComputeInvocationsPerWorkgroup = new Unsigned32();
	private final Unsigned32 maxComputeWorkgroupSizeX = new Unsigned32();
	private final Unsigned32 maxComputeWorkgroupSizeY = new Unsigned32();
	private final Unsigned32 maxComputeWorkgroupSizeZ = new Unsigned32();
	private final Unsigned32 maxComputeWorkgroupsPerDimension = new Unsigned32();

	private WGPULimits () {
	}

	@Deprecated
	public WGPULimits (Runtime runtime) {
		super(runtime);
	}

	/** Creates this struct on the java heap. In general, this should <b>not</b> be used because these structs cannot be directly
	 * passed into native code. */
	public static WGPULimits createHeap () {
		return new WGPULimits();
	}

	/** Creates this struct in direct memory. This is how most structs should be created (unless, they are members of a nothing
	 * struct)
	 *
	 * @see WgpuJavaStruct#useDirectMemory */
	public static WGPULimits createDirect () {
		WGPULimits struct = new WGPULimits();
		struct.useDirectMemory();
		return struct;
	}

	public long getMaxTextureDimension1D () {
		return maxTextureDimension1D.get();
	}

	public WGPULimits setMaxTextureDimension1D (long val) {
		this.maxTextureDimension1D.set(val);
		return this;
	}

	public long getMaxTextureDimension2D () {
		return maxTextureDimension2D.get();
	}

	public WGPULimits setMaxTextureDimension2D (long val) {
		this.maxTextureDimension2D.set(val);
		return this;
	}

	public long getMaxTextureDimension3D () {
		return maxTextureDimension3D.get();
	}

	public WGPULimits setMaxTextureDimension3D (long val) {
		this.maxTextureDimension3D.set(val);
		return this;
	}

	public long getMaxTextureArrayLayers () {
		return maxTextureArrayLayers.get();
	}

	public WGPULimits setMaxTextureArrayLayers (long val) {
		this.maxTextureArrayLayers.set(val);
		return this;
	}

	public long getMaxBindGroups () {
		return maxBindGroups.get();
	}

	public WGPULimits setMaxBindGroups (long val) {
		this.maxBindGroups.set(val);
		return this;
	}

	public long getMaxBindGroupsPlusVertexBuffers () {
		return maxBindGroupsPlusVertexBuffers.get();
	}

	public WGPULimits setMaxBindGroupsPlusVertexBuffers (long val) {
		this.maxBindGroupsPlusVertexBuffers.set(val);
		return this;
	}

	public long getMaxBindingsPerBindGroup () {
		return maxBindingsPerBindGroup.get();
	}

	public WGPULimits setMaxBindingsPerBindGroup (long val) {
		this.maxBindingsPerBindGroup.set(val);
		return this;
	}

	public long getMaxDynamicUniformBuffersPerPipelineLayout () {
		return maxDynamicUniformBuffersPerPipelineLayout.get();
	}

	public WGPULimits setMaxDynamicUniformBuffersPerPipelineLayout (long val) {
		this.maxDynamicUniformBuffersPerPipelineLayout.set(val);
		return this;
	}

	public long getMaxDynamicStorageBuffersPerPipelineLayout () {
		return maxDynamicStorageBuffersPerPipelineLayout.get();
	}

	public WGPULimits setMaxDynamicStorageBuffersPerPipelineLayout (long val) {
		this.maxDynamicStorageBuffersPerPipelineLayout.set(val);
		return this;
	}

	public long getMaxSampledTexturesPerShaderStage () {
		return maxSampledTexturesPerShaderStage.get();
	}

	public WGPULimits setMaxSampledTexturesPerShaderStage (long val) {
		this.maxSampledTexturesPerShaderStage.set(val);
		return this;
	}

	public long getMaxSamplersPerShaderStage () {
		return maxSamplersPerShaderStage.get();
	}

	public WGPULimits setMaxSamplersPerShaderStage (long val) {
		this.maxSamplersPerShaderStage.set(val);
		return this;
	}

	public long getMaxStorageBuffersPerShaderStage () {
		return maxStorageBuffersPerShaderStage.get();
	}

	public WGPULimits setMaxStorageBuffersPerShaderStage (long val) {
		this.maxStorageBuffersPerShaderStage.set(val);
		return this;
	}

	public long getMaxStorageTexturesPerShaderStage () {
		return maxStorageTexturesPerShaderStage.get();
	}

	public WGPULimits setMaxStorageTexturesPerShaderStage (long val) {
		this.maxStorageTexturesPerShaderStage.set(val);
		return this;
	}

	public long getMaxUniformBuffersPerShaderStage () {
		return maxUniformBuffersPerShaderStage.get();
	}

	public WGPULimits setMaxUniformBuffersPerShaderStage (long val) {
		this.maxUniformBuffersPerShaderStage.set(val);
		return this;
	}

	public @jnr.ffi.types.u_int64_t long getMaxUniformBufferBindingSize () {
		return maxUniformBufferBindingSize.get();
	}

	public WGPULimits setMaxUniformBufferBindingSize (@jnr.ffi.types.u_int64_t long val) {
		this.maxUniformBufferBindingSize.set(val);
		return this;
	}

	public @jnr.ffi.types.u_int64_t long getMaxStorageBufferBindingSize () {
		return maxStorageBufferBindingSize.get();
	}

	public WGPULimits setMaxStorageBufferBindingSize (@jnr.ffi.types.u_int64_t long val) {
		this.maxStorageBufferBindingSize.set(val);
		return this;
	}

	public long getMinUniformBufferOffsetAlignment () {
		return minUniformBufferOffsetAlignment.get();
	}

	public WGPULimits setMinUniformBufferOffsetAlignment (long val) {
		this.minUniformBufferOffsetAlignment.set(val);
		return this;
	}

	public long getMinStorageBufferOffsetAlignment () {
		return minStorageBufferOffsetAlignment.get();
	}

	public WGPULimits setMinStorageBufferOffsetAlignment (long val) {
		this.minStorageBufferOffsetAlignment.set(val);
		return this;
	}

	public long getMaxVertexBuffers () {
		return maxVertexBuffers.get();
	}

	public WGPULimits setMaxVertexBuffers (long val) {
		this.maxVertexBuffers.set(val);
		return this;
	}

	public @jnr.ffi.types.u_int64_t long getMaxBufferSize () {
		return maxBufferSize.get();
	}

	public WGPULimits setMaxBufferSize (@jnr.ffi.types.u_int64_t long val) {
		this.maxBufferSize.set(val);
		return this;
	}

	public long getMaxVertexAttributes () {
		return maxVertexAttributes.get();
	}

	public WGPULimits setMaxVertexAttributes (long val) {
		this.maxVertexAttributes.set(val);
		return this;
	}

	public long getMaxVertexBufferArrayStride () {
		return maxVertexBufferArrayStride.get();
	}

	public WGPULimits setMaxVertexBufferArrayStride (long val) {
		this.maxVertexBufferArrayStride.set(val);
		return this;
	}

	public long getMaxInterStageShaderComponents () {
		return maxInterStageShaderComponents.get();
	}

	public WGPULimits setMaxInterStageShaderComponents (long val) {
		this.maxInterStageShaderComponents.set(val);
		return this;
	}

	public long getMaxInterStageShaderVariables () {
		return maxInterStageShaderVariables.get();
	}

	public WGPULimits setMaxInterStageShaderVariables (long val) {
		this.maxInterStageShaderVariables.set(val);
		return this;
	}

	public long getMaxColorAttachments () {
		return maxColorAttachments.get();
	}

	public WGPULimits setMaxColorAttachments (long val) {
		this.maxColorAttachments.set(val);
		return this;
	}

	public long getMaxColorAttachmentBytesPerSample () {
		return maxColorAttachmentBytesPerSample.get();
	}

	public WGPULimits setMaxColorAttachmentBytesPerSample (long val) {
		this.maxColorAttachmentBytesPerSample.set(val);
		return this;
	}

	public long getMaxComputeWorkgroupStorageSize () {
		return maxComputeWorkgroupStorageSize.get();
	}

	public WGPULimits setMaxComputeWorkgroupStorageSize (long val) {
		this.maxComputeWorkgroupStorageSize.set(val);
		return this;
	}

	public long getMaxComputeInvocationsPerWorkgroup () {
		return maxComputeInvocationsPerWorkgroup.get();
	}

	public WGPULimits setMaxComputeInvocationsPerWorkgroup (long val) {
		this.maxComputeInvocationsPerWorkgroup.set(val);
		return this;
	}

	public long getMaxComputeWorkgroupSizeX () {
		return maxComputeWorkgroupSizeX.get();
	}

	public WGPULimits setMaxComputeWorkgroupSizeX (long val) {
		this.maxComputeWorkgroupSizeX.set(val);
		return this;
	}

	public long getMaxComputeWorkgroupSizeY () {
		return maxComputeWorkgroupSizeY.get();
	}

	public WGPULimits setMaxComputeWorkgroupSizeY (long val) {
		this.maxComputeWorkgroupSizeY.set(val);
		return this;
	}

	public long getMaxComputeWorkgroupSizeZ () {
		return maxComputeWorkgroupSizeZ.get();
	}

	public WGPULimits setMaxComputeWorkgroupSizeZ (long val) {
		this.maxComputeWorkgroupSizeZ.set(val);
		return this;
	}

	public long getMaxComputeWorkgroupsPerDimension () {
		return maxComputeWorkgroupsPerDimension.get();
	}

	public WGPULimits setMaxComputeWorkgroupsPerDimension (long val) {
		this.maxComputeWorkgroupsPerDimension.set(val);
		return this;
	}

}

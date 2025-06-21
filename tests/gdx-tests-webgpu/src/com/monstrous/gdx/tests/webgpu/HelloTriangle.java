/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.tests.webgpu;


import com.monstrous.gdx.webgpu.utils.JavaWebGPU;
import com.monstrous.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;
import org.lwjgl.Version;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;

// Render a triangle in a window
//
// Following the tutorial "WebGPU in C++" https://eliemichel.github.io/LearnWebGPU/
//
// This uses direct webgpu functions and does not make use of e.g. WebGPUApplication.
// It is an example of low-level coding against the WebGPU API.

public class HelloTriangle {
	public final static int WIDTH = 640;
	public final static int HEIGHT = 480;

	private final WGPUBackendType backend = WGPUBackendType.Undefined;// .D3D12; // or Vulkan, etc.
	private final boolean vsyncEnabled = true;

	private WebGPU_JNI webGPU;
	private Pointer surface;
	private WGPUTextureFormat surfaceFormat;
	private Pointer device;
	private Pointer queue;
	private Pointer pipeline;

	// The window handle
	private long window;
	private long windowHandle;
	private double currentTime;

	public static void main (String[] args) {
		new HelloTriangle();
	}

	public HelloTriangle () {

		// create a window
		openWindow(WIDTH, HEIGHT, "WebGPU: Hello Triangle!");

		initWebGPU(getWindowHandle());

		// render loop
		while (!getShouldClose()) {

			renderFrame();

			webGPU.wgpuDeviceTick(device);

			// Poll for window events.
			pollEvents();
		}

		exitWebGPU();
		closeWindow();
	}

	private void renderFrame () {

		Pointer targetView = getNextSurfaceTextureView();
		if (targetView.address() == 0) {
			System.out.println("*** Invalid target view");
			return;
		}

		Pointer commandEncoder = prepareEncoder();
		Pointer renderPass = prepareRenderPass(commandEncoder, targetView);

		render(renderPass); // do some rendering in this render pass

		webGPU.wgpuRenderPassEncoderEnd(renderPass);
		webGPU.wgpuRenderPassEncoderRelease(renderPass);

		finishEncoder(commandEncoder);

		// At the end of the frame
		webGPU.wgpuTextureViewRelease(targetView);
		webGPU.wgpuSurfacePresent(surface);
	}

	private void render (Pointer renderPass) {
		// Select which render pipeline to use
		webGPU.wgpuRenderPassEncoderSetPipeline(renderPass, pipeline);

		// Draw 1 instance of a 3-vertices shape
		webGPU.wgpuRenderPassEncoderDraw(renderPass, 3, 1, 0, 0);
	}

	private void initWebGPU (long windowHandle) {
		webGPU = JavaWebGPU.init();

		Pointer instance = webGPU.wgpuCreateInstance(null);

		surface = JavaWebGPU.getUtils().glfwGetWGPUSurface(instance, windowHandle);

		device = initDevice(instance);

		webGPU.wgpuInstanceRelease(instance); // we can release the instance now that we have the device

		queue = webGPU.wgpuDeviceGetQueue(device);

		initSwapChain(WIDTH, HEIGHT);
		pipeline = initPipeline();
	}

	private Pointer getAdapterSync (Pointer instance, WGPURequestAdapterOptions options) {

		Pointer userBuf = JavaWebGPU.createLongArrayPointer(new long[1]);
		WGPURequestAdapterCallback callback = (WGPURequestAdapterStatus status, Pointer adapter, String message,
			Pointer userdata) -> {
			if (status == WGPURequestAdapterStatus.Success)
				userdata.putPointer(0, adapter);
			else
				System.out.println("Could not get adapter: " + message);
		};
		webGPU.wgpuInstanceRequestAdapter(instance, options, callback, userBuf);
		// on native implementations, we don't have to wait for asynchronous operation. It returns result immediately.
		return userBuf.getPointer(0);
	}

	private Pointer getDeviceSync (Pointer adapter, WGPUDeviceDescriptor deviceDescriptor) {

		Pointer userBuf = JavaWebGPU.createLongArrayPointer(new long[1]);
		WGPURequestDeviceCallback callback = (WGPURequestDeviceStatus status, Pointer device, String message, Pointer userdata) -> {
			if (status == WGPURequestDeviceStatus.Success)
				userdata.putPointer(0, device);
			else
				System.out.println("Could not get device: " + message);
		};
		webGPU.wgpuAdapterRequestDevice(adapter, deviceDescriptor, callback, userBuf);
		// on native implementations, we don't have to wait for asynchronous operation. It returns result immediately.
		return userBuf.getPointer(0);
	}

	private Pointer initDevice (Pointer instance) {

		// Select an Adapter
		//
		WGPURequestAdapterOptions options = WGPURequestAdapterOptions.createDirect();
		options.setNextInChain();
		options.setCompatibleSurface(surface);
		options.setBackendType(backend);
		options.setPowerPreference(WGPUPowerPreference.HighPerformance);

		// Get Adapter

		Pointer adapter = getAdapterSync(instance, options);

		// Get Adapter properties out of interest
		WGPUAdapterProperties adapterProperties = WGPUAdapterProperties.createDirect();
		adapterProperties.setNextInChain();

		webGPU.wgpuAdapterGetProperties(adapter, adapterProperties);

		System.out.println("VendorID: " + adapterProperties.getVendorID());
		System.out.println("Vendor name: " + adapterProperties.getVendorName());
		System.out.println("Device ID: " + adapterProperties.getDeviceID());
		System.out.println("Back end: " + adapterProperties.getBackendType());
		System.out.println("Description: " + adapterProperties.getDriverDescription());

		WGPURequiredLimits requiredLimits = WGPURequiredLimits.createDirect();
		setDefaultLimits(requiredLimits.getLimits());

		// Get a Device
		//
		WGPUDeviceDescriptor deviceDescriptor = WGPUDeviceDescriptor.createDirect();
		deviceDescriptor.setNextInChain();
		deviceDescriptor.setLabel("My Device");
		deviceDescriptor.setRequiredLimits(requiredLimits);
		deviceDescriptor.setRequiredFeatureCount(0);
		deviceDescriptor.setRequiredFeatures(JavaWebGPU.createNullPointer());

		Pointer device = getDeviceSync(adapter, deviceDescriptor);

		// use a lambda expression to define a callback function
		WGPUErrorCallback deviceCallback = (WGPUErrorType type, String message, Pointer userdata) -> {
			System.out.println("*** Device error: " + type + " : " + message);
			System.exit(-1);
		};
		webGPU.wgpuDeviceSetUncapturedErrorCallback(device, deviceCallback, null);

		// Find out the preferred surface format of the window
		WGPUSurfaceCapabilities caps = WGPUSurfaceCapabilities.createDirect();
		webGPU.wgpuSurfaceGetCapabilities(surface, adapter, caps);
		Pointer formats = caps.getFormats();
		int format = formats.getInt(0);
		surfaceFormat = WGPUTextureFormat.values()[format];

		webGPU.wgpuAdapterRelease(adapter); // we can release our adapter as soon as we have a device
		return device;
	}

	private void initSwapChain (int width, int height) {
		// configure the surface
		WGPUSurfaceConfiguration config = WGPUSurfaceConfiguration.createDirect();
		config.setNextInChain().setWidth(width).setHeight(height).setFormat(surfaceFormat).setViewFormatCount(0)
			.setViewFormats(JavaWebGPU.createNullPointer()).setUsage(WGPUTextureUsage.RenderAttachment).setDevice(device)
			.setPresentMode(vsyncEnabled ? WGPUPresentMode.Fifo : WGPUPresentMode.Immediate)
			.setAlphaMode(WGPUCompositeAlphaMode.Auto);

		webGPU.wgpuSurfaceConfigure(surface, config);
	}

	private Pointer getNextSurfaceTextureView () {
		// [...] Get the next surface texture
		WGPUSurfaceTexture surfaceTexture = WGPUSurfaceTexture.createDirect();
		webGPU.wgpuSurfaceGetCurrentTexture(surface, surfaceTexture);
		// System.out.println("get current texture: "+surfaceTexture.status.get());
		if (surfaceTexture.getStatus() != WGPUSurfaceGetCurrentTextureStatus.Success) {
			System.out.println("*** No current texture");
			return JavaWebGPU.createNullPointer();
		}
		// [...] Create surface texture view
		WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.createDirect();
		viewDescriptor.setNextInChain();
		viewDescriptor.setLabel("Surface texture view");
		Pointer tex = surfaceTexture.getTexture();
		WGPUTextureFormat format = webGPU.wgpuTextureGetFormat(tex);
		// System.out.println("Set format "+format);
		viewDescriptor.setFormat(format);
		viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
		viewDescriptor.setBaseMipLevel(0);
		viewDescriptor.setMipLevelCount(1);
		viewDescriptor.setBaseArrayLayer(0);
		viewDescriptor.setArrayLayerCount(1);
		viewDescriptor.setAspect(WGPUTextureAspect.All);
		Pointer view = webGPU.wgpuTextureCreateView(surfaceTexture.getTexture(), viewDescriptor);

		// we can release the texture now as the texture view now has its own reference to it
		webGPU.wgpuTextureRelease(surfaceTexture.getTexture());
		return view;
	}

	private Pointer prepareEncoder () {
		WGPUCommandEncoderDescriptor encoderDescriptor = WGPUCommandEncoderDescriptor.createDirect();
		encoderDescriptor.setNextInChain();
		encoderDescriptor.setLabel("My Encoder");

		return webGPU.wgpuDeviceCreateCommandEncoder(device, encoderDescriptor);
	}

	private void exitWebGPU () {
		webGPU.wgpuRenderPipelineRelease(pipeline);
		webGPU.wgpuSurfaceUnconfigure(surface);
		webGPU.wgpuQueueRelease(queue);
		webGPU.wgpuDeviceRelease(device);
		webGPU.wgpuSurfaceRelease(surface);

	}

	private Pointer prepareRenderPass (Pointer encoder, Pointer targetView) {

		WGPURenderPassColorAttachment renderPassColorAttachment = WGPURenderPassColorAttachment.createDirect();
		renderPassColorAttachment.setNextInChain();
		renderPassColorAttachment.setView(targetView);
		renderPassColorAttachment.setResolveTarget(JavaWebGPU.createNullPointer());
		renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
		renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);

		// background colour
		renderPassColorAttachment.getClearValue().setR(0.9);
		renderPassColorAttachment.getClearValue().setG(0.1);
		renderPassColorAttachment.getClearValue().setB(0.2);
		renderPassColorAttachment.getClearValue().setA(1.0);

		renderPassColorAttachment.setDepthSlice(-1L); // undefined

		WGPURenderPassDescriptor renderPassDescriptor = WGPURenderPassDescriptor.createDirect();
		renderPassDescriptor.setNextInChain();

		renderPassDescriptor.setLabel("Main Render Pass");

		renderPassDescriptor.setColorAttachmentCount(1);
		renderPassDescriptor.setColorAttachments(renderPassColorAttachment);
		renderPassDescriptor.setOcclusionQuerySet(JavaWebGPU.createNullPointer());
		renderPassDescriptor.setDepthStencilAttachment(); // no depth buffer or stencil buffer

		return webGPU.wgpuCommandEncoderBeginRenderPass(encoder, renderPassDescriptor);
	}

	private void finishEncoder (Pointer encoder) {
		// Finish the command encoder, which gives us the command buffer
		WGPUCommandBufferDescriptor bufferDescriptor = WGPUCommandBufferDescriptor.createDirect();
		bufferDescriptor.setNextInChain();
		bufferDescriptor.setLabel("Command Buffer");
		Pointer commandBuffer = webGPU.wgpuCommandEncoderFinish(encoder, bufferDescriptor);

		// Release the command encoder
		webGPU.wgpuCommandEncoderRelease(encoder);

		// Submit the command buffer to the queue
		Pointer bufferPtr = JavaWebGPU.createLongArrayPointer(new long[] {commandBuffer.address()});
		webGPU.wgpuQueueSubmit(queue, 1, bufferPtr);

		// Now we can release the command buffer
		webGPU.wgpuCommandBufferRelease(commandBuffer);
	}

	private Pointer initPipeline () {

		Pointer shaderModule = makeShaderModule();

		WGPURenderPipelineDescriptor pipelineDesc = WGPURenderPipelineDescriptor.createDirect();
		pipelineDesc.setNextInChain();
		pipelineDesc.setLabel("my pipeline");

		pipelineDesc.getVertex().setBufferCount(0); // no vertex buffer, because we define it in the shader
		pipelineDesc.getVertex().setBuffers();

		pipelineDesc.getVertex().setModule(shaderModule);
		pipelineDesc.getVertex().setEntryPoint("vs_main");
		pipelineDesc.getVertex().setConstantCount(0);
		pipelineDesc.getVertex().setConstants();

		pipelineDesc.getPrimitive().setTopology(WGPUPrimitiveTopology.TriangleList);
		pipelineDesc.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Undefined);
		pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
		pipelineDesc.getPrimitive().setCullMode(WGPUCullMode.None);

		WGPUFragmentState fragmentState = WGPUFragmentState.createDirect();
		fragmentState.setNextInChain();
		fragmentState.setModule(shaderModule);
		fragmentState.setEntryPoint("fs_main");
		fragmentState.setConstantCount(0);
		fragmentState.setConstants();

		// blending
		WGPUBlendState blendState = WGPUBlendState.createDirect();
		blendState.getColor().setSrcFactor(WGPUBlendFactor.SrcAlpha);
		blendState.getColor().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
		blendState.getColor().setOperation(WGPUBlendOperation.Add);
		blendState.getAlpha().setSrcFactor(WGPUBlendFactor.One);
		blendState.getAlpha().setDstFactor(WGPUBlendFactor.Zero);
		blendState.getAlpha().setOperation(WGPUBlendOperation.Add);

		WGPUColorTargetState colorTarget = WGPUColorTargetState.createDirect();

		colorTarget.setFormat(surfaceFormat); // match output surface
		colorTarget.setBlend(blendState);
		colorTarget.setWriteMask(WGPUColorWriteMask.All);

		fragmentState.setTargetCount(1);
		fragmentState.setTargets(colorTarget);

		pipelineDesc.setFragment(fragmentState);

		pipelineDesc.setDepthStencil(); // no depth or stencil buffer

		pipelineDesc.getMultisample().setCount(1);
		pipelineDesc.getMultisample().setMask(-1L);
		pipelineDesc.getMultisample().setAlphaToCoverageEnabled(0);

		pipelineDesc.setLayout(JavaWebGPU.createNullPointer());
		pipeline = webGPU.wgpuDeviceCreateRenderPipeline(device, pipelineDesc);
		if (pipeline.address() == 0) throw new RuntimeException("Pipeline creation failed");

		// We no longer need to access the shader module
		webGPU.wgpuShaderModuleRelease(shaderModule);
		return pipeline;
	}

	private String readShaderSource () {

		return "// triangleShader.wgsl\n" + "\n" + "@vertex\n"
			+ "fn vs_main(@builtin(vertex_index) in_vertex_index: u32) -> @builtin(position) vec4f {\n"
			+ "    var p = vec2f(0.0, 0.0);\n" + "    if (in_vertex_index == 0u) {\n" + "        p = vec2f(-0.5, -0.5);\n"
			+ "    } else if (in_vertex_index == 1u) {\n" + "        p = vec2f(0.5, -0.5);\n" + "    } else {\n"
			+ "        p = vec2f(0.0, 0.5);\n" + "    }\n" + "    return vec4f(p, 0.0, 1.0);\n" + "}\n" + "\n" + "@fragment\n"
			+ "fn fs_main() -> @location(0) vec4f {\n" + "    return vec4f(0.0, 0.4, 1.0, 1.0);\n" + "}";
	}

	private Pointer makeShaderModule () {

		String shaderSource = readShaderSource();

		// Create Shader Module
		WGPUShaderModuleDescriptor shaderDesc = WGPUShaderModuleDescriptor.createDirect();
		shaderDesc.setLabel("triangle shader");

		WGPUShaderModuleWGSLDescriptor shaderCodeDesc = WGPUShaderModuleWGSLDescriptor.createDirect();
		shaderCodeDesc.getChain().setNext();
		shaderCodeDesc.getChain().setSType(WGPUSType.ShaderModuleWGSLDescriptor);
		shaderCodeDesc.setCode(shaderSource);

		shaderDesc.getNextInChain().set(shaderCodeDesc.getPointerTo());

		Pointer shaderModule = webGPU.wgpuDeviceCreateShaderModule(device, shaderDesc);
		if (shaderModule.address() == 0) throw new RuntimeException("ShaderModule: compile failed.");
		return shaderModule;
	}

	final static long WGPU_LIMIT_U32_UNDEFINED = -1;
	final static long WGPU_LIMIT_U64_UNDEFINED = -1L;

	public void setDefaultLimits (WGPULimits limits) {
		limits.setMaxTextureDimension1D(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxTextureDimension2D(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxTextureDimension3D(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxTextureArrayLayers(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxBindGroups(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxBindGroupsPlusVertexBuffers(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxBindingsPerBindGroup(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxDynamicUniformBuffersPerPipelineLayout(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxDynamicStorageBuffersPerPipelineLayout(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxSampledTexturesPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxSamplersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxStorageBuffersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxStorageTexturesPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxUniformBuffersPerShaderStage(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxUniformBufferBindingSize(WGPU_LIMIT_U64_UNDEFINED);
		limits.setMaxStorageBufferBindingSize(WGPU_LIMIT_U64_UNDEFINED);
		limits.setMinUniformBufferOffsetAlignment(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMinStorageBufferOffsetAlignment(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxVertexBuffers(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxBufferSize(WGPU_LIMIT_U64_UNDEFINED);
		limits.setMaxVertexAttributes(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxVertexBufferArrayStride(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxInterStageShaderComponents(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxInterStageShaderVariables(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxColorAttachments(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxColorAttachmentBytesPerSample(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeWorkgroupStorageSize(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeInvocationsPerWorkgroup(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeWorkgroupSizeX(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeWorkgroupSizeY(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeWorkgroupSizeZ(WGPU_LIMIT_U32_UNDEFINED);
		limits.setMaxComputeWorkgroupsPerDimension(WGPU_LIMIT_U32_UNDEFINED);
	}

	//
 	//  GLFW Window handling ************************************
	//



	public void openWindow (int width, int height, String title) {
		// this.application = application;

		System.out.println("LWJGL version:" + Version.getVersion());

		// Setup an error callback. The default implementation
		// will print the error message in System.err.
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW. Most GLFW functions will not work before doing this.
		if (!glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");

		// glfwSetWindowUserPointer(window, this);

		// Configure GLFW
		glfwDefaultWindowHints(); // optional, the current window hints are already the default
		glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE); // the window will stay hidden after creation
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // because we will use webgpu

		// Create the window
		window = glfwCreateWindow(width, height, title, NULL, NULL);

		if (window == NULL) throw new RuntimeException("Failed to create the GLFW window");

		windowHandle = GLFWNativeWin32.glfwGetWin32Window(window);

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(window, (vidmode.width() - pWidth.get(0)) / 2, (vidmode.height() - pHeight.get(0)) / 2);
		} // the stack frame is popped automatically

		// Make the window visible
		glfwShowWindow(window);
	}

	public long getWindowHandle () {
		return windowHandle;
	}

	public boolean getShouldClose () {
		return glfwWindowShouldClose(window);
	}

	public void setShouldClose (boolean value) {
		glfwSetWindowShouldClose(window, value); // We will detect this in the rendering loop
	}

	public void pollEvents () {
		// Poll for window events. The key callback above will only be
		// invoked during this call.
		glfwPollEvents();
	}

	public float getDeltaTime () {
		double prevTime = currentTime;
		currentTime = glfwGetTime();
		return (float)(currentTime - prevTime);
	}

	public void closeWindow () {

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(window);
		glfwDestroyWindow(window);

		// Terminate GLFW and free the error callback
		glfwTerminate();
		glfwSetErrorCallback(null).free();
	}
}

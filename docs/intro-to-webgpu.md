# Introduction to WebGPU

This is a very brief overview of a WebGPU application.  Please note that when using gdx-webgpu this is all taken care of behind the scenes.
But now and then you may want to dig a little deeper, and this introduction tries to explain some basic concepts. To keep this simple, some details and error handling has been omitted.

# Initializing WebGPU
WebGPU needs to be set up for use when an application is started.  There are a number of steps involved.

1. Obtain an Instance
To work with WebGPU, first we need an Instance of WebGPU.  We need this in order to obtain a so-called Adapter.

```java
	WGPUInstance instance = WGPU.createInstance();
```
2. Obtain an Adapter

To obtain an Adapter we use an asynchronous call.  This is done by providing a callback object to the request.  The method onCallback
is executed once the adapter is available and the callback is called.  In practice, this is instantaneous on the native platform.
```java
	RequestAdapterCallback callback = new RequestAdapterCallback() {
		@Override
		protected void onCallback(WGPURequestAdapterStatus status, WGPUAdapter adapter) {
		   if(status == WGPURequestAdapterStatus.Success) {
				WebGPUApplication.this.adapter = adapter;
		   }
		}
	};

	WGPURequestAdapterOptions options = WGPURequestAdapterOptions.obtain();
	options.setBackendType( WGPUBackendType.Vulkan);	// request Vulkan backend for example
	instance.requestAdapter(options, WGPUCallbackMode.AllowProcessEvents, callback);
```		
Once we have the Adapter, we no longer need the Instance.
```java	
        instance.release()	
```
4. Obtain a Device

A Device is also obtained with an asynchronous call.

```java
	RequestDeviceCallback callback = new RequestDeviceCallback() {
		@Override
		protected void onCallback(WGPURequestDeviceStatus status, WGPUDevice device) {
			if(status == WGPURequestDeviceStatus.Success) {
				WebGPUApplication.this.device = device;
			}
		}
	});

	WGPUDeviceDescriptor deviceDescriptor = WGPUDeviceDescriptor.obtain();
	deviceDescriptor.setLabel("My Device");
	adapter.requestDevice(deviceDescriptor, WGPUCallbackMode.AllowProcessEvents, callback); 
```		


Once we have the Device we are finished with the Adapter and we can release it:

```java
	adapter.release();
	adapter.dispose();
```		

5. Get a command queue
```java	
	queue = device.getQueue();
```		
6. Create a surface

The surface represents the graphics output, i.e. the window canvas.  
To create it, we need to pass the window handle and call a platform specific method. For example, for Windows:
```java		
	surface = instance.createWindowsSurface(config.windowHandle);
```		

To get the texture format of the surface, we request the capabilities of the adapter and use the first format.
This is the preferred format for the surface, it is best to always use this for render output so that 
no conversion needs to be performed.

```java		
	WGPUSurfaceCapabilities surfaceCapabilities = new WGPUSurfaceCapabilities();
	wgpu.surface.getCapabilities(wgpu.adapter, surfaceCapabilities);
	surfaceFormat = surfaceCapabilities.getFormats().get(0);	// first entry
```			

7. Set up Swap Chain

The swap chain is a configuration of the surface to allow the application to render output to one texture, while another texture
is displayed on the screen. There can also be a swap chain of more than two textures, creating a pipeline of content that queues 
up to be displayed on the screen.

```java
	WGPUSurfaceConfiguration config = WGPUSurfaceConfiguration.obtain();
	config.setWidth(640);
	config.setHeight(480);
	config.setFormat(surfaceFormat);
	config.setViewFormats(null);
	config.setUsage(WGPUTextureUsage.RenderAttachment);
	config.setDevice(device);
	config.setPresentMode(WGPUPresentMode.Fifo);
	config.setAlphaMode(WGPUCompositeAlphaMode.Auto);
	surface.configure(config);
```

# Exiting WebGPU

When the application exits, we should release the WebGPU resources that we obtained 
during the initialisation and that were not released already during the start-up such as Instance and Adapter..
```java
	surface.unconfigure();
	surface.release();
        surface.dispose();
	queue.dispose();
        device.destroy();
	device.dispose();
```

# Render Loop

The application will spend most of its time in the render loop.  It will run through the loop once per render frame, typically 60 times per second.
This following steps are performed once per render frame.  Note that the first few and last few steps are performed in the framework.  Steps 3 to 5
are performed within ApplicationListener.render(), in other words in application code and depend on the user's code.  In this example, we assume
the user code does a very simple render pass to just draw one triangle.


1. Get the next texture view from the swap chain to serve as render output.
```java		
	targetView = getNextSurfaceTextureView();
```
	 
In actual fact, there are a few steps involved, So we'll go into more detail on what happens in `getNextSurfaceTextureView`:
First we get the current surface texture from the surface. (Despite the name, a SurfaceTexture is not a Texture but it does contain a Texture).

```java
	WGPUSurfaceTexture surfaceTextureOut = WGPUSurfaceTexture.obtain();
	surface.getCurrentTexture(surfaceTextureOut);
```		
Then we extract the texture from the surface texture

```java
	WGPUTexture textureOut = WGPUTexture.obtain();
	surfaceTextureOut.getTexture(textureOut);
```		
To render to a texture, we don't need the texture itself, but a texture view.  So next, construct a texture view
from the texture:

```java
	WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
	viewDescriptor.setLabel("Surface texture view");
	viewDescriptor.setFormat(textureOut.getFormat());
	viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
	viewDescriptor.setBaseMipLevel(0);
	viewDescriptor.setMipLevelCount(1);
	viewDescriptor.setBaseArrayLayer(0);
	viewDescriptor.setArrayLayerCount(1);
	viewDescriptor.setAspect(WGPUTextureAspect.All);

	WGPUTextureView targetView = new WGPUTextureView();
	textureOut.createView(viewDescriptor, targetView);
	  textureOut.release();
```	
		 
2. Create a command encoder.

```java
	WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
	encoderDesc.setLabel("The Command Encoder");
	WGPUCommandEncoder encoder = new WGPUCommandEncoder();
	device.createCommandEncoder(encoderDesc, encoder);
```	

3. Create a render pass

Note: step 3 to 5 are performed by the user's code in ApplicationListener.render(). Typically, these steps are performed by WgSpriteBatch or WgModelBatch.

```java
        // fill in the details of the render pass in its descriptor

	// create a color attachment
	renderPassColorAttachment = WGPURenderPassColorAttachment.obtain();
	renderPassColorAttachment.setNextInChain(null);

	// clear to a grey background color
	renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
	renderPassColorAttachment.getClearValue().setColor(0.5f, 0.5f, 0.5f, 1.0f);	
	renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);

	// attach to the targetView obtained earlier
	renderPassColorAttachment.setView(targetView);
	renderPassColorAttachment.setResolveTarget(null);

	// add the color attachment to the render pass descriptor
	WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
	colorAttachments.push_back(renderPassColorAttachment);

	WGPURenderPassDescriptor renderPassDescriptor = new WGPURenderPassDescriptor();
	renderPassDescriptor.setColorAttachments(colorAttachments);	// set the 1 color attachment
	renderPassDesc.setDepthStencilAttachment(null);	// no depth attachment
	renderPassDesc.setTimestampWrites(null);			

	// request the encoder to create a render pass matching the render pass descriptor
	WGPURenderPassEncoder renderPass = new WGPURenderPassEncoder();
	encoder.beginRenderPass(renderPassDescriptor, renderPass);
```
	
4. Draw to the render pass

(We'll skip the part where you create a pipeline).

```java
	renderPass.setPipeline(pipeline);
	renderPass.draw( 3, 1, 0, 0);
```	

5. End the render pass.

```java	
	renderPass.end();
	renderPass.release();
```	

6	Finish the encoder obtained in step 3 and capture the result in a command buffer. Once we have the command buffer, we can release the encoder.

```java
	WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
	cmdBufferDescriptor.setNextInChain(null);
	cmdBufferDescriptor.setLabel("Command buffer");
	commandBuffer = new WGPUCommandBuffer();
	encoder.finish(cmdBufferDescriptor, commandBuffer);
	encoder.release();
	encoder.dispose();
```
7.  Submit the command buffer to the command queue.  Once we've done that we can release the command buffer.

```java
	queue.submit(1, commandBuffer);
	commandBuffer.release();
```
8. We are now finished with the texture view, so we can release it.

```java
	targetView.release();
```	

9. Depending on the platform (not on Web), we need to present the surface in order to make it visible

```java		
	if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) 
		surface.present();
```





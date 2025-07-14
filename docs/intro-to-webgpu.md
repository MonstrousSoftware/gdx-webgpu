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
is execute once the adapter is available and the callback is called.  In practice, this is instantaneous on the native platform.
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
      instance.requestAdapter(option, WGPUCallbackMode.AllowProcessEvents, callback);
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
7. Get a command encoder
```java		
	encoder = new WGPUCommandEncoder();		
```
todo: swap chain

# Exiting WebGPU

When the application exits, we should release the WebGPU resources that we obtained 
during the initialisation and that were not released already during the start-up such as Instance and Adapter..
```java
        // encoder.dispose()
        surface.release()
        queue.dispose();
        device.dispose();
        
        command.dispose();
```

# Render Loop

The application will spend most of its time in the render loop.  It will run through the loop once per render frame, typically 60 times per second.
This following steps are performed once per render frame.  Note that the first few and last few steps are performed in the framework.  Steps 3 to 5
are performed within ApplicationListener.render(), in other words in application code and depend on the user's code.  In this example, we assume
the user code calls WgSpriteBatch to render some sprites.


1. Get the next texture view from the swap chain to serve as render output.
```java		
      targetView = getNextSurfaceTextureView();
```
	 
In actual fact, there are a few steps involved, So we'll go into more detail on what happens in getNextSurfaceTextureView:
First we get the current surface texture from the surface. (Despite the name, a SurfaceTexture is not a Texture but it does contain a Texture).

```java
      WGPUSurfaceTexture surfaceTextureOut = WGPUSurfaceTexture.obtain();
      surface.getCurrentTexture(surfaceTextureOut);
```		
Then we extract the texture from the surface texture

```java
      WGPUTexture textureOut = WGPUTexture.obtain();
      surfaceTextureOut.getTexture(textureOut);
      //surfaceTextureOut.dispose();	// can we dispose now?
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
```	
		 
2. Create a command encoder.

```java
      WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
      encoderDesc.setLabel("The Command Encoder");
      device.createCommandEncoder(encoderDesc, encoder);
```	

3. Create a render pass

Note: step 3 to 5 are performed for example by WgSpriteBatch.

```java
      WGPURenderPassDescriptor renderPassDescriptor = new WGPURenderPassDescriptor();
      
      // fill in the details of the render pass
      // some steps skipped
      
      // create a color attachment
      renderPassColorAttachment = new WGPURenderPassColorAttachment();
      renderPassColorAttachment.setNextInChain(null);
      
      // attach to the targetView obained earlier
      renderPassColorAttachment.setView(targetView);
      
      // add the color attachment to the render pass descriptor
      WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
      colorAttachments.push_back(renderPassColorAttachment);
      renderPassDescriptor.setColorAttachments(colorAttachments);
      
      // request the encoder to create a render pass matching the render pass descriptor
      WGPURenderPassEncoder renderPass = new WGPURenderPassEncoder();
      webgpu.encoder.beginRenderPass(renderPassDescriptor, renderPass);
```
	
4. Draw to the render pass		

```java
      renderPass.setBindGroup( 0, bg, dynamicOffset );
      
      renderPass.setVertexBuffer( 0, vertexBuffer.getBuffer(), vbOffset, numBytes);
      renderPass.setIndexBuffer( indexBuffer.getBuffer(), WGPUIndexFormat.Uint16, 0,  numSprites *6*Short.BYTES);
      
      renderPass.drawIndexed( numSprites *6, 1, 0, 0, 0);
```	

5. End the render pass.

```java		
      renderPass.end();
```		
		
6	Finish the encoder obtained in step 3 and capture the result in a command buffer. Once we have the command buffer, we can release the encoder.

```java
      WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
      cmdBufferDescriptor.setNextInChain(null);
      cmdBufferDescriptor.setLabel("Command buffer");
      commandBuffer = new WGPUCommandBuffer();
      encoder.finish(cmdBufferDescriptor, commandBuffer);
      encoder.release();		 
```
7.  Submit the command buffer to the command queue.  Once we've done that we can release the command buffer.

```java
      queue.submit(1, commandBuffer);
      commandBuffer.release();
```
8. We are now finished with the texture view, so we can release it.

```java
      targetView.release();
      targetView.dispose();		
      targetView = null;	
```		
9. Depending on the platform (not on Web), we need to present the surface in order to make it visible

```java		
    if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) 
      surface.present();
```





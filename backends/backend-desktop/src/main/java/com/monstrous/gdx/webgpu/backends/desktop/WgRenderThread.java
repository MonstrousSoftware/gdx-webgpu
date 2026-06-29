package com.monstrous.gdx.webgpu.backends.desktop;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import java.util.concurrent.ArrayBlockingQueue;

public class WgRenderThread  extends Thread {

    private final WGPUDevice device;
    private final ArrayBlockingQueue<WGPUCommandBuffer> commandQueue;
    private final ArrayBlockingQueue<WGPUTexture> textureQueue;
    private final ArrayBlockingQueue<WGPUTextureView> textureViewQueue;

    private final ApplicationListener listener;
    public WGPUCommandEncoder encoder;
    private WGPUCommandBuffer command;
    private WGPUTextureView[] defaultTargetViews;
    private WGPUTexture surfaceTextureTexture;
    //private WGPUTextureView targetView;
    private WGPUSurface surface;

    public WgRenderThread(WGPUDevice device, ArrayBlockingQueue<WGPUCommandBuffer> commandQueue,
                    ArrayBlockingQueue<WGPUTexture> textureQueue,
                    ArrayBlockingQueue<WGPUTextureView> textureViewQueue,
                    ApplicationListener listener) {
        this.device = device;
        this.commandQueue = commandQueue;
        this.textureQueue = textureQueue;
        this.textureViewQueue = textureViewQueue;
        this.listener = listener;
        encoder = new WGPUCommandEncoder();
        command = new WGPUCommandBuffer();
//        surfaceTextureTexture = new WGPUTexture();
//        targetView = new WGPUTextureView();
        defaultTargetViews = new WGPUTextureView[1];
    }

    public void run() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        surface = webgpu.surface;
        WGPUTextureView currentTargetView = null;

        for(;;) {



            // need to do  getNextSurfaceTextureView(); ?
            // needed for RenderPassBuilder

            // obtain render surface texture from the swap chain
            currentTargetView = getNextSurfaceTextureView();

            // if (currentTargetView == null) return;

            defaultTargetViews[0] = currentTargetView;
            // defaultSurfaceFormats[0] is already set to surface format
            webgpu.targetViews = defaultTargetViews;
            //webgpu.surfaceFormats = defaultSurfaceFormats;


            // create an encoder
            WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
            encoderDesc.setLabel("The Command Encoder");
            device.createCommandEncoder(encoderDesc, encoder);

            webgpu.encoder = encoder;



            // call application render code
            listener.render();

            // resolve time stamps after render pass end and before encoder finish
            // todo gpuTimer.resolveTimeStamps(encoder);

            // finish the encoder and create a command
            WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
            cmdBufferDescriptor.setNextInChain(WGPUChainedStruct.NULL);
            cmdBufferDescriptor.setLabel("Command buffer");
            encoder.finish(cmdBufferDescriptor, command);
            encoder.release();

            // pass command to main thread via a queue

            boolean ok = commandQueue.offer(command);   // returns false if queue was already full
            if(!ok)
                System.out.println("Command queue full");
            textureViewQueue.offer(currentTargetView);
            textureQueue.offer(surfaceTextureTexture);

            // sync
            while(!commandQueue.isEmpty()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }


//            System.out.println("This code is running in a thread");
//            try {
//                Thread.sleep(1000);
//            } catch (InterruptedException e) {
//                throw new RuntimeException(e);
//            }
        }
    }

    private WGPUTextureView getNextSurfaceTextureView() {

        // get the surface texture for this frame
        WGPUSurfaceTexture surfaceTexture = new WGPUSurfaceTexture(); //.obtain();
        surface.getCurrentTexture(surfaceTexture);
        WGPUSurfaceGetCurrentTextureStatus status = surfaceTexture.getStatus();

        // after setFullScreenMode or setWindowedMode the status may be 'Outdated' for one frame
        if (status != WGPUSurfaceGetCurrentTextureStatus.SuccessOptimal
            && status != WGPUSurfaceGetCurrentTextureStatus.SuccessSuboptimal) {
            // System.out.println("Surface texture status: "+status);
            return null;
        }


        // get texture from surface texture
        surfaceTextureTexture = new WGPUTexture();  // todo needs releasing
        surfaceTexture.getTexture(surfaceTextureTexture);
        if (!surfaceTextureTexture.isValid()) {
            System.out.println("Surface texture texture is not valid!");
            return null;
        }
        surfaceTexture.dispose();

        // surfaceTexture is not releasable.
        // we will release surfaceTextureTexture after SurfacePresent (due to WGPU constraint, on Dawn we could release
        // it here)

        // create a texture view for the texture
        WGPUTextureFormat textureFormat = surfaceTextureTexture.getFormat();

        WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
        viewDescriptor.setLabel("Surface texture view");
        viewDescriptor.setFormat(textureFormat);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.All);

        WGPUTextureView targetView = new WGPUTextureView();
        surfaceTextureTexture.createView(viewDescriptor, targetView);

        return targetView;
    }
}

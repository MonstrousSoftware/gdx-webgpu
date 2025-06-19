package com.badlogic.gdx.webgpu.wrappers;



import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.webgpu.WebGPUGraphicsBase;
import com.badlogic.gdx.webgpu.graphics.WgTexture;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.*;
import jnr.ffi.Pointer;

/** WebGPU graphics context. Used to initialize and terminate WebGPU and to render frames.
 */
public class WebGPUGraphicsContext  implements WebGPUGraphicsBase, Disposable {
    private final WebGPU_JNI webGPU;
    public WebGPUDevice device;
    public WebGPUQueue queue;
    public Pointer surface;
    public WGPUTextureFormat surfaceFormat;
    public Pointer targetView;
    public WebGPUCommandEncoder commandEncoder;
    public WgTexture depthTexture;
    private WGPUSupportedLimits supportedLimits;
    private WgTexture multiSamplingTexture;
    private final Configuration config;
    private final Rectangle viewport = new Rectangle();
    private boolean scissorEnabled = false;
    private Rectangle scissor;
    private int width, height;


    public static class Configuration {
        public long windowHandle;
        public int numSamples;
        public boolean vSyncEnabled;
        public boolean gpuTimingEnabled;
        public WGPUBackendType requestedBackendType;

        public Configuration(long windowHandle, int numSamples, boolean vSyncEnabled, boolean gpuTimingEnabled, WGPUBackendType requestedBackendType) {
            this.windowHandle = windowHandle;
            this.numSamples = numSamples;
            this.vSyncEnabled = vSyncEnabled;
            this.gpuTimingEnabled = gpuTimingEnabled;
            this.requestedBackendType = requestedBackendType;
        }
    }

    public WebGPUGraphicsContext(WebGPU_JNI webGPU, Configuration config) {
        this.webGPU = webGPU;
        this.config = config;

        Pointer instance = webGPU.wgpuCreateInstance(null);

        surface = JavaWebGPU.getUtils().glfwGetWGPUSurface(instance, config.windowHandle);
        WebGPUAdapter adapter = new WebGPUAdapter(instance, surface, config.requestedBackendType, WGPUPowerPreference.HighPerformance);

        device = new WebGPUDevice(adapter, config.gpuTimingEnabled);

        // Find out the preferred surface format of the window
        WGPUSurfaceCapabilities caps = WGPUSurfaceCapabilities.createDirect();
        webGPU.wgpuSurfaceGetCapabilities(surface, adapter.getHandle(), caps);
        Pointer formats = caps.getFormats();
        int format = formats.getInt(0);
        surfaceFormat = WGPUTextureFormat.values()[format];

        adapter.dispose();  // finished with adapter now that we have a device

        webGPU.wgpuInstanceRelease(instance); // we can release the instance now that we have the device

        queue = new WebGPUQueue(device);

        // create a swap chain via resize
    }


    public void renderFrame (ApplicationListener listener) {

        targetView = getNextSurfaceTextureView();
        if (targetView.address() == 0) {
            System.out.println("*** Invalid target view");
            return;
        }

        // obtain a command encoder
        commandEncoder = new WebGPUCommandEncoder(device);

        listener.render();	// call user code

        // finish command encoder to get a command buffer
        WebGPUCommandBuffer commandBuffer = commandEncoder.finish();
        commandEncoder.dispose();
        commandEncoder = null;
        queue.submit(commandBuffer);	// submit command buffer
        commandBuffer.dispose();

        // At the end of the frame
        webGPU.wgpuTextureViewRelease(targetView);
        webGPU.wgpuSurfacePresent(surface);
        targetView = null;
        device.tick();
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

    public void resize(int width, int height){
        System.out.println("resize: "+width+" x "+height);

        if(width * height == 0 )   // on minimize, don't create zero sized textures
            return;
        terminateDepthBuffer();
        if(surface != null)
            webGPU.wgpuSurfaceUnconfigure(surface);

        initSwapChain(width, height, config.vSyncEnabled);
        initDepthBuffer(width, height, config.numSamples);

        if(config.numSamples > 1 ) {
            if(multiSamplingTexture != null)
                multiSamplingTexture.dispose();
            multiSamplingTexture = new WgTexture("multisampling", width, height, false, true, surfaceFormat, config.numSamples);
        }

        if(scissor == null)
            scissor = new Rectangle();
        scissor.set(0,0,width, height); // on resize, set scissor to whole window
        viewport.set(0,0,width, height);
        this.width = width;
        this.height = height;
    }

    public void setViewport(int x, int y, int w, int h){
        viewport.set(x,y,w,h);
    }
    public Rectangle getViewport(){
        return viewport;
    }


    @Override
    public void enableScissor(boolean mode) {
        scissorEnabled = mode;
    }

    @Override
    public boolean isScissorEnabled(){
        return scissorEnabled;
    }

    @Override
    public void setScissor(int x, int y, int w, int h) {
        if (x + w > width || y + h > height) {
            // alert on invalid use, and avoid crash
            Gdx.app.error("setScissor", "dimensions outside render target");
            return;
        }
        scissor.set(x,y,w,h);
    }

    @Override
    public Rectangle getScissor() {
        return scissor;
    }

    private void initSwapChain (int width, int height, boolean vsyncEnabled) {
        // configure the surface
        System.out.println("initSwapChain: "+width+" x "+height);
        WGPUSurfaceConfiguration config = WGPUSurfaceConfiguration.createDirect();
        config.setNextInChain().setWidth(width).setHeight(height).setFormat(surfaceFormat).setViewFormatCount(0)
                .setViewFormats(JavaWebGPU.createNullPointer()).setUsage(WGPUTextureUsage.RenderAttachment).setDevice(device.getHandle())
                .setPresentMode(vsyncEnabled ? WGPUPresentMode.Fifo : WGPUPresentMode.Immediate)
                .setAlphaMode(WGPUCompositeAlphaMode.Auto);

        webGPU.wgpuSurfaceConfigure(surface, config);
    }

    private void initDepthBuffer(int width, int height, int samples){
        System.out.println("initDepthBuffer: "+width+" x "+height);
        depthTexture = new WgTexture("depth texture", width, height, 1, WGPUTextureUsage.RenderAttachment,
                WGPUTextureFormat.Depth24Plus, samples, WGPUTextureFormat.Depth24Plus );
    }

    private void terminateDepthBuffer(){
        // Destroy the depth texture
        if(depthTexture != null) {
            depthTexture.dispose();
        }
        depthTexture = null;
    }


    @Override
    public void dispose() {
        webGPU.wgpuSurfaceUnconfigure(surface);
        queue.dispose();
        device.dispose();
//
//		webGPU.wgpuQueueRelease(queue);
//		webGPU.wgpuDeviceRelease(device);
        webGPU.wgpuSurfaceRelease(surface);

        terminateDepthBuffer();
    }

    public WebGPU_JNI getWebGPU () {
        return webGPU;
    }

    @Override
    public WebGPUDevice getDevice() {
        return device;
    }

    @Override
    public WebGPUQueue getQueue() {
        return queue;
    }

    @Override
    public WGPUTextureFormat getSurfaceFormat () {
        return surfaceFormat;
    }
    @Override
    public Pointer getTargetView () {
        return targetView;
    }
    @Override
    public WebGPUCommandEncoder getCommandEncoder () {
        return commandEncoder;
    }


//    public WGPUSupportedLimits getSupportedLimits() {
//        return supportedLimits;
//    }
//
//    public void setSupportedLimits(WGPUSupportedLimits supportedLimits) {
//        this.supportedLimits = supportedLimits;
//    }

    @Override
    public WgTexture getDepthTexture () {
        return depthTexture;
    }


    @Override
    public WGPUBackendType getRequestedBackendType() {
        return config.requestedBackendType;
    }

    @Override
    public int getSamples() {
        return config.numSamples;
    }

    public WgTexture getMultiSamplingTexture() {
        return multiSamplingTexture;
    }


}

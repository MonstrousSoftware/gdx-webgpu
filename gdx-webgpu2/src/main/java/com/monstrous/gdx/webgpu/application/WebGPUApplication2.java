package com.monstrous.gdx.webgpu.application;



import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUAdapterType;
import com.github.xpenatan.webgpu.WGPUBackendType;
import com.github.xpenatan.webgpu.WGPUCallbackMode;
import com.github.xpenatan.webgpu.WGPUErrorType;
import com.github.xpenatan.webgpu.WGPUFeatureName;
import com.github.xpenatan.webgpu.WGPURequestAdapterStatus;
import com.github.xpenatan.webgpu.WGPURequestDeviceStatus;
import com.github.xpenatan.webgpu.WebGPUAdapter;
import com.github.xpenatan.webgpu.WebGPUDevice;
import com.github.xpenatan.webgpu.WebGPUQueue;
import com.github.xpenatan.webgpu.*;

/** WebGPU graphics context and implementation of application life cycle. Used to initialize and terminate WebGPU and to render frames.
 * Also provides shared WebGPU resources, like the device, the default queue, the surface etc.
 */
public class WebGPUApplication2 extends WebGPUContext implements Disposable {

    private InitState initState;

    //private WgTexture multiSamplingTexture;
    //private final GPUTimer gpuTimer;
    private final Configuration config;
    private final Rectangle viewportRectangle = new Rectangle();
    private boolean scissorEnabled = false;
    private Rectangle scissor;
    private int width, height;
    private final WebGPUCommandBuffer command;

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

    // note:  JWebGPULoader.init must be called before we get here to load the native library

    public WebGPUApplication2(Configuration config) {
        //this.webGPU = webGPU;
        this.config = config;

        // create instance, adapter, device and queue
        init();

        while(!isReady())
            update();

        command = new WebGPUCommandBuffer();

        while(!isReady())
            update();

        surface = instance.createWindowsSurface(config.windowHandle);

        if(surface != null) {
            System.out.println("Surface created");
            WebGPUSurfaceCapabilities surfaceCapabilities = new WebGPUSurfaceCapabilities();
            surface.getCapabilities(adapter, surfaceCapabilities);
            surfaceFormat = surfaceCapabilities.getFormats(0);
            System.out.println("surfaceFormat: " + surfaceFormat);

            // initSwapChain will be done via resize()

            // Release the adapter only after it has been fully utilized
            adapter.release();
            adapter.dispose();
            adapter = null;
        }
        else {
            System.out.println("Surface not created");
        }

//        surface = JavaWebGPU.getUtils().glfwGetWGPUSurface(instance, config.windowHandle);
//        WebGPUAdapter adapter = new WebGPUAdapter(instance, surface, config.requestedBackendType, WGPUPowerPreference.HighPerformance);

        // device = new WebGPUDevice(adapter, config.gpuTimingEnabled);

        // Find out the preferred surface format of the window
//        WGPUSurfaceCapabilities caps = WGPUSurfaceCapabilities.createDirect();
//        webGPU.wgpuSurfaceGetCapabilities(surface, adapter.getHandle(), caps);
//        Pointer formats = caps.getFormats();
//        int format = formats.getInt(0);
//        surfaceFormat = WGPUTextureFormat.values()[format];

        // todo webGPU.wgpuInstanceRelease(instance); // we can release the instance now that we have the device
        // do we need instance for processEvents?

        //gpuTimer = new GPUTimer(device, config.gpuTimingEnabled);

        encoder = new WebGPUCommandEncoder();
        while(!isReady())
            update();
//        initSwapChain(640, 480, true);

    }

    private void init() {

        WebGPUInstance instance = WGPU.createInstance();
        if(instance.isValid()) {
            initState = InitState.INSTANCE_VALID;
            this.instance = instance;
            requestAdapter();
        }
        else {
            initState = InitState.INSTANCE_NOT_VALID;
            instance.dispose();
        }
    }

    private void requestAdapter() {
        WebGPURequestAdapterOptions op = WebGPURequestAdapterOptions.obtain();
        //op.setBackendType(WGPUBackendType.Vulkan);
        RequestAdapterCallback callback = new RequestAdapterCallback() {
            @Override
            protected void onCallback(WGPURequestAdapterStatus status, WebGPUAdapter adapter) {
                System.out.println("Adapter Status: " + status);
                if(status == WGPURequestAdapterStatus.Success) {
                    initState = InitState.ADAPTER_VALID;
                    WebGPUApplication2.this.adapter = adapter;
                    requestDevice();
                }
                else {
                    initState = InitState.ADAPTER_NOT_VALID;
                }
            }
        };
        instance.requestAdapter(op, WGPUCallbackMode.AllowProcessEvents, callback);
    }


    private void requestDevice() {
        WebGPUAdapterInfo info = WebGPUAdapterInfo.obtain();
        if(adapter.getInfo(info)) {
            WGPUBackendType backendType = info.getBackendType();
            System.out.println("BackendType: " + backendType);
            WGPUAdapterType adapterType = info.getAdapterType();
            System.out.println("AdapterType: " + adapterType);
            String vendor = info.getVendor().c_str();
            System.out.println("Vendor: " + vendor);
            String architecture = info.getArchitecture().c_str();
            System.out.println("Architecture: " + architecture);
            String description = info.getDescription().c_str();
            System.out.println("Description: " + description);
            String device = info.getDevice().c_str();
            System.out.println("Device: " + device);
            System.out.println("Has Feature DepthClipControl: " + adapter.hasFeature(WGPUFeatureName.DepthClipControl));
        }

        WebGPUDeviceDescriptor deviceDescriptor = WebGPUDeviceDescriptor.obtain();
        WebGPULimits limits = WebGPULimits.obtain();
        setDefaultLimits(limits);
        deviceDescriptor.setRequiredLimits(limits);
        deviceDescriptor.setLabel("My Device");

        WGPUVectorFeatureName features = WGPUVectorFeatureName.obtain();
        features.push_back(WGPUFeatureName.DepthClipControl);
        deviceDescriptor.setRequiredFeatures(features);

        deviceDescriptor.getDefaultQueue().setLabel("The default queue");

        adapter.requestDevice(deviceDescriptor, WGPUCallbackMode.AllowProcessEvents, new RequestDeviceCallback() {
            @Override
            protected void onCallback(WGPURequestDeviceStatus status, WebGPUDevice device) {
                System.out.println("Device Status: " + status);
                if(status == WGPURequestDeviceStatus.Success) {
                    initState = InitState.DEVICE_VALID;
                    WebGPUApplication2.this.device = device;
                    queue = device.getQueue();
                    System.out.println("Platform: " + WGPU.getPlatformType());

                    WebGPUSupportedFeatures features = WebGPUSupportedFeatures.obtain();
                    device.getFeatures(features);
                    int featureCount = features.getFeatureCount();
                    System.out.println("Total Features: " + featureCount);
                    for(int i = 0; i < featureCount; i++) {
                        WGPUFeatureName featureName = features.getFeatureAt(i);
                        System.out.println("Feature name: " + featureName);
                    }
                    features.dispose();

                    WebGPULimits limits = WebGPULimits.obtain();
                    device.getLimits(limits);
                    System.out.println("Device limits: " + featureCount);
                    System.out.println("MaxTextureDimension1D: " + limits.getMaxTextureDimension1D());
                    System.out.println("MaxTextureDimension2D: " + limits.getMaxTextureDimension2D());
                    System.out.println("MaxTextureDimension3D: " + limits.getMaxTextureDimension3D());
                    System.out.println("MaxTextureArrayLayers: " + limits.getMaxTextureArrayLayers());
                }
                else {
                    initState = InitState.DEVICE_NOT_VALID;
                }
            }
        }, new UncapturedErrorCallback() {
            @Override
            protected void onCallback(WGPUErrorType errorType, String message) {
                System.err.println("ErrorType: " + errorType);
                System.err.println("Error Message: " + message);
                initState = InitState.ERROR;
            }
        });
    }

    public boolean isReady() {
        return initState == InitState.DEVICE_VALID;
    }

    /** returns null if gpu timing is not enabled in application configuration. */
//    @Override
//    public GPUTimer getGPUTimer() {
//        return gpuTimer;
//    }

//    @Override
//    // maybe smooth this to one value per second to keep values readable
//    public float getAverageGPUtime(int pass){
//        return gpuTimer.getAverageGPUtime(pass);
//    }
//





    public void renderFrame (ApplicationListener listener) {
        if(targetView == null)
            targetView = getNextSurfaceTextureView();
        if (targetView == null) {
            System.out.println("*** Invalid target view");
            return;
        }

        WebGPUCommandEncoderDescriptor encoderDesc = WebGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("My command encoder");
        device.createCommandEncoder(encoderDesc, encoder);

        listener.render();

//        WebGPURenderPassColorAttachment renderPassColorAttachment = WebGPURenderPassColorAttachment.obtain();
//        renderPassColorAttachment.setView(textureView);
//        renderPassColorAttachment.setResolveTarget(null);
//        renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
//        renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
//        renderPassColorAttachment.getClearValue().setColor(r, g, b, 1.0f);
//
//        WGPUVectorRenderPassColorAttachment colorAttachmentVector = WGPUVectorRenderPassColorAttachment.obtain();
//        colorAttachmentVector.push_back(renderPassColorAttachment);
//
//        WebGPURenderPassDescriptor renderPassDesc  = WebGPURenderPassDescriptor.obtain();
//        renderPassDesc.setColorAttachments(colorAttachmentVector);
//        renderPassDesc.setDepthStencilAttachment(null);
//        renderPassDesc.setTimestampWrites(null);
//        encoder.beginRenderPass(renderPassDesc, renderPass);
//
//        renderPass.setPipeline(pipeline);
//        renderPass.draw(3, 1, 0, 0);
//
//        renderPass.end();
//        renderPass.release();

        // resolve time stamps after render pass end and before encoder finish
        // todo gpuTimer.resolveTimeStamps(encoder);

        WebGPUCommandBufferDescriptor cmdBufferDescriptor = WebGPUCommandBufferDescriptor.obtain();
        cmdBufferDescriptor.setNextInChain(null);
        cmdBufferDescriptor.setLabel("Command buffer");
        encoder.finish(cmdBufferDescriptor, command);
        encoder.release();

        queue.submit(1, command);
        command.release();

        targetView.release();
        targetView = null;

        if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) {
            surface.present();
        }
    }


    public void update() {
        if(instance != null) {
            instance.processEvents();
        }
        if(initState == InitState.ERROR) {
            throw new RuntimeException("WebGPU Error");
        }
    }


    private WebGPUTextureView getNextSurfaceTextureView() {
        WebGPUTextureView textureViewOut = WebGPUTextureView.obtain();
        WebGPUSurfaceTexture surfaceTextureOut = WebGPUSurfaceTexture.obtain();
        surface.getCurrentTexture(surfaceTextureOut);

        WebGPUTexture textureOut = WebGPUTexture.obtain();
        surfaceTextureOut.getTexture(textureOut);

        WGPUTextureFormat textureFormat = textureOut.getFormat();

        WebGPUTextureViewDescriptor viewDescriptor = WebGPUTextureViewDescriptor.obtain();
        viewDescriptor.setLabel("Surface texture view");
        viewDescriptor.setFormat(textureFormat);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.All);

        textureOut.createView(viewDescriptor, textureViewOut);
        textureOut.release();
        return textureViewOut;
    }

    public void resize(int width, int height){
        System.out.println("resize: "+width+" x "+height);

        if(width * height == 0 )   // on minimize, don't create zero sized textures
            return;
        terminateDepthBuffer();

        exitSwapChain();

        initSwapChain(width, height, config.vSyncEnabled);
        initDepthBuffer(width, height, config.numSamples);

//        if(config.numSamples > 1 ) {
//            if(multiSamplingTexture != null)
//                multiSamplingTexture.dispose();
//            multiSamplingTexture = new WgTexture("multisampling", width, height, false, true, surfaceFormat, config.numSamples);
//        }

        if(scissor == null)
            scissor = new Rectangle();
        System.out.println("set scissor & viewport: "+width+" x "+height);
        scissor.set(0,0,width, height); // on resize, set scissor to whole window
        viewportRectangle.set(0,0,width, height);
        this.width = width;
        this.height = height;

        //         make sure we get a target view from the new swap chain
        //         to present at the end of this frame
//        if(targetView != null)
//            targetView.release();
//        targetView = getNextSurfaceTextureView();
    }

    public void setViewportRectangle(int x, int y, int w, int h){
        viewportRectangle.set(x,y,w,h);
    }

    public void setViewportRectangle(Rectangle rect){
        viewportRectangle.set(rect.x,rect.y,rect.width,rect.height);
    }

    public Rectangle getViewportRectangle(){
        return viewportRectangle;
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



    private void initSwapChain(int width, int height, boolean vsyncEnabled) {
        WebGPUSurfaceConfiguration config = WebGPUSurfaceConfiguration.obtain();
        config.setWidth(width);
        config.setHeight(height);
        config.setFormat(surfaceFormat);
        config.setViewFormats(null);
        config.setUsage(WGPUTextureUsage.RenderAttachment);
        config.setDevice(device);
        config.setPresentMode(vsyncEnabled ? WGPUPresentMode.Fifo : WGPUPresentMode.Immediate);
        config.setAlphaMode(WGPUCompositeAlphaMode.Auto);
        surface.configure(config);
    }

    private void exitSwapChain(){
        //System.out.println("exitSwapChain");
        if(surface != null)
            surface.unconfigure();
    }

    private void initDepthBuffer(int width, int height, int samples){
        //System.out.println("initDepthBuffer: "+width+" x "+height);
        // todo
//        depthTexture = new WgTexture("depth texture", width, height, 1, WGPUTextureUsage.RenderAttachment,
//                WGPUTextureFormat.Depth24Plus, samples, WGPUTextureFormat.Depth24Plus );
    }

    private void terminateDepthBuffer(){
//        // Destroy the depth texture
//        if(depthTexture != null) {
//            depthTexture.dispose();
//        }
//        depthTexture = null;
    }


    @Override
    public void dispose() {
        exitSwapChain();
        queue.dispose();
        device.dispose();
        //gpuTimer.dispose();

        surface.release();
        command.dispose();

        terminateDepthBuffer();
    }

//    public WebGPU_JNI getWebGPU () {
//        return webGPU;
//    }

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
    public WebGPUTextureView getTargetView () {
        return targetView;
    }
    /** Push a texture view to use for output, instead of the screen. */
//    @Override
//    public WebGPUTextureView pushTargetView(WgTexture texture, Rectangle oldViewport) {
//        WebGPUTextureView prevTargetView = targetView;
//        // todo   targetView = texture.getTextureView();
//        oldViewport.set(getViewportRectangle());
//        setViewportRectangle(0,0,texture.getWidth(), texture.getHeight());
//        return prevTargetView;
//    }

    @Override
    public void popTargetView(WebGPUTextureView prevTargetView, Rectangle oldViewport) {
        setViewportRectangle(oldViewport);
        targetView = prevTargetView;
    }


//    @Override
//    public void popDepthTexture(WgTexture prevDepth) {
//        depthTexture  = prevDepth;
//    }

    @Override
    public WebGPUCommandEncoder getCommandEncoder () {
        return encoder;
    }


//    public WGPUSupportedLimits getSupportedLimits() {
//        return supportedLimits;
//    }
//
//    public void setSupportedLimits(WGPUSupportedLimits supportedLimits) {
//        this.supportedLimits = supportedLimits;
//    }

//    @Override
//    public WgTexture getDepthTexture () {
//        return depthTexture;
//    }


//    @Override
//    public WGPUBackendType getRequestedBackendType() {
//        return config.requestedBackendType;
//    }

    @Override
    public int getSamples() {
        return config.numSamples;
    }

//    public WgTexture getMultiSamplingTexture() {
//        return multiSamplingTexture;
//    }


    final static int WGPU_LIMIT_U32_UNDEFINED = -1;
    final static int WGPU_LIMIT_U64_UNDEFINED = -1;

    public void setDefaultLimits (WebGPULimits limits) {
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
//        limits.setMaxInterStageShaderComponents(WGPU_LIMIT_U32_UNDEFINED);
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

    enum InitState {
        NOT_INITIALIZED(0),
        ERROR(1),
        INSTANCE_VALID(2),
        ADAPTER_VALID(3),
        DEVICE_VALID(4),
        INSTANCE_NOT_VALID(-1),
        ADAPTER_NOT_VALID(-2),
        DEVICE_NOT_VALID(-3);

        int status;

        InitState(int status) {
            this.status = status;
        }
    }
}

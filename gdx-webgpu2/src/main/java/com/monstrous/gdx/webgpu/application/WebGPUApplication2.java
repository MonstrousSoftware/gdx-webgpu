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
import com.github.xpenatan.webgpu.WGPUAdapter;
import com.github.xpenatan.webgpu.WGPUDevice;
import com.github.xpenatan.webgpu.WGPUQueue;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.wrappers.GPUTimer;

/** WebGPU graphics context and implementation of application life cycle. Used to initialize and terminate WebGPU and to render frames.
 * Also provides shared WebGPU resources, like the device, the default queue, the surface etc.
 */
public class WebGPUApplication2 extends WebGPUContext implements Disposable {

    private InitState initState;

    private WgTexture multiSamplingTexture;
    private final GPUTimer gpuTimer;
    private final Configuration config;
    private final Rectangle viewportRectangle = new Rectangle();
    private boolean scissorEnabled = false;
    private Rectangle scissor;
    private int width, height;
    private final WGPUCommandBuffer command;
    private final float[] gpuTime = new float[GPUTimer.MAX_PASSES];
    private boolean mustResize = false;
    private int newWidth, newHeight;    // for resize

    public static class Configuration {
        public long windowHandle;
        public int numSamples;
        public boolean vSyncEnabled;
        public boolean gpuTimingEnabled;
        public Backend requestedBackendType;

        public Configuration(long windowHandle, int numSamples, boolean vSyncEnabled, boolean gpuTimingEnabled, Backend requestedBackendType) {
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

        command = new WGPUCommandBuffer();

        while(!isReady())
            update();

        surface = instance.createWindowsSurface(config.windowHandle);

        if(surface != null) {
            System.out.println("Surface created");
            // Find out the preferred surface format of the window
            // = the first one listed under capabilities
            WGPUSurfaceCapabilities surfaceCapabilities = WGPUSurfaceCapabilities.obtain();
            surface.getCapabilities(adapter, surfaceCapabilities);
            surfaceFormat = surfaceCapabilities.getFormats().get(0);
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

        // todo webGPU.wgpuInstanceRelease(instance); // we can release the instance now that we have the device
        // do we need instance for processEvents?

        gpuTimer = new GPUTimer(device, config.gpuTimingEnabled);

        encoder = new WGPUCommandEncoder();
//        while(!isReady())
//            update();
//        initSwapChain(640, 480, true);

    }

    private void init() {

        WGPUInstance instance = WGPU.createInstance();
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
        WGPURequestAdapterOptions op = WGPURequestAdapterOptions.obtain();
        op.setBackendType(convertBackendType(config.requestedBackendType));
        RequestAdapterCallback callback = new RequestAdapterCallback() {
            @Override
            protected void onCallback(WGPURequestAdapterStatus status, WGPUAdapter adapter) {
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
        WGPUAdapterInfo info = WGPUAdapterInfo.obtain();
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

        WGPUDeviceDescriptor deviceDescriptor = WGPUDeviceDescriptor.obtain();
        WGPULimits limits = WGPULimits.obtain();
        setDefaultLimits(limits);
        deviceDescriptor.setRequiredLimits(limits);
        deviceDescriptor.setLabel("My Device");

        WGPUVectorFeatureName features = WGPUVectorFeatureName.obtain();
        features.push_back(WGPUFeatureName.DepthClipControl);
        features.push_back(WGPUFeatureName.TimestampQuery);
        deviceDescriptor.setRequiredFeatures(features);

        deviceDescriptor.getDefaultQueue().setLabel("The default queue");

        adapter.requestDevice(deviceDescriptor, WGPUCallbackMode.AllowProcessEvents, new RequestDeviceCallback() {
            @Override
            protected void onCallback(WGPURequestDeviceStatus status, WGPUDevice device) {
                System.out.println("Device Status: " + status);
                if(status == WGPURequestDeviceStatus.Success) {
                    initState = InitState.DEVICE_VALID;
                    WebGPUApplication2.this.device = device;
                    queue = device.getQueue();
                    System.out.println("Platform: " + WGPU.getPlatformType());

                    WGPUSupportedFeatures features = WGPUSupportedFeatures.obtain();
                    device.getFeatures(features);
                    int featureCount = features.getFeatureCount();
                    System.out.println("Total Features: " + featureCount);
                    for(int i = 0; i < featureCount; i++) {
                        WGPUFeatureName featureName = features.getFeatureAt(i);
                        System.out.println("\tFeature name: " + featureName);
                    }
                    features.dispose();

                    WGPULimits limits = WGPULimits.obtain();
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
    @Override
    public GPUTimer getGPUTimer() {
        return gpuTimer;
    }

    @Override
    // smoothed to one value per second to keep values readable
    public float getAverageGPUtime(int pass){
        return gpuTime[pass];
    }

    /** called once per second. Used to gather statistics */
    // todo called?
    public void secondsTick() {
        //  request average gpu time once per second to keep it readable
        for(int i = 0; i < getGPUTimer().getNumPasses(); i++) {
            gpuTime[i] = gpuTimer.getAverageGPUtime(i);
        }
    }


    public void renderFrame (ApplicationListener listener) {
        if(mustResize){
            doResize(newWidth, newHeight);
            listener.resize(newWidth, newHeight);
            mustResize = false;
        }

        targetView = getNextSurfaceTextureView();


        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("The Command Encoder");
        device.createCommandEncoder(encoderDesc, encoder);

        listener.render();

        // resolve time stamps after render pass end and before encoder finish
        gpuTimer.resolveTimeStamps(encoder);

        WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
        cmdBufferDescriptor.setNextInChain(null);
        cmdBufferDescriptor.setLabel("Command buffer");
        encoder.finish(cmdBufferDescriptor, command);
        encoder.release();


        queue.submit(1, command);
        command.release();

        // fetch time stamps after submitting the command buffer
        gpuTimer.fetchTimestamps();

        targetView.release();
        targetView = null;

        if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) {
            surface.present();
        }

    }



    public void resize(int width, int height){
        mustResize = true;
        this.newWidth = width;
        this.newHeight = height;
    }

    public void update() {
        if(instance != null) {
            instance.processEvents();
        }
        if(initState == InitState.ERROR) {
            throw new RuntimeException("WebGPU Error");
        }
    }


    private WGPUTextureView getNextSurfaceTextureView() {
        WGPUTextureView textureViewOut = new WGPUTextureView();
        WGPUSurfaceTexture surfaceTextureOut = WGPUSurfaceTexture.obtain();
        surface.getCurrentTexture(surfaceTextureOut);

        WGPUTexture textureOut = WGPUTexture.obtain();
        surfaceTextureOut.getTexture(textureOut);
        //surfaceTextureOut.dispose();

        WGPUTextureFormat textureFormat = textureOut.getFormat();

        WGPUTextureViewDescriptor viewDescriptor = WGPUTextureViewDescriptor.obtain();
        viewDescriptor.setLabel("Surface texture view");
        viewDescriptor.setFormat(textureFormat);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.All);

        textureOut.createView(viewDescriptor, textureViewOut);
        // not on WGPU: textureOut.release();
        return textureViewOut;
    }

    // Note that normally resize() is called from outside renderFrame(), e.g. targetView is null.
    // If resize is called from within renderFrame() i.e. from ApplicationListener.render()
    // then there may be problems.
    // This also (?) applies for calling newWindow()
    //
    public void doResize(int width, int height){
        System.out.println("resize: "+width+" x "+height);

        if(width * height == 0 )   // on minimize, don't create zero sized textures
            return;

        terminateDepthBuffer();
        exitSwapChain();
        System.out.println("starting new swap chain");
        if(targetView != null) {
            targetView.release();
        }
        initSwapChain(width, height, config.vSyncEnabled);

        System.out.println("init depth buffer");
        initDepthBuffer(width, height, config.numSamples);
        System.out.println("got new  depth buffer");

        if(config.numSamples > 1 ) {
            System.out.println("renew multisampling texture");
            if(multiSamplingTexture != null)
                multiSamplingTexture.dispose();
            multiSamplingTexture = new WgTexture("multisampling", width, height, false, true, surfaceFormat, config.numSamples);
        }

        if(scissor == null)
            scissor = new Rectangle();
        System.out.println("set scissor & viewport: "+width+" x "+height);
        scissor.set(0,0,width, height); // on resize, set scissor to whole window
        viewportRectangle.set(0,0,width, height);
        this.width = width;
        this.height = height;

        //         make sure we get a target view from the new swap chain
        //         to present at the end of this frame
        // commented out because of error:   Surface image is already acquired

        // the target view is no longer valid because the surface has changed
//        if(targetView != null) {
//            targetView.release();
//            targetView = null;
//        }
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

        WGPUSurfaceConfiguration config = WGPUSurfaceConfiguration.obtain();
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
        System.out.println("exitSwapChain");
        surface.unconfigure();
    }

    public void drop(){
        terminateDepthBuffer();
        exitSwapChain();
    }

    private void initDepthBuffer(int width, int height, int samples){
        //System.out.println("initDepthBuffer: "+width+" x "+height);
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
        exitSwapChain();
        queue.dispose();
        device.dispose();
        gpuTimer.dispose();

        surface.release();
        command.dispose();

        terminateDepthBuffer();
    }


    @Override
    public WGPUDevice getDevice() {
        return device;
    }

    @Override
    public WGPUQueue getQueue() {
        return queue;
    }

    @Override
    public WGPUTextureFormat getSurfaceFormat () {
        return surfaceFormat;
    }

    @Override
    public WGPUTextureView getTargetView () {
        return targetView;
    }
    /** Push a texture view to use for output, instead of the screen. */
    @Override
    public WGPUTextureView pushTargetView(WgTexture texture, Rectangle oldViewport) {
        WGPUTextureView prevTargetView = targetView;
        targetView = texture.getTextureView();
        oldViewport.set(getViewportRectangle());
        setViewportRectangle(0,0,texture.getWidth(), texture.getHeight());
        return prevTargetView;
    }

    @Override
    public void popTargetView(WGPUTextureView prevTargetView, Rectangle oldViewport) {
        setViewportRectangle(oldViewport);
        targetView = prevTargetView;
    }

    @Override
    public WgTexture pushDepthTexture(WgTexture depth) {
        WgTexture prevDepth = depthTexture;
        depthTexture = depth;
        return prevDepth;
    }

    @Override
    public void popDepthTexture(WgTexture prevDepth) {
        depthTexture  = prevDepth;
    }

    @Override
    public WGPUCommandEncoder getCommandEncoder () {
        return encoder;
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


//    @Override
//    public WGPUBackendType getRequestedBackendType() {
//        return config.requestedBackendType;
//    }

    @Override
    public int getSamples() {
        return config.numSamples;
    }

    public WgTexture getMultiSamplingTexture() {
        return multiSamplingTexture;
    }


    final static int WGPU_LIMIT_U32_UNDEFINED = -1;
    final static int WGPU_LIMIT_U64_UNDEFINED = -1;

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

        final int status;

        InitState(int status) {
            this.status = status;
        }
    }

    /** map Backend enum to WGPU enum
     * Note the WGPU enum values cannot be used until the shared library is loaded.
     */
    public WGPUBackendType convertBackendType(Backend backend){
        WGPUBackendType type;
        switch(backend){

            case D3D11:     type = WGPUBackendType.D3D11; break;
            case D3D12:     type = WGPUBackendType.D3D12; break;
            case METAL:     type = WGPUBackendType.Metal; break;
            case OPENGL:    type = WGPUBackendType.OpenGL; break;
            case OPENGL_ES: type = WGPUBackendType.OpenGLES; break;
            case VULKAN:    type = WGPUBackendType.Vulkan; break;
            case HEADLESS:  type = WGPUBackendType.Null; break;

            case DEFAULT:
            default:        type = WGPUBackendType.Undefined; break;
        }
        return type;
    }
}

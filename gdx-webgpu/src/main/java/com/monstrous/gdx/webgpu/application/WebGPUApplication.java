package com.monstrous.gdx.webgpu.application;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.Interpolation;
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
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;


/** WebGPU graphics context and implementation of application life cycle. Used to initialize and terminate WebGPU and to render frames.
 * Also provides shared WebGPU resources, like the device, the default queue, the surface etc.
 */
public class WebGPUApplication extends WebGPUContext implements Disposable {

    private InitState initState;

    private WgTexture multiSamplingTexture;
    private GPUTimer gpuTimer;
    private final Configuration config;
    private final Rectangle viewportRectangle = new Rectangle();
    private boolean scissorEnabled = false;
    private Rectangle scissor;
    private int width, height;
    private WGPUCommandBuffer command;
    private final float[] gpuTime = new float[GPUTimer.MAX_PASSES];
    private WGPUTextureView textureViewOut;
    private WGPUTexture surfaceTextureTexture;
    private boolean swapChainActive = false;

    public static class Configuration {
        public int numSamples;
        public boolean vSyncEnabled;
        public boolean gpuTimingEnabled;
        public Backend requestedBackendType;

        public Configuration(int numSamples, boolean vSyncEnabled, boolean gpuTimingEnabled, Backend requestedBackendType) {
            this.numSamples = numSamples;
            this.vSyncEnabled = vSyncEnabled;
            this.gpuTimingEnabled = gpuTimingEnabled;
            this.requestedBackendType = requestedBackendType;
        }
    }

    // note:  JWebGPULoader.init must be called before we get here to load the native library


    public WebGPUApplication(Configuration config, WGPUInstance instance, WGPUSurface surface) {
        this.config = config;
        this.instance = instance;
        this.surface = surface;

        // create instance, adapter, device and queue
        requestAdapter();
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
                    requestDevice( adapter);
                }
                else {
                    initState = InitState.ADAPTER_NOT_VALID;
                    Gdx.app.error("WebGPU requestAdapter", "No adapter available for backend type "+config.requestedBackendType);
                }
            }
        };
        instance.requestAdapter(op, WGPUCallbackMode.AllowProcessEvents, callback);
    }

    private void requestDevice( WGPUAdapter adapter) {
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
                    WebGPUApplication.this.device = device;
                    queue = device.getQueue();

                    // Find out the preferred surface format of the window
                    // = the first one listed under capabilities
                    WGPUSurfaceCapabilities surfaceCapabilities = WGPUSurfaceCapabilities.obtain();
                    surface.getCapabilities(adapter, surfaceCapabilities);
                    surfaceFormat = surfaceCapabilities.getFormats().get(0);
                    System.out.println("surfaceFormat: " + surfaceFormat);

                    // allocate some objects we will reuse a lot
                    encoder = new WGPUCommandEncoder();
                    command = new WGPUCommandBuffer();
                    textureViewOut = new WGPUTextureView();
                    surfaceTextureTexture = new WGPUTexture();

                    gpuTimer = new GPUTimer(device, config.gpuTimingEnabled);

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

                    initState = InitState.DEVICE_VALID;
                }
                else {
                    initState = InitState.DEVICE_NOT_VALID;
                }
                // Release the adapter only after it has been fully utilized
                adapter.release();
                adapter.dispose();
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
    public void secondsTick() {
        //  request average gpu time once per second to keep it readable
        for(int i = 0; i < getGPUTimer().getNumPasses(); i++) {
            gpuTime[i] = gpuTimer.getAverageGPUtime(i);
        }
    }


    public void renderFrame (ApplicationListener listener) {

        targetView = getNextSurfaceTextureView();

        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("The Command Encoder");
        device.createCommandEncoder(encoderDesc, encoder);

        listener.render();

        // resolve time stamps after render pass end and before encoder finish
        gpuTimer.resolveTimeStamps(encoder);

        WGPUCommandBufferDescriptor cmdBufferDescriptor = WGPUCommandBufferDescriptor.obtain();
        cmdBufferDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        cmdBufferDescriptor.setLabel("Command buffer");
        encoder.finish(cmdBufferDescriptor, command);
        encoder.release();

        queue.submit(command);
        command.release();

        // fetch time stamps after submitting the command buffer
        gpuTimer.fetchTimestamps();

        targetView.release();

        if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) {
            surface.present();
        }
        surfaceTextureTexture.release();

        frameNumber++;
    }





    public void update() {
        if(instance != null) {
            // this is important to advance asynchronous webgpu operations
            // e.g. mapAsync
            instance.processEvents();
        }
        if(initState == InitState.ERROR) {
            throw new RuntimeException("WebGPU Error");
        }
    }


    private WGPUTextureView getNextSurfaceTextureView() {

        // get the surface texture for this frame
        WGPUSurfaceTexture surfaceTexture = WGPUSurfaceTexture.obtain();
        surface.getCurrentTexture(surfaceTexture);

        // get texture from surface texture
        surfaceTexture.getTexture(surfaceTextureTexture);

        // surfaceTexture is not releasable.
        // we will release surfaceTextureTexture after SurfacePresent (due to WGPU constraint, on Dawn we could release it here)

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

        surfaceTextureTexture.createView(viewDescriptor, textureViewOut);

        return textureViewOut;
    }

    /** Set Vertical Sync of swap chain */
    public void setVSync(boolean vsync){
        if(swapChainActive) {
            exitSwapChain();
            initSwapChain(width, height, vsync);
        }
    }


    // Should not be called during a renderFrame() as the swap chain is then being used.
    // E.g. WgDesktopWindow calls this via postRunnable() after having received an async resize event from GLFW.
    //
    public void resize(int width, int height){
        System.out.println("resize: "+width+" x "+height);

        // if there was already a swap chain, release it
        // (there won't be one on the very first resize, or coming back from a minimize)
        if(swapChainActive) {
            terminateDepthBuffer();
            exitSwapChain();
            swapChainActive = false;
        }


        if(width * height == 0 )   // on minimize, don't create zero sized textures
            return;

        initSwapChain(width, height, config.vSyncEnabled);
        initDepthBuffer(width, height, config.numSamples);
        swapChainActive = true;

        if(config.numSamples > 1 ) {
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
        System.out.println("initSwapChain");
        WGPUSurfaceConfiguration config = WGPUSurfaceConfiguration.obtain();
        config.setWidth(width);
        config.setHeight(height);
        config.setFormat(surfaceFormat);
        config.setViewFormats(WGPUVectorTextureFormat.NULL);
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

    private void initDepthBuffer(int width, int height, int samples){
        System.out.println("initDepthBuffer: "+width+" x "+height);
        depthTexture = new WgTexture("depth texture", width, height, false, WGPUTextureUsage.RenderAttachment,
                WGPUTextureFormat.Depth24Plus, samples, WGPUTextureFormat.Depth24Plus );
    }

    private void terminateDepthBuffer(){
        // Destroy the depth texture
        // todo release? destroy?
        depthTexture.dispose();
        depthTexture = null;
    }


    @Override
    public void dispose() {
        terminateDepthBuffer();
        exitSwapChain();

        command.dispose();
        encoder.dispose();
        textureViewOut.dispose();
        surfaceTextureTexture.dispose();

        surface.release();
        surface.dispose();
        queue.release();
        queue.dispose();
        device.destroy();
        device.dispose();
        gpuTimer.dispose();

        WebGPURenderPass.clearPool();
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
    public RenderOutputState pushTargetView(WGPUTextureView textureView, WGPUTextureFormat textureFormat, int width, int height, WgTexture depthTex) {
        RenderOutputState state = new RenderOutputState(targetView, surfaceFormat, depthTexture, getViewportRectangle(), getSamples());
        targetView = textureView;
        surfaceFormat = textureFormat;
        config.numSamples = 1;      // no antialiasing to texture output
        if(depthTex != null){
            depthTexture = depthTex;
        }
        setViewportRectangle(0,0,width, height);    // scissors too?
        return state;
    }

    @Override
    public void popTargetView(RenderOutputState prevState) {
        setViewportRectangle(prevState.viewport);
        targetView = prevState.targetView;
        surfaceFormat = prevState.surfaceFormat;
        depthTexture = prevState.depthTexture;
        config.numSamples = prevState.numSamples;
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


    public boolean hasLinearOutput(){
        switch(surfaceFormat) {
            case RGBA8UnormSrgb:
            case BGRA8UnormSrgb:
            case BC2RGBAUnormSrgb:
            case BC3RGBAUnormSrgb:
            case BC7RGBAUnormSrgb:
            case ETC2RGBA8UnormSrgb:
                // some more exotic formats to add...
                return true;
            case RGBA8Unorm:
            case BGRA8Unorm:
            case BC2RGBAUnorm:
            case BC3RGBAUnorm:
            case BC7RGBAUnorm:
            case ETC2RGBA8Unorm:
                // some more exotic formats to add...
                return false;
            default:
                Gdx.app.error("hasLinearOutput", "surfaceFormat not known: "+surfaceFormat);
        }
        return true;
    }

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

    public enum InitState {
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

    public interface OnInitCallback {
        void onInit(WebGPUApplication application, WGPUAdapter adapter);
    }
}

package com.monstrous.gdx.webgpu.backends.teavmc;

import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GL31;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWApplicationConfiguration;
import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWCursor;
import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWWindow;
import com.github.xpenatan.gdx.teavm.backends.glfw.graphics.GLFWGraphics;
import com.github.xpenatan.gdx.teavm.backends.glfw.graphics.gl.GLFWGLGraphics;
import com.github.xpenatan.gdx.teavm.backends.glfw.utils.GLFW;
import com.github.xpenatan.gdx.teavm.backends.glfw.utils.GLFWNative;
import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.webgpu.JWebGPULoader;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUBackendType;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUPlatformType;
import com.github.xpenatan.webgpu.WGPUPowerPreference;
import com.github.xpenatan.webgpu.WGPUSurface;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WebGPUInitialization;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.utils.WgGL20;
import org.teavm.interop.Address;
import org.teavm.interop.Function;

/** GLFW graphics implementation used by the native TeaVM C WebGPU backend. */
public class WgCGraphics extends AbstractGraphics implements GLFWGraphics, WgGraphics {

    private final GLFWWindow window;
    private final WgCApplicationConfiguration config;
    private final int[] tmpBuffer = new int[1];
    private final int[] tmpBuffer2 = new int[1];
    private final GLVersion glVersion = new GLVersion(
            Application.ApplicationType.Desktop, "0.0 WebGPU", "WebGPU", "wgpu-native");

    private GL20 gl20 = new WgGL20();
    private volatile WebGPUApplication context;
    private WGPUInstance instance;
    private volatile Throwable initializationError;
    private boolean ready;
    private boolean disposed;

    private volatile int backBufferWidth;
    private volatile int backBufferHeight;
    private volatile int logicalWidth;
    private volatile int logicalHeight;
    private boolean continuousRendering = true;
    private BufferFormat bufferFormat;

    private long lastFrameTime = -1;
    private float deltaTime;
    private boolean resetDeltaTime;
    private long frameId;
    private long frameCounterStart;
    private int frames;
    private int fps;

    private int windowPosXBeforeFullscreen;
    private int windowPosYBeforeFullscreen;
    private int windowWidthBeforeFullscreen;
    private int windowHeightBeforeFullscreen;
    private DisplayMode displayModeBeforeFullscreen;

    private final GLFW.GLFWFramebufferSizeCallback resizeCallback = Function.get(
            GLFW.GLFWFramebufferSizeCallback.class, WgCGraphics.class, "resizeCallback");

    public WgCGraphics(GLFWWindow window, WgCApplicationConfiguration config) {
        this.window = window;
        this.config = config;
        updateFramebufferInfo();
        GLFW.setFramebufferSizeCallback(window.getWindowHandle(), resizeCallback);
        initializeWebGPU();
    }

    private void initializeWebGPU() {
        JWebGPULoader.init((success, error) -> {
            if (!success) {
                initializationError = error != null ? error : new RuntimeException("Unable to load jWebGPU");
                return;
            }
            if (disposed) {
                return;
            }
            try {
                WGPUInstance newInstance = WGPU.setupInstance();
                if (newInstance == null || !newInstance.isValid()) {
                    throw new RuntimeException("WebGPU: cannot create an instance");
                }

                WGPUSurface surface = createSurface(newInstance);
                if (surface == null || surface.native_isNULL()) {
                    throw new RuntimeException("WebGPU: cannot create the native window surface");
                }

                WebGPUApplication.Configuration webGPUConfig = new WebGPUApplication.Configuration(
                        config.samples, config.vSyncEnabled, config.enableGPUtiming, config.backend);
                WebGPUApplication newContext = new WebGPUApplication(webGPUConfig, newInstance, surface);
                instance = newInstance;
                context = newContext;

                WGPUBackendType backendType = WebGPUApplication.convertBackendType(config.backend);
                WebGPUInitialization.setup(
                        newInstance, WGPUPowerPreference.HighPerformance, backendType, newContext);
            } catch (Throwable throwable) {
                initializationError = throwable;
            }
        });
    }

    private WGPUSurface createSurface(WGPUInstance newInstance) {
        long glfwWindow = window.getWindowHandle();
        WGPUPlatformType platform = WGPU.getPlatformType();
        if (platform == WGPUPlatformType.WGPU_Windows) {
            long nativeWindow = GLFWNative.getWin32Window(glfwWindow);
            if (nativeWindow == 0) {
                throw new RuntimeException("WebGPU: cannot get the Win32 window handle");
            }
            return newInstance.createWindowsSurface(nativeObject(nativeWindow));
        }
        if (platform == WGPUPlatformType.WGPU_Linux) {
            int glfwPlatform = GLFWNative.getPlatform();
            if (glfwPlatform == GLFW.GLFW_PLATFORM_WAYLAND) {
                long display = GLFWNative.getWaylandDisplay();
                long nativeWindow = GLFWNative.getWaylandWindow(glfwWindow);
                if (display == 0 || nativeWindow == 0) {
                    throw new RuntimeException("WebGPU: cannot get the Wayland display or surface");
                }
                return newInstance.createLinuxSurface(true, nativeObject(nativeWindow), nativeObject(display));
            }
            if (glfwPlatform == GLFW.GLFW_PLATFORM_X11) {
                long display = GLFWNative.getX11Display();
                long nativeWindow = GLFWNative.getX11Window(glfwWindow);
                if (display == 0 || nativeWindow == 0) {
                    throw new RuntimeException("WebGPU: cannot get the X11 display or window");
                }
                return newInstance.createLinuxSurface(false, nativeObject(nativeWindow), nativeObject(display));
            }
            throw new RuntimeException("WebGPU: unsupported GLFW Linux platform: " + glfwPlatform);
        }
        if (platform == WGPUPlatformType.WGPU_Mac) {
            long nativeWindow = GLFWNative.getCocoaWindow(glfwWindow);
            if (nativeWindow == 0) {
                throw new RuntimeException("WebGPU: cannot get the Cocoa window handle");
            }
            return newInstance.createMacSurface(nativeObject(nativeWindow));
        }
        throw new RuntimeException("WebGPU: unsupported desktop platform: " + platform);
    }

    private static NativeObject nativeObject(long address) {
        return NativeObject.native_new().native_setAddress(address);
    }

    public static void resizeCallback(Address windowHandle, int width, int height) {
        try {
            GLFWWindow window = GLFWWindow.byAddress(windowHandle);
            WgCGraphics graphics = (WgCGraphics) window.getRendererGraphics();
            graphics.updateFramebufferInfo();
            graphics.resizeSurfaceIfReady();
            if (window.isListenerInitialized()) {
                window.makeCurrent();
                window.getListener().resize(graphics.getWidth(), graphics.getHeight());
            }
            window.requestRendering();
        } catch (Throwable throwable) {
            Gdx.app.error("WgCGraphics", "Error while resizing the WebGPU surface", throwable);
        }
    }

    private void resizeSurfaceIfReady() {
        WebGPUApplication currentContext = context;
        if (ready && currentContext != null) {
            currentContext.resize(backBufferWidth, backBufferHeight);
        }
    }

    @Override
    public GLFWWindow getWindow() {
        return window;
    }

    @Override
    public WebGPUContext getContext() {
        return context;
    }

    @Override
    public boolean isReady() {
        // GLFW reports a zero-sized framebuffer while the window is minimized. The WebGPU device
        // remains initialized, but no presentation frame can be acquired until the window is restored.
        return ready && backBufferWidth > 0 && backBufferHeight > 0;
    }

    @Override
    public void updateFramebufferInfo() {
        GLFW.getFramebufferSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
        backBufferWidth = tmpBuffer[0];
        backBufferHeight = tmpBuffer2[0];
        GLFW.getWindowSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
        logicalWidth = tmpBuffer[0];
        logicalHeight = tmpBuffer2[0];
        GLFWApplicationConfiguration glfwConfig = window.getConfig();
        bufferFormat = new BufferFormat(glfwConfig.r, glfwConfig.g, glfwConfig.b, glfwConfig.a,
                glfwConfig.depth, glfwConfig.stencil, config.samples, false);
    }

    @Override
    public void update() {
        Throwable error = initializationError;
        if (error != null) {
            throw new GdxRuntimeException("Failed to initialize the TeaVM C WebGPU backend", error);
        }

        WebGPUApplication currentContext = context;
        if (currentContext != null) {
            currentContext.update();
            if (currentContext.isError()) {
                throw new GdxRuntimeException("Failed to initialize WebGPU");
            }
            if (!ready && currentContext.isReady()) {
                ready = true;
                currentContext.resize(backBufferWidth, backBufferHeight);
            }
        }

        long time = System.nanoTime();
        if (lastFrameTime == -1) {
            lastFrameTime = time;
        }
        if (resetDeltaTime) {
            resetDeltaTime = false;
            deltaTime = 0;
        } else {
            deltaTime = (time - lastFrameTime) / 1_000_000_000f;
        }
        lastFrameTime = time;

        if (time - frameCounterStart >= 1_000_000_000L) {
            fps = frames;
            frames = 0;
            frameCounterStart = time;
            if (ready) {
                currentContext.secondsTick();
            }
        }
        frames++;
        frameId++;
    }

    @Override
    public void beginFrame() {
        if (isReady()) {
            context.beginFrame();
        }
    }

    @Override
    public void endFrame() {
        if (isReady()) {
            context.endFrame();
        }
    }

    @Override
    public void makeCurrent() {
        // WebGPU has no thread-local graphics context to make current.
    }

    @Override
    public void initialClear() {
        // The first application render owns the first WebGPU frame and its clear operations.
    }

    @Override
    public boolean isGL30Available() {
        return false;
    }

    @Override
    public boolean isGL31Available() {
        return false;
    }

    @Override
    public boolean isGL32Available() {
        return false;
    }

    @Override
    public GL20 getGL20() {
        return gl20;
    }

    @Override
    public GL30 getGL30() {
        return null;
    }

    @Override
    public GL31 getGL31() {
        return null;
    }

    @Override
    public GL32 getGL32() {
        return null;
    }

    @Override
    public void setGL20(GL20 gl20) {
        this.gl20 = gl20;
    }

    @Override
    public void setGL30(GL30 gl30) {
        if (gl30 != null) {
            throw new UnsupportedOperationException("OpenGL 3 is not available in the WebGPU backend");
        }
    }

    @Override
    public void setGL31(GL31 gl31) {
        if (gl31 != null) {
            throw new UnsupportedOperationException("OpenGL 3.1 is not available in the WebGPU backend");
        }
    }

    @Override
    public void setGL32(GL32 gl32) {
        if (gl32 != null) {
            throw new UnsupportedOperationException("OpenGL 3.2 is not available in the WebGPU backend");
        }
    }

    @Override
    public int getWidth() {
        return window.getConfig().hdpiMode == HdpiMode.Pixels ? backBufferWidth : logicalWidth;
    }

    @Override
    public int getHeight() {
        return window.getConfig().hdpiMode == HdpiMode.Pixels ? backBufferHeight : logicalHeight;
    }

    @Override
    public int getBackBufferWidth() {
        return backBufferWidth;
    }

    @Override
    public int getBackBufferHeight() {
        return backBufferHeight;
    }

    @Override
    public int getLogicalWidth() {
        return logicalWidth;
    }

    @Override
    public int getLogicalHeight() {
        return logicalHeight;
    }

    @Override
    public long getFrameId() {
        return frameId;
    }

    @Override
    public float getDeltaTime() {
        return deltaTime;
    }

    public void resetDeltaTime() {
        resetDeltaTime = true;
    }

    @Override
    public int getFramesPerSecond() {
        return fps;
    }

    @Override
    public GraphicsType getType() {
        return GraphicsType.LWJGL3;
    }

    @Override
    public GLVersion getGLVersion() {
        return glVersion;
    }

    @Override
    public float getPpiX() {
        return getPpcX() * 2.54f;
    }

    @Override
    public float getPpiY() {
        return getPpcY() * 2.54f;
    }

    @Override
    public float getPpcX() {
        GLFWGLGraphics.NativeMonitor monitor = (GLFWGLGraphics.NativeMonitor) getMonitor();
        GLFW.getMonitorPhysicalSize(monitor.getMonitorHandle(), tmpBuffer, tmpBuffer2);
        int millimeters = tmpBuffer[0];
        return millimeters == 0 ? 0 : getDisplayMode().width / (float) millimeters * 10;
    }

    @Override
    public float getPpcY() {
        GLFWGLGraphics.NativeMonitor monitor = (GLFWGLGraphics.NativeMonitor) getMonitor();
        GLFW.getMonitorPhysicalSize(monitor.getMonitorHandle(), tmpBuffer, tmpBuffer2);
        int millimeters = tmpBuffer2[0];
        return millimeters == 0 ? 0 : getDisplayMode().height / (float) millimeters * 10;
    }

    @Override
    public boolean supportsDisplayModeChange() {
        return true;
    }

    @Override
    public Monitor getPrimaryMonitor() {
        return GLFWApplicationConfiguration.getPrimaryMonitor();
    }

    @Override
    public Monitor getMonitor() {
        Monitor[] monitors = getMonitors();
        Monitor result = monitors[0];

        GLFW.getWindowPos(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
        int windowX = tmpBuffer[0];
        int windowY = tmpBuffer2[0];
        GLFW.getWindowSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
        int windowWidth = tmpBuffer[0];
        int windowHeight = tmpBuffer2[0];
        int bestOverlap = 0;

        for (Monitor monitor : monitors) {
            DisplayMode mode = getDisplayMode(monitor);
            int overlap = Math.max(0,
                    Math.min(windowX + windowWidth, monitor.virtualX + mode.width) - Math.max(windowX, monitor.virtualX))
                    * Math.max(0,
                            Math.min(windowY + windowHeight, monitor.virtualY + mode.height)
                                    - Math.max(windowY, monitor.virtualY));
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                result = monitor;
            }
        }
        return result;
    }

    @Override
    public Monitor[] getMonitors() {
        return GLFWApplicationConfiguration.getMonitors();
    }

    @Override
    public DisplayMode[] getDisplayModes() {
        return GLFWApplicationConfiguration.getDisplayModes(getMonitor());
    }

    @Override
    public DisplayMode[] getDisplayModes(Monitor monitor) {
        return GLFWApplicationConfiguration.getDisplayModes(monitor);
    }

    @Override
    public DisplayMode getDisplayMode() {
        return GLFWApplicationConfiguration.getDisplayMode(getMonitor());
    }

    @Override
    public DisplayMode getDisplayMode(Monitor monitor) {
        return GLFWApplicationConfiguration.getDisplayMode(monitor);
    }

    @Override
    public int getSafeInsetLeft() {
        return 0;
    }

    @Override
    public int getSafeInsetTop() {
        return 0;
    }

    @Override
    public int getSafeInsetBottom() {
        return 0;
    }

    @Override
    public int getSafeInsetRight() {
        return 0;
    }

    @Override
    public boolean setFullscreenMode(DisplayMode displayMode) {
        window.getInput().resetPollingStates();
        GLFWGLGraphics.NativeDisplayMode newMode = (GLFWGLGraphics.NativeDisplayMode) displayMode;
        if (isFullscreen()) {
            GLFWGLGraphics.NativeDisplayMode currentMode = (GLFWGLGraphics.NativeDisplayMode) getDisplayMode();
            if (currentMode.getMonitor() == newMode.getMonitor()
                    && currentMode.refreshRate == newMode.refreshRate) {
                GLFW.setWindowSize(window.getWindowHandle(), newMode.width, newMode.height);
            } else {
                GLFW.setWindowMonitor(window.getWindowHandle(), newMode.getMonitor(), 0, 0,
                        newMode.width, newMode.height, newMode.refreshRate);
            }
        } else {
            storeCurrentWindowPositionAndDisplayMode();
            GLFW.setWindowMonitor(window.getWindowHandle(), newMode.getMonitor(), 0, 0,
                    newMode.width, newMode.height, newMode.refreshRate);
        }
        updateFramebufferInfo();
        resizeSurfaceIfReady();
        return true;
    }

    private void storeCurrentWindowPositionAndDisplayMode() {
        windowPosXBeforeFullscreen = window.getPositionX();
        windowPosYBeforeFullscreen = window.getPositionY();
        windowWidthBeforeFullscreen = logicalWidth;
        windowHeightBeforeFullscreen = logicalHeight;
        displayModeBeforeFullscreen = getDisplayMode();
    }

    @Override
    public boolean setWindowedMode(int width, int height) {
        window.getInput().resetPollingStates();
        if (!isFullscreen()) {
            if (width != logicalWidth || height != logicalHeight) {
                GridPoint2 newPosition = GLFWApplicationConfiguration.calculateCenteredWindowPosition(
                        (GLFWGLGraphics.NativeMonitor) getMonitor(), width, height);
                GLFW.setWindowSize(window.getWindowHandle(), width, height);
                window.setPosition(newPosition.x, newPosition.y);
            }
        } else {
            if (displayModeBeforeFullscreen == null) {
                storeCurrentWindowPositionAndDisplayMode();
            }
            if (width != windowWidthBeforeFullscreen || height != windowHeightBeforeFullscreen) {
                GridPoint2 newPosition = GLFWApplicationConfiguration.calculateCenteredWindowPosition(
                        (GLFWGLGraphics.NativeMonitor) getMonitor(), width, height);
                GLFW.setWindowMonitor(window.getWindowHandle(), 0, newPosition.x, newPosition.y,
                        width, height, displayModeBeforeFullscreen.refreshRate);
            } else {
                GLFW.setWindowMonitor(window.getWindowHandle(), 0,
                        windowPosXBeforeFullscreen, windowPosYBeforeFullscreen,
                        width, height, displayModeBeforeFullscreen.refreshRate);
            }
        }
        updateFramebufferInfo();
        resizeSurfaceIfReady();
        return true;
    }

    @Override
    public void setTitle(String title) {
        GLFW.setWindowTitle(window.getWindowHandle(), title == null ? "" : title);
    }

    @Override
    public void setUndecorated(boolean undecorated) {
        window.getConfig().setDecorated(!undecorated);
        GLFW.setWindowAttrib(window.getWindowHandle(), GLFW.GLFW_DECORATED,
                undecorated ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
    }

    @Override
    public void setResizable(boolean resizable) {
        window.getConfig().setResizable(resizable);
        GLFW.setWindowAttrib(window.getWindowHandle(), GLFW.GLFW_RESIZABLE,
                resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
    }

    @Override
    public void setVSync(boolean vsync) {
        window.getConfig().vSyncEnabled = vsync;
        WebGPUApplication currentContext = context;
        if (currentContext != null) {
            currentContext.setVSync(vsync);
        }
    }

    @Override
    public void setForegroundFPS(int fps) {
        window.getConfig().foregroundFPS = fps;
    }

    @Override
    public BufferFormat getBufferFormat() {
        return bufferFormat;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return false;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous) {
        continuousRendering = isContinuous;
    }

    @Override
    public boolean isContinuousRendering() {
        return continuousRendering;
    }

    @Override
    public void requestRendering() {
        window.requestRendering();
    }

    @Override
    public boolean isFullscreen() {
        return GLFW.getWindowMonitor(window.getWindowHandle()) != 0;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
        return new GLFWCursor(window, pixmap, xHotspot, yHotspot);
    }

    @Override
    public void setCursor(Cursor cursor) {
        GLFW.setCursor(window.getWindowHandle(), ((GLFWCursor) cursor).glfwCursor);
    }

    @Override
    public void setSystemCursor(SystemCursor systemCursor) {
        GLFWCursor.setSystemCursor(window.getWindowHandle(), systemCursor);
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        GLFW.setFramebufferSizeCallback(window.getWindowHandle(), null);
        if (context != null) {
            context.dispose();
        }
        if (instance != null) {
            instance.release();
            instance.dispose();
        }
    }
}

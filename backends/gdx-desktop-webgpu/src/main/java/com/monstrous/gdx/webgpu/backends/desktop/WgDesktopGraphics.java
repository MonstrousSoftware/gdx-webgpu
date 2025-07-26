/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
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

package com.monstrous.gdx.webgpu.backends.desktop;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.HdpiMode;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;

import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUSurface;
import com.github.xpenatan.webgpu.WGPUSurfaceCapabilities;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.utils.WgGL20;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Configuration;

import java.nio.IntBuffer;
import static org.lwjgl.glfw.GLFW.GLFW_PLATFORM_WAYLAND;
import static org.lwjgl.glfw.GLFW.glfwGetPlatform;
import static org.lwjgl.glfw.GLFWNativeWayland.glfwGetWaylandDisplay;
import static org.lwjgl.glfw.GLFWNativeX11.glfwGetX11Display;

public class WgDesktopGraphics implements WgGraphics, Disposable {

	final WgDesktopWindow window;
	final WgDesktopApplication app;
	GL20 gl20;
	private GL30 gl30;
	private GL31 gl31;
	private GL32 gl32;
	private GLVersion glVersion;
	private volatile int backBufferWidth;
	private volatile int backBufferHeight;
	private volatile int logicalWidth;
	private volatile int logicalHeight;
	private volatile boolean isContinuous = true;
	private BufferFormat bufferFormat;
	private long lastFrameTime = -1;
	private float deltaTime;
	private boolean resetDeltaTime = false;
	private long frameId;
	private long frameCounterStart = 0;
	private int frames;
	private int fps;
	private int windowPosXBeforeFullscreen;
	private int windowPosYBeforeFullscreen;
	private int windowWidthBeforeFullscreen;
	private int windowHeightBeforeFullscreen;
	private DisplayMode displayModeBeforeFullscreen = null;
	public final WebGPUApplication context;


	final IntBuffer tmpBuffer = BufferUtils.createIntBuffer(1);
	final IntBuffer tmpBuffer2 = BufferUtils.createIntBuffer(1);

	final GLFWFramebufferSizeCallback resizeCallback = new GLFWFramebufferSizeCallback() {
		@Override
		public void invoke (long windowHandle, final int width, final int height) {
			if (!"glfw_async".equals(Configuration.GLFW_LIBRARY_NAME.get())) {
                System.out.println("resize callback");
				updateFramebufferInfo();
				if (!window.isListenerInitialized()) {
					return;
				}
				window.makeCurrent();
                System.out.println("context resize");
				context.resize(getWidth(), getHeight());
			} else {
				window.asyncResized = true;
                System.out.println("Window.async resized");
			}
		}
	};

	public WgDesktopGraphics(WgDesktopWindow window, long win32handle) {

		this.window = window;

		this.gl20 = new WgGL20();
		this.gl30 = null;
		this.gl31 = null;
		this.gl32 = null;
		updateFramebufferInfo();

		app = (WgDesktopApplication) Gdx.app;
		Gdx.graphics = this;
        Gdx.gl = this.gl20;

		WebGPUApplication.Configuration config = new WebGPUApplication.Configuration(
            app.getConfiguration().samples,
			app.getConfiguration().vSyncEnabled,
            app.getConfiguration().enableGPUtiming,
            app.getConfiguration().backend );

        this.context = new WebGPUApplication(config, new WebGPUApplication.OnInitCallback() {
            @Override
            public void onInit(WebGPUApplication application) {
                if(application.isReady()) {
                    System.out.println("Creating surface for window handle: "+win32handle);
                    application.surface = createSurface(application.instance, win32handle);

                    if(application.surface != null) {
                        System.out.println("surface:" + application.surface);
                        System.out.println("Surface created");
                        // Find out the preferred surface format of the window
                        // = the first one listed under capabilities
                        WGPUSurfaceCapabilities surfaceCapabilities = WGPUSurfaceCapabilities.obtain();
                        application.surface.getCapabilities(application.adapter, surfaceCapabilities);
                        application.surfaceFormat = surfaceCapabilities.getFormats().get(0);
                        System.out.println("surfaceFormat: " + application.surfaceFormat);

                        // Release the adapter only after it has been fully utilized
                        application.adapter.release();
                        application.adapter.dispose();
                        application.adapter = null;
                    }
                    else {
                        System.out.println("Surface not created");
                    }

                    // todo webGPU.wgpuInstanceRelease(instance); // we can release the instance now that we have the device
                    // do we need instance for processEvents?
                }
                else {
                    throw new RuntimeException("Failed to initialize WebGPU");
                }
            }
        });

        context.resize(getWidth(), getHeight());

        GLFW.glfwSetFramebufferSizeCallback(window.getWindowHandle(), resizeCallback);
    }

    private WGPUSurface createSurface(WGPUInstance instance, long windowHandle) {
        WGPUSurface surface = null;
        String osName = System.getProperty("os.name").toLowerCase();
        if(osName.contains("win")) {
            long display = GLFWNativeWin32.glfwGetWin32Window(windowHandle);
            surface = instance.createWindowsSurface(display);
        }
        else if(osName.contains("linux")) {
            if(glfwGetPlatform() == GLFW_PLATFORM_WAYLAND) {
                long display = glfwGetWaylandDisplay();
                surface = instance.createLinuxSurface(true, windowHandle, display);
            }
            else {
                long display = glfwGetX11Display();
                surface = instance.createLinuxSurface(false, windowHandle, display);
            }
        }
        else if(osName.contains("mac")) {
            surface = instance.createMacSurface(windowHandle);
        }
        return surface;
    }

    @Override
    public WebGPUApplication getContext() {
        return context;
    }

	public WgDesktopWindow getWindow () {
		return window;
	}


    void updateFramebufferInfo () {
		GLFW.glfwGetFramebufferSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		this.backBufferWidth = tmpBuffer.get(0);
		this.backBufferHeight = tmpBuffer2.get(0);
		GLFW.glfwGetWindowSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		WgDesktopGraphics.this.logicalWidth = tmpBuffer.get(0);
		WgDesktopGraphics.this.logicalHeight = tmpBuffer2.get(0);
		WgDesktopApplicationConfiguration config = window.getConfig();
		bufferFormat = new BufferFormat(config.r, config.g, config.b, config.a, config.depth, config.stencil, config.samples,
			false);
	}

	void update () {
        context.update();

		long time = System.nanoTime();
		if (lastFrameTime == -1) lastFrameTime = time;
		if (resetDeltaTime) {
			resetDeltaTime = false;
			deltaTime = 0;
		} else
			deltaTime = (time - lastFrameTime) / 1000000000.0f;
		lastFrameTime = time;

		if (time - frameCounterStart >= 1000000000) {
			fps = frames;
			frames = 0;
			frameCounterStart = time;

            // allow context class to gather 1-second stats
            context.secondsTick();
		}
		frames++;
		frameId++;
	}

	@Override
	public boolean isGL30Available () {
		return gl30 != null;
	}

	@Override
	public boolean isGL31Available () {
		return gl31 != null;
	}

	@Override
	public boolean isGL32Available () {
		return gl32 != null;
	}

	@Override
	public GL20 getGL20 () {
		return gl20;
	}

	@Override
	public GL30 getGL30 () {
		return gl30;
	}

	@Override
	public GL31 getGL31 () {
		return gl31;
	}

	@Override
	public GL32 getGL32 () {
		return gl32;
	}

	@Override
	public void setGL20 (GL20 gl20) {
		this.gl20 = gl20;
	}

	@Override
	public void setGL30 (GL30 gl30) {
		this.gl30 = gl30;
	}

	@Override
	public void setGL31 (GL31 gl31) {
		this.gl31 = gl31;
	}

	@Override
	public void setGL32 (GL32 gl32) {
		this.gl32 = gl32;
	}

	@Override
	public int getWidth () {
		if (window.getConfig().hdpiMode == HdpiMode.Pixels) {
			return backBufferWidth;
		} else {
			return logicalWidth;
		}
	}

	@Override
	public int getHeight () {
		if (window.getConfig().hdpiMode == HdpiMode.Pixels) {
			return backBufferHeight;
		} else {
			return logicalHeight;
		}
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
    public float getBackBufferScale () {
        return getBackBufferWidth() / (float)getWidth();
    }


    public int getLogicalWidth () {
		return logicalWidth;
	}

	public int getLogicalHeight () {
		return logicalHeight;
	}

	@Override
	public long getFrameId () {
		return frameId;
	}

	@Override
	public float getDeltaTime () {
		return deltaTime;
	}

    @Override
    @Deprecated
    public float getRawDeltaTime() {
        return 0;
    }

    public void resetDeltaTime () {
		resetDeltaTime = true;
	}

	@Override
	public int getFramesPerSecond () {
		return fps;
	}

	@Override
	public GraphicsType getType () {
		return GraphicsType.LWJGL3;
	}

	@Override
	public GLVersion getGLVersion () {
		return glVersion;
	}

	@Override
	public float getPpiX () {
		return getPpcX() * 2.54f;
	}

	@Override
	public float getPpiY () {
		return getPpcY() * 2.54f;
	}

	@Override
	public float getPpcX () {
		WebGPUMonitor monitor = (WebGPUMonitor)getMonitor();
		GLFW.glfwGetMonitorPhysicalSize(monitor.monitorHandle, tmpBuffer, tmpBuffer2);
		int sizeX = tmpBuffer.get(0);
		DisplayMode mode = getDisplayMode();
		return mode.width / (float)sizeX * 10;
	}

	@Override
	public float getPpcY () {
		WebGPUMonitor monitor = (WebGPUMonitor)getMonitor();
		GLFW.glfwGetMonitorPhysicalSize(monitor.monitorHandle, tmpBuffer, tmpBuffer2);
		int sizeY = tmpBuffer2.get(0);
		DisplayMode mode = getDisplayMode();
		return mode.height / (float)sizeY * 10;
	}

    @Override
    public float getDensity() {
        return 0;
    }

    @Override
	public boolean supportsDisplayModeChange () {
		return true;
	}

	@Override
	public Monitor getPrimaryMonitor () {
		return WgDesktopApplicationConfiguration.toWebGPUMonitor(GLFW.glfwGetPrimaryMonitor());
	}

	@Override
	public Monitor getMonitor () {
		Monitor[] monitors = getMonitors();
		Monitor result = monitors[0];

		GLFW.glfwGetWindowPos(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		int windowX = tmpBuffer.get(0);
		int windowY = tmpBuffer2.get(0);
		GLFW.glfwGetWindowSize(window.getWindowHandle(), tmpBuffer, tmpBuffer2);
		int windowWidth = tmpBuffer.get(0);
		int windowHeight = tmpBuffer2.get(0);
		int overlap;
		int bestOverlap = 0;

		for (Monitor monitor : monitors) {
			DisplayMode mode = getDisplayMode(monitor);

			overlap = Math.max(0,
				Math.min(windowX + windowWidth, monitor.virtualX + mode.width) - Math.max(windowX, monitor.virtualX))
				* Math.max(0, Math.min(windowY + windowHeight, monitor.virtualY + mode.height) - Math.max(windowY, monitor.virtualY));

			if (bestOverlap < overlap) {
				bestOverlap = overlap;
				result = monitor;
			}
		}
		return result;
	}

	@Override
	public Monitor[] getMonitors () {
		PointerBuffer glfwMonitors = GLFW.glfwGetMonitors();
        assert glfwMonitors != null;
        Monitor[] monitors = new Monitor[glfwMonitors.limit()];
		for (int i = 0; i < glfwMonitors.limit(); i++) {
			monitors[i] = WgDesktopApplicationConfiguration.toWebGPUMonitor(glfwMonitors.get(i));
		}
		return monitors;
	}

	@Override
	public DisplayMode[] getDisplayModes () {
		return WgDesktopApplicationConfiguration.getDisplayModes(getMonitor());
	}

	@Override
	public DisplayMode[] getDisplayModes (Monitor monitor) {
		return WgDesktopApplicationConfiguration.getDisplayModes(monitor);
	}

	@Override
	public DisplayMode getDisplayMode () {
		return WgDesktopApplicationConfiguration.getDisplayMode(getMonitor());
	}

	@Override
	public DisplayMode getDisplayMode (Monitor monitor) {
		return WgDesktopApplicationConfiguration.getDisplayMode(monitor);
	}

	@Override
	public int getSafeInsetLeft () {
		return 0;
	}

	@Override
	public int getSafeInsetTop () {
		return 0;
	}

	@Override
	public int getSafeInsetBottom () {
		return 0;
	}

	@Override
	public int getSafeInsetRight () {
		return 0;
	}

	@Override
	public boolean setFullscreenMode (DisplayMode displayMode) {
        System.out.println("setFullScreenMode()");


		window.getInput().resetPollingStates();
		WebGPUDisplayMode newMode = (WebGPUDisplayMode)displayMode;
		if (isFullscreen()) {
			WebGPUDisplayMode currentMode = (WebGPUDisplayMode)getDisplayMode();
			if (currentMode.getMonitor() == newMode.getMonitor() && currentMode.refreshRate == newMode.refreshRate) {
				// same monitor and refresh rate
				GLFW.glfwSetWindowSize(window.getWindowHandle(), newMode.width, newMode.height);
			} else {
				// different monitor and/or refresh rate
				GLFW.glfwSetWindowMonitor(window.getWindowHandle(), newMode.getMonitor(), 0, 0, newMode.width, newMode.height,
					newMode.refreshRate);
			}
		} else {
			// store window position so we can restore it when switching from fullscreen to windowed later
			storeCurrentWindowPositionAndDisplayMode();

            //context.drop(); // TEST

            System.out.println("calling glfwSetWindowMonitor()");
			// switch from windowed to fullscreen
            // this will trigger a resize before we return
			GLFW.glfwSetWindowMonitor(window.getWindowHandle(), newMode.getMonitor(), 0, 0, newMode.width, newMode.height,
				newMode.refreshRate);

            System.out.println("after glfwSetWindowMonitor()");

            // probably don't need this
            System.out.println("setFullScreenMode: set viewport: 0,0, "+backBufferWidth+", "+backBufferHeight);
            context.setViewportRectangle(0, 0, backBufferWidth, backBufferHeight);
		}
		updateFramebufferInfo();

		setVSync(window.getConfig().vSyncEnabled);

		return true;
	}

	private void storeCurrentWindowPositionAndDisplayMode () {
		windowPosXBeforeFullscreen = window.getPositionX();
		windowPosYBeforeFullscreen = window.getPositionY();
		windowWidthBeforeFullscreen = logicalWidth;
		windowHeightBeforeFullscreen = logicalHeight;
		displayModeBeforeFullscreen = getDisplayMode();
	}

	@Override
	public boolean setWindowedMode (int width, int height) {
		window.getInput().resetPollingStates();
		if (!isFullscreen()) {
			GridPoint2 newPos = null;
			boolean centerWindow = false;
			if (width != logicalWidth || height != logicalHeight) {
				centerWindow = true; // recenter the window since its size changed
				newPos = WgDesktopApplicationConfiguration.calculateCenteredWindowPosition((WebGPUMonitor)getMonitor(), width, height);
			}
			GLFW.glfwSetWindowSize(window.getWindowHandle(), width, height);
			if (centerWindow) {
				window.setPosition(newPos.x, newPos.y); // on macOS the centering has to happen _after_ the new window size was set
			}
		} else { // if we were in fullscreen mode, we should consider restoring a previous display mode
			if (displayModeBeforeFullscreen == null) {
				storeCurrentWindowPositionAndDisplayMode();
			}
			if (width != windowWidthBeforeFullscreen || height != windowHeightBeforeFullscreen) { // center the window since its size
				// changed
				GridPoint2 newPos = WgDesktopApplicationConfiguration.calculateCenteredWindowPosition((WebGPUMonitor)getMonitor(), width,
					height);
				GLFW.glfwSetWindowMonitor(window.getWindowHandle(), 0, newPos.x, newPos.y, width, height,
					displayModeBeforeFullscreen.refreshRate);
			} else { // restore previous position
				GLFW.glfwSetWindowMonitor(window.getWindowHandle(), 0, windowPosXBeforeFullscreen, windowPosYBeforeFullscreen, width,
					height, displayModeBeforeFullscreen.refreshRate);
			}
		}
		updateFramebufferInfo();
		return true;
	}

	@Override
	public void setTitle (String title) {
		if (title == null) {
			title = "";
		}
		GLFW.glfwSetWindowTitle(window.getWindowHandle(), title);
	}

	@Override
	public void setUndecorated (boolean undecorated) {
		getWindow().getConfig().setDecorated(!undecorated);
		GLFW.glfwSetWindowAttrib(window.getWindowHandle(), GLFW.GLFW_DECORATED, undecorated ? GLFW.GLFW_FALSE : GLFW.GLFW_TRUE);
	}

	@Override
	public void setResizable (boolean resizable) {
		getWindow().getConfig().setResizable(resizable);
		GLFW.glfwSetWindowAttrib(window.getWindowHandle(), GLFW.GLFW_RESIZABLE, resizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
	}

	@Override
	public void setVSync (boolean vsync) {
		getWindow().getConfig().vSyncEnabled = vsync;
//		GLFW.glfwSwapInterval(vsync ? 1 : 0);
	}

	/** Sets the target framerate for the application, when using continuous rendering. Must be positive. The cpu sleeps as needed.
	 * Use 0 to never sleep. If there are multiple windows, the value for the first window created is used for all. Default is 0.
	 *
	 * @param fps fps */
	@Override
	public void setForegroundFPS (int fps) {
		getWindow().getConfig().foregroundFPS = fps;
	}

	@Override
	public BufferFormat getBufferFormat () {
		return bufferFormat;
	}

	@Override
	public boolean supportsExtension (String extension) {
		return GLFW.glfwExtensionSupported(extension);
	}

	@Override
	public void setContinuousRendering (boolean isContinuous) {
		this.isContinuous = isContinuous;
	}

	@Override
	public boolean isContinuousRendering () {
		return isContinuous;
	}

	@Override
	public void requestRendering () {
		window.requestRendering();
	}

	@Override
	public boolean isFullscreen () {
		return GLFW.glfwGetWindowMonitor(window.getWindowHandle()) != 0;
	}

	@Override
	public Cursor newCursor (Pixmap pixmap, int xHotspot, int yHotspot) {
		return new WgDesktopCursor(getWindow(), pixmap, xHotspot, yHotspot);
	}

	@Override
	public void setCursor (Cursor cursor) {
		GLFW.glfwSetCursor(getWindow().getWindowHandle(), ((WgDesktopCursor)cursor).glfwCursor);
	}

	@Override
	public void setSystemCursor (SystemCursor systemCursor) {
		WgDesktopCursor.setSystemCursor(getWindow().getWindowHandle(), systemCursor);
	}

	@Override
	public void dispose () {
		this.resizeCallback.free();
		context.dispose();
	}

	public static class WebGPUDisplayMode extends DisplayMode {
		final long monitorHandle;

		WebGPUDisplayMode (long monitor, int width, int height, int refreshRate, int bitsPerPixel) {
			super(width, height, refreshRate, bitsPerPixel);
			this.monitorHandle = monitor;
		}

		public long getMonitor () {
			return monitorHandle;
		}
	}

	public static class WebGPUMonitor extends Monitor {
		final long monitorHandle;

		WebGPUMonitor (long monitor, int virtualX, int virtualY, String name) {
			super(virtualX, virtualY, name);
			this.monitorHandle = monitor;
		}

		public long getMonitorHandle () {
			return monitorHandle;
		}
	}
}

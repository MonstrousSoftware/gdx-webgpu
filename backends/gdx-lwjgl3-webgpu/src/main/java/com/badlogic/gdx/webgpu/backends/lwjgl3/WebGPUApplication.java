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

package com.badlogic.gdx.webgpu.backends.lwjgl3;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.backends.lwjgl3.audio.Lwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.OpenALLwjgl3Audio;
import com.badlogic.gdx.backends.lwjgl3.audio.mock.MockAudio;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.WebGPU_JNI;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.*;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.system.Callback;

import java.io.File;

import static org.lwjgl.glfw.GLFW.GLFW_CLIENT_API;
import static org.lwjgl.glfw.GLFW.GLFW_NO_API;

public class  WebGPUApplication implements WebGPUApplicationBase {
	private final WebGPUApplicationConfiguration config;
	final Array<WebGPUWindow> windows = new Array<WebGPUWindow>();
	private volatile WebGPUWindow currentWindow;
	private Lwjgl3Audio audio;
	private final Files files;
	private final Net net;
	private final ObjectMap<String, Preferences> preferences = new ObjectMap<String, Preferences>();
	private final Lwjgl3Clipboard clipboard;
	private int logLevel = LOG_INFO;
	private ApplicationLogger applicationLogger;
	private volatile boolean running = true;
	private final Array<Runnable> runnables = new Array<Runnable>();
	private final Array<Runnable> executedRunnables = new Array<Runnable>();
	private final Array<LifecycleListener> lifecycleListeners = new Array<LifecycleListener>();
	private static GLFWErrorCallback errorCallback;
	private static GLVersion glVersion;
	private static Callback glDebugCallback;
	private final Sync sync;




	private WebGPU_JNI webGPU;

	static void initializeGlfw () {
		if (errorCallback == null) {
			Lwjgl3NativesLoader.load();
			errorCallback = GLFWErrorCallback.createPrint(WebGPUApplicationConfiguration.errorStream);
			GLFW.glfwSetErrorCallback(errorCallback);
			if (SharedLibraryLoader.os == Os.MacOsX)
				GLFW.glfwInitHint(GLFW.GLFW_ANGLE_PLATFORM_TYPE, GLFW.GLFW_ANGLE_PLATFORM_TYPE_METAL);
			GLFW.glfwInitHint(GLFW.GLFW_JOYSTICK_HAT_BUTTONS, GLFW.GLFW_FALSE);
			if (!GLFW.glfwInit()) {
				throw new GdxRuntimeException("Unable to initialize GLFW");
			}
		}
	}

	public WebGPUApplication (ApplicationListener listener) {
		this(listener, new WebGPUApplicationConfiguration());
	}

	public WebGPUApplication (ApplicationListener listener, WebGPUApplicationConfiguration config) {

		initializeGlfw();
		setApplicationLogger(new Lwjgl3ApplicationLogger());
		webGPU = JavaWebGPU.init();

		this.config = config; // = WebGPUApplicationConfiguration.copy(config);
		if (config.title == null) config.title = listener.getClass().getSimpleName();

		Gdx.app = this;
		if (!config.disableAudio) {
			try {
				this.audio = createAudio(config);
			} catch (Throwable t) {
				log("WebGPUApplication", "Couldn't initialize audio, disabling audio", t);
				this.audio = new MockAudio();
			}
		} else {
			this.audio = new MockAudio();
		}
		Gdx.audio = audio;
		this.files = Gdx.files = createFiles();
		Lwjgl3ApplicationConfiguration lwConfig = new Lwjgl3ApplicationConfiguration();
		lwConfig.setMaxNetThreads(config.maxNetThreads);
		this.net = Gdx.net = new Lwjgl3Net(lwConfig);
		this.clipboard = new Lwjgl3Clipboard();

		this.sync = new Sync();

		createWindow(config, listener);


		try {
			loop();
			cleanupWindows();
		} catch (Throwable t) {
			if (t instanceof RuntimeException)
				throw (RuntimeException)t;
			else
				throw new GdxRuntimeException(t);
		} finally {
			cleanup();
		}
	}

	protected void loop () {
		Array<WebGPUWindow> closedWindows = new Array<WebGPUWindow>();
		while (running && windows.size > 0) {
			// FIXME put it on a separate thread
			audio.update();

			boolean haveWindowsRendered = false;
			closedWindows.clear();
			int targetFramerate = -2;
			for (WebGPUWindow window : windows) {
				window.makeCurrent();
				currentWindow = window;

				if (targetFramerate == -2) targetFramerate = window.getConfig().foregroundFPS;
				synchronized (lifecycleListeners) {
					haveWindowsRendered |= window.update();
				}
				if (window.shouldClose()) {
					closedWindows.add(window);
				}
			}
			GLFW.glfwPollEvents();

			boolean shouldRequestRendering;
			synchronized (runnables) {
				shouldRequestRendering = runnables.size > 0;
				executedRunnables.clear();
				executedRunnables.addAll(runnables);
				runnables.clear();
			}
			for (Runnable runnable : executedRunnables) {
				runnable.run();
			}
			if (shouldRequestRendering) {
				// Must follow Runnables execution so changes done by Runnables are reflected
				// in the following render.
				for (WebGPUWindow window : windows) {
					if (!window.getGraphics().isContinuousRendering()) window.requestRendering();
				}
			}

			for (WebGPUWindow closedWindow : closedWindows) {
				if (windows.size == 1) {
					// Lifecycle listener methods have to be called before ApplicationListener methods. The
					// application will be disposed when _all_ windows have been disposed, which is the case,
					// when there is only 1 window left, which is in the process of being disposed.
					for (int i = lifecycleListeners.size - 1; i >= 0; i--) {
						LifecycleListener l = lifecycleListeners.get(i);
						l.pause();
						l.dispose();
					}
					lifecycleListeners.clear();
				}
				closedWindow.dispose();

				windows.removeValue(closedWindow, false);
			}

			if (!haveWindowsRendered) {
				// Sleep a few milliseconds in case no rendering was requested
				// with continuous rendering disabled.
				try {
					Thread.sleep(1000 / config.idleFPS);
				} catch (InterruptedException e) {
					// ignore
				}
			} else if (targetFramerate > 0) {
				sync.sync(targetFramerate); // sleep as needed to meet the target framerate
			}
		}
	}

	protected void cleanupWindows () {
		synchronized (lifecycleListeners) {
			for (LifecycleListener lifecycleListener : lifecycleListeners) {
				lifecycleListener.pause();
				lifecycleListener.dispose();
			}
		}
		for (WebGPUWindow window : windows) {
			window.dispose();
		}
		windows.clear();
	}

	protected void cleanup () {
		WebGPUCursor.disposeSystemCursors();
		audio.dispose();
		errorCallback.free();
		errorCallback = null;
		if (glDebugCallback != null) {
			glDebugCallback.free();
			glDebugCallback = null;
		}
		GLFW.glfwTerminate();
	}

	@Override
	public ApplicationListener getApplicationListener () {
		return currentWindow.getListener();
	}

	@Override
	public Graphics getGraphics () {
		return currentWindow.getGraphics();
	}

	public WebGPU_JNI getWebGPU () {
		return webGPU;
	}


	@Override
	public Audio getAudio () {
		return audio;
	}

	@Override
	public Input getInput () {
		return currentWindow.getInput();
	}

	@Override
	public Files getFiles () {
		return files;
	}

	@Override
	public Net getNet () {
		return net;
	}

	@Override
	public void debug (String tag, String message) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message);
	}

	@Override
	public void debug (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_DEBUG) getApplicationLogger().debug(tag, message, exception);
	}

	@Override
	public void log (String tag, String message) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message);
	}

	@Override
	public void log (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_INFO) getApplicationLogger().log(tag, message, exception);
	}

	@Override
	public void error (String tag, String message) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message);
	}

	@Override
	public void error (String tag, String message, Throwable exception) {
		if (logLevel >= LOG_ERROR) getApplicationLogger().error(tag, message, exception);
	}

	@Override
	public void setLogLevel (int logLevel) {
		this.logLevel = logLevel;
	}

	@Override
	public int getLogLevel () {
		return logLevel;
	}

	@Override
	public void setApplicationLogger (ApplicationLogger applicationLogger) {
		this.applicationLogger = applicationLogger;
	}

	@Override
	public ApplicationLogger getApplicationLogger () {
		return applicationLogger;
	}

	@Override
	public ApplicationType getType () {
		return ApplicationType.Desktop;
	}

	@Override
	public int getVersion () {
		return 0;
	}

	@Override
	public long getJavaHeap () {
		return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
	}

	@Override
	public long getNativeHeap () {
		return getJavaHeap();
	}

	@Override
	public Preferences getPreferences (String name) {
		if (preferences.containsKey(name)) {
			return preferences.get(name);
		} else {
			Preferences prefs = new Lwjgl3Preferences(
				new Lwjgl3FileHandle(new File(config.preferencesDirectory, name), config.preferencesFileType));
			preferences.put(name, prefs);
			return prefs;
		}
	}

	@Override
	public Clipboard getClipboard () {
		return clipboard;
	}

	@Override
	public void postRunnable (Runnable runnable) {
		synchronized (runnables) {
			runnables.add(runnable);
		}
	}

	@Override
	public void exit () {
		running = false;
	}

	@Override
	public void addLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.add(listener);
		}
	}

	@Override
	public void removeLifecycleListener (LifecycleListener listener) {
		synchronized (lifecycleListeners) {
			lifecycleListeners.removeValue(listener, true);
		}
	}

	@Override
	public Lwjgl3Audio createAudio (WebGPUApplicationConfiguration config) {
		return new OpenALLwjgl3Audio(config.audioDeviceSimultaneousSources, config.audioDeviceBufferCount,
			config.audioDeviceBufferSize);
	}

	@Override
	public Lwjgl3Input createInput (WebGPUWindow window) {
		return new DefaultWebGPUInput(window);
	}

	protected Files createFiles () {
		return new Lwjgl3Files();
	}

	public WebGPUApplicationConfiguration getConfiguration(){
		return config;
	}

	/** Creates a new {@link WebGPUWindow} using the provided listener and {@link WebGPUWindowConfiguration}.
	 *
	 * This function only just instantiates a {@link WebGPUWindow} and returns immediately. The actual window creation is postponed
	 * with {@link Application#postRunnable(Runnable)} until after all existing windows are updated. */
	public WebGPUWindow newWindow (ApplicationListener listener, WebGPUWindowConfiguration config) {
		WebGPUApplicationConfiguration appConfig = WebGPUApplicationConfiguration.copy(this.config);
		appConfig.setWindowConfiguration(config);
		if (appConfig.title == null) appConfig.title = listener.getClass().getSimpleName();
		return createWindow(appConfig, listener);
	}

	private WebGPUWindow createWindow (final WebGPUApplicationConfiguration config, ApplicationListener listener) {
		final WebGPUWindow window = new WebGPUWindow(listener, lifecycleListeners, config, this);
		WebGPUWindow save = currentWindow;
		currentWindow = window;
		createWindow(window, config);
		windows.add(window);
		currentWindow = save;
		if (currentWindow != null) {
			// ensure we switch Gdx back to current window in the render loop.
			currentWindow.makeCurrent();
		}
		return window;
	}

	void createWindow (WebGPUWindow window, WebGPUApplicationConfiguration config) {
		long windowHandle = createGlfwWindow(config, 0);
		window.create(windowHandle);
		window.setVisible(config.initialVisible);

// for (int i = 0; i < 2; i++) {
// window.getGraphics().gl20.glClearColor(config.initialBackgroundColor.r, config.initialBackgroundColor.g,
// config.initialBackgroundColor.b, config.initialBackgroundColor.a);
// window.getGraphics().gl20.glClear(GL11.GL_COLOR_BUFFER_BIT);
// GLFW.glfwSwapBuffers(windowHandle);
// }


	}

	static long createGlfwWindow (WebGPUApplicationConfiguration config, long sharedContextWindow) {
		GLFW.glfwDefaultWindowHints();
		GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, config.windowResizable ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, config.windowMaximized ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
		GLFW.glfwWindowHint(GLFW.GLFW_AUTO_ICONIFY, config.autoIconify ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);

		GLFW.glfwWindowHint(GLFW_CLIENT_API, GLFW_NO_API); // because we will use webgpu

// GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, config.r);
// GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, config.g);
// GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, config.b);
// GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, config.a);
// GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, config.stencil);
// GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, config.depth);
// GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, config.samples);
//
// if (config.glEmulation == WebGPUApplicationConfiguration.GLEmulation.GL30
// || config.glEmulation == WebGPUApplicationConfiguration.GLEmulation.GL31
// || config.glEmulation == WebGPUApplicationConfiguration.GLEmulation.GL32) {
// GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, config.gles30ContextMajorVersion);
// GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, config.gles30ContextMinorVersion);
// if (SharedLibraryLoader.os == Os.MacOsX) {
// // hints mandatory on OS X for GL 3.2+ context creation, but fail on Windows if the
// // WGL_ARB_create_context extension is not available
// // see: http://www.glfw.org/docs/latest/compat.html
// GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
// GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
// }
// } else {
// if (config.glEmulation == WebGPUApplicationConfiguration.GLEmulation.ANGLE_GLES20) {
// GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API);
// GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API);
// GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2);
// GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 0);
// }
// }
//
// if (config.transparentFramebuffer) {
// GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE);
// }
//
// if (config.debug) {
// GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE);
// }

		long windowHandle = 0;

		if (config.fullscreenMode != null) {
			GLFW.glfwWindowHint(GLFW.GLFW_REFRESH_RATE, config.fullscreenMode.refreshRate);
			windowHandle = GLFW.glfwCreateWindow(config.fullscreenMode.width, config.fullscreenMode.height, config.title,
				config.fullscreenMode.getMonitor(), sharedContextWindow);

		} else {
			GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, config.windowDecorated ? GLFW.GLFW_TRUE : GLFW.GLFW_FALSE);
			windowHandle = GLFW.glfwCreateWindow(config.windowWidth, config.windowHeight, config.title, 0, sharedContextWindow);

		}
		if (windowHandle == 0) {
			throw new GdxRuntimeException("Couldn't create window");
		}
		WebGPUWindow.setSizeLimits(windowHandle, config.windowMinWidth, config.windowMinHeight, config.windowMaxWidth,
			config.windowMaxHeight);
		if (config.fullscreenMode == null) {
			if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
				if (config.windowX == -1 && config.windowY == -1) { // i.e., center the window
					int windowWidth = Math.max(config.windowWidth, config.windowMinWidth);
					int windowHeight = Math.max(config.windowHeight, config.windowMinHeight);
					if (config.windowMaxWidth > -1) windowWidth = Math.min(windowWidth, config.windowMaxWidth);
					if (config.windowMaxHeight > -1) windowHeight = Math.min(windowHeight, config.windowMaxHeight);

					long monitorHandle = GLFW.glfwGetPrimaryMonitor();
					if (config.windowMaximized && config.maximizedMonitor != null) {
						monitorHandle = config.maximizedMonitor.monitorHandle;
					}

					GridPoint2 newPos = WebGPUApplicationConfiguration.calculateCenteredWindowPosition(
						WebGPUApplicationConfiguration.toWebGPUMonitor(monitorHandle), windowWidth, windowHeight);
					GLFW.glfwSetWindowPos(windowHandle, newPos.x, newPos.y);
				} else {
					GLFW.glfwSetWindowPos(windowHandle, config.windowX, config.windowY);
				}
			}

			if (config.windowMaximized) {
				GLFW.glfwMaximizeWindow(windowHandle);
			}
		}
		if (config.windowIconPaths != null) {
			WebGPUWindow.setIcon(windowHandle, config.windowIconPaths, config.windowIconFileType);
		}

		return windowHandle;
	}


}

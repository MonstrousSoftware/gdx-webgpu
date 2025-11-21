package com.monstrous.gdx.webgpu.backends.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.opengl.GLSurfaceView;
import android.opengl.GLSurfaceView.EGLConfigChooser;
import android.opengl.GLSurfaceView.Renderer;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationBase;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidCursor;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
import com.badlogic.gdx.backends.android.AndroidGL20;
import com.badlogic.gdx.backends.android.AndroidGL30;
import com.badlogic.gdx.backends.android.AndroidGraphics;
import com.badlogic.gdx.backends.android.surfaceview.GLSurfaceView20;
import com.badlogic.gdx.backends.android.surfaceview.GdxEglConfigChooser;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.graphics.Cursor.SystemCursor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.GL31;
import com.badlogic.gdx.graphics.GL32;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.GLVersion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.SnapshotArray;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUAndroidWindow;
import com.github.xpenatan.webgpu.WGPUBackendType;
import com.github.xpenatan.webgpu.WGPUInstance;
import com.github.xpenatan.webgpu.WGPUPowerPreference;
import com.github.xpenatan.webgpu.WGPUSurface;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WebGPUInitialization;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.utils.WgGL20;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

/**
 * An implementation of {@link Graphics} for Android.
 *
 * @author mzechner
 */
public class WgAndroidGraphics extends AbstractGraphics implements WgGraphics, WgSurfaceView.WgRenderer {

    private static final String LOG_TAG = "AndroidGraphics";

    /**
     * When {@link AndroidFragmentApplication#onPause()} or {@link WgAndroidApplication#onPause()} call
     * {@link WgAndroidGraphics#pause()} they <b>MUST</b> enforce continuous rendering. If not, {@link #onDrawFrame()} will not
     * be called in the GLThread while {@link #pause()} is sleeping in the Android UI Thread which will cause the
     * {@link WgAndroidGraphics#pause} variable never be set to false. As a result, the {@link WgAndroidGraphics#pause()} method will
     * kill the current process to avoid ANR
     */
    static volatile boolean enforceContinuousRendering = false;

    final WgSurfaceView view;
    int width;
    int height;
    int safeInsetLeft, safeInsetTop, safeInsetBottom, safeInsetRight;
    AndroidApplicationBase app;
    String extensions;

    protected long lastFrameTime = System.nanoTime();
    protected float deltaTime = 0;
    protected long frameStart = System.nanoTime();
    protected long frameId = -1;
    protected int frames = 0;
    protected int fps;

    volatile boolean created = false;
    volatile boolean running = false;
    volatile boolean pause = false;
    volatile boolean resume = false;
    volatile boolean destroy = false;

    private float ppiX = 0;
    private float ppiY = 0;
    private float ppcX = 0;
    private float ppcY = 0;
    private float density = 1;

    protected final AndroidApplicationConfiguration config;
    private BufferFormat bufferFormat;
    private boolean isContinuous = true;
    private ApplicationListener applicationListener;

    public WebGPUApplication context;

    private WGPUInstance instance;
    private WGPUAndroidWindow androidWindow;
    private WgGL20 gl20;

    public WgAndroidGraphics(AndroidApplicationBase application, AndroidApplicationConfiguration config,
                             ResolutionStrategy resolutionStrategy) {
        this(application, config, resolutionStrategy, true);
    }

    public WgAndroidGraphics(AndroidApplicationBase application, AndroidApplicationConfiguration config,
                             ResolutionStrategy resolutionStrategy, boolean focusableView) {
        bufferFormat = new BufferFormat(config.r, config.g, config.b, config.a, config.depth, config.stencil, config.numSamples,
            config.coverageSampling);
        this.config = config;
        this.app = application;
        applicationListener = app.getApplicationListener();
        view = createGLSurfaceView(application, resolutionStrategy);
        if(focusableView) {
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
        }
    }

    protected WgSurfaceView createGLSurfaceView(AndroidApplicationBase application, final ResolutionStrategy resolutionStrategy) {
        WgSurfaceView view = new WgSurfaceView(application.getContext(), resolutionStrategy);
        return view;
    }

    public void onPauseGLSurfaceView() {
        if(view != null) {
            view.onPause();
        }
    }

    public void onResumeGLSurfaceView() {
        if(view != null) {
            view.onResume();
        }
    }

    protected void updatePpi() {
        DisplayMetrics metrics = new DisplayMetrics();
        app.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        ppiX = metrics.xdpi;
        ppiY = metrics.ydpi;
        ppcX = metrics.xdpi / 2.54f;
        ppcY = metrics.ydpi / 2.54f;
        density = metrics.density;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GL20 getGL20() {
        return gl20;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGL20(GL20 gl20) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isGL30Available() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GL30 getGL30() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setGL30(GL30 gl30) {
    }

    @Override
    public boolean isGL31Available() {
        return false;
    }

    @Override
    public GL31 getGL31() {
        return null;
    }

    @Override
    public void setGL31(GL31 gl31) {

    }

    @Override
    public boolean isGL32Available() {
        return false;
    }

    @Override
    public GL32 getGL32() {
        return null;
    }

    @Override
    public void setGL32(GL32 gl32) {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getHeight() {
        return height;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getBackBufferWidth() {
        return width;
    }

    @Override
    public int getBackBufferHeight() {
        return height;
    }

    @Override
    public void onSurfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        this.width = width;
        this.height = height;
        updatePpi();
        updateSafeAreaInsets();
//        gl.glViewport(0, 0, this.width, this.height);
        if(created == false) {
            applicationListener.create();
            created = true;
            synchronized(this) {
                running = true;
            }
        }
        applicationListener.resize(width, height);
    }

    @Override
    public void surfaceDestroyed() {
    }

    @Override
    public void onSurfaceCreated(Surface surface) {
        instance = WGPU.setupInstance();
        if (!instance.isValid()) {
            throw new RuntimeException("WebGPU: cannot get instance");
        }

        androidWindow = new WGPUAndroidWindow();
        androidWindow.initLogcat();
        androidWindow.createAndroidSurface(surface);
        WGPUSurface androidSurface = instance.createAndroidSurface(androidWindow);

        WebGPUApplication.Configuration configg = new WebGPUApplication.Configuration(1, true, false,
            WebGPUContext.Backend.VULKAN);
        context = new WebGPUApplication(configg, instance, androidSurface);

        WebGPUInitialization.setup(instance, WGPUPowerPreference.HighPerformance, WGPUBackendType.Vulkan, context);

        this.gl20 = new WgGL20();

//        eglContext = ((EGL10)EGLContext.getEGL()).eglGetCurrentContext();
//        setupGL(gl);
//        logConfig(config);
        updatePpi();
        updateSafeAreaInsets();

        Mesh.invalidateAllMeshes(app);
        Texture.invalidateAllTextures(app);
        Cubemap.invalidateAllCubemaps(app);
        TextureArray.invalidateAllTextureArrays(app);
        ShaderProgram.invalidateAllShaderPrograms(app);
        FrameBuffer.invalidateAllFrameBuffers(app);

        logManagedCachesStatus();

        Display display = app.getWindowManager().getDefaultDisplay();
        this.width = display.getWidth();
        this.height = display.getHeight();
        this.lastFrameTime = System.nanoTime();

//        gl.glViewport(0, 0, this.width, this.height);
    }

    protected void logConfig(EGLConfig config) {
        EGL10 egl = (EGL10)EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int r = getAttrib(egl, display, config, EGL10.EGL_RED_SIZE, 0);
        int g = getAttrib(egl, display, config, EGL10.EGL_GREEN_SIZE, 0);
        int b = getAttrib(egl, display, config, EGL10.EGL_BLUE_SIZE, 0);
        int a = getAttrib(egl, display, config, EGL10.EGL_ALPHA_SIZE, 0);
        int d = getAttrib(egl, display, config, EGL10.EGL_DEPTH_SIZE, 0);
        int s = getAttrib(egl, display, config, EGL10.EGL_STENCIL_SIZE, 0);
        int samples = Math.max(getAttrib(egl, display, config, EGL10.EGL_SAMPLES, 0),
            getAttrib(egl, display, config, GdxEglConfigChooser.EGL_COVERAGE_SAMPLES_NV, 0));
        boolean coverageSample = getAttrib(egl, display, config, GdxEglConfigChooser.EGL_COVERAGE_SAMPLES_NV, 0) != 0;

        Gdx.app.log(LOG_TAG, "framebuffer: (" + r + ", " + g + ", " + b + ", " + a + ")");
        Gdx.app.log(LOG_TAG, "depthbuffer: (" + d + ")");
        Gdx.app.log(LOG_TAG, "stencilbuffer: (" + s + ")");
        Gdx.app.log(LOG_TAG, "samples: (" + samples + ")");
        Gdx.app.log(LOG_TAG, "coverage sampling: (" + coverageSample + ")");

        bufferFormat = new BufferFormat(r, g, b, a, d, s, samples, coverageSample);
    }

    int[] value = new int[1];

    private int getAttrib(EGL10 egl, EGLDisplay display, EGLConfig config, int attrib, int defValue) {
        if(egl.eglGetConfigAttrib(display, config, attrib, value)) {
            return value[0];
        }
        return defValue;
    }

    Object synch = new Object();

    void resume() {
        synchronized(synch) {
            running = true;
            resume = true;
        }
    }

    void pause() {
        synchronized(synch) {
            if(!running) return;
            running = false;
            pause = true;

            view.queueEvent(new Runnable() {
                @Override
                public void run() {
                    if(!pause) {
                        // pause event already picked up by onDrawFrame
                        return;
                    }

                    // it's ok to call ApplicationListener's events
                    // from onDrawFrame because it's executing in GL thread
                    onDrawFrame();
                }
            });

            while(pause) {
                try {
                    // Android ANR time is 5 seconds, so wait up to 4 seconds before assuming
                    // deadlock and killing process.
                    synch.wait(4000);
                    if(pause) {
                        // pause will never go false if onDrawFrame is never called by the GLThread
                        // when entering this method, we MUST enforce continuous rendering
                        Gdx.app.error(LOG_TAG, "waiting for pause synchronization took too long; assuming deadlock and killing");
                        android.os.Process.killProcess(android.os.Process.myPid());
                    }
                } catch(InterruptedException ignored) {
                    Gdx.app.log(LOG_TAG, "waiting for pause synchronization failed!");
                }
            }
        }
    }

    void destroy() {
        synchronized(synch) {
            running = false;
            destroy = true;

            while(destroy) {
                try {
                    synch.wait();
                } catch(InterruptedException ex) {
                    Gdx.app.log(LOG_TAG, "waiting for destroy synchronization failed!");
                }
            }
        }
    }

    @Override
    public void onDrawFrame() {
        long time = System.nanoTime();
        // After pause deltaTime can have somewhat huge value that destabilizes the mean, so let's cut it off
        if(!resume) {
            deltaTime = (time - lastFrameTime) / 1000000000.0f;
        }
        else {
            deltaTime = 0;
        }
        lastFrameTime = time;

        boolean lrunning = false;
        boolean lpause = false;
        boolean ldestroy = false;
        boolean lresume = false;

        synchronized(synch) {
            lrunning = running;
            lpause = pause;
            ldestroy = destroy;
            lresume = resume;

            if(resume) {
                resume = false;
            }

            if(pause) {
                pause = false;
                synch.notifyAll();
            }

            if(destroy) {
                destroy = false;
                synch.notifyAll();
            }
        }

        if(lresume) {
            SnapshotArray<LifecycleListener> lifecycleListeners = app.getLifecycleListeners();
            synchronized(lifecycleListeners) {
                LifecycleListener[] listeners = lifecycleListeners.begin();
                for(int i = 0, n = lifecycleListeners.size; i < n; ++i) {
                    listeners[i].resume();
                }
                lifecycleListeners.end();
            }
            applicationListener.resume();
            Gdx.app.log(LOG_TAG, "resumed");
        }

        if(lrunning) {
            synchronized(app.getRunnables()) {
                app.getExecutedRunnables().clear();
                app.getExecutedRunnables().addAll(app.getRunnables());
                app.getRunnables().clear();
            }

            for(int i = 0; i < app.getExecutedRunnables().size; i++) {
                app.getExecutedRunnables().get(i).run();
            }
            app.getInput().processEvents();
            frameId++;
            applicationListener.render();
        }

        if(lpause) {
            SnapshotArray<LifecycleListener> lifecycleListeners = app.getLifecycleListeners();
            synchronized(lifecycleListeners) {
                LifecycleListener[] listeners = lifecycleListeners.begin();
                for(int i = 0, n = lifecycleListeners.size; i < n; ++i) {
                    listeners[i].pause();
                }
            }
            applicationListener.pause();
            Gdx.app.log(LOG_TAG, "paused");
        }

        if(ldestroy) {
            SnapshotArray<LifecycleListener> lifecycleListeners = app.getLifecycleListeners();
            synchronized(lifecycleListeners) {
                LifecycleListener[] listeners = lifecycleListeners.begin();
                for(int i = 0, n = lifecycleListeners.size; i < n; ++i) {
                    listeners[i].dispose();
                }
            }
            applicationListener.dispose();
            Gdx.app.log(LOG_TAG, "destroyed");
        }

        if(time - frameStart > 1000000000) {
            fps = frames;
            frames = 0;
            frameStart = time;
        }
        frames++;
    }

    @Override
    public long getFrameId() {
        return frameId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public float getDeltaTime() {
        return deltaTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GraphicsType getType() {
        return GraphicsType.AndroidGL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GLVersion getGLVersion() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getFramesPerSecond() {
        return fps;
    }

    public void clearManagedCaches() {
        Mesh.clearAllMeshes(app);
        Texture.clearAllTextures(app);
        Cubemap.clearAllCubemaps(app);
        TextureArray.clearAllTextureArrays(app);
        ShaderProgram.clearAllShaderPrograms(app);
        FrameBuffer.clearAllFrameBuffers(app);

        logManagedCachesStatus();
    }

    protected void logManagedCachesStatus() {
        Gdx.app.log(LOG_TAG, Mesh.getManagedStatus());
        Gdx.app.log(LOG_TAG, Texture.getManagedStatus());
        Gdx.app.log(LOG_TAG, Cubemap.getManagedStatus());
        Gdx.app.log(LOG_TAG, ShaderProgram.getManagedStatus());
        Gdx.app.log(LOG_TAG, FrameBuffer.getManagedStatus());
    }

    public View getView() {
        return view;
    }

    @Override
    public float getPpiX() {
        return ppiX;
    }

    @Override
    public float getPpiY() {
        return ppiY;
    }

    @Override
    public float getPpcX() {
        return ppcX;
    }

    @Override
    public float getPpcY() {
        return ppcY;
    }

    @Override
    public float getDensity() {
        return density;
    }

    @Override
    public boolean supportsDisplayModeChange() {
        return false;
    }

    @Override
    public boolean setFullscreenMode(DisplayMode displayMode) {
        return false;
    }

    @Override
    public Monitor getPrimaryMonitor() {
        return new AndroidMonitor(0, 0, "Primary Monitor");
    }

    @Override
    public Monitor getMonitor() {
        return getPrimaryMonitor();
    }

    @Override
    public Monitor[] getMonitors() {
        return new Monitor[]{getPrimaryMonitor()};
    }

    @Override
    public DisplayMode[] getDisplayModes(Monitor monitor) {
        return getDisplayModes();
    }

    @Override
    public DisplayMode getDisplayMode(Monitor monitor) {
        return getDisplayMode();
    }

    @Override
    public DisplayMode[] getDisplayModes() {
        return new DisplayMode[]{getDisplayMode()};
    }

    @TargetApi(Build.VERSION_CODES.P)
    protected void updateSafeAreaInsets() {
        safeInsetLeft = 0;
        safeInsetTop = 0;
        safeInsetRight = 0;
        safeInsetBottom = 0;

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                DisplayCutout displayCutout = app.getApplicationWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
                if(displayCutout != null) {
                    safeInsetRight = displayCutout.getSafeInsetRight();
                    safeInsetBottom = displayCutout.getSafeInsetBottom();
                    safeInsetTop = displayCutout.getSafeInsetTop();
                    safeInsetLeft = displayCutout.getSafeInsetLeft();
                }
            } // Some Application implementations (such as Live Wallpapers) do not implement Application#getApplicationWindow()
            catch(UnsupportedOperationException e) {
                Gdx.app.log("AndroidGraphics", "Unable to get safe area insets");
            }
        }
    }

    @Override
    public int getSafeInsetLeft() {
        return safeInsetLeft;
    }

    @Override
    public int getSafeInsetTop() {
        return safeInsetTop;
    }

    @Override
    public int getSafeInsetBottom() {
        return safeInsetBottom;
    }

    @Override
    public int getSafeInsetRight() {
        return safeInsetRight;
    }

    @Override
    public boolean setWindowedMode(int width, int height) {
        return false;
    }

    @Override
    public void setTitle(String title) {

    }

    @Override
    public void setUndecorated(boolean undecorated) {
        final int mask = (undecorated) ? 1 : 0;
        app.getApplicationWindow().setFlags(LayoutParams.FLAG_FULLSCREEN, mask);
    }

    @Override
    public void setResizable(boolean resizable) {

    }

    @Override
    public DisplayMode getDisplayMode() {
        Display display;
        DisplayMetrics metrics = new DisplayMetrics();

        DisplayManager displayManager = (DisplayManager)app.getContext().getSystemService(Context.DISPLAY_SERVICE);
        display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        display.getRealMetrics(metrics); // Deprecated but no direct equivalent

        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        int refreshRate = MathUtils.roundPositive(display.getRefreshRate());
        int bitsPerPixel = config.r + config.g + config.b + config.a;

        return new AndroidDisplayMode(width, height, refreshRate, bitsPerPixel);
    }

    @Override
    public BufferFormat getBufferFormat() {
        return bufferFormat;
    }

    @Override
    public void setVSync(boolean vsync) {
    }

    @Override
    public void setForegroundFPS(int fps) {
    }

    @Override
    public boolean supportsExtension(String extension) {
        if(extensions == null) extensions = Gdx.gl.glGetString(GL10.GL_EXTENSIONS);
        return extensions.contains(extension);
    }

    @Override
    public void setContinuousRendering(boolean isContinuous) {
        if(view != null) {
            // ignore setContinuousRendering(false) while pausing
            this.isContinuous = enforceContinuousRendering || isContinuous;
            int renderMode = this.isContinuous ? GLSurfaceView.RENDERMODE_CONTINUOUSLY : GLSurfaceView.RENDERMODE_WHEN_DIRTY;
            view.setRenderMode(renderMode);
        }
    }

    @Override
    public boolean isContinuousRendering() {
        return isContinuous;
    }

    @Override
    public void requestRendering() {
        if(view != null) {
            view.requestRender();
        }
    }

    @Override
    public boolean isFullscreen() {
        return true;
    }

    @Override
    public Cursor newCursor(Pixmap pixmap, int xHotspot, int yHotspot) {
        return null;
    }

    @Override
    public void setCursor(Cursor cursor) {
    }

    @Override
    public void setSystemCursor(SystemCursor systemCursor) {

        // TODO
//        View view = ((AndroidGraphics)app.getGraphics()).getView();
//        AndroidCursor.setSystemCursor(view, systemCursor);
    }

    @Override
    public WebGPUContext getContext() {
        return context;
    }

    private class AndroidDisplayMode extends DisplayMode {
        protected AndroidDisplayMode(int width, int height, int refreshRate, int bitsPerPixel) {
            super(width, height, refreshRate, bitsPerPixel);
        }
    }

    private class AndroidMonitor extends Monitor {
        public AndroidMonitor(int virtualX, int virtualY, String name) {
            super(virtualX, virtualY, name);
        }
    }
}

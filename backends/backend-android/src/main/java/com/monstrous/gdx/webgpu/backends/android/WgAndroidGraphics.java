package com.monstrous.gdx.webgpu.backends.android;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.DisplayCutout;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import com.badlogic.gdx.AbstractGraphics;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.backends.android.AndroidApplicationBase;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.AndroidFragmentApplication;
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

/**
 * An implementation of {@link Graphics} for Android, backed by WebGPU.
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

    public WebGPUApplication context;

    private WGPUInstance instance;
    private WGPUAndroidWindow androidWindow;
    private WgGL20 gl20;

    Object synch = new Object();

    /**
     * Indicates that the owning WgAndroidApplication has completed its init() method,
     * created all subsystems (input, audio, files, net) and wired Gdx.* singletons.
     * Until this is true, the render thread will not execute the main render logic.
     */
    volatile boolean appInitialized = false;

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
        view = createGLSurfaceView(application, resolutionStrategy);
        view.setRenderer(this);
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
        // no-op, we control the GL/WebGPU implementation
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

        if (context != null && context.isReady()) {
            context.resize(width, height);
        }

        if(!created) {
            app.getApplicationListener().create();
            created = true;
            synchronized(this) {
                running = true;
            }
        }
        app.getApplicationListener().resize(width, height);
    }

    @Override
    public void surfaceDestroyed() {
        // Nothing special here; lifecycle is managed via pause/destroy.
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

        WebGPUApplication.Configuration configg = new WebGPUApplication.Configuration(config.numSamples <= 0 ? 1 : config.numSamples,
                true,
                false,
                WebGPUContext.Backend.VULKAN);
        context = new WebGPUApplication(configg, instance, androidSurface);

        WebGPUInitialization.setup(instance, WGPUPowerPreference.HighPerformance, WGPUBackendType.Vulkan, context);

        this.gl20 = new WgGL20();
        Gdx.gl = this.gl20;
        Gdx.gl20 = this.gl20;

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
    }

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

    void markAppInitialized() {
        appInitialized = true;
    }

    @Override
    public void onDrawFrame() {
        // Do not start rendering until WgAndroidApplication has completed init().
        if (!appInitialized) {
            // We still want to advance timing so deltaTime is sensible when we start.
            long now = System.nanoTime();
            lastFrameTime = now;
            frameStart = now;
            return;
        }

        long time = System.nanoTime();
        deltaTime = (time - lastFrameTime) / 1000000000.0f;
        lastFrameTime = time;
        frameId++;

        boolean lrunning;
        boolean lpause;
        boolean ldestroy;
        boolean lresume;

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
            for(int i = 0; i < app.getLifecycleListeners().size; i++) {
                LifecycleListener listener = app.getLifecycleListeners().get(i);
                listener.resume();
            }
            Gdx.app.getApplicationListener().resume();
        }

        if(lrunning) {
            synchronized(app.getRunnables()) {
                app.getExecutedRunnables().clear();
                app.getExecutedRunnables().addAll(app.getRunnables());
                app.getRunnables().clear();
            }

            for(int i = 0; i < app.getExecutedRunnables().size; i++) {
                try {
                    app.getExecutedRunnables().get(i).run();
                } catch(Throwable t) {
                    t.printStackTrace();
                }
            }

            app.getInput().processEvents();

            if (context != null && context.isReady()) {
                context.update();
                context.renderFrame(app.getApplicationListener());
            }
        }

        if(lpause) {
            Gdx.app.getApplicationListener().pause();
            for(int i = 0; i < app.getLifecycleListeners().size; i++) {
                LifecycleListener listener = app.getLifecycleListeners().get(i);
                listener.pause();
            }
        }

        if(ldestroy) {
            Gdx.app.getApplicationListener().dispose();
            for(int i = 0; i < app.getLifecycleListeners().size; i++) {
                LifecycleListener listener = app.getLifecycleListeners().get(i);
                listener.dispose();
            }

            clearManagedCaches();
        }

        frames++;
        if(time - frameStart >= 1000000000L) {
            fps = frames;
            frames = 0;
            frameStart = time;
            if (context != null && context.getGPUTimer() != null) {
                context.secondsTick();
            }
        }
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
            }
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
        if (context != null) {
            context.setVSync(vsync);
        }
    }

    @Override
    public void setForegroundFPS(int fps) {
    }

    @Override
    public boolean supportsExtension(String extension) {
        // WebGPU backend does not expose GL extensions in the usual way; return false by default.
        return false;
    }

    @Override
    public boolean isContinuousRendering() {
        return isContinuous;
    }

    @Override
    public void setContinuousRendering(boolean isContinuous) {
        if(!enforceContinuousRendering) {
            this.isContinuous = isContinuous;
            view.setRenderMode(isContinuous ? WgSurfaceView.RENDERMODE_CONTINUOUSLY : WgSurfaceView.RENDERMODE_WHEN_DIRTY);
            synchronized(synch) {
                synch.notifyAll();
            }
        }
    }

    @Override
    public void requestRendering() {
        view.requestRender();
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

    @Override
    public boolean isReadyForRendering() {
        // Only start the render loop when the Activity has finished init() and we have a listener.
        return appInitialized && app.getApplicationListener() != null;
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

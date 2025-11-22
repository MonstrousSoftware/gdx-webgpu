package com.monstrous.gdx.webgpu.backends.android;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;

public class WgSurfaceView extends SurfaceView implements SurfaceHolder.Callback2 {

    final ResolutionStrategy resolutionStrategy;

    public Input.OnscreenKeyboardType onscreenKeyboardType = Input.OnscreenKeyboardType.Default;

    private Surface surface;

    // Rendering constants and state
    public static final int RENDERMODE_WHEN_DIRTY = 0;
    public static final int RENDERMODE_CONTINUOUSLY = 1;

    private volatile int renderMode = RENDERMODE_CONTINUOUSLY;

    private WgRenderer renderer;

    private final Object renderLock = new Object();
    private RenderThread renderThread;

    private boolean hasSurface = false;
    private boolean paused = false;
    private boolean surfaceSizeChanged = false;
    private boolean requestRender = false;
    private boolean shouldExit = false;
    private int format;
    private int width;
    private int height;

    public WgSurfaceView(Context context, ResolutionStrategy resolutionStrategy) {
        super(context);
        this.resolutionStrategy = resolutionStrategy;
        getHolder().addCallback(this);
    }

    public void setRenderer(WgRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder holder) {
        // On API 26+ this is called when the surface needs to be redrawn.
        // For WHEN_DIRTY mode, just request a render.
        requestRender();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (renderLock) {
            this.format = format;
            this.width = width;
            this.height = height;
            surfaceSizeChanged = true;
            renderLock.notifyAll();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (renderLock) {
            surface = holder.getSurface();
            hasSurface = true;
            shouldExit = false;

            if (renderThread == null) {
                renderThread = new RenderThread();
                renderThread.start();
            } else {
                renderLock.notifyAll();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (renderLock) {
            hasSurface = false;
            renderLock.notifyAll();
        }
        // We don't stop the thread here; it will idle waiting for a new surface
        // and will be terminated from onPause() / onDestroy lifecycle if needed.
        if (renderer != null) {
            renderer.surfaceDestroyed();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        ResolutionStrategy.MeasuredDimension measures = resolutionStrategy.calcMeasures(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measures.width, measures.height);
    }

    public void onPause() {
        synchronized (renderLock) {
            paused = true;
            shouldExit = true;
            renderLock.notifyAll();
        }
        if (renderThread != null) {
            try {
                renderThread.join();
            } catch (InterruptedException ignored) {
            }
            renderThread = null;
        }
    }

    public void onResume() {
        synchronized (renderLock) {
            paused = false;
            shouldExit = false;
            if (renderThread == null && hasSurface) {
                renderThread = new RenderThread();
                renderThread.start();
            }
            renderLock.notifyAll();
        }
    }

    public void queueEvent(Runnable r) {
        // For compatibility with AndroidGraphics, runnables are executed on the render thread.
        // We implement a simple queue inside RenderThread.
        if (renderThread != null) {
            renderThread.queueEvent(r);
        }
    }

    public void requestRender() {
        synchronized (renderLock) {
            requestRender = true;
            renderLock.notifyAll();
        }
    }

    public void setRenderMode(int renderMode) {
        if (renderMode != RENDERMODE_WHEN_DIRTY && renderMode != RENDERMODE_CONTINUOUSLY)
            throw new IllegalArgumentException("renderMode");
        synchronized (renderLock) {
            this.renderMode = renderMode;
            renderLock.notifyAll();
        }
    }

    public int getRenderMode() {
        return renderMode;
    }

    private final class RenderThread extends Thread {

        private final java.util.ArrayDeque<Runnable> eventQueue = new java.util.ArrayDeque<>();

        RenderThread() {
            setName("WgSurfaceView-RenderThread");
        }

        void queueEvent(Runnable r) {
            synchronized (renderLock) {
                eventQueue.addLast(r);
                renderLock.notifyAll();
            }
        }

        @Override
        public void run() {
            Surface localSurface = null;
            boolean surfaceCreatedCalled = false;

            while (true) {
                synchronized (renderLock) {
                    if (shouldExit) {
                        break;
                    }

                    // Wait for a valid surface, not paused, and renderer ready
                    while ((paused || !hasSurface || renderer == null || !renderer.isReadyForRendering()) && !shouldExit) {
                        try {
                            renderLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                    }
                    if (shouldExit) break;

                    localSurface = surface;

                    // Handle queued events
                    while (!eventQueue.isEmpty()) {
                        Runnable r = eventQueue.pollFirst();
                        if (r != null) r.run();
                    }

                    if (!surfaceCreatedCalled && renderer != null && localSurface != null) {
                        renderer.onSurfaceCreated(localSurface);
                        surfaceCreatedCalled = true;
                    }

                    if (surfaceSizeChanged && renderer != null) {
                        renderer.onSurfaceChanged(getHolder(), format, width, height);
                        surfaceSizeChanged = false;
                    }

                    // Decide whether to render this frame
                    if (renderMode == RENDERMODE_WHEN_DIRTY && !requestRender) {
                        try {
                            renderLock.wait();
                        } catch (InterruptedException ignored) {
                        }
                        continue;
                    }
                    requestRender = false;
                }

                // Outside synchronized block: perform rendering
                if (renderer != null && localSurface != null) {
                    renderer.onDrawFrame();
                }
            }
        }
    }

    interface WgRenderer {
        void onSurfaceCreated(Surface surface);

        void onDrawFrame();

        void onSurfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height);

        void surfaceDestroyed();

        /**
         * @return true when the renderer is ready to start rendering (e.g. application init completed).
         */
        boolean isReadyForRendering();
    }
}

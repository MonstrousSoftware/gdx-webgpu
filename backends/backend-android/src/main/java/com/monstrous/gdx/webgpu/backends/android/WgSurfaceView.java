package com.monstrous.gdx.webgpu.backends.android;

import android.content.Context;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.android.surfaceview.ResolutionStrategy;
import com.github.xpenatan.webgpu.JWebGPULoader;

public class WgSurfaceView extends SurfaceView implements SurfaceHolder.Callback2 {

    final ResolutionStrategy resolutionStrategy;

    public Input.OnscreenKeyboardType onscreenKeyboardType = Input.OnscreenKeyboardType.Default;

    private Surface surface;

    public WgSurfaceView(Context context, ResolutionStrategy resolutionStrategy) {
        super(context);
        this.resolutionStrategy = resolutionStrategy;
        getHolder().addCallback(this);
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {

    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        JWebGPULoader.init((isSuccess, e) -> {
            System.out.println("WebGPU Init Success: " + isSuccess);
            if (isSuccess) {
                surface = surfaceHolder.getSurface();
            } else {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    @Override
    protected void onMeasure (int widthMeasureSpec, int heightMeasureSpec) {
        ResolutionStrategy.MeasuredDimension measures = resolutionStrategy.calcMeasures(widthMeasureSpec, heightMeasureSpec);
        setMeasuredDimension(measures.width, measures.height);
    }

    public void onPause() {

    }

    public void onResume() {

    }

    public void queueEvent(Runnable r) {

    }

    public void requestRender() {

    }

    public void setRenderMode(int renderMode) {

    }

    interface WgRenderer {
        void onSurfaceCreated(Surface surface);
        void onDrawFrame();
        void onSurfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height);
        void surfaceDestroyed();
    }
}

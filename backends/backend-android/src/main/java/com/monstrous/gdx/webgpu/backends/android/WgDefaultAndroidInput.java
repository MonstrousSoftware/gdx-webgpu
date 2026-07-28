package com.monstrous.gdx.webgpu.backends.android;

import android.animation.Animator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.DefaultAndroidInput;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightProvider;
import com.badlogic.gdx.backends.android.keyboardheight.StandardKeyboardHeightProvider;
import com.badlogic.gdx.utils.GdxRuntimeException;

import java.lang.reflect.Field;

/**
 * Adapts {@link DefaultAndroidInput} to the WebGPU Android application and surface classes.
 * <p>
 * The upstream implementation assumes {@code AndroidApplication}, {@code AndroidGraphics}, and
 * {@code GLSurfaceView20}. The WebGPU backend uses its own equivalents, so affected keyboard
 * methods are overridden with the same behavior using the WebGPU types.
 */
public class WgDefaultAndroidInput extends DefaultAndroidInput {

    private final WgAndroidApplication wgApp;
    private final AndroidApplicationConfiguration wgConfig;
    private KeyboardHeightObserver wgObserver;
    private int cachedHeight;
    private boolean cachedVisible;

    // Cached reflection handles for private state that must stay synchronized with the super class.
    private static final Field RELATIVE_LAYOUT_FIELD;
    private static final Field ONSCREEN_VISIBLE_FIELD;
    static {
        try {
            RELATIVE_LAYOUT_FIELD = DefaultAndroidInput.class.getDeclaredField("relativeLayoutField");
            RELATIVE_LAYOUT_FIELD.setAccessible(true);
            ONSCREEN_VISIBLE_FIELD = DefaultAndroidInput.class.getDeclaredField("onscreenVisible");
            ONSCREEN_VISIBLE_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("DefaultAndroidInput private fields changed", e);
        }
    }

    public WgDefaultAndroidInput(WgAndroidApplication app, Context context, View view,
                                  AndroidApplicationConfiguration config) {
        super(app, context, view, config);
        this.wgApp = app;
        this.wgConfig = config;
    }

    @Override
    public void setKeyboardHeightObserver(KeyboardHeightObserver observer) {
        // Keep our own copy so the overridden onKeyboardHeightChanged can use it
        // (the parent field is private).
        this.wgObserver = observer;
        super.setKeyboardHeightObserver(observer);
    }

    private RelativeLayout getRelativeLayout() {
        try {
            return (RelativeLayout) RELATIVE_LAYOUT_FIELD.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private boolean wgIsNativeInputOpen() {
        RelativeLayout relativeLayout = getRelativeLayout();
        return relativeLayout != null && relativeLayout.getVisibility() == View.VISIBLE;
    }

    private AutoCompleteTextView wgGetEditText() {
        RelativeLayout relativeLayout = getRelativeLayout();
        return relativeLayout != null ? (AutoCompleteTextView) relativeLayout.getChildAt(0) : null;
    }

    private void setOnscreenVisible(boolean visible) {
        try {
            ONSCREEN_VISIBLE_FIELD.setBoolean(this, visible);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Cannot update DefaultAndroidInput onscreen keyboard state", e);
        }
    }

    @Override
    public void setOnscreenKeyboardVisible(boolean visible) {
        setOnscreenKeyboardVisible(visible, OnscreenKeyboardType.Default);
    }

    @Override
    public void setOnscreenKeyboardVisible(final boolean visible, final OnscreenKeyboardType type) {
        if (wgIsNativeInputOpen()) {
            throw new GdxRuntimeException("Can't open keyboard if already open");
        }

        setOnscreenVisible(visible);
        wgApp.handler.post(() -> {
            InputMethodManager manager =
                    (InputMethodManager) wgApp.getSystemService(Context.INPUT_METHOD_SERVICE);
            WgSurfaceView surfaceView = wgApp.graphics.view;

            if (visible) {
                OnscreenKeyboardType keyboardType =
                        type == null ? OnscreenKeyboardType.Default : type;
                if (surfaceView.onscreenKeyboardType != keyboardType) {
                    surfaceView.onscreenKeyboardType = keyboardType;
                    manager.restartInput(surfaceView);
                }

                surfaceView.setFocusable(true);
                surfaceView.setFocusableInTouchMode(true);
                surfaceView.requestFocus();
                manager.showSoftInput(surfaceView, 0);
            } else {
                manager.hideSoftInputFromWindow(surfaceView.getWindowToken(), 0);
            }
        });
    }

    private int wgGetSoftButtonsBarHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        wgApp.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        wgApp.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        return Math.max(realHeight - usableHeight, 0);
    }

    private void dispatchHeightAndVisibilityChangesToObserver(boolean visible, int height) {
        if (wgObserver == null) {
            return;
        }

        boolean visibilityChanged = visible != cachedVisible;
        boolean heightChanged = height != cachedHeight;
        if (!visibilityChanged && !heightChanged) {
            return;
        }

        if (visibilityChanged) {
            if (visible) {
                wgObserver.onKeyboardShow(height);
            } else {
                wgObserver.onKeyboardHide();
            }
        } else if (visible) {
            // Height changed while the keyboard remained visible.
            wgObserver.onKeyboardShow(height);
        }

        if (heightChanged) {
            wgObserver.onKeyboardHeightChanged(height);
        }

        cachedVisible = visible;
        cachedHeight = height;
    }

    @Override
    public void onKeyboardHeightChanged(boolean visible, int height, int leftInset, int rightInset, int orientation) {
        // Replicated from DefaultAndroidInput, but uses wgApp instead of (AndroidApplication)app.
        KeyboardHeightProvider keyboardHeightProvider = wgApp.getKeyboardHeightProvider();
        boolean isStandardHeightProvider = keyboardHeightProvider instanceof StandardKeyboardHeightProvider;
        if (wgConfig.useImmersiveMode && isStandardHeightProvider) {
            height += wgGetSoftButtonsBarHeight();
        }

        if (!wgIsNativeInputOpen()) {
            dispatchHeightAndVisibilityChangesToObserver(visible, height);
            RelativeLayout relativeLayout = getRelativeLayout();
            if (relativeLayout != null) {
                relativeLayout.setY(-height);
            }
            return;
        }

        AutoCompleteTextView editText = wgGetEditText();
        if (height == 0 && isStandardHeightProvider && editText != null && editText.isPopupShowing()) {
            return;
        }

        RelativeLayout relativeLayout = getRelativeLayout();
        if (!visible) {
            closeTextInputField(false);
            dispatchHeightAndVisibilityChangesToObserver(false, height);
            if (relativeLayout != null) {
                relativeLayout.setY(height);
            }
            return;
        }

        dispatchHeightAndVisibilityChangesToObserver(true, editText != null ? height + editText.getHeight() : height);

        if (relativeLayout != null) {
            if ((wgApp.getWindow().getAttributes().softInputMode
                    & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
                height = 0;
            }
            final int animHeight = height;

            FrameLayout.LayoutParams containerParams = (FrameLayout.LayoutParams) relativeLayout.getLayoutParams();
            containerParams.leftMargin = leftInset;
            containerParams.rightMargin = rightInset;
            relativeLayout.setLayoutParams(containerParams);

            relativeLayout.animate()
                    .y(-animHeight)
                    .setDuration(100)
                    .setListener(new Animator.AnimatorListener() {
                        @Override public void onAnimationCancel(Animator animation) {}
                        @Override public void onAnimationRepeat(Animator animation) {}
                        @Override public void onAnimationStart(Animator animation) {}
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            AutoCompleteTextView editText = wgGetEditText();
                            if (editText != null && editText.isPopupShowing()) {
                                editText.showDropDown();
                            }
                        }
                    });
        }
    }
}

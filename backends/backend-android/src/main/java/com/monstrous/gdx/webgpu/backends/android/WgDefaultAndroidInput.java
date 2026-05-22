package com.monstrous.gdx.webgpu.backends.android;

import android.animation.Animator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.DefaultAndroidInput;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightProvider;
import com.badlogic.gdx.backends.android.keyboardheight.StandardKeyboardHeightProvider;

import java.lang.reflect.Field;

/**
 * Subclass of {@link DefaultAndroidInput} that fixes the hard-coded {@code (AndroidApplication)app}
 * cast in the upstream keyboard-height callback.
 * <p>
 * {@link WgAndroidApplication} extends Activity + {@code AndroidApplicationBase} but does
 * <b>not</b> extend {@code AndroidApplication}, so that cast throws {@code ClassCastException}.
 * This class overrides the affected method with equivalent libGDX 1.14.1 logic using the correct type.
 */
public class WgDefaultAndroidInput extends DefaultAndroidInput {

    private final WgAndroidApplication wgApp;
    private final AndroidApplicationConfiguration wgConfig;
    private KeyboardHeightObserver wgObserver;
    private int cachedHeight;
    private boolean cachedVisible;

    // Cached reflection handle for the one private field we need from the super class.
    private static final Field RELATIVE_LAYOUT_FIELD;
    static {
        try {
            RELATIVE_LAYOUT_FIELD = DefaultAndroidInput.class.getDeclaredField("relativeLayoutField");
            RELATIVE_LAYOUT_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("DefaultAndroidInput layout changed: relativeLayoutField not found", e);
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

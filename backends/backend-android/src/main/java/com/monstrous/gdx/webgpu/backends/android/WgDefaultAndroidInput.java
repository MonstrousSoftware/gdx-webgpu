package com.monstrous.gdx.webgpu.backends.android;

import android.animation.Animator;
import android.content.Context;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;
import android.widget.AutoCompleteTextView;
import android.widget.RelativeLayout;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.badlogic.gdx.backends.android.DefaultAndroidInput;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightObserver;
import com.badlogic.gdx.backends.android.keyboardheight.KeyboardHeightProvider;
import com.badlogic.gdx.backends.android.keyboardheight.StandardKeyboardHeightProvider;

import java.lang.reflect.Field;

/**
 * Subclass of {@link DefaultAndroidInput} that fixes two hard-coded {@code (AndroidApplication)app}
 * casts in the upstream class ({@code onKeyboardHeightChanged} and {@code getSoftButtonsBarHeight}).
 * <p>
 * {@link WgAndroidApplication} extends Activity + {@code AndroidApplicationBase} but does
 * <b>not</b> extend {@code AndroidApplication}, so those casts throw {@code ClassCastException}.
 * This class overrides the affected method with equivalent logic using the correct type.
 */
public class WgDefaultAndroidInput extends DefaultAndroidInput {

    private final WgAndroidApplication wgApp;
    private final AndroidApplicationConfiguration wgConfig;
    private KeyboardHeightObserver wgObserver;

    // Cached reflection handle for the one private field we need from the super class.
    private static final Field RELATIVE_LAYOUT_FIELD;
    static {
        try {
            RELATIVE_LAYOUT_FIELD = DefaultAndroidInput.class.getDeclaredField("relativeLayoutField");
            RELATIVE_LAYOUT_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException("DefaultAndroidInput layout changed — relativeLayoutField not found", e);
        }
    }

    public WgDefaultAndroidInput(WgAndroidApplication app, Context context, Object view,
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

    // ---- helpers that replicate private methods from super, with correct casts ----

    private RelativeLayout getRelativeLayout() {
        try {
            return (RelativeLayout) RELATIVE_LAYOUT_FIELD.get(this);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private boolean wgIsNativeInputOpen() {
        RelativeLayout rl = getRelativeLayout();
        return rl != null && rl.getVisibility() == View.VISIBLE;
    }

    private AutoCompleteTextView wgGetEditText() {
        RelativeLayout rl = getRelativeLayout();
        return rl != null ? (AutoCompleteTextView) rl.getChildAt(0) : null;
    }

    private int wgGetSoftButtonsBarHeight() {
        // Original casts to (AndroidApplication)app just to reach getWindowManager().
        // WgAndroidApplication extends Activity, which already has getWindowManager().
        DisplayMetrics metrics = new DisplayMetrics();
        wgApp.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int usableHeight = metrics.heightPixels;
        wgApp.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
        int realHeight = metrics.heightPixels;
        return Math.max(realHeight - usableHeight, 0);
    }

    // ---- the actual fix ----

    @Override
    public void onKeyboardHeightChanged(int height, int leftInset, int rightInset, int orientation) {
        // Replicated from DefaultAndroidInput, but uses wgApp instead of (AndroidApplication)app.
        KeyboardHeightProvider keyboardHeightProvider = wgApp.getKeyboardHeightProvider();
        boolean isStandardHeightProvider = keyboardHeightProvider instanceof StandardKeyboardHeightProvider;

        if (wgConfig.useImmersiveMode && isStandardHeightProvider) {
            height += wgGetSoftButtonsBarHeight();
        }

        if (!wgIsNativeInputOpen()) {
            if (wgObserver != null) wgObserver.onKeyboardHeightChanged(height);
            return;
        }

        RelativeLayout relativeLayout = getRelativeLayout();

        if (height == 0) {
            // Don't close keyboard on floating keyboards
            if (!isStandardHeightProvider && (keyboardHeightProvider.getKeyboardLandscapeHeight() != 0
                    || keyboardHeightProvider.getKeyboardPortraitHeight() != 0)) {
                closeTextInputField(false);
            }
            // What should I say at this point, everything is busted on android
            AutoCompleteTextView editText = wgGetEditText();
            if (isStandardHeightProvider && editText != null && editText.isPopupShowing()) {
                return;
            }
            if (wgObserver != null) wgObserver.onKeyboardHeightChanged(0);
            if (relativeLayout != null) relativeLayout.setY(0);
            return;
        }

        AutoCompleteTextView editText = wgGetEditText();
        if (wgObserver != null && editText != null) {
            wgObserver.onKeyboardHeightChanged(height + editText.getHeight());
        }

        if (relativeLayout != null) {
            // This is weird, if I don't do that there is a weird scaling/position error after rotating the 2. time
            relativeLayout.setX(0);
            relativeLayout.setScaleX(1);
            relativeLayout.setY(0);
            // @off
            if ((wgApp.getWindow().getAttributes().softInputMode
                    & WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                    != WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING) {
                height = 0;
            }
            final int animHeight = height;
            relativeLayout.animate()
                    .y(-animHeight)
                    .scaleX(((float) Gdx.graphics.getWidth() - rightInset - leftInset) / Gdx.graphics.getWidth())
                    .x((float) (leftInset - rightInset) / 2)
                    .setDuration(100)
                    .setListener(new Animator.AnimatorListener() {
                        @Override public void onAnimationCancel(Animator animation) {}
                        @Override public void onAnimationRepeat(Animator animation) {}
                        @Override public void onAnimationStart(Animator animation) {}
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            AutoCompleteTextView et = wgGetEditText();
                            if (et != null && et.isPopupShowing()) {
                                et.showDropDown();
                            }
                        }
                    });
            // @on
        }
    }
}




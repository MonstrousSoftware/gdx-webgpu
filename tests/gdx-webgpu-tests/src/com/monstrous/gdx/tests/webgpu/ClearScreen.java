
package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

// demonstrates the use of WebGPUScreenUtils
//
public class ClearScreen extends GdxTest {
    private static final int VSYNC_TOGGLE_SECONDS = 10;
    private static final float COLOR_CHANGE_SECONDS = 3f;
    private static final Color[] CLEAR_COLORS = new Color[] {
            new Color(0.15f, 0.15f, 0.20f, 1.0f),
            new Color(1.0f, 0.0f, 0.0f, 1.0f),
            new Color(0.0f, 1.0f, 0.0f, 1.0f),
            new Color(0.0f, 0.0f, 1.0f, 1.0f),
    };

    private float elapsed;
    private int lastSecond = -1;
    private int lastVsyncToggleSecond = 0;
    private boolean vsync = true;
    private final Color clearColor = new Color();

    @Override
    public void render() {
        elapsed += Gdx.graphics.getDeltaTime();

        int second = (int) elapsed;
        if (second - lastVsyncToggleSecond >= VSYNC_TOGGLE_SECONDS) {
            lastVsyncToggleSecond = second;
            vsync = !vsync;
            Gdx.graphics.setVSync(vsync);
        }
        if (second != lastSecond) {
            lastSecond = second;
            Gdx.app.log("ClearScreen", "fps: " + Gdx.graphics.getFramesPerSecond() + ", vsync: " + vsync);
        }

        float colorTime = elapsed / COLOR_CHANGE_SECONDS;
        int colorStep = (int) colorTime;
        int from = colorStep % CLEAR_COLORS.length;
        int to = (from + 1) % CLEAR_COLORS.length;
        float t = MathUtils.clamp(colorTime - colorStep, 0f, 1f);
        clearColor.set(CLEAR_COLORS[from]).lerp(CLEAR_COLORS[to], t);

        WgScreenUtils.clear(clearColor.r, clearColor.g, clearColor.b, clearColor.a);
    }

    @Override
    public void dispose() {
        if (!vsync) {
            Gdx.graphics.setVSync(true);
        }
    }
}

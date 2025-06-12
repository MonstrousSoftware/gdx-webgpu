package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplicationConfiguration;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUSpriteBatch;
import com.badlogic.gdx.webgpu.wrappers.WebGPUTexture;

import java.text.DecimalFormat;

public class SpriteBatchTest extends GdxTest {
    private static final String TAG = "SpriteBatchTest";
    DecimalFormat df = new DecimalFormat("000.0000000000");
    int SPRITES = 20000;

    long startTime = TimeUtils.nanoTime();
    int frames = 0;
    private OrthographicCamera camera;
    WebGPUTexture texture;
    WebGPUSpriteBatch spriteBatch;
    Sprite[] sprites = new Sprite[SPRITES * 2];
    float angle = 0;
    float ROTATION_SPEED = 20;
    float scale = 1;
    float SCALE_SPEED = -1;

    // launcher
    public static void main (String[] argv) {

        WebGPUApplicationConfiguration config = new WebGPUApplicationConfiguration();
        config.setWindowedMode(640, 480);
        config.setTitle("WebGPUTest");
        //config.backend = WGPUBackendType.Vulkan;
        //config.backend = WGPUBackendType.D3D12;
        config.enableGPUtiming = false;
        config.useVsync(false);

        new WebGPUApplication(new SpriteBatchTest(), config);
    }

    @Override
    public void create() {
        Gdx.app.log(TAG, "[" + this.hashCode() + "] create() START"); // Log instance and start
        spriteBatch = new WebGPUSpriteBatch(SPRITES);
        texture = new WebGPUTexture(Gdx.files.internal("data/badlogicsmall.jpg"));

        camera = new OrthographicCamera();
        Gdx.app.log(TAG, "[" + this.hashCode() + "] camera assigned: " + (camera != null)); // Log assignment result

        int width = 32;
        int height = 32;

        try {
            for (int i = 0; i < SPRITES; i++) {
                int x = (int) (Math.random() * (Gdx.graphics.getWidth() - width + width * 0.5f));
                int y = (int) (Math.random() * (Gdx.graphics.getHeight() - height + height * 0.5f));
                sprites[i] = new Sprite(texture, width, height);
                sprites[i].setPosition(x, y);
                sprites[i].setOrigin(width * 0.5f, height * 0.5f);
            }
        } catch (Throwable t) {
            Gdx.app.error(TAG, "[" + this.hashCode() + "] Error during sprite creation!", t);
            throw t; // Re-throw to ensure failure is visible
        }
        Gdx.app.log(TAG, "[" + this.hashCode() + "] create() END"); // Log instance and end
    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log(TAG, "[" + this.hashCode() + "] resize(" + width + ", " + height + ") called. Camera is null? " + (camera == null)); // Log instance and camera state
        if (camera != null) {
            camera.setToOrtho(false, width, height);
        } else {
            Gdx.app.error(TAG, "[" + this.hashCode() + "] resize() called but camera is NULL!");
        }
    }

    @Override
    public void render() {
        renderSprites();
    }

    private void renderSprites() {
        if (camera == null) {
            Gdx.app.error(TAG, "[" + this.hashCode() + "] renderSprites() called but camera is NULL! Cannot update.");
            return;
        }

        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);

        float begin = 0;
        float end = 0;
        float draw1 = 0;
        float drawText = 0;

        long start = TimeUtils.nanoTime();
        spriteBatch.begin();
        begin = (TimeUtils.nanoTime() - start) / 1000000000.0f;

        float angleInc = ROTATION_SPEED * Gdx.graphics.getDeltaTime();
        scale += SCALE_SPEED * Gdx.graphics.getDeltaTime();
        if (scale < 0.5f) {
            scale = 0.5f;
            SCALE_SPEED = 1;
        }
        if (scale > 1.0f) {
            scale = 1.0f;
            SCALE_SPEED = -1;
        }

        start = TimeUtils.nanoTime();
        for (int i = 0; i < SPRITES; i++) {
            if (angleInc != 0) sprites[i].rotate(angleInc);
            if (scale != 1) sprites[i].setScale(scale);
            sprites[i].draw(spriteBatch);
        }
        draw1 = (TimeUtils.nanoTime() - start) / 1000000000.0f;

        start = TimeUtils.nanoTime();
        drawText = (TimeUtils.nanoTime() - start) / 1000000000.0f;

        start = TimeUtils.nanoTime();
        spriteBatch.end();
        end = (TimeUtils.nanoTime() - start) / 1000000000.0f;

        if (TimeUtils.nanoTime() - startTime > 1000000000) {
            Gdx.app.log(TAG, "fps: " + frames + ", render calls: " + spriteBatch.renderCalls + ", begin: " + df.format(begin) + ", " + df.format(draw1) + ", " + df.format(drawText) + ", end: " + df.format(end));
            frames = 0;
            startTime = TimeUtils.nanoTime();
        }
        frames++;
    }

    @Override
    public void dispose() {
        Gdx.app.log(TAG, "[" + this.hashCode() + "] dispose() called.");
        if (spriteBatch != null) {
            spriteBatch.dispose();
            spriteBatch = null;
        }
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
    }
}

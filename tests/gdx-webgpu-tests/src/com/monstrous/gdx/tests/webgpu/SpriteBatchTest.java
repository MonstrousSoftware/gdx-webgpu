package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

import java.text.DecimalFormat;

public class SpriteBatchTest extends GdxTest {
    private static final String TAG = "SpriteBatchTest";
    DecimalFormat df = new DecimalFormat("000.0000000000");
    int SPRITES = 16000;

    long startTime = TimeUtils.nanoTime();
    int frames = 0;
    private OrthographicCamera camera;
    WgTexture texture;
    WgSpriteBatch spriteBatch;
    ScreenViewport viewport;
    Sprite[] sprites = new Sprite[SPRITES * 2];
    float angle = 0;
    float ROTATION_SPEED = 20;
    float scale = 1;
    float SCALE_SPEED = -1;
    WebGPUContext webgpu;


    @Override
    public void create() {
        WgGraphics gfx = (WgGraphics)Gdx.graphics;
        webgpu = gfx.getContext();
        Gdx.app.log(TAG, "[" + this.hashCode() + "] create() START"); // Log instance and start

        viewport = new ScreenViewport();
        spriteBatch = new WgSpriteBatch(SPRITES);
        texture = new WgTexture(Gdx.files.internal("data/badlogicsmall.jpg"));

    }

    private void generateSprites(int screenWidth, int screenHeight){
        int width = 32;
        int height = 32;

        try {
            for (int i = 0; i < SPRITES; i++) {
                int x = (int) (Math.random() * screenWidth);
                int y = (int) (Math.random() * screenHeight);
                if(sprites[i] == null) {
                    sprites[i] = new Sprite(texture, width, height);
                    sprites[i].setOrigin(width * 0.5f, height * 0.5f);
                }
                sprites[i].setOriginBasedPosition(x, y);

            }
        } catch (Throwable t) {
            Gdx.app.error(TAG, "[" + this.hashCode() + "] Error during sprite creation!", t);
            throw t; // Re-throw to ensure failure is visible
        }
        Gdx.app.log(TAG, "[" + this.hashCode() + "] create() END"); // Log instance and end
    }

    @Override
    public void resize(int width, int height) {
        System.out.println("Resize: "+width+" x "+height);
        viewport.update(width, height, true);
        generateSprites(width,height);
    }

    @Override
    public void render() {

        viewport.apply();
        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);

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

            for(int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
                Gdx.app.log(TAG, "GPU time (pass "+pass+" "+webgpu.getGPUTimer().getPassName(pass)+") : "+(int)webgpu.getAverageGPUtime(pass)+ " microseconds");
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

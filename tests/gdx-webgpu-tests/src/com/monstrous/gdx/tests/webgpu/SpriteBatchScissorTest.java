package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

import java.text.DecimalFormat;

public class SpriteBatchScissorTest extends GdxTest {
    int SPRITES = 1600;

    WgTexture texture;
    WgSpriteBatch spriteBatch;
    ScreenViewport viewport;
    Sprite[] sprites = new Sprite[SPRITES * 2];
    float ROTATION_SPEED = 20;
    float scale = 1;
    float SCALE_SPEED = -1;
    WebGPUContext webgpu;

    @Override
    public void create() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        viewport = new ScreenViewport();
        spriteBatch = new WgSpriteBatch(SPRITES);
        texture = new WgTexture(Gdx.files.internal("data/badlogicsmall.jpg"));
    }

    private void generateSprites(int screenWidth, int screenHeight) {
        int width = 32;
        int height = 32;

        for (int i = 0; i < SPRITES; i++) {
            int x = (int) (Math.random() * screenWidth);
            int y = (int) (Math.random() * screenHeight);
            if (sprites[i] == null) {
                sprites[i] = new Sprite(texture, width, height);
                sprites[i].setOrigin(width * 0.5f, height * 0.5f);
            }
            sprites[i].setOriginBasedPosition(x, y);

        }
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        generateSprites(width, height);
    }

    @Override
    public void render() {

        viewport.apply();

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

        spriteBatch.setProjectionMatrix(viewport.getCamera().combined);
        spriteBatch.begin();

        spriteBatch.setScissorRect(100, 100, 200, 200);
        for (int i = 0; i < SPRITES / 4; i++) {
            if (angleInc != 0)
                sprites[i].rotate(angleInc);
            if (scale != 1)
                sprites[i].setScale(scale);
            sprites[i].draw(spriteBatch);
        }

        spriteBatch.setScissorRect(300, 200, 200, 200);
        for (int i = 0; i < SPRITES / 4; i++) {
            if (angleInc != 0)
                sprites[i].rotate(angleInc);
            if (scale != 1)
                sprites[i].setScale(scale);
            sprites[i].draw(spriteBatch);
        }

        spriteBatch.setScissorRect(0, 0, 50, 50);
        for (int i = 0; i < SPRITES / 4; i++) {
            if (angleInc != 0)
                sprites[i].rotate(angleInc);
            if (scale != 1)
                sprites[i].setScale(scale);
            sprites[i].draw(spriteBatch);
        }

        spriteBatch.end();

    }

    @Override
    public void dispose() {
        spriteBatch.dispose();
        texture.dispose();
    }
}

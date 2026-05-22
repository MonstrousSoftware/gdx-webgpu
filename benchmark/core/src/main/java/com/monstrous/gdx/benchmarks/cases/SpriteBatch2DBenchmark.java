package com.monstrous.gdx.benchmarks.cases;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.benchmarks.BenchmarkBackend;
import com.monstrous.gdx.benchmarks.BenchmarkCase;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;

import java.util.Random;

public class SpriteBatch2DBenchmark implements BenchmarkCase {
    private static final int SPRITE_WIDTH = 32;
    private static final int SPRITE_HEIGHT = 32;
    private static final float ROTATION_SPEED = 20f;

    private BenchmarkConfig config;
    private ScreenViewport viewport;
    private Batch batch;
    private Texture texture;
    private Sprite[] sprites;
    private float scale = 1f;
    private float scaleSpeed = -1f;

    @Override
    public String getName() {
        return "sprite2d";
    }

    @Override
    public void create(BenchmarkBackend backend, BenchmarkConfig config) {
        this.config = config;
        viewport = new ScreenViewport();
        batch = backend.createSpriteBatch(config.sprites);
        texture = backend.createTexture("data/badlogicsmall.jpg");
        sprites = new Sprite[config.sprites];
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
        generateSprites(width, height);
    }

    @Override
    public void render() {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        float delta = Gdx.graphics.getDeltaTime();
        float angleInc = config.rotate ? ROTATION_SPEED * delta : 0f;
        if (config.scale) {
            scale += scaleSpeed * delta;
            if (scale < 0.5f) {
                scale = 0.5f;
                scaleSpeed = 1f;
            } else if (scale > 1f) {
                scale = 1f;
                scaleSpeed = -1f;
            }
        }

        batch.begin();
        for (int i = 0; i < sprites.length; i++) {
            Sprite sprite = sprites[i];
            if (angleInc != 0f) {
                sprite.rotate(angleInc);
            }
            if (config.scale) {
                sprite.setScale(scale);
            }
            sprite.draw(batch);
        }
        batch.end();
    }

    @Override
    public void dispose() {
        if (batch instanceof Disposable) {
            ((Disposable)batch).dispose();
        }
        if (texture != null) {
            texture.dispose();
        }
    }

    private void generateSprites(int screenWidth, int screenHeight) {
        Random random = new Random(0x51f15e2dL);
        for (int i = 0; i < sprites.length; i++) {
            int x = random.nextInt(Math.max(1, screenWidth));
            int y = random.nextInt(Math.max(1, screenHeight));
            Sprite sprite = new Sprite(texture, SPRITE_WIDTH, SPRITE_HEIGHT);
            sprite.setOrigin(SPRITE_WIDTH * 0.5f, SPRITE_HEIGHT * 0.5f);
            sprite.setOriginBasedPosition(x, y);
            sprites[i] = sprite;
        }
    }
}

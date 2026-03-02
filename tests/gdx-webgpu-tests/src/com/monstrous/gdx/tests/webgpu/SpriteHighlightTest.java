package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgHighlightShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

/**
 * Test WebGPU 2D sprite highlighting. Renders a sprite with a colored highlight/outline around it, similar to object
 * selection in game engines. The highlight only appears around non-transparent pixels.
 */
public class SpriteHighlightTest extends GdxTest {

    WgSpriteBatch batch;
    WgHighlightShaderProgram highlightShader;
    WgBitmapFont font;
    WgTexture texture1;
    WgTexture texture2;
    Sprite sprite1;
    Sprite sprite2;
    float time = 0;
    float currentThickness = 1.0f;
    float currentAlphaThreshold = 0.1f;
    float currentSmoothing = 0.0f;

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        highlightShader = new WgHighlightShaderProgram(currentThickness, currentAlphaThreshold, currentSmoothing);
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        // Load a texture with alpha channel
        texture1 = new WgTexture(Gdx.files.internal("data/walkanim.png"));
        texture2 = new WgTexture(Gdx.files.internal("data/particle-star.png"));

        sprite1 = new Sprite(texture1);
        sprite1.setOriginCenter();
        sprite1.setCenter(Gdx.graphics.getWidth() / 2f - 100, Gdx.graphics.getHeight() / 2f);

        sprite2 = new Sprite(texture2);
        sprite2.setSize(128, 128);
        sprite2.setOriginCenter();
        sprite2.setCenter(Gdx.graphics.getWidth() / 2f + 100, Gdx.graphics.getHeight() / 2f);
    }

    @Override
    public void render() {
        time += Gdx.graphics.getDeltaTime();

        // Handle keyboard input to adjust parameters
        handleInput();

        // Rotate sprite
        sprite1.setRotation(time * 30f);

        batch.begin(Color.BLACK);

        // STEP 1: Render normal sprites first
        batch.setShader((WgHighlightShaderProgram) null);
        sprite1.setColor(Color.WHITE);
        sprite1.draw(batch);
        sprite2.setColor(Color.WHITE);
        sprite2.draw(batch);

        // STEP 2: Render highlight/outline on top (allows inward strokes at edges with no transparent space)
        batch.setShader(highlightShader);
        sprite1.setColor(Color.CYAN);
        sprite1.draw(batch);
        sprite2.setColor(Color.RED);
        sprite2.draw(batch);
        batch.setShader((WgShaderProgram) null);

        // STEP 3: Render UI
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 10, 30);
        font.draw(batch, "2D Sprite Highlighting Test", 10, 50);
        font.draw(batch, String.format("Thickness: %.1f pixels [UP/DOWN to adjust]", currentThickness), 10, 70);
        font.draw(batch, String.format("Alpha Threshold: %.2f [LEFT/RIGHT to adjust]", currentAlphaThreshold), 10, 90);
        font.draw(batch, String.format("Smoothing: %.2f [Q/E to adjust]", currentSmoothing), 10, 110);
        font.draw(batch, "Press SPACE to recreate shader with new values", 10, 130);

        batch.end();
    }

    private void handleInput() {
        boolean needsRecreate = false;

        // Adjust thickness
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            currentThickness += 0.5f;
            if (currentThickness > 20.0f)
                currentThickness = 20.0f;
            needsRecreate = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            currentThickness -= 0.5f;
            if (currentThickness < 0.5f)
                currentThickness = 0.5f;
            needsRecreate = true;
        }

        // Adjust alpha threshold
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            currentAlphaThreshold += 0.05f;
            if (currentAlphaThreshold > 1.0f)
                currentAlphaThreshold = 1.0f;
            needsRecreate = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            currentAlphaThreshold -= 0.05f;
            if (currentAlphaThreshold < 0.0f)
                currentAlphaThreshold = 0.0f;
            needsRecreate = true;
        }

        // Adjust smoothing
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            currentSmoothing += 0.25f;
            if (currentSmoothing > 5.0f)
                currentSmoothing = 5.0f;
            needsRecreate = true;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            currentSmoothing -= 0.25f;
            if (currentSmoothing < 0.0f)
                currentSmoothing = 0.0f;
            needsRecreate = true;
        }

        // Recreate shader with new parameters
        if (needsRecreate || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            highlightShader.dispose();
            highlightShader = new WgHighlightShaderProgram(currentThickness, currentAlphaThreshold, currentSmoothing);
            batch.clearPipelineCache();
        }
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void dispose() {
        batch.dispose();
        highlightShader.dispose();
        font.dispose();
        texture1.dispose();
        texture2.dispose();
    }
}

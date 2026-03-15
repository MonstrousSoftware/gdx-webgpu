package com.monstrous.gdx.tests.webgpu.utils;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.tests.webgpu.WebGPUTests;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import java.util.List;

/**
 * Runs all registered WebGPU tests sequentially, spending a fixed number of seconds on each.
 * Used by both the desktop and web test launchers to validate that every test passes without errors.
 */
public class AutoTestRunner extends ApplicationAdapter {
    private List<String> testNames;
    private int currentTestIndex = -1;
    private float timer = 0;
    private final float TEST_DURATION = 3.0f; // seconds per test
    private ApplicationListener currentTest;
    private boolean pendingSwitch = false;

    private WgSpriteBatch uiBatch;
    private WgBitmapFont uiFont;

    @Override
    public void create() {
        testNames = WebGPUTests.getNames();
        System.out.println("Starting Auto Test Runner with " + testNames.size() + " tests.");

        uiBatch = new WgSpriteBatch();
        uiFont = new WgBitmapFont();
        uiFont.setColor(Color.RED);

        nextTest();
    }

    private void nextTest() {
        currentTestIndex++;
        if (currentTestIndex >= testNames.size()) {
            System.out.println("All tests completed!");
            Gdx.app.exit();
            return;
        }

        String name = testNames.get(currentTestIndex);
        System.out.println("Running test (" + (currentTestIndex + 1) + "/" + testNames.size() + "): " + name);
        currentTest = WebGPUTests.newTest(name);

        try {
            currentTest.create();
            currentTest.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        } catch (Exception e) {
            System.err.println("Error creating test " + name + ": " + e.getMessage());
            e.printStackTrace();
            // move to next test immediately on error
            timer = TEST_DURATION;
            return;
        }

        timer = 0;
        pendingSwitch = false;
    }

    @Override
    public void render() {
        if (pendingSwitch) {
            if (currentTest != null) {
                currentTest.pause();
                currentTest.dispose();
                currentTest = null;
            }
            nextTest();
            return;
        }

        // Clear the screen before rendering the test
        WgScreenUtils.clear(0, 0, 0, 1);

        if (currentTest != null) {
            try {
                currentTest.render();
            } catch (Exception e) {
                System.err.println("Error rendering test " + testNames.get(currentTestIndex) + ": " + e.getMessage());
                e.printStackTrace();
                timer = TEST_DURATION; // force next test
            }
        }

        timer += Gdx.graphics.getDeltaTime();

        // Render UI overlay
        if (currentTestIndex >= 0 && currentTestIndex < testNames.size()) {
            String testName = testNames.get(currentTestIndex);
            String info = (currentTestIndex + 1) + " / " + testNames.size() + " - " + testName;

            uiBatch.begin();
            float estimatedWidth = info.length() * 8f;
            float x = (Gdx.graphics.getWidth() - estimatedWidth) / 2f;

            // Current test in white
            uiFont.setColor(Color.WHITE);
            uiFont.draw(uiBatch, info, x, 70);

            // Previous test in gray above
            if (currentTestIndex > 0) {
                String prevName = (currentTestIndex) + " / " + testNames.size() + " - " + testNames.get(currentTestIndex - 1);
                float prevWidth = prevName.length() * 8f;
                float prevX = (Gdx.graphics.getWidth() - prevWidth) / 2f;
                uiFont.setColor(Color.GRAY);
                uiFont.draw(uiBatch, prevName, prevX, 90);
            }

            // Next test in gray below
            if (currentTestIndex < testNames.size() - 1) {
                String nextName = (currentTestIndex + 2) + " / " + testNames.size() + " - " + testNames.get(currentTestIndex + 1);
                float nextWidth = nextName.length() * 8f;
                float nextX = (Gdx.graphics.getWidth() - nextWidth) / 2f;
                uiFont.setColor(Color.GRAY);
                uiFont.draw(uiBatch, nextName, nextX, 50);
            }

            uiBatch.end();
        }

        if (timer >= TEST_DURATION) {
            pendingSwitch = true;
        }
    }

    @Override
    public void resize(int width, int height) {
        if (currentTest != null) {
            currentTest.resize(width, height);
        }
    }

    @Override
    public void pause() {
        if (currentTest != null) {
            currentTest.pause();
        }
    }

    @Override
    public void resume() {
        if (currentTest != null) {
            currentTest.resume();
        }
    }

    @Override
    public void dispose() {
        if (currentTest != null) {
            currentTest.dispose();
        }
        if (uiBatch != null) {
            uiBatch.dispose();
        }
        if (uiFont != null) {
            uiFont.dispose();
        }
    }
}


package com.monstrous.gdx.tests.webgpu.utils;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.tests.webgpu.WebGPUTests;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs all registered WebGPU tests sequentially, spending a fixed number of seconds on each.
 * Used by both the desktop, web, and Android test launchers to validate that every test passes without errors.
 * <p>
 * Every test lifecycle call is wrapped in try-catch so a single failing test cannot crash the runner.
 * After all tests complete, a summary of passed/failed tests is printed to stdout.
 */
public class AutoTestRunner extends ApplicationAdapter {
    private List<String> testNames;
    private int currentTestIndex = -1;
    private float timer = 0;
    private final float TEST_DURATION = 3.0f; // seconds per test
    private ApplicationListener currentTest;
    private boolean pendingSwitch = false;

    // --- FPS-stability loading detection ---
    /** Number of consecutive stable frames required before considering a test "loaded". */
    private static final int STABLE_FRAMES_REQUIRED = 10;
    /** A frame with deltaTime above this threshold (in seconds) is considered a loading spike. */
    private static final float SPIKE_THRESHOLD = 0.10f; // 100 ms = < 10 fps
    /** Maximum seconds to wait for stability before starting the timer anyway. */
    private static final float LOAD_TIMEOUT = 15.0f;
    /** Count of consecutive frames with deltaTime below the spike threshold. */
    private int stableFrameCount = 0;
    /** True once FPS has stabilised (or the load timeout was reached). */
    private boolean testLoaded = false;
    /** Accumulated wall-clock time since the test's first render(), used for the load timeout. */
    private float loadWaitTime = 0;

    private WgSpriteBatch uiBatch;
    private WgBitmapFont uiFont;

    /** Tracks which tests failed (test name + phase + message). */
    private final List<String> failedTests = new ArrayList<>();
    private boolean currentTestFailed = false;

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
            printSummary();
            Gdx.app.exit();
            return;
        }

        String name = testNames.get(currentTestIndex);
        System.out.println("Running test (" + (currentTestIndex + 1) + "/" + testNames.size() + "): " + name);
        currentTestFailed = false;
        testLoaded = false;
        stableFrameCount = 0;
        loadWaitTime = 0;
        currentTest = WebGPUTests.newTest(name);

        try {
            currentTest.create();
            currentTest.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        } catch (Throwable e) {
            recordFailure(name, "create", e);
            // move to next test immediately on error
            timer = TEST_DURATION;
            testLoaded = true; // allow timer to proceed
            return;
        }

        timer = 0;
        pendingSwitch = false;
    }

    /** Safely dispose the current test, catching any errors. */
    private void disposeCurrentTest() {
        if (currentTest == null) return;
        String name = testNames.get(currentTestIndex);
        try {
            currentTest.pause();
        } catch (Throwable e) {
            recordFailure(name, "pause", e);
        }
        try {
            currentTest.dispose();
        } catch (Throwable e) {
            recordFailure(name, "dispose", e);
        }
        currentTest = null;
    }

    private void recordFailure(String testName, String phase, Throwable e) {
        String msg = testName + " [" + phase + "]: " + e.getClass().getSimpleName() + " - " + e.getMessage();
        System.err.println("FAIL: " + msg);
        e.printStackTrace();
        if (!currentTestFailed) {
            currentTestFailed = true;
            failedTests.add(msg);
        }
    }

    private void printSummary() {
        int total = testNames.size();
        int failed = failedTests.size();
        int passed = total - failed;
        System.out.println("========================================");
        System.out.println("Auto Test Runner complete: " + passed + " / " + total + " passed.");
        if (failed > 0) {
            System.out.println("FAILED tests (" + failed + "):");
            for (String f : failedTests) {
                System.out.println("  - " + f);
            }
        }
        System.out.println("========================================");
    }

    @Override
    public void render() {
        if (pendingSwitch) {
            disposeCurrentTest();
            nextTest();
            return;
        }

        // Clear the screen before rendering the test
        WgScreenUtils.clear(0, 0, 0, 1);

        float dt = Gdx.graphics.getDeltaTime();

        if (currentTest != null) {
            try {
                currentTest.render();

                // --- FPS-stability loading detection ---
                // While the test is still loading (compiling shaders, uploading textures,
                // parsing models) individual frames take much longer than normal, causing
                // deltaTime spikes.  We wait until we see STABLE_FRAMES_REQUIRED consecutive
                // frames below the SPIKE_THRESHOLD before we consider the test "loaded" and
                // start the countdown timer.  A LOAD_TIMEOUT safety net ensures we don't
                // wait forever on a test that is genuinely slow to render.
                if (!testLoaded) {
                    loadWaitTime += dt;
                    if (dt < SPIKE_THRESHOLD) {
                        stableFrameCount++;
                    } else {
                        stableFrameCount = 0;
                    }
                    if (stableFrameCount >= STABLE_FRAMES_REQUIRED || loadWaitTime >= LOAD_TIMEOUT) {
                        testLoaded = true;
                        timer = 0;
                    }
                }
            } catch (Throwable e) {
                String name = testNames.get(currentTestIndex);
                recordFailure(name, "render", e);
                timer = TEST_DURATION; // force next test
                testLoaded = true;
            }
        }

        // Only advance the timer once the test has fully loaded
        if (testLoaded) {
            timer += dt;
        }

        // Render UI overlay
        if (currentTestIndex >= 0 && currentTestIndex < testNames.size()) {
            String testName = testNames.get(currentTestIndex);
            String info = (currentTestIndex + 1) + " / " + testNames.size() + " - " + testName;

            uiBatch.begin();
            float estimatedWidth = info.length() * 8f;
            float x = (Gdx.graphics.getWidth() - estimatedWidth) / 2f;

            // Current test in white (red if failed)
            uiFont.setColor(currentTestFailed ? Color.RED : Color.WHITE);
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
            try {
                currentTest.resize(width, height);
            } catch (Throwable e) {
                recordFailure(testNames.get(currentTestIndex), "resize", e);
            }
        }
        // Update the UI batch projection so the overlay text stays on screen after resize
        if (uiBatch != null) {
            uiBatch.getProjectionMatrix().setToOrtho2D(0, 0, width, height, 0, 100);
        }
    }

    @Override
    public void pause() {
        if (currentTest != null) {
            try {
                currentTest.pause();
            } catch (Throwable e) {
                recordFailure(testNames.get(currentTestIndex), "pause", e);
            }
        }
    }

    @Override
    public void resume() {
        if (currentTest != null) {
            try {
                currentTest.resume();
            } catch (Throwable e) {
                recordFailure(testNames.get(currentTestIndex), "resume", e);
            }
        }
    }

    @Override
    public void dispose() {
        disposeCurrentTest();
        if (uiBatch != null) {
            uiBatch.dispose();
        }
        if (uiFont != null) {
            uiFont.dispose();
        }
    }
}

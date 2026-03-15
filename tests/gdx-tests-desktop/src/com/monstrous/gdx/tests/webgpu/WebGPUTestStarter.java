/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.tests.webgpu;

import java.util.List;

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.TimeUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.monstrous.gdx.tests.webgpu.utils.GdxTestWrapper;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopWindowConfiguration;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

// test starter
public class WebGPUTestStarter {
    /**
     * Runs libgdx tests.
     *
     *
     *
     * @param argv command line arguments
     */
    public static void main(String[] argv) {

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setWindowedMode(900, 740);
        config.enableGPUtiming = true;
        config.backend = WebGPUContext.Backend.DEFAULT;
        config.backendWebGPU = JWebGPUBackend.DAWN; // WGPU or DAWN
        config.samples = 4; // anti-aliasing (4) or not (1)
        config.useVsync(true);

        if (argv.length > 0) {
            String testName = argv[0];
            if (testName.equalsIgnoreCase("auto")) {
                new WgDesktopApplication(new AutoTestRunner(), config);
                return;
            }
            ApplicationListener test = WebGPUTests.newTest(testName);
            if (test != null) {
                new WgDesktopApplication(test, config);
                return;
            }
            System.out.println("Test not found: " + testName);
        }

        new WgDesktopApplication(new TestChooser(), config);
    }

    static class AutoTestRunner extends ApplicationAdapter {
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

            // Clear the screen before rendering the test to prevent flickering or "on top" rendering
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

            // Render UI on top
            if (currentTestIndex >= 0 && currentTestIndex < testNames.size()) {
                String testName = testNames.get(currentTestIndex);
                String info = (currentTestIndex + 1) + " / " + testNames.size() + " - " + testName;

                uiBatch.begin();
                // Draw it higher from the bottom to avoid being cut off by test-specific overlays.
                float estimatedWidth = info.length() * 8f;
                float x = (Gdx.graphics.getWidth() - estimatedWidth) / 2f;
                // Draw current test in White (higher up now: y=70)
                uiFont.setColor(Color.WHITE);
                uiFont.draw(uiBatch, info, x, 70);

                // Draw previous test in Gray above current test (y=90)
                if (currentTestIndex > 0) {
                    String prevName = (currentTestIndex) + " / " + testNames.size() + " - " + testNames.get(currentTestIndex - 1);
                    float prevWidth = prevName.length() * 8f;
                    float prevX = (Gdx.graphics.getWidth() - prevWidth) / 2f;
                    uiFont.setColor(Color.GRAY);
                    uiFont.draw(uiBatch, prevName, prevX, 90);
                }

                // Draw next test in Gray below current test (y=50)
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

    static class TestChooser extends ApplicationAdapter {
        private Stage stage;
        private Skin skin;
        TextButton lastClickedTestButton;

        public void create() {

            final Preferences prefs = Gdx.app.getPreferences("webgpu-tests");

            stage = new WgStage(new ScreenViewport());
            Gdx.input.setInputProcessor(stage);
            skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

            Table container = new Table();
            stage.addActor(container);
            container.setFillParent(true);

            Table table = new Table();

            ScrollPane scroll = new ScrollPane(table, skin);
            scroll.setSmoothScrolling(false);
            scroll.setFadeScrollBars(false);
            stage.setScrollFocus(scroll);

            // stage.addActor(table);
            // table.setFillParent(true);

            int tableSpace = 4;
            table.pad(10).defaults().expandX().space(tableSpace);
            for (final String testName : WebGPUTests.getNames()) {
                final TextButton testButton = new TextButton(testName, skin);
                // testButton.setDisabled(!options.isTestCompatible(testName));
                testButton.setName(testName);
                table.add(testButton).fillX();
                table.row();
                testButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        ApplicationListener test = WebGPUTests.newTest(testName);
                        WgDesktopWindowConfiguration winConfig = new WgDesktopWindowConfiguration();
                        winConfig.setTitle(testName);
                        winConfig.setWindowedMode(640, 480);
                        winConfig.setWindowPosition(((WgDesktopGraphics) Gdx.graphics).getWindow().getPositionX() + 40,
                                ((WgDesktopGraphics) Gdx.graphics).getWindow().getPositionY() + 40);
                        winConfig.useVsync(false);
                        Gdx.app.setLogLevel(Application.LOG_DEBUG);
                        ((WgDesktopApplication) Gdx.app).newWindow(test, winConfig);
                        System.out.println("Started test: " + testName);
                        prefs.putString("LastTest", testName);
                        prefs.flush();
                        if (testButton != lastClickedTestButton) {
                            testButton.setColor(Color.CYAN);
                            if (lastClickedTestButton != null) {
                                lastClickedTestButton.setColor(Color.WHITE);
                            }
                            lastClickedTestButton = testButton;
                        }
                    }
                });
            }

            container.add(scroll).grow();
            container.row();

            lastClickedTestButton = (TextButton) table.findActor(prefs.getString("LastTest"));
            if (lastClickedTestButton != null) {
                lastClickedTestButton.setColor(Color.CYAN);
                scroll.layout();
                float scrollY = lastClickedTestButton.getY() + scroll.getScrollHeight() / 2
                        + lastClickedTestButton.getHeight() / 2 + tableSpace * 2 + 20;
                scroll.scrollTo(0, scrollY, 0, 0, false, false);

                // Since ScrollPane takes some time for scrolling to a position, we just "fake" time
                stage.act(1f);
                stage.act(1f);
                // context not ready outside render()
                // stage.draw();
            }
        }

        @Override
        public void render() {
            /// ScreenUtils.clear(0, 0, 0, 1);
            stage.act();
            stage.draw();
        }

        @Override
        public void resize(int width, int height) {
            stage.getViewport().update(width, height, true);
        }

        @Override
        public void dispose() {
            skin.dispose();
            stage.dispose();
        }
    }
}

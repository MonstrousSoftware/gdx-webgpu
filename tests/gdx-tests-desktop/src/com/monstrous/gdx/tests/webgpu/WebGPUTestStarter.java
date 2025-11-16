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

import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.Color;
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
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopWindowConfiguration;
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
        config.setWindowedMode(320, 640);
        config.enableGPUtiming = true;
        config.backend = WebGPUContext.Backend.DEFAULT;
        config.backendWebGPU = JWebGPUBackend.DAWN; // WGPU or DAWN

        new WgDesktopApplication(new TestChooser(), config);
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

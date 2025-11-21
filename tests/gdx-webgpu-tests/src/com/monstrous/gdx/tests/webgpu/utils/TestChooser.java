package com.monstrous.gdx.tests.webgpu.utils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.input.NativeInputConfiguration;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.WebGPUTests;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

public class TestChooser extends ApplicationAdapter {
    private Stage stage;
    private Skin skin;
    TextButton lastClickedTestButton;
    private ApplicationListener test;
    boolean dispose = false;

    // Tunable UI scale factors
    private float uiScale = 1f;
    private float buttonHeight = 32f;
    private float buttonPad = 4f;
    private float fontScale = 1f;

    public void create() {
        final Preferences prefs = Gdx.app.getPreferences("webgpu-tests");

        // Detect mobile vs desktop/web and choose a slightly different scale.
        boolean mobile = Gdx.app.getType() == Application.ApplicationType.Android
                || Gdx.app.getType() == Application.ApplicationType.iOS;
        if (mobile) {
            uiScale = 1.8f;   // overall UI scale boost
            buttonHeight = 54f;
            buttonPad = 8f;
            fontScale = 1.3f;
        } else {
            uiScale = 1.0f;
            buttonHeight = 32f;
            buttonPad = 4f;
            fontScale = 1.0f;
        }

        stage = new WgStage(new ScreenViewport());

        Gdx.input = new InputWrapper(Gdx.input) {
            @Override
            public boolean keyUp(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    if (test != null) {
                        Gdx.app.log("GdxTestTeaVM", "Exiting current test.");
                        dispose = true;
                    }
                }
                return false;
            }
        };

        ((InputWrapper) Gdx.input).multiplexer.addProcessor(stage);

        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

        Table container = new Table();
        stage.addActor(container);
        container.setFillParent(true);

        Table table = new Table();

        ScrollPane scroll = new ScrollPane(table, skin);
        scroll.setSmoothScrolling(false);
        scroll.setFadeScrollBars(false);
        stage.setScrollFocus(scroll);

        int tableSpace = (int)(buttonPad * uiScale);
        table.pad(10 * uiScale).defaults()
                .expandX()
                .space(tableSpace)
                .height(buttonHeight * uiScale);

        for (final String testName : WebGPUTests.getNames()) {
            final TextButton testButton = new TextButton(testName, skin);
            testButton.setName(testName);

            // Center text and apply larger font scale on mobile for readability.
            testButton.getLabel().setAlignment(Align.center);
            testButton.getLabel().setFontScale(fontScale);

            table.add(testButton).fillX();
            table.row();

            testButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    ((InputWrapper) Gdx.input).multiplexer.removeProcessor(stage);
                    test = WebGPUTests.newTest(testName);
                    test.create();
                    test.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
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
                    + lastClickedTestButton.getHeight() / 2 + tableSpace * 2 + 20 * uiScale;
            scroll.scrollTo(0, scrollY, 0, 0, false, false);
            stage.act(1f);
            stage.act(1f);
        }
    }

    @Override
    public void render() {
        if (test == null) {
            stage.act();
            stage.draw();
        } else {
            if (dispose) {
                test.pause();
                test.dispose();
                test = null;
                stage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
                Gdx.graphics.setVSync(true);
                InputWrapper wrapper = ((InputWrapper) Gdx.input);
                wrapper.multiplexer.addProcessor(stage);
                wrapper.multiplexer.removeProcessor(wrapper.lastProcessor);
                wrapper.lastProcessor = null;
                dispose = false;
            } else {
                test.render();
            }
        }
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

    static class InputWrapper extends InputAdapter implements Input {
        Input input;
        InputProcessor lastProcessor;
        InputMultiplexer multiplexer;

        public InputWrapper(Input input) {
            this.input = input;
            this.multiplexer = new InputMultiplexer();
            this.multiplexer.addProcessor(this);
            input.setInputProcessor(multiplexer);
        }

        @Override
        public float getAccelerometerX() {
            return input.getAccelerometerX();
        }

        @Override
        public float getAccelerometerY() {
            return input.getAccelerometerY();
        }

        @Override
        public float getAccelerometerZ() {
            return input.getAccelerometerZ();
        }

        @Override
        public float getGyroscopeX() {
            return input.getGyroscopeX();
        }

        @Override
        public float getGyroscopeY() {
            return input.getGyroscopeY();
        }

        @Override
        public float getGyroscopeZ() {
            return input.getGyroscopeZ();
        }

        @Override
        public int getMaxPointers() {
            return input.getMaxPointers();
        }

        @Override
        public int getX() {
            return input.getX();
        }

        @Override
        public int getX(int pointer) {
            return input.getX(pointer);
        }

        @Override
        public int getDeltaX() {
            return input.getDeltaX();
        }

        @Override
        public int getDeltaX(int pointer) {
            return input.getDeltaX(pointer);
        }

        @Override
        public int getY() {
            return input.getY();
        }

        @Override
        public int getY(int pointer) {
            return input.getY(pointer);
        }

        @Override
        public int getDeltaY() {
            return input.getDeltaY();
        }

        @Override
        public int getDeltaY(int pointer) {
            return input.getDeltaY(pointer);
        }

        @Override
        public boolean isTouched() {
            return input.isTouched();
        }

        @Override
        public boolean justTouched() {
            return input.justTouched();
        }

        @Override
        public boolean isTouched(int pointer) {
            return input.isTouched(pointer);
        }

        @Override
        public float getPressure() {
            return input.getPressure();
        }

        @Override
        public float getPressure(int pointer) {
            return input.getPressure(pointer);
        }

        @Override
        public boolean isButtonPressed(int button) {
            return input.isButtonPressed(button);
        }

        @Override
        public boolean isKeyPressed(int key) {
            return input.isKeyPressed(key);
        }

        @Override
        public boolean isKeyJustPressed(int key) {
            return input.isKeyJustPressed(key);
        }

        @Override
        public boolean isButtonJustPressed(int button) {
            return input.isButtonJustPressed(button);
        }

        @Override
        public void getTextInput(TextInputListener listener, String title, String text, String hint) {
            input.getTextInput(listener, title, text, hint);
        }

        @Override
        public void getTextInput(TextInputListener listener, String title, String text, String hint,
                OnscreenKeyboardType type) {
            input.getTextInput(listener, title, text, hint, type);
        }

        @Override
        public void setOnscreenKeyboardVisible(boolean visible) {
            input.setOnscreenKeyboardVisible(visible);
        }

        @Override
        public void setOnscreenKeyboardVisible(boolean visible, OnscreenKeyboardType type) {
            input.setOnscreenKeyboardVisible(visible, type);
        }

        @Override
        public void openTextInputField(NativeInputConfiguration configuration) {
            input.openTextInputField(configuration);
        }

        @Override
        public void closeTextInputField(boolean sendReturn) {
            input.closeTextInputField(sendReturn);
        }

        @Override
        public void setKeyboardHeightObserver(KeyboardHeightObserver observer) {
            input.setKeyboardHeightObserver(observer);
        }

        @Override
        public void vibrate(int milliseconds) {
            input.vibrate(milliseconds);
        }

        @Override
        public void vibrate(int milliseconds, boolean fallback) {
            input.vibrate(milliseconds, fallback);
        }

        @Override
        public void vibrate(int milliseconds, int amplitude, boolean fallback) {
            input.vibrate(milliseconds, amplitude, fallback);
        }

        @Override
        public void vibrate(VibrationType vibrationType) {
            input.vibrate(vibrationType);
        }

        @Override
        public float getAzimuth() {
            return input.getAzimuth();
        }

        @Override
        public float getPitch() {
            return input.getPitch();
        }

        @Override
        public float getRoll() {
            return input.getRoll();
        }

        @Override
        public void getRotationMatrix(float[] matrix) {
            input.getRotationMatrix(matrix);
        }

        @Override
        public long getCurrentEventTime() {
            return input.getCurrentEventTime();
        }

        @Override
        public void setCatchKey(int keycode, boolean catchKey) {
            input.setCatchKey(keycode, catchKey);
        }

        @Override
        public boolean isCatchKey(int keycode) {
            return input.isCatchKey(keycode);
        }

        @Override
        public void setInputProcessor(InputProcessor processor) {
            multiplexer.removeProcessor(lastProcessor);
            multiplexer.addProcessor(processor);
            lastProcessor = processor;
        }

        @Override
        public InputProcessor getInputProcessor() {
            return input.getInputProcessor();
        }

        @Override
        public boolean isPeripheralAvailable(Peripheral peripheral) {
            return input.isPeripheralAvailable(peripheral);
        }

        @Override
        public int getRotation() {
            return input.getRotation();
        }

        @Override
        public Orientation getNativeOrientation() {
            return input.getNativeOrientation();
        }

        @Override
        public void setCursorCatched(boolean catched) {
            input.setCursorCatched(catched);
        }

        @Override
        public boolean isCursorCatched() {
            return input.isCursorCatched();
        }

        @Override
        public void setCursorPosition(int x, int y) {
            input.setCursorPosition(x, y);
        }
    }
}

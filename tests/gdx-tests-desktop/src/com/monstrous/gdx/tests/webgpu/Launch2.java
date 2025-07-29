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
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopWindowConfiguration;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

// Test case: this sometimes crashes when opening the new window and sometimes doesn't.

public class Launch2 {

    public static void main (String[] argv) {

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setWindowedMode(640, 480);
        config.setTitle("WebGPU");
        config.backend = WebGPUContext.Backend.VULKAN;

        config.enableGPUtiming = false;

        config.useVsync(true);


        new WgDesktopApplication(new TestChooser(), config);
    }


    static class TestChooser extends ApplicationAdapter {
        private Stage stage;
        private Skin skin;

        @Override
        public void create () {
            Gdx.graphics.setContinuousRendering(false);
            stage = new WgStage(new ScreenViewport());
            Gdx.input.setInputProcessor(stage);
            skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));



            Table container = new Table();
            stage.addActor(container);
            container.setFillParent(true);

            final TextButton testButton = new TextButton("   GO!   ", skin);
            container.add(testButton);


            testButton.addListener(new ChangeListener() {
                @Override
                public void changed (ChangeEvent event, Actor actor) {



                    ApplicationListener test = new Lighting2();

                    WgDesktopWindowConfiguration winConfig = new WgDesktopWindowConfiguration();
                    winConfig.setWindowedMode(640, 480);
                    winConfig.setWindowPosition(((WgDesktopGraphics)Gdx.graphics).getWindow().getPositionX() + 40,
                        ((WgDesktopGraphics)Gdx.graphics).getWindow().getPositionY() + 40);
                    winConfig.useVsync(true);


                    ((WgDesktopApplication)Gdx.app).newWindow(test, winConfig);
                    System.out.println("Started test");
                }
            });

        }

        @Override
        public void render () {
            stage.act();
            stage.draw();
        }

        @Override
        public void resize (int width, int height) {
            stage.getViewport().update(width, height, true);
        }

        @Override
        public void dispose () {
            skin.dispose();
            stage.dispose();
        }
    }


}

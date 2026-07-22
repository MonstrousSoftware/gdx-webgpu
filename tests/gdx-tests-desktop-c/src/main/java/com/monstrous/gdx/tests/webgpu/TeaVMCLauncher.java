package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.gdx.teavm.backends.glfw.GLFWWindowConfiguration;
import com.github.xpenatan.webgpu.JWebGPULoader;
import com.monstrous.gdx.tests.webgpu.utils.AutoTestRunner;
import com.monstrous.gdx.tests.webgpu.utils.TestChooser;
import com.monstrous.gdx.tests.webgpu.utils.WindowOpener;
import com.monstrous.gdx.tests.webgpu.utils.WindowOpenerRegistry;
import com.monstrous.gdx.webgpu.backends.teavmc.WgCApplication;
import com.monstrous.gdx.webgpu.backends.teavmc.WgCApplicationConfiguration;
import com.monstrous.gdx.webgpu.backends.teavmc.WgCGraphics;

/** Launches the shared gdx-webgpu tests on the native TeaVM C backend. */
public final class TeaVMCLauncher {
    private TeaVMCLauncher() {
    }

    public static void main(String[] args) {
        WgCApplicationConfiguration config = new WgCApplicationConfiguration();
        config.setTitle("gdx-webgpu TeaVM C tests");
        config.setWindowedMode(900, 740);
        config.enableGPUtiming = false;
        config.samples = 4;
        config.useVsync(true);

        if(args.length == 0) {
            registerWindowOpener();
        }
        new WgCApplication(createListener(args), config);
    }

    private static void registerWindowOpener() {
        WindowOpenerRegistry.setOpener(new WindowOpener() {
            @Override
            public boolean open(String testName) {
                ApplicationListener test = WebGPUTests.newTest(testName);
                if(test == null) {
                    return false;
                }

                WgCGraphics graphics = (WgCGraphics)Gdx.graphics;
                GLFWWindowConfiguration windowConfig = new GLFWWindowConfiguration();
                windowConfig.setTitle(testName + " - TeaVM C - " + JWebGPULoader.getBackend());
                windowConfig.setWindowedMode(640, 480);
                windowConfig.setWindowPosition(graphics.getWindow().getPositionX() + 40,
                        graphics.getWindow().getPositionY() + 40);
                windowConfig.useVsync(false);
                ((WgCApplication)Gdx.app).newWindow(test, windowConfig);
                System.out.println("Started test (new window): " + testName);
                return true;
            }
        });
    }

    private static ApplicationListener createListener(String[] args) {
        if(args.length == 0) {
            return new TestChooser();
        }
        if(args[0].trim().equalsIgnoreCase("auto")) {
            return new AutoTestRunner();
        }

        String testName = args[0].trim();
        if(WebGPUTests.forName(testName) == null) {
            throw new IllegalArgumentException("Test not found: " + testName);
        }

        ApplicationListener test = WebGPUTests.newTest(testName);
        if(test == null) {
            throw new IllegalStateException("Unable to create test: " + testName);
        }
        return test;
    }
}

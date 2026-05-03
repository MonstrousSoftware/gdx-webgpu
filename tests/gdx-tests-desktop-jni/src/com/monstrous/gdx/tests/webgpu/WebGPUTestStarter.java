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

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.monstrous.gdx.tests.webgpu.utils.AutoTestRunner;
import com.monstrous.gdx.tests.webgpu.utils.TestChooser;
import com.monstrous.gdx.tests.webgpu.utils.WindowOpener;
import com.monstrous.gdx.tests.webgpu.utils.WindowOpenerRegistry;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopWindowConfiguration;

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

        // Register a WindowOpener so the shared TestChooser can open new desktop windows
        WindowOpenerRegistry.setOpener(new WindowOpener() {
            @Override
            public boolean open(String testName) {
                ApplicationListener test = WebGPUTests.newTest(testName);
                if (test == null) return false;
                WgDesktopWindowConfiguration winConfig = new WgDesktopWindowConfiguration();
                winConfig.setTitle(testName);
                winConfig.setWindowedMode(640, 480);
                // position the new window slightly offset from the current one
                winConfig.setWindowPosition(((WgDesktopGraphics) Gdx.graphics).getWindow().getPositionX() + 40,
                        ((WgDesktopGraphics) Gdx.graphics).getWindow().getPositionY() + 40);
                winConfig.useVsync(false);
                Gdx.app.setLogLevel(Application.LOG_DEBUG);
                ((WgDesktopApplication) Gdx.app).newWindow(test, winConfig);
                System.out.println("Started test (new window): " + testName);
                return true;
            }
        });

        new WgDesktopApplication(new TestChooser(), config);
    }
}

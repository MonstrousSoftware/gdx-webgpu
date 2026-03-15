package com.monstrous.gdx.tests.webgpu;

import android.os.Bundle;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.utils.AutoTestRunner;
import com.monstrous.gdx.tests.webgpu.utils.TestChooser;
import com.monstrous.gdx.webgpu.backends.android.WgAndroidApplication;

/**
 * Android test launcher for gdx-webgpu.
 * <p>
 * Supports three modes via Intent extras:
 * <ul>
 *   <li><b>Auto mode</b> – runs every registered test for 3 seconds each:
 *       {@code adb shell am start -n com.monstrous.gdx.tests.webgpu/.GdxTestActivity --es test auto}</li>
 *   <li><b>Single test</b> – runs one test by class name:
 *       {@code adb shell am start -n com.monstrous.gdx.tests.webgpu/.GdxTestActivity --es test Particles3D}</li>
 *   <li><b>Interactive chooser</b> (default) – no extras needed.</li>
 * </ul>
 */
public class GdxTestActivity extends WgAndroidApplication {

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useRotationVectorSensor = true;
        config.useGyroscope = true;
        config.renderUnderCutout = true;

        // Read the "test" Intent extra to decide which mode to launch.
        // Passed via:  adb shell am start ... --es test "auto"
        //         or:  adb shell am start ... --es test "Particles3D"
        ApplicationListener listener;
        String testExtra = getIntent().getStringExtra("test");

        if (testExtra != null && testExtra.equalsIgnoreCase("auto")) {
            listener = new AutoTestRunner();
        } else if (testExtra != null && !testExtra.isEmpty()) {
            ApplicationListener test = WebGPUTests.newTest(testExtra);
            if (test != null) {
                listener = test;
            } else {
                System.out.println("Test not found: " + testExtra + ", falling back to TestChooser.");
                listener = new TestChooser();
            }
        } else {
            listener = new TestChooser();
        }

        initialize(listener, config);
    }
}

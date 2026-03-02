package com.monstrous.gdx.tests.webgpu;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.utils.TestChooser;
import com.monstrous.gdx.webgpu.backends.android.WgAndroidApplication;

public class GdxTestActivity extends WgAndroidApplication {

    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        // and run the application...
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        config.useImmersiveMode = true;
        config.useRotationVectorSensor = true;
        config.useGyroscope = true;
        config.renderUnderCutout = true;
        initialize(new TestChooser(), config);
    }
}

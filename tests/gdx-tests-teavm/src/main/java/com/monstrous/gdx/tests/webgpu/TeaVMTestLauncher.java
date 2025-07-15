package main.java.com.monstrous.gdx.tests.webgpu;

import com.github.xpenatan.gdx.backends.teavm.TeaApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.ASimpleGame;
import com.monstrous.gdx.tests.webgpu.LightingTest;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaApplication;
import main.java.FullScreenTest;
import main.java.HelloTexture;
import main.java.SpriteBatchTest;

public class TeaVMTestLauncher {

    public static void main(String[] args) {
        TeaApplicationConfiguration config = new TeaApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = true;
        config.preloadAssets = true;
        config.useGL30 = true;

        new WgTeaApplication(new LightingTest(), config);
//        new WgTeaApplication(new HelloTexture(), config);
    }
}

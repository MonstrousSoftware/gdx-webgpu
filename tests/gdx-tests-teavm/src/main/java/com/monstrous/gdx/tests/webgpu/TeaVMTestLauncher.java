package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.monstrous.gdx.tests.webgpu.utils.AutoTestRunner;
import com.monstrous.gdx.tests.webgpu.utils.TestChooser;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaApplication;
import com.monstrous.gdx.webgpu.backends.teavm.WgTeaPreloadApplicationListener;
import org.teavm.jso.browser.Window;

public class TeaVMTestLauncher {

    public static String startupLogo = "webgpu-preload.png";

    public static void main(String[] args) {
        WebApplicationConfiguration config = new WebApplicationConfiguration("canvas");
        config.width = 0;
        config.height = 0;
        config.showDownloadLogs = false;

        // example of overriding the default start-up logo
        WgTeaPreloadApplicationListener preloadAppListener = new WgTeaPreloadApplicationListener();
        preloadAppListener.startupLogo = startupLogo;

        // Check URL query parameters for auto mode or a specific test name.
        // Usage: index.html?auto        → runs all tests sequentially
        //        index.html?test=Particles3D → runs a single test
        ApplicationListener listener;
        String query = Window.current().getLocation().getSearch();

        if (query != null && query.contains("auto")) {
            listener = new AutoTestRunner();
        } else if (query != null && query.contains("test=")) {
            String testName = extractParam(query, "test");
            ApplicationListener test = WebGPUTests.newTest(testName);
            if (test != null) {
                listener = test;
            } else {
                System.out.println("Test not found: " + testName + ", falling back to TestChooser.");
                listener = new TestChooser();
            }
        } else {
            listener = new TestChooser();
        }

        new WgTeaApplication(listener, preloadAppListener, config);
    }

    private static String extractParam(String query, String param) {
        // query starts with '?', e.g. "?test=Particles3D&auto"
        String search = param + "=";
        int idx = query.indexOf(search);
        if (idx < 0) return "";
        int start = idx + search.length();
        int end = query.indexOf('&', start);
        if (end < 0) end = query.length();
        return query.substring(start, end);
    }
}

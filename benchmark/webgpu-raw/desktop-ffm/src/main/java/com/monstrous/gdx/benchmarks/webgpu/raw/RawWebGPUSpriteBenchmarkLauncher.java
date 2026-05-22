package com.monstrous.gdx.benchmarks.webgpu.raw;

import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;

public class RawWebGPUSpriteBenchmarkLauncher {
    public static void main(String[] args) {
        BenchmarkConfig benchmarkConfig = BenchmarkConfig.fromArgs(args);
        JWebGPUBackend webgpuBackend = parseWebGPUBackend(args, JWebGPUBackend.WGPU);
        WebGPUContext.Backend backend = parseBackend(args, WebGPUContext.Backend.DEFAULT);
        int samples = parseIntArg(args, "samples", 1);
        String binding = parseStringArg(args, "binding", System.getProperty("benchmark.binding", "ffm"));
        if (samples != 1) {
            throw new IllegalArgumentException("The raw WebGPU sprite benchmark currently supports --samples=1 only.");
        }

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setTitle("gdx benchmark raw WebGPU sprites");
        config.setWindowedMode(benchmarkConfig.width, benchmarkConfig.height);
        config.backendWebGPU = webgpuBackend;
        config.backend = backend;
        config.enableGPUtiming = false;
        config.samples = 1;
        config.setForegroundFPS(0);
        config.useVsync(false);

        String backendName = "webgpu-raw-" + binding + "-" + webgpuBackend + "-" + backend;
        new WgDesktopApplication(new RawSpriteBenchmarkApplication(backendName, benchmarkConfig), config);
    }

    private static JWebGPUBackend parseWebGPUBackend(String[] args, JWebGPUBackend defaultValue) {
        String value = parseStringArg(args, "webgpu", null);
        return value == null ? defaultValue : JWebGPUBackend.valueOf(value);
    }

    private static WebGPUContext.Backend parseBackend(String[] args, WebGPUContext.Backend defaultValue) {
        String value = parseStringArg(args, "backend", null);
        return value == null ? defaultValue : WebGPUContext.Backend.valueOf(value);
    }

    private static int parseIntArg(String[] args, String key, int defaultValue) {
        String value = parseStringArg(args, key, null);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static String parseStringArg(String[] args, String key, String defaultValue) {
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }
}

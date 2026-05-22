package com.monstrous.gdx.benchmarks.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.monstrous.gdx.benchmarks.BenchmarkApplication;
import com.monstrous.gdx.benchmarks.BenchmarkBackend;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class WebGPUBenchmarkLauncher {
    public static void main(String[] args) {
        BenchmarkConfig benchmarkConfig = BenchmarkConfig.fromArgs(args);
        JWebGPUBackend webgpuBackend = parseWebGPUBackend(args, JWebGPUBackend.WGPU);
        WebGPUContext.Backend backend = parseBackend(args, WebGPUContext.Backend.DEFAULT);
        int samples = parseIntArg(args, "samples", 1);
        String binding = parseStringArg(args, "binding", System.getProperty("benchmark.binding", "jni"));

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setTitle("gdx benchmark WebGPU");
        config.setWindowedMode(benchmarkConfig.width, benchmarkConfig.height);
        config.backendWebGPU = webgpuBackend;
        config.backend = backend;
        config.enableGPUtiming = false;
        config.samples = samples;
        config.setForegroundFPS(0);
        config.useVsync(false);

        String backendName = "webgpu-" + binding + "-" + webgpuBackend + "-" + backend;
        new WgDesktopApplication(new BenchmarkApplication(new WebGPUBenchmarkBackend(backendName), benchmarkConfig),
                config);
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

    private static class WebGPUBenchmarkBackend implements BenchmarkBackend {
        private final String name;

        WebGPUBenchmarkBackend(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Batch createSpriteBatch(int maxSprites) {
            return new WgSpriteBatch(maxSprites);
        }

        @Override
        public Texture createTexture(String internalPath) {
            return new WgTexture(Gdx.files.internal(internalPath));
        }
    }
}

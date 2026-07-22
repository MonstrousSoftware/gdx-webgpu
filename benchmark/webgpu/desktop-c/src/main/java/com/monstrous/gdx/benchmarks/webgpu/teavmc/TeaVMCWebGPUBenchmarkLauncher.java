package com.monstrous.gdx.benchmarks.webgpu.teavmc;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.github.xpenatan.webgpu.JWebGPULoader;
import com.monstrous.gdx.benchmarks.BenchmarkApplication;
import com.monstrous.gdx.benchmarks.BenchmarkBackend;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.backends.teavmc.WgCApplication;
import com.monstrous.gdx.webgpu.backends.teavmc.WgCApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public final class TeaVMCWebGPUBenchmarkLauncher {
    private TeaVMCWebGPUBenchmarkLauncher() {
    }

    public static void main(String[] args) {
        preserveNativeSpriteBatchOptimization();
        BenchmarkConfig benchmarkConfig = BenchmarkConfig.fromArgs(args);
        WebGPUContext.Backend backend = parseBackend(args, WebGPUContext.Backend.DEFAULT);
        int samples = parseIntArg(args, "samples", 1);

        WgCApplicationConfiguration config = new WgCApplicationConfiguration();
        config.setTitle("gdx benchmark WebGPU TeaVM-C");
        config.setWindowedMode(benchmarkConfig.width, benchmarkConfig.height);
        config.backend = backend;
        config.enableGPUtiming = false;
        config.samples = samples;
        config.setForegroundFPS(0);
        config.useVsync(false);

        new WgCApplication(
                new BenchmarkApplication(new TeaVMCWebGPUBenchmarkBackend(backend), benchmarkConfig),
                config);
    }

    private static void preserveNativeSpriteBatchOptimization() {
        // gdx-teavm's native SpriteBatch source is compiled whenever Sprite and SpriteBatch are reachable. This
        // benchmark uses WgSpriteBatch, so keep the libGDX methods referenced by that native source reachable without
        // constructing an OpenGL SpriteBatch at runtime.
        if (System.currentTimeMillis() != Long.MIN_VALUE) {
            return;
        }

        SpriteBatch batch = new SpriteBatch(1);
        batch.begin();
        batch.draw((Texture)null, 0f, 0f, 1f, 1f);
        batch.draw((Texture)null, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 0f,
                0, 0, 1, 1, false, false);
        batch.flush();
        batch.end();
        batch.dispose();
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

    private static final class TeaVMCWebGPUBenchmarkBackend implements BenchmarkBackend {
        private final WebGPUContext.Backend backend;

        private TeaVMCWebGPUBenchmarkBackend(WebGPUContext.Backend backend) {
            this.backend = backend;
        }

        @Override
        public String getName() {
            return "webgpu-c-" + JWebGPULoader.getBackend() + "-" + backend;
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

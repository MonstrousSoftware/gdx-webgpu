package com.monstrous.gdx.benchmarks.webgpu.raw;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.TimeUtils;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class RawSpriteBenchmarkApplication extends ApplicationAdapter {
    private static final long SECOND = 1000000000L;

    private final String backendName;
    private final BenchmarkConfig config;
    private RawSpriteRenderer renderer;
    private long secondStartNanos;
    private int secondFrames;
    private int loggedSeconds;
    private int sampleCount;
    private int minFps = Integer.MAX_VALUE;
    private int maxFps;
    private long totalFps;
    private boolean finished;

    public RawSpriteBenchmarkApplication(String backendName, BenchmarkConfig config) {
        this.backendName = backendName;
        this.config = config;
    }

    @Override
    public void create() {
        if (!"sprite2d".equalsIgnoreCase(config.testName) && !"spritebatch2d".equalsIgnoreCase(config.testName)) {
            throw new IllegalArgumentException("Unknown raw WebGPU benchmark test: " + config.testName);
        }

        renderer = new RawSpriteRenderer(config);
        renderer.create();
        secondStartNanos = TimeUtils.nanoTime();

        System.out.println("BENCH_START backend=" + backendName
                + " test=sprite2d-raw"
                + " sprites=" + config.sprites
                + " size=" + config.width + "x" + config.height
                + " warmup=" + config.warmupSeconds
                + " seconds=" + config.seconds
                + " rotate=" + config.rotate
                + " scale=" + config.scale
                + " vsync=" + BenchmarkConfig.VSYNC_ENABLED);
    }

    @Override
    public void resize(int width, int height) {
        if (renderer != null) {
            renderer.resize(width, height);
        }
    }

    @Override
    public void render() {
        renderer.render();
        secondFrames++;

        long now = TimeUtils.nanoTime();
        if (now - secondStartNanos >= SECOND) {
            boolean warmup = loggedSeconds < config.warmupSeconds;
            int fps = secondFrames;
            System.out.println("BENCH_SECOND backend=" + backendName
                    + " test=sprite2d-raw"
                    + " sprites=" + config.sprites
                    + " warmup=" + warmup
                    + " fps=" + fps);
            if (!warmup) {
                sampleCount++;
                totalFps += fps;
                minFps = Math.min(minFps, fps);
                maxFps = Math.max(maxFps, fps);
                if (sampleCount >= config.seconds) {
                    finish();
                    return;
                }
            }
            loggedSeconds++;
            secondFrames = 0;
            secondStartNanos = now;
        }
    }

    @Override
    public void dispose() {
        if (renderer != null) {
            renderer.dispose();
        }
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        int min = sampleCount == 0 ? 0 : minFps;
        int avg = sampleCount == 0 ? 0 : (int)Math.round((double)totalFps / sampleCount);
        System.out.println("BENCH_RESULT backend=" + backendName
                + " test=sprite2d-raw"
                + " sprites=" + config.sprites
                + " avgFps=" + avg
                + " minFps=" + min
                + " maxFps=" + maxFps
                + " samples=" + sampleCount);
        writeResult(avg, min);
        Gdx.app.exit();
    }

    private void writeResult(int avg, int min) {
        if (config.resultFile == null || config.resultFile.length() == 0) {
            return;
        }

        File file = new File(config.resultFile);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        boolean writeHeader = !file.isFile() || file.length() == 0;
        try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
            if (writeHeader) {
                writer.println("backend\ttest\tsprites\tvsync\tavgFps\tminFps\tmaxFps\tsamples");
            }
            writer.println(backendName
                    + "\tsprite2d-raw"
                    + "\t" + config.sprites
                    + "\t" + BenchmarkConfig.VSYNC_ENABLED
                    + "\t" + avg
                    + "\t" + min
                    + "\t" + maxFps
                    + "\t" + sampleCount);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write benchmark result file: " + config.resultFile, e);
        }
    }
}

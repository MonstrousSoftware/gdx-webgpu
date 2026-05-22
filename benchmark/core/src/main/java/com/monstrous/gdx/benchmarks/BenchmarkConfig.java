package com.monstrous.gdx.benchmarks;

public class BenchmarkConfig {
    public static final boolean VSYNC_ENABLED = false;

    public String testName = "sprite2d";
    public int sprites = 8191;
    public int width = 640;
    public int height = 480;
    public int warmupSeconds = 2;
    public int seconds = 10;
    public boolean rotate = true;
    public boolean scale = true;
    public String resultFile;

    public static BenchmarkConfig fromArgs(String[] args) {
        BenchmarkConfig config = new BenchmarkConfig();
        for (String arg : args) {
            if (arg == null || arg.length() == 0) {
                continue;
            }
            if (!arg.startsWith("--")) {
                config.testName = arg;
                continue;
            }

            int eq = arg.indexOf('=');
            String key = eq >= 0 ? arg.substring(2, eq) : arg.substring(2);
            String value = eq >= 0 ? arg.substring(eq + 1) : "true";

            if ("test".equalsIgnoreCase(key)) {
                config.testName = value;
            } else if ("sprites".equalsIgnoreCase(key)) {
                config.sprites = Integer.parseInt(value);
            } else if ("width".equalsIgnoreCase(key)) {
                config.width = Integer.parseInt(value);
            } else if ("height".equalsIgnoreCase(key)) {
                config.height = Integer.parseInt(value);
            } else if ("warmup".equalsIgnoreCase(key)) {
                config.warmupSeconds = Integer.parseInt(value);
            } else if ("seconds".equalsIgnoreCase(key)) {
                config.seconds = Integer.parseInt(value);
            } else if ("rotate".equalsIgnoreCase(key)) {
                config.rotate = Boolean.parseBoolean(value);
            } else if ("scale".equalsIgnoreCase(key)) {
                config.scale = Boolean.parseBoolean(value);
            } else if ("resultFile".equalsIgnoreCase(key)) {
                config.resultFile = value;
            }
        }
        return config;
    }
}

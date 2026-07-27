# gdx benchmark tests

This module contains benchmark cases that are backend-agnostic. Desktop modules provide backend factories so a
benchmark can use the same test body with stock libGDX LWJGL3 or gdx-webgpu.

## SpriteBatch 2D

Compare WebGPU and stock libGDX LWJGL3 in one run:

```bash
./gradlew :benchmark:compare
```

Preset task for the standard 8191-sprite comparison:

```bash
./gradlew :benchmark:compareSprite2d
```

This uses JNI WebGPU with `WGPU` and `WebGPUContext.Backend.DEFAULT`, then runs stock libGDX LWJGL3.

Run the isolated raw WebGPU sprite pipeline:

```bash
./gradlew :benchmark:rawSprite2dWgpuJni
```

This benchmark does not use `WgSpriteBatch`. It pre-creates one WebGPU pipeline and bind group, uploads compact
per-instance sprite data, and draws all sprites with one instanced triangle-strip draw.
Run the same raw benchmark through FFM with:

```bash
./gradlew :benchmark:rawSprite2dWgpuFfm
```

Preset task for the same benchmark using the WebGPU Vulkan backend:

```bash
./gradlew :benchmark:compareSprite2dVulkan
```

Preset task for the same benchmark using the WebGPU OpenGL backend:

```bash
./gradlew :benchmark:compareSprite2dOpenGL
```

Preset tasks for FFM:

```bash
./gradlew :benchmark:compareSprite2dFfm
./gradlew :benchmark:compareSprite2dFfmVulkan
./gradlew :benchmark:compareSprite2dFfmOpenGL
./gradlew :benchmark:compareSprite2dFfmD3D12
./gradlew :benchmark:compareSprite2dFfmDawn
```

Run the full explicit matrix:

```bash
./gradlew :benchmark:compareSprite2dMatrix
```

The matrix runs JNI `WGPU DEFAULT`, JNI `WGPU VULKAN`, JNI `WGPU OPENGL`, JNI `WGPU D3D12`,
JNI `DAWN DEFAULT`, FFM `WGPU DEFAULT`, FFM `WGPU VULKAN`, FFM `WGPU OPENGL`, FFM `WGPU D3D12`,
FFM `DAWN DEFAULT`, stock libGDX LWJGL3, GraalVM WebGPU JNI `WGPU DEFAULT`, GraalVM WebGPU FFM `WGPU DEFAULT`,
raw JNI `WGPU DEFAULT`, raw FFM `WGPU DEFAULT`, TeaVM-C `WGPU DEFAULT`, then TeaVM-C `DAWN DEFAULT`.
This avoids relying on whatever
`WebGPUContext.Backend.DEFAULT` chooses on the current machine.
It also writes a Markdown report to `benchmark/build/benchmark-results/sprite2d-matrix/results.md`.
If `native-image` is unavailable, the two GraalVM rows are reported as skipped and the rest of the matrix continues.
Set `GRAALVM_HOME` to a GraalVM installation to include them; `-PbenchIncludeGraalvm=true` forces the build attempt.
Benchmark runs force vsync off on every platform module; there is no benchmark command-line option to enable it.
The report includes a `VSync` column so capped runs are visible.

The compare task runs WebGPU first, then LWJGL3, with the same shared benchmark settings. Configure it with Gradle
properties:

```bash
./gradlew :benchmark:compare -PbenchSprites=8191 -PbenchSeconds=10 -PbenchWarmup=2 -Pwebgpu=WGPU -PnativeBackend=DEFAULT
```

Run WebGPU JNI:

```bash
./gradlew :benchmark:webgpu:desktop-jni:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run WebGPU FFM:

```bash
./gradlew :benchmark:webgpu:desktop-ffm:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Build WebGPU TeaVM-C with WGPU (the default), then run the configurable benchmark task:

```bash
./gradlew :benchmark:webgpu:desktop-c:gdx_teavm_glfw_build
./gradlew :benchmark:webgpu:desktop-c:benchmark -PbenchSprites=8191 -PbenchSeconds=10 -PbenchWarmup=2
```

Use `-PwebgpuCBackend=dawn` on each command to build and run the same TeaVM-C benchmark with Dawn:

```bash
./gradlew :benchmark:webgpu:desktop-c:gdx_teavm_glfw_build -PwebgpuCBackend=dawn
./gradlew :benchmark:webgpu:desktop-c:benchmark -PwebgpuCBackend=dawn
```

The TeaVM-C module detects Windows x64, Linux x64, macOS x64, or macOS arm64 and resolves the matching jWebGPU
native artifact. The `gdx_teavm_glfw_build` task generates and compiles the native executable, while
`gdx_teavm_glfw_run` generates, builds, and runs it with default benchmark arguments. The module's `benchmark` task
runs an existing build and accepts the `bench*`, `nativeBackend`, and `webgpuSamples` Gradle properties shown above.
The full matrix uses `gdx_teavm_glfw_benchmark` in isolated nested builds so WGPU and Dawn are both generated and
measured in one matrix invocation.

The stock WebGPU benchmark code is split into `benchmark:webgpu:core` for `WebGPUBenchmarkLauncher` and
`benchmark:webgpu:desktop-jni` / `benchmark:webgpu:desktop-ffm` for JVM platform dependencies. The
`benchmark:webgpu:desktop-c` module has a TeaVM-C-specific launcher and reuses the same backend-agnostic benchmark
case from `benchmark:core`.

Run WebGPU through GraalVM native image:

```bash
./gradlew :benchmark:compareSprite2dGraalvm
./gradlew :benchmark:compareSprite2dGraalvmFfm
./gradlew :benchmark:graalvm:desktop-jni:benchmarkJvm
./gradlew :benchmark:graalvm:desktop-ffm:benchmarkJvm
./gradlew :benchmark:graalvm:desktop-jni:benchmarkRelease
./gradlew :benchmark:graalvm:desktop-ffm:benchmarkRelease
```

`benchmarkRelease` builds the optimized native executable, copies the benchmark texture plus native LWJGL/libGDX
libraries beside it, and runs WebGPU through the selected GraalVM desktop binding. Configure both with the same
properties used by the other WebGPU benchmark tasks, for example:

```bash
./gradlew :benchmark:graalvm:desktop-jni:benchmarkRelease -PbenchSprites=8191 -Pwebgpu=WGPU -PnativeBackend=DEFAULT
./gradlew :benchmark:graalvm:desktop-ffm:benchmarkRelease -PbenchSprites=8191 -Pwebgpu=WGPU -PnativeBackend=DEFAULT
```

The native-image tasks require a GraalVM JDK with `native-image`; set `GRAALVM_HOME` or run Gradle from a GraalVM
`JAVA_HOME` if the current JVM is not GraalVM.

Run raw WebGPU JNI:

```bash
./gradlew :benchmark:webgpu-raw:desktop-jni:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run raw WebGPU FFM:

```bash
./gradlew :benchmark:webgpu-raw:desktop-ffm:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

The raw benchmark code is split into `benchmark:webgpu-raw:core` for the renderer/test and
`benchmark:webgpu-raw:desktop-jni` / `benchmark:webgpu-raw:desktop-ffm` for launchers.

Run stock libGDX LWJGL3:

```bash
./gradlew :benchmark:lwjgl3:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2"
```

Each run closes itself and prints `BENCH_RESULT` with average, min, and max FPS after warmup.

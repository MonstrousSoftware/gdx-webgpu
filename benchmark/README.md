# gdx benchmark tests

This module contains benchmark cases that are backend-agnostic. Desktop launchers in sibling modules provide backend
factories so a benchmark can use the same test body with stock libGDX LWJGL3 or gdx-webgpu.

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
FFM `DAWN DEFAULT`, stock libGDX LWJGL3, raw JNI `WGPU DEFAULT`, then raw FFM `WGPU DEFAULT`.
This avoids relying on whatever
`WebGPUContext.Backend.DEFAULT` chooses on the current machine.
It also writes a Markdown report to `benchmark/build/benchmark-results/sprite2d-matrix/results.md`.
Benchmark runs force vsync off on every platform module; there is no benchmark command-line option to enable it.
The report includes a `VSync` column so capped runs are visible.

The compare task runs WebGPU first, then LWJGL3, with the same shared benchmark settings. Configure it with Gradle
properties:

```bash
./gradlew :benchmark:compare -PbenchSprites=8191 -PbenchSeconds=10 -PbenchWarmup=2 -Pwebgpu=WGPU -PnativeBackend=DEFAULT
```

Run WebGPU JNI:

```bash
./gradlew :benchmark:webgpu-jni:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run WebGPU FFM:

```bash
./gradlew :benchmark:webgpu-ffm:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run raw WebGPU JNI:

```bash
./gradlew :benchmark:webgpu-raw-jni:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run raw WebGPU FFM:

```bash
./gradlew :benchmark:webgpu-raw-ffm:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2 --webgpu=WGPU --backend=DEFAULT"
```

Run stock libGDX LWJGL3:

```bash
./gradlew :benchmark:lwjgl3:benchmark --args="--test=sprite2d --sprites=8191 --seconds=10 --warmup=2"
```

Each run closes itself and prints `BENCH_RESULT` with average, min, and max FPS after warmup.

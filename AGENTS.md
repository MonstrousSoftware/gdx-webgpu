# gdx-webgpu — AI Context Memory File

> **Purpose:** This file helps AI assistants remember how this project works across chat sessions.
> Last updated: 2026-03-28

---

## Project Overview

**gdx-webgpu** is a **WebGPU rendering backend for libGDX** (the Java game framework). It replaces OpenGL with WebGPU for cross-platform GPU rendering (desktop via Dawn/wgpu-native, web via WebGPU browser API, and Android).

Desktop currently supports two native binding flavors for jWebGPU:
- **JNI flavor** (`webgpu-core` + `webgpu-desktop-jni`)
- **FFM flavor** (`webgpu-desktop-ffm` main jar + platform jars, Java 24+)

The Java ↔ WebGPU binding is provided by **[xpenatan/jWebGPU](https://github.com/xpenatan/jWebGPU)**. This project uses both JNI and FFM variants depending on Gradle flavor settings.

Descriptor structs still follow jWebGPU's **flyweight/obtain() scratch memory** pattern: `.obtain()` reuses a singleton native memory block and is overwritten by the next `.obtain()` call of the same type. Never hold two `.obtain()` results of the same type simultaneously.

---

## Workspace Structure

```
gdx-webgpu/
├── gdx-webgpu/              ← Core library (platform-independent)
│   └── src/main/java/com/monstrous/gdx/webgpu/
│       ├── application/      ← WebGPUContext, WebGPUApplication, WgGraphics
│       ├── graphics/         ← WgTexture, WgCubemap, Binder, WgMesh, WgShaderProgram
│       │   ├── g2d/          ← WgSpriteBatch, WgBitmapFont, WgTextureAtlas
│       │   ├── g3d/          ← WgModelBatch, WgModel
│       │   │   ├── shaders/  ← WgDefaultShader, WgDepthShader, MaterialsCache
│       │   │   ├── attributes/ ← WgCubemapAttribute, PBRFloatAttribute, CSMShadowAttribute, CascadedShadowAttribute
│       │   │   ├── environment/ ← WgCascadedShadowLight
│       │   │   ├── environment/ibl/ ← IBLGenerator
│       │   │   └── utils/    ← WgModelBuilder
│       │   ├── shader/builder/ ← Shader builder utilities
│       │   └── utils/        ← WgFrameBuffer, WgShapeRenderer, WgScreenReader, WgGL20
│       ├── wrappers/         ← Low-level WebGPU wrapper classes
│       │   ├── WebGPUPipeline, PipelineCache, PipelineSpecification
│       │   ├── WebGPUBindGroup, WebGPUBindGroupLayout, BindGroupCache
│       │   ├── WebGPURenderPass, RenderPassBuilder, RenderPassType
│       │   ├── WebGPUBuffer, WebGPUUniformBuffer, WebGPUVertexBuffer, WebGPUIndexBuffer
│       │   ├── WebGPUComputePipeline, WebGPUPipelineLayout
│       │   ├── SkyBox, ProceduralSkyBox, GammaCorrection, GPUTimer
│       │   └── WebGPUVertexLayout
│       └── rendering/        ← (empty, future use)
├── backends/
│   ├── backend-desktop/      ← GLFW+Dawn/wgpu-native desktop backend
│   ├── backend-teavm/        ← TeaVM web backend
│   └── backend-android/      ← Android backend
├── tests/                    ← Test applications
└── docs/                     ← Documentation
```

---

## Key Architecture Concepts

### Rendering Pipeline Flow

1. **Frame lifecycle:** `WebGPUApplication.beginFrame()` → get surface texture → create command encoder → user rendering → `endFrame()` → finish encoder → submit to queue → present
2. **WgModelBatch flow:** `begin(camera, clearColor)` → creates `WebGPURenderPass` via `RenderPassBuilder.create()` → user calls `render(modelInstance, environment)` → `end()` calls `flush()` (sort renderables, set pipeline, render) → `renderPass.end()`
3. **Pipeline selection:** `WgDefaultShader.setPipeline()` configures `PipelineSpecification` from the current render pass (color formats, depth format, sample count) → `PipelineCache.findPipeline()` does linear scan → creates if not found

### Bind Group System (WebGPU resource binding)

- **`WebGPUBindGroupLayout`**: Describes binding types (buffers, textures, samplers) with `begin()/addBuffer()/addTexture()/addSampler()/end()` pattern
- **`WebGPUBindGroup`**: Holds actual resource bindings, lazily (re)created on `getBindGroup()`/`create()` when marked dirty
- **`Binder`**: High-level uniform manager — maps string names to (groupId, bindingId, offset) tuples, manages bind groups per group ID, builds pipeline layout
- **Group layout ordering**: WebGPU requires groups in sequential order 0, 1, 2, ... The `Binder` stores layouts in a `HashMap<Integer, WebGPUBindGroupLayout>` and iterates by sorted key index when building pipeline layout

### Frame Buffer / MRT (Multiple Render Targets)

- **`WgFrameBuffer`**: Creates color+depth textures, uses `pushTargetView`/`popTargetView` for nestable FBO rendering
- **MRT support**: `WgFrameBuffer(WGPUTextureFormat[] formats, ...)` creates multiple color attachments
- **Push/pop system**: `WebGPUContext.pushTargetView()` saves current state → sets new target views/formats/depth/viewport. `popTargetView()` restores. FBOs use per-instance arrays for safe nesting
- **MSAA**: Only on screen target (index 0), disabled for FBO rendering (numSamples forced to 1 in pushTargetView)

### Shader System

- **`WgShaderProgram`**: Compiles WGSL source (with prefix defines) to a `WGPUShaderModule`
- **`WgDefaultShader`**: Main PBR shader. Uses 4 bind groups:
  - Group 0: Frame uniforms (camera, lights, shadow map/CSM, cube maps, IBL)
  - Group 1: Materials (managed by `MaterialsCache`)
  - Group 2: Instance data (model matrices, morph weights — storage buffer)
  - Group 3: Skinning (joint matrices — storage buffer, optional)
- **CSM support**: `WgDefaultShader` supports cascaded shadow maps via `CascadedShadowAttribute` / `WgCascadedShadowLight` with shadow depth texture arrays and per-cascade matrices/splits/bias values.
- **Dynamic offsets**: Uniform buffer supports multiple "slices" per frame for different render passes with independent camera/lighting state

### Pipeline Caching

- **`PipelineSpecification`**: Describes all pipeline state (shader, vertex layout, blend, depth, topology, formats, samples, etc.)
- **`PipelineCache`**: Stores created `WebGPUPipeline` objects. Lookup uses hash + equals comparison
- **`PipelineSpecification.hashCode()`**: Cached with dirty flag. Includes all relevant fields
- **Per-shader `PipelineCache`**: Each `WgDefaultShader` instance owns its own cache

### Resource Patterns

- **`WebGPURenderPass`**: pooling Uses libGDX `Pool`. `obtain()` → use → `end()` releases encoder + returns to pool. `dispose()` destroys native wrapper
- **`BindGroupCache`**: Pool for bind groups used by WgImmediateModeRenderer / WgSpriteBatch
- **Texture creation**: `WgTexture` wraps `WGPUTexture` + `WGPUTextureView` + lazy `WGPUSampler`. Mipmap generation done on CPU
- **sRGB handling**: Color textures use `RGBA8UnormSrgb` (auto gamma); non-color (normals, data) use `RGBA8Unorm`

### WgShapeRenderer / WgImmediateModeRenderer (Debug Lines & Immediate-Mode Drawing)

- **`WgShapeRenderer`**: High-level shape API (lines, rects, circles, boxes, etc.). Delegates all vertex submission to a `WgImmediateModeRenderer` (implements libGDX `ImmediateModeRenderer`)
- **Depth shift mechanism**: OpenGL projection matrices produce Z in [-1,1]; WebGPU clip space expects [0,1]. Depth remapping is applied inside rendering classes that consume user matrices:
  - **`WgModelBatch.begin()`**: applies `shiftDepthMatrix = idt().scl(1,1,0.5).trn(0,0,0.5)` to `camera.combined`, restores it in `end()`
  - **`WgImmediateModeRenderer.begin()`**: applies the same depth shift to the incoming `projModelView`
  - **`WgSpriteBatch.updateMatrices()`**: applies its internal sprite-batch depth remap before uploading `combinedMatrix`
  - Result: shader classes and external callers do not manually convert depth ranges
- **`WgImmediateModeRenderer` depth test control**: `enableDepthTest()` / `disableDepthTest()` / `isDepthTestEnabled()` toggle the underlying `PipelineSpecification.useDepthTest`. Used by external engines (e.g., XpeEngine's `XShapeRenderer`) for overlay rendering where debug lines must always appear on top of scene geometry
- **`WgImmediateModeRenderer.setPipeline()` FBO sync**: Before pipeline lookup, syncs `colorFormats` and `numSamples` from the current `WebGPURenderPass`. Without this, the pipeline spec retains stale screen surface values, causing broken depth test when rendering inside an FBO (different format, MSAA disabled)
- **Vertex layout**: Position(3) + ColorPacked(1) + TexCoords(2) + Normal(3) = 9 floats per vertex. The default WGSL shader ignores normals. `@location` mapping: 0=position, 1=color, 2=uv, 3=normal
- **Batching**: Each `begin()`/`end()` pair is one render pass. Within a pass, the IMR accumulates vertices in a CPU-side `FloatBuffer`, flushing on `end()` (or when buffer full). Multiple flushes per frame share a single large GPU vertex buffer via `vbOffset`. Uniform buffer uses slices (one slice per flush for the projection matrix)
- **Bind group pooling**: `BindGroupCache` provides bind groups per flush (uniform slice + texture). All are returned to pool on `end()` → `reset()`

### Key Conventions

- **`(WgGraphics) Gdx.graphics`** → cast to get `WebGPUContext` via `.getContext()`
- **Depth format**: Always `Depth24Plus`
- **Depth range**: WebGPU uses [0,1] (vs OpenGL [-1,1]). The `shiftDepthMatrix` conversion is applied exclusively in the three rendering classes (`WgModelBatch`, `WgSpriteBatch`, `WgImmediateModeRenderer`). Shader classes and user code never need to handle this — they always work with standard OpenGL-convention matrices
- **`encoder` field on `WebGPUContext`**: The current frame's command encoder. May be replaced temporarily (e.g., IBLGenerator creates local encoders)
- **`surfaceFormats[]` / `targetViews[]`**: Arrays (not single values) to support MRT

---

## Known Issues / Gotchas

1. **`obtain()` is NOT a pool** — it's scratch memory reuse. Never hold two `obtain()` results of the same struct type simultaneously. Use `new` when you need coexisting instances (see `IBLGenerator.copyTextureToCubeMap()`)
2. **Descriptor objects via `obtain()` are consumed immediately** — after passing to a WebGPU create/begin function, the memory is available for reuse
3. **Single-target `pushTargetView()` uses shared scratch arrays** — safe for non-nested use, but nested calls would corrupt saved state. `WgFrameBuffer` uses per-instance arrays for this reason
4. **`WebGPUUniformBuffer` alignment**: Dynamic offset stride hardcoded to 256 bytes (the WebGPU spec minimum). TODO: query actual device limits
5. **Mipmap generation**: Done on CPU in `WgTexture.load()`. TODO: compute shader
6. **`RenderPassType` enum**: Has several unused values (`SHADOW_PASS`, `DEPTH_PREPASS`, etc.) marked for cleanup
7. **`WebGPUComputePipeline`**: Marked "TO BE COMPLETED" — basic structure exists but limited functionality

---

## Build & Run

- **Build system**: Gradle with Kotlin DSL
- **Java version**: Check `gradle.properties` for specifics
- **Desktop entry point**: `backends/backend-desktop/` → `WgDesktopApplication`
- **TeaVM web entry point**: `backends/backend-teavm/`
- **Test projects**: `tests/gdx-tests-desktop/`, `tests/gdx-tests-teavm/`

### Running Desktop Tests

Desktop tests are launched from `tests:gdx-tests-desktop` with wrapper tasks for flavor selection.

```bash
# JNI flavor (default desktop binding) - run all tests in auto mode:
./gradlew gdx_webgpu_tests_run_desktop_jni -PdesktopTestArgs="auto"

# JNI flavor - run one test:
./gradlew gdx_webgpu_tests_run_desktop_jni -PdesktopTestArgs="SpriteBatchTest"

# FFM flavor (Java 24+) - run all tests in auto mode:
./gradlew gdx_webgpu_tests_run_desktop_ffm -PdesktopTestArgs="auto"

# FFM flavor (Java 24+) - run one test:
./gradlew gdx_webgpu_tests_run_desktop_ffm -PdesktopTestArgs="SpriteBatchTest"
```

- `gdx_webgpu_tests_run_desktop_ffm` enforces Java 24+ for the Gradle JVM/task properties.
- You can still run `gdx_webgpu_tests_run_desktop` directly, but wrapper tasks are preferred so flavor wiring is explicit.

### Running Android Tests

First build and install the debug APK, then launch via `adb`:

```bash
# Build & install:
./gradlew :tests:gdx-tests-android:installDebug

# Run ALL tests sequentially (auto mode):
adb shell am start -n com.monstrous.gdx.tests.webgpu/.GdxTestActivity --es test auto

# Run a SINGLE test by class name:
adb shell am start -n com.monstrous.gdx.tests.webgpu/.GdxTestActivity --es test Particles3D

# Interactive chooser (default — no extras):
adb shell am start -n com.monstrous.gdx.tests.webgpu/.GdxTestActivity
```

- The `--es test <value>` passes a string Intent extra to `GdxTestActivity`.
- Monitor test output: `adb logcat -s "System.out" | grep "Running test"`
- If `adb` is not on PATH, use the full path: `<SDK>/platform-tools/adb` (SDK path is in `local.properties`).

### Running TeaVM (Web) Tests

Build the TeaVM web app first, which compiles to JS and starts a local Jetty server:

```bash
./gradlew gdx_webgpu_tests_run_teavm
```

Then open the local URL (shown in console output) in a WebGPU-capable browser. URL query parameters control the mode:

```
# Interactive chooser (default):
http://localhost:8080/

# Run ALL tests sequentially (auto mode):
http://localhost:8080/?auto

# Run a SINGLE test by class name:
http://localhost:8080/?test=Particles3D
```

---

## Maintenance Notes

- Keep this file focused on current architecture, library usage, and test execution commands.

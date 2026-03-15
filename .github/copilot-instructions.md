# gdx-webgpu — AI Context Memory File

> **Purpose:** This file helps AI assistants remember how this project works across chat sessions.
> Last updated: 2026-03-15

---

## Project Overview

**gdx-webgpu** is a **WebGPU rendering backend for libGDX** (the Java game framework). It replaces OpenGL with WebGPU for cross-platform GPU rendering (desktop via Dawn/wgpu-native, web via WebGPU browser API, and Android).

The Java ↔ WebGPU binding is provided by **[xpenatan/jWebGPU](https://github.com/nicokempe/jWebGPU)** — a JNI/IDL-based binding layer (`com.github.xpenatan.webgpu.*` package). This binding uses a **flyweight/obtain() pattern** for descriptor structs: `.obtain()` returns a reused singleton native memory block (scratch memory), NOT a pooled object — no explicit release is needed; the memory is overwritten on the next `.obtain()` of the same type. Never hold two `.obtain()` results of the same type simultaneously.

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
│       │   │   ├── attributes/ ← WgCubemapAttribute, PBRFloatAttribute
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
│       │   ├── SkyBox, GammaCorrection, GPUTimer
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
  - Group 0: Frame uniforms (camera, lights, shadow map, cube maps, IBL)
  - Group 1: Materials (managed by `MaterialsCache`)
  - Group 2: Instance data (model matrices, morph weights — storage buffer)
  - Group 3: Skinning (joint matrices — storage buffer, optional)
- **Dynamic offsets**: Uniform buffer supports multiple "slices" per frame for different render passes with independent camera/lighting state

### Pipeline Caching

- **`PipelineSpecification`**: Describes all pipeline state (shader, vertex layout, blend, depth, topology, formats, samples, etc.)
- **`PipelineCache`**: Stores created `WebGPUPipeline` objects. Lookup uses hash + equals comparison
- **`PipelineSpecification.hashCode()`**: Cached with dirty flag. Includes all relevant fields
- **Per-shader `PipelineCache`**: Each `WgDefaultShader` instance owns its own cache

### Resource Patterns

- **`WebGPURenderPass` pooling**: Uses libGDX `Pool`. `obtain()` → use → `end()` releases encoder + returns to pool. `dispose()` destroys native wrapper
- **`BindGroupCache`**: Pool for bind groups used by WgImmediateModeRenderer / WgSpriteBatch
- **Texture creation**: `WgTexture` wraps `WGPUTexture` + `WGPUTextureView` + lazy `WGPUSampler`. Mipmap generation done on CPU
- **sRGB handling**: Color textures use `RGBA8UnormSrgb` (auto gamma); non-color (normals, data) use `RGBA8Unorm`

### Key Conventions

- **`(WgGraphics) Gdx.graphics`** → cast to get `WebGPUContext` via `.getContext()`
- **Depth format**: Always `Depth24Plus`
- **Depth range**: WebGPU uses [0,1] (vs OpenGL [-1,1]). A `shiftDepthMatrix` is used for projection correction
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

Use the Gradle task `gdx_webgpu_tests_run_desktop`:

```bash
# Run ALL tests sequentially (auto-advances through every test):
./gradlew gdx_webgpu_tests_run_desktop --args="auto"

# Run a SINGLE test by class name:
./gradlew gdx_webgpu_tests_run_desktop --args="Particles3D"
```

- The `auto` argument loops through all registered tests automatically.
- Passing a test class name (e.g., `SpriteBatchTest`, `IBL_Spheres`) launches only that test interactively.

---

## Change Log (AI fixes)

### 2026-03-15
- **FIXED (Critical):** `Binder.getPipelineLayout()` — HashMap iteration gave non-deterministic bind group layout ordering. Now iterates sequentially by group index 0, 1, 2, ...
- **FIXED (Critical):** `WebGPUPipeline.canRender()` — Only compared hash codes (collision risk). Now uses hash as fast-path + `equals()` for correctness
- **FIXED (Moderate):** `BindGroupCache` — Only tracked last bind group, leaking earlier ones. Now tracks all active bind groups and returns them all to pool on `reset()`
- **FIXED (GC/Perf):** `RenderPassBuilder.createFirstTargetOnly()` — Created `new WGPUTextureFormat[]` every call (per-frame via SkyBox). Now uses pre-allocated static `singleFormatScratch` array
- **FIXED (GC/Perf):** `SkyBox.getOrCreatePipeline()` — String concatenation (`colorFormat.name() + "_" + numSamples`) every frame for HashMap lookup. Replaced with `LongMap<WebGPUPipeline>` keyed by encoded `(ordinal << 32 | samples)` — zero allocation
- **FIXED (GC/Perf):** `PipelineSpecification.hashCode()` — Used `Objects.hash(...)` with 29+ arguments, creating a varargs `Object[]` and boxing all primitives on every call. Replaced with manual `31 * result + field.hashCode()` chain
- **FIXED (GC/Perf):** `WebGPUVertexLayout.hashCode()` — Allocated `new String[size]`, sorted, and iterated on every call (transitive via `PipelineSpecification.hashCode()`). Now caches hash with dirty flag, recomputes only when `setVertexAttributeLocation()` or `setStepMode()` is called
- **FIXED (GC/Perf):** `WgDefaultShader.setPipeline()` / `WgSpriteBatch.setPipeline()` — Called `formats.clone()` on format change. Now reuses existing array via `System.arraycopy()` when array length matches; allocates only on length change (rare MRT transitions)
- **FIXED (Critical):** `WgDesktopWindow.resizeCallback` — Resize during active frame caused WebGPU validation error (surface texture size mismatch with MSAA/depth attachments). The resize runnable now brackets `context.resize()` with `endFrame()`/`beginFrame()` to properly restart the frame at the new size. Note: `beginFrame()` must remain at the top of `update()` because runnables (input/lifecycle listeners) may execute WebGPU commands requiring an active frame context.
- **FIXED (Critical):** `WgTeaGraphics.resize()` — Same resize-during-active-frame bug as desktop. `context.resize()` was called between `begin()` (which calls `beginFrame()`) and `end()` (which calls `endFrame()`), causing surface texture size mismatch on browser window resize. Now brackets `context.resize()` with `endFrame()`/`beginFrame()`.
- **FIXED (Critical):** `WgTeaGraphics.init()` — `addInitQueue()` / `subtractInitQueue()` deadlock caused web app to hang at preloading. `addInitQueue()` in `init()` set `initQueue=1`, but `subtractInitQueue()` was inside `begin()` which only runs from `step()` which requires `initQueue==0`. Removed the `addInitQueue()`/`subtractInitQueue()` pair entirely; all WebGPU calls (`beginFrame`, `endFrame`, `resize`) are now guarded by the `webGPUReady` flag instead, allowing the preload screen to render via standard GL while WebGPU initializes asynchronously.
- **FIXED (Moderate):** `WgDesktopWindow.resizeCallback` — After resize, `deltaTime` spiked because GLFW's modal resize loop on Windows blocks the event loop. Added `graphics.resetDeltaTime()` after resize to zero out the accumulated time, preventing particles/physics from exploding on the first frame after resize.
- **VERIFIED (Android):** `WgAndroidGraphics.onSurfaceChanged()` — Resize is safe. Called by the render thread BEFORE `onDrawFrame()` (sequential in `WgSurfaceView.RenderThread.run()` loop), so `context.resize()` always completes before `beginFrame()`.
- **VERIFIED:** Full per-frame allocation audit of all rendering hot paths (SpriteBatch, DefaultShader, ImmediateModeRenderer, ShapeRenderer, ModelBatch, RenderPassBuilder, SkyBox, PipelineCache, MaterialsCache, Binder, BindGroupCache, GPUTimer). Zero per-frame `new` allocations confirmed. All 77 automated tests pass.

# gdx-webgpu — AI Context Memory File

> **Purpose:** This file helps AI assistants remember how this project works across chat sessions.
> Last updated: 2026-03-16

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

- **`WebGPURenderPass`**: pooling Uses libGDX `Pool`. `obtain()` → use → `end()` releases encoder + returns to pool. `dispose()` destroys native wrapper
- **`BindGroupCache`**: Pool for bind groups used by WgImmediateModeRenderer / WgSpriteBatch
- **Texture creation**: `WgTexture` wraps `WGPUTexture` + `WGPUTextureView` + lazy `WGPUSampler`. Mipmap generation done on CPU
- **sRGB handling**: Color textures use `RGBA8UnormSrgb` (auto gamma); non-color (normals, data) use `RGBA8Unorm`

### WgShapeRenderer / WgImmediateModeRenderer (Debug Lines & Immediate-Mode Drawing)

- **`WgShapeRenderer`**: High-level shape API (lines, rects, circles, boxes, etc.). Delegates all vertex submission to a `WgImmediateModeRenderer` (implements libGDX `ImmediateModeRenderer`)
- **Depth shift mechanism**: OpenGL projection matrices produce Z in [-1,1]; WebGPU clip space expects [0,1]. The depth conversion `shiftDepthMatrix = idt().scl(1,1,0.5).trn(0,0,0.5)` is applied **inside the three rendering classes** that receive a user-supplied camera/projection matrix:
  - **`WgModelBatch.begin()`**: Shifts `camera.combined` in-place, restores it in `end()`
  - **`WgSpriteBatch.updateMatrices()`**: Shifts the internal `combinedMatrix` before writing to the uniform buffer
  - **`WgImmediateModeRenderer.begin()`**: Shifts the incoming `projModelView` matrix before storing it
  - This means shader classes (`WgDefaultShader`, `WgDepthShader`, etc.) and callers (`WgShapeRenderer`, user code) never need to handle the depth conversion — it is fully transparent
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

Use the Gradle task `gdx_webgpu_tests_run_desktop`:

```bash
# Run ALL tests sequentially (auto-advances through every test):
./gradlew gdx_webgpu_tests_run_desktop --args="auto"

# Run a SINGLE test by class name:
./gradlew gdx_webgpu_tests_run_desktop --args="Particles3D"
```

- The `auto` argument loops through all registered tests automatically.
- Passing a test class name (e.g., `SpriteBatchTest`, `IBL_Spheres`) launches only that test interactively.

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
- **FIXED (Critical):** `WgDesktopWindow.resizeCallback` — Resize during active frame caused WebGPU validation error (surface texture size mismatch with MSAA/depth attachments) and a visible blink (empty frame presented). Replaced the posted-runnable approach (`endFrame()`/`resize()`/`beginFrame()` mid-frame) with a deferred pending-resize pattern: `resizeCallback` now sets `pendingResize`/`pendingResizeWidth`/`pendingResizeHeight` flags, and `update()` applies the resize *before* `beginFrame()`. This ensures the swap chain is reconfigured at the correct size before the surface texture is acquired — no empty frame is ever presented, eliminating the blink entirely. The `refreshCallback` continues to call `update()` during GLFW's Windows modal resize loop, so content renders continuously at the correct size.
- **FIXED (Critical):** `WgTeaGraphics.resize()` — Same resize-during-active-frame bug as desktop. `context.resize()` was called between `begin()` (which calls `beginFrame()`) and `end()` (which calls `endFrame()`), causing surface texture size mismatch on browser window resize. Now brackets `context.resize()` with `endFrame()`/`beginFrame()`.
- **FIXED (Critical):** `WgTeaGraphics.init()` — `addInitQueue()` / `subtractInitQueue()` deadlock caused web app to hang at preloading. `addInitQueue()` in `init()` set `initQueue=1`, but `subtractInitQueue()` was inside `begin()` which only runs from `step()` which requires `initQueue==0`. Removed the `addInitQueue()`/`subtractInitQueue()` pair entirely; all WebGPU calls (`beginFrame`, `endFrame`, `resize`) are now guarded by the `webGPUReady` flag instead, allowing the preload screen to render via standard GL while WebGPU initializes asynchronously.
- **FIXED (Critical):** `WgDesktopGraphics.resetDeltaTime()` — Setting `deltaTime = 0` after resize caused consumers like ImGui to crash (requires strictly positive DeltaTime). Removed the entire `resetDeltaTime` mechanism (`resetDeltaTime` field, the special-case branch in `update()`, the `resetDeltaTime()` method, and the call in `WgDesktopWindow.update()`). The `refreshCallback` already keeps `graphics.update()` running during GLFW's Windows modal resize loop, so `lastFrameTime` stays current and there is no delta spike to prevent — the timing is naturally continuous.
- **VERIFIED (Android):** `WgAndroidGraphics.onSurfaceChanged()` — Resize is safe. Called by the render thread BEFORE `onDrawFrame()` (sequential in `WgSurfaceView.RenderThread.run()` loop), so `context.resize()` always completes before `beginFrame()`.
- **VERIFIED:** Full per-frame allocation audit of all rendering hot paths (SpriteBatch, DefaultShader, ImmediateModeRenderer, ShapeRenderer, ModelBatch, RenderPassBuilder, SkyBox, PipelineCache, MaterialsCache, Binder, BindGroupCache, GPUTimer). Zero per-frame `new` allocations confirmed. All 77 automated tests pass.

### 2026-03-16
- **ADDED:** `PipelineSpecification.isDepthTestEnabled()` — New getter to query the current `useDepthTest` state.
- **ADDED:** `WgImmediateModeRenderer.enableDepthTest()` / `disableDepthTest()` / `isDepthTestEnabled()` — Public API to control depth testing on the underlying pipeline specification. Used by XpeEngine's `XShapeRenderer` for overlay rendering (debug lines always visible on top of scene geometry).
- **FIXED (Critical):** `WgImmediateModeRenderer.setPipeline()` — Pipeline spec was never synced with the current render pass's `colorFormats` and `numSamples`. The spec retained stale values from construction time (screen surface format and MSAA count). When rendering inside an FBO (different format, MSAA disabled), the pipeline was created with mismatched format/samples, causing broken depth testing — debug lines rendered as if they were always behind scene models. Now syncs `colorFormats` and `numSamples` from the render pass before pipeline lookup, matching what `WgDefaultShader.setPipeline()` already does.
- **ADDED:** `WgShapeRenderer.enableDepthShift()` / `disableDepthShift()` / `isDepthShiftEnabled()` — Controls whether the OpenGL[-1,1]→WebGPU[0,1] depth remapping matrix is applied. Default: enabled (correct for 2D ortho cameras). Disable when rendering 3D debug lines alongside `WgModelBatch` so depth values match `WgDefaultShader` (which passes `camera.combined` raw and handles depth in the shader). The depth shift is applied in `WgShapeRenderer.begin()` by pre-multiplying `shiftDepthMatrix` onto the combined matrix before passing to the `WgImmediateModeRenderer`.
- **DOCUMENTED:** Full architecture of `WgShapeRenderer` ↔ `WgImmediateModeRenderer` relationship added to AGENTS.md Key Architecture Concepts: vertex layout (9 floats: pos3+colorPacked1+uv2+normal3), batching model (CPU FloatBuffer → GPU vertex buffer via vbOffset), bind group pooling (BindGroupCache per flush), uniform slicing (one matrix per flush).
- **REFACTORED:** Centralized OpenGL→WebGPU depth conversion into the three rendering classes (`WgModelBatch`, `WgSpriteBatch`, `WgImmediateModeRenderer`). Previously the depth shift was scattered: `WgModelBatch.begin()` mutated `camera.combined` but never restored it in `end()` (bug — permanently corrupted the user's camera); `WgShapeRenderer` had enable/disable depth shift logic with `shiftDepthMatrix`/`shiftedMatrix`/`depthShiftEnabled` fields; `WgDefaultShader` had an unused `shiftDepthMatrix` field. Now:
  - **`WgModelBatch`**: Still shifts `camera.combined` in `begin()`, but now **restores** it in `end()` via `savedCombined` — camera is no longer permanently mutated
  - **`WgImmediateModeRenderer`**: Now applies `shiftDepthMatrix` internally in `begin()` to the incoming `projModelView` matrix
  - **`WgSpriteBatch`**: Already applied the shift internally in `updateMatrices()` — unchanged
  - **`WgShapeRenderer`**: Removed `shiftDepthMatrix`, `shiftedMatrix`, `depthShiftEnabled`, `enableDepthShift()`, `disableDepthShift()`, `isDepthShiftEnabled()` — always passes `combinedMatrix` directly to the IMR
  - **`WgDefaultShader`**: Removed unused `shiftDepthMatrix` field and commented-out code. Added shift of `shadowProjViewTransform` in `bindLights()` — the shadow map was rendered through WgModelBatch (which shifts during rendering), so the shader must shift the lookup matrix to match
  - **`WgDepthShader`**: Cleaned up obsolete depth-range TODO comment
  - **`WgDirectionalShadowLight`**: Removed all depth shift logic (`shiftDepthMatrix`, `shiftedCombined`, `tmpMat4`). `update()` no longer mutates `cam.combined`; `getProjViewTrans()` returns raw `cam.combined`. The depth shift for shadow map lookups is now handled by `WgDefaultShader.bindLights()`
  - **`DebugLinesDepthTest`**: Removed `shapeRenderer.disableDepthShift()` call — no longer needed


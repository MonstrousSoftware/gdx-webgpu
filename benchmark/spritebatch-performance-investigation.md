# SpriteBatch WebGPU Performance Investigation

Date: 2026-05-22

## Scope

The focused comparison was `webgpu-jni-WGPU-DEFAULT` against `lwjgl3` using the benchmark SpriteBatch 2D case with 8191 sprites, 640x480, rotation enabled, scale enabled, one texture, and vsync forced off.

The measured gap remains large. The best WebGPU JNI WGPU DEFAULT runs seen during this investigation were still about 1000 FPS below LWJGL3, and several promising short-run changes did not hold up in longer runs.

## Baseline

| Case | Avg FPS |
|---|---:|
| `webgpu-jni-WGPU-DEFAULT` from earlier matrix | about 3340 |
| `lwjgl3` from earlier matrix | about 4400 |

Later focused runs fluctuated, but the same pattern remained: WebGPU hovered roughly in the low/mid 3000s while LWJGL3 stayed roughly in the low/mid 4000s.

## Attempts And Results

| Attempt | Result | Status |
|---|---|---|
| Forced vsync off in benchmark launchers and config | Fixed the misleading 240 FPS vsync-cap result when the window was foregrounded. | Kept |
| Added focused benchmark tasks so `webgpu-jni-WGPU-DEFAULT` and `lwjgl3` can be compared without running the whole backend matrix | Reduced iteration time and avoided running unrelated backends. | Kept |
| Removed depth attachment from `WgSpriteBatch` render pass and pipeline spec | Avoids creating/binding a depth attachment for pure sprite rendering. Did not close the FPS gap by itself. | Kept |
| Cached the active SpriteBatch pipeline by hash | Avoids repeated pipeline-cache scan when the spec is unchanged. Small overhead reduction, not a full fix. | Kept |
| Avoided redundant scissor calls when scissor is disabled | Small overhead reduction, not a full fix. | Kept |
| Changed surface format preference to choose the sRGB equivalent when available | No meaningful improvement in FPS. It is likely still the correct color-output behavior, but not the missing performance. | Kept for now |
| Temporarily removed the vertex-color `pow()` conversion from the SpriteBatch WGSL shader | No meaningful improvement. Shader ALU was not the bottleneck. | Reverted |
| Temporarily loaded the benchmark texture as non-color `RGBA8Unorm` instead of sRGB | Small/noisy results and one run was worse. Texture color format was not the main bottleneck. | Reverted |
| Temporarily disabled blending | No meaningful improvement. Blend hardware cost was not the main bottleneck. | Reverted |
| Temporarily rendered 1x1 sprites to reduce fragment work | FPS stayed similar. The benchmark is not primarily fragment-fill bound. | Reverted |
| Temporarily disabled rotate/scale work in the benchmark | Both backends got faster; the WebGPU gap stayed large. Sprite math is not the root cause. | Reverted |
| Tested 1 sprite and 4096 sprites | 1 sprite showed similar WebGPU/LWJGL3 FPS, while 4096 sprites already showed a large gap. The problem scales with sprite count / per-frame sprite data / draw path, not fixed frame overhead. | Diagnostic only |
| Replaced direct `FloatBuffer` staging with Java `float[]` staging plus one `BufferUtils.copy` per flush | Slower, roughly mid/high 2000 FPS in one focused run. | Reverted |
| Tried per-sprite `BufferUtils.copy` into the direct buffer | Much slower, about 1900 FPS. | Reverted |
| Added a jWebGPU `WGPUQueue.writeBuffer(... float[] ...)` JNI overload and used it from SpriteBatch | Initial short runs looked slightly better, but longer runs were inconsistent and this path did not remain clearly superior. | Not used by current gdx-webgpu code |
| Deferred SpriteBatch render-pass creation until after CPU vertex upload | One short run looked better, later runs were unstable/lower. No reliable gain. | Reverted |
| Reworked SpriteBatch as instanced rendering inside the core class, first with 6 generated vertices and then with a 4-vertex triangle strip | Did not beat the current non-instanced path. Likely still mixed too much with existing core state and wrapper overhead to isolate the real issue. | Reverted |
| Tried WGPU present mode `Mailbox` instead of `Immediate` when vsync is false | Slower in focused testing. | Reverted |

## Conclusions

The current evidence rules out several easy explanations:

- It is not vsync once benchmark defaults are used.
- It is not primarily fragment fill.
- It is not primarily the Sprite rotation/scale math.
- It is not primarily the shader color conversion.
- It is not fixed frame setup cost, because the 1-sprite case is close to LWJGL3.
- It is not solved by a quick instancing patch inside the existing `WgSpriteBatch`.

The gap appears when sprite count grows, which points toward the per-frame sprite data path and/or the exact WebGPU rendering pipeline setup. The existing renderer still has several layers involved: libGDX `Sprite`, `Batch`, `WgSpriteBatch`, wrapper render passes, wrapper bind groups, pipeline specification/cache state, direct buffer upload, and texture/sampler state inherited from core.

## Current Retained Changes

These changes remain in the working tree because they are conservative and still reduce unnecessary work:

- `WgSpriteBatch` uses a no-depth render pass and no-depth pipeline.
- `WgSpriteBatch` caches the active pipeline hash.
- `WgSpriteBatch` skips redundant scissor application when scissor is disabled.
- `WebGPUApplication` prefers an sRGB surface format when a matching sRGB format is available.
- Benchmark launchers force foreground FPS unlimited and vsync false.

These changes do not solve the performance gap.

## Next Step

The next investigation step should be an isolated WebGPU-only sprite benchmark that does not use `WgSpriteBatch`. The benchmark should pre-create immutable GPU state, keep one pipeline and one bind group hot, upload only compact per-instance sprite data each frame, and draw all 8191 sprites with a single instanced draw call.

That gives us a cleaner answer to two questions:

1. Can a minimal WebGPU sprite path match or beat LWJGL3 on this workload?
2. If it can, which part of the current core `WgSpriteBatch` path is responsible for the gap?

## Raw Benchmark Added

A new raw benchmark group was added at `benchmark/webgpu-raw`. Its `core` module is intentionally separate from `WgSpriteBatch` and uses:

- One startup-created pipeline.
- One startup-created bind group.
- One uniform buffer for the projection matrix.
- One per-frame instance buffer with 10 floats per sprite.
- One instanced draw: 4 triangle-strip vertices by 8191 instances.

Short smoke-test results in this workspace with `-PbenchWarmup=1 -PbenchSeconds=2`:

| Case | Avg FPS |
|---|---:|
| `webgpu-raw-jni-WGPU-DEFAULT` | 4654 |
| current `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 2782 |
| `lwjgl3` | 4098 |

Default-duration results with the task defaults (`warmup=2`, `seconds=10`) were:

| Case | Avg FPS | Min FPS | Max FPS |
|---|---:|---:|---:|
| `webgpu-raw-jni-WGPU-DEFAULT` | 5125 | 3967 | 6100 |
| current `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 2868 | 2629 | 3308 |
| `lwjgl3` | 4648 | 3969 | 4927 |

This proves that WebGPU itself can be fast enough for this 8191-sprite workload in this project. The remaining problem is in the current core SpriteBatch path, not in WGPU, Dawn, or WebGPU as an API.

Two implementation details from the raw module are worth carrying forward:

- Descriptor objects passed through jWebGPU are lifetime-sensitive. The raw benchmark keeps bind-group layout entries and bind-group entries alive for the renderer lifetime, matching the safer pattern used by the existing wrapper classes.
- Direct single-attachment render-pass overloads were unreliable in this path; using a `WGPUVectorRenderPassColorAttachment` matched the existing `RenderPassBuilder` pattern and avoided a native `invalid load op` panic.

Reference used for the raw pipeline direction: https://toji.dev/webgpu-gltf-case-study/

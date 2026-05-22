# WebGPU Sprite Pipeline Audit

Date: 2026-05-22

## Scope

This audit compares the current core `WgSpriteBatch` render path with the isolated raw WebGPU sprite benchmark. The goal is to explain why the raw benchmark is faster than both core WebGPU SpriteBatch and LWJGL3, and to check the current design against the efficient WebGPU rendering patterns described in:

- https://toji.dev/webgpu-gltf-case-study/
- https://toji.dev/webgpu-best-practices/buffer-uploads

## 2026-05-22 Core Pipeline Update

`WgSpriteBatch` now has a WebGPU-native instanced fast path for the stock batch:

- The old CPU-expanded vertex path is still retained as fallback.
- The fast path is enabled only for the stock `WgSpriteBatch` with the default shader/vertex layout and no active custom shader.
- Normal `Batch.draw(...)` texture/region overloads now write one compact instance record instead of four expanded vertices.
- `Sprite.draw(batch)` and `BitmapFont` float-array submissions use the instanced path when the submitted quad has one shared packed color.
- Unsupported cases, including custom subclasses, custom shaders, custom vertex attributes, partial float-array batches, and per-vertex color variation, continue through the original vertex/index path.
- The instanced path uses one instance buffer and a 4-vertex `TriangleStrip` draw with `@builtin(vertex_index)` quad expansion in WGSL.

The retained instance layout is 13 floats per sprite:

`origin.xy, xAxis.xy, yAxis.xy, uv0.xy, uvXAxis.xy, uvYAxis.xy, packedColor`

This is larger than the raw benchmark's 10-float layout because it preserves arbitrary affine quad shape and UV axes from libGDX's `Batch`/`Sprite` inputs.

## Current Core SpriteBatch Pipeline

Frame flow for the original benchmark path before the instanced fast path:

1. `WebGPUApplication.beginFrame()` acquires the surface texture and creates a command encoder.
2. `WgSpriteBatch.begin()` creates a render pass through `RenderPassBuilder.create(...)`.
3. `WgSpriteBatch.setPipeline()` updates the pipeline spec for the active render pass, looks up the cached pipeline, and sets it.
4. Each `Sprite.draw(batch)` calls `WgSpriteBatch.draw(Texture, float[], offset, 20)`.
5. `WgSpriteBatch` copies 20 floats per sprite into a direct CPU vertex buffer: 4 vertices x `(x, y, color, u, v)`.
6. `flush()` writes the uniform slice, gets the bind group, uploads the CPU-expanded vertex buffer with `queue.writeBuffer`, binds the vertex and index buffers, and calls `drawIndexed(spriteCount * 6, 1, ...)`.
7. `WgSpriteBatch.end()` ends the render pass.
8. `WebGPUApplication.endFrame()` submits and presents.

For the 8191-sprite benchmark, core SpriteBatch uploads about 655 KB of vertex data per frame:

`8191 sprites * 4 vertices * 5 floats * 4 bytes = 655280 bytes`

## Raw WebGPU Sprite Pipeline

The raw benchmark path:

1. Creates one bind group layout, one bind group, one pipeline layout, and one render pipeline at startup.
2. Creates one uniform buffer and one instance buffer.
3. Stores sprite centers once.
4. Every frame, writes 10 floats per sprite: center, half-size, rotation, and UV rectangle.
5. Uploads about 327 KB of instance data per frame.
6. Uses one render pass, one pipeline set, one bind group set, one vertex buffer set, and one draw call:

`draw(4, spriteCount, 0, 0)`

For 8191 sprites, raw uploads:

`8191 sprites * 10 floats * 4 bytes = 327640 bytes`

The vertex shader expands each sprite instance into a quad using `@builtin(vertex_index)`.

## Measurements

Default benchmark settings: 8191 sprites, 640x480, rotate enabled, scale enabled, vsync false.

| Path | Avg FPS | Min FPS | Max FPS |
|---|---:|---:|---:|
| old `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 2868 | 2629 | 3308 |
| new `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 4223 | 4024 | 4399 |
| new `webgpu-ffm-WGPU-DEFAULT` `WgSpriteBatch` | 4087 | 3984 | 4174 |
| `lwjgl3` | 4195 | 4090 | 4265 |
| `webgpu-raw-jni-WGPU-DEFAULT` | 7095 | 6853 | 7361 |

Diagnostic run with rotation and scale disabled:

| Path | Avg FPS | Min FPS | Max FPS |
|---|---:|---:|---:|
| `webgpu-raw-jni-WGPU-DEFAULT` | 5112 | 4848 | 5454 |
| current `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 3470 | 3433 | 3494 |
| `lwjgl3` | 5176 | 5104 | 5281 |

Disabling rotation/scale helps the core path, but it does not close the gap. The core path remains much slower than raw and LWJGL3 even when the `Sprite` objects have little transform work to do.

## Validation Against Efficient WebGPU Patterns

The core pipeline is not completely wrong. Several pieces are already aligned with WebGPU best practice:

- Pipelines are cached.
- Bind group layouts and the pipeline layout are created outside the render loop.
- The benchmark uses one texture and one draw flush.
- The SpriteBatch path now avoids a depth attachment.
- `writeBuffer()` is a reasonable upload mechanism for frequently updated buffers.

The main issue is that `WgSpriteBatch` is still modeled as an OpenGL-style SpriteBatch translation. It expands every sprite to four CPU-side vertices, uploads all expanded vertex data every frame, and uses an indexed quad draw. That keeps the libGDX `Batch` contract intact, but it is not the efficient WebGPU shape for a workload where every sprite is the same mesh with per-sprite transform/UV data.

The raw path follows the WebGPU-friendly shape more closely:

- Static GPU state is created up front.
- Repeated geometry is represented as one instanced primitive.
- The per-frame upload is smaller.
- Quad expansion moves to the vertex shader.
- The draw loop has minimal state changes.

## Why Raw Is Faster

Raw is faster for two different reasons:

1. It is a cleaner WebGPU command path.
   It skips `Binder`, `WebGPURenderPass`, `WebGPUVertexBuffer`, dynamic uniform offsets, index-buffer binding, pipeline-spec hashing, and most general-purpose SpriteBatch state.

2. More importantly, it is a different renderer architecture.
   It uploads one compact instance per sprite instead of four complete vertices per sprite. The GPU expands the quad. This halves the per-frame upload size and cuts CPU-side vertex writes substantially.

The raw benchmark is not a full replacement for `Batch`. It does not currently support all core SpriteBatch behavior: arbitrary per-vertex colors, arbitrary user-supplied quad vertices, multiple textures, blend mode changes, BitmapFont edge cases, and all draw overload combinations. It is a proof that WebGPU can be fast for this workload when the renderer is structured around WebGPU’s strengths.

## Conclusion

The original core SpriteBatch path was correct as a compatibility implementation, but it was not the right high-performance WebGPU pipeline for this benchmark. The main issue was not JNI vs FFM and not WGPU vs LWJGL3 as a graphics API comparison. The issue was that core `WgSpriteBatch` preserved the OpenGL/libGDX CPU-expanded vertex model for every sprite.

The implemented instanced fast path fixes that for the common stock-batch cases and brings core WebGPU JNI slightly above the same-session LWJGL3 result in this benchmark: `4223` vs `4195` average FPS.

The raw benchmark remains faster because it is not doing the same libGDX compatibility work. In particular, it writes compact renderer-native instance data directly and computes the shared benchmark rotation once per frame, while `Sprite.draw(batch)` still updates libGDX `Sprite` state and exposes already-expanded vertex data to `Batch`.

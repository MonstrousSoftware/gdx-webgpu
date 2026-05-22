# WebGPU Sprite Pipeline Audit

Date: 2026-05-22

## Scope

This audit compares the current core `WgSpriteBatch` render path with the isolated raw WebGPU sprite benchmark. The goal is to explain why the raw benchmark is faster than core WebGPU SpriteBatch and LWJGL3, and to collect data before deciding which core improvements should be applied.

References used for the renderer direction:

- https://toji.dev/webgpu-gltf-case-study/
- https://toji.dev/webgpu-best-practices/buffer-uploads

## Current Status

The core classes were reverted after the investigation. Current `WgSpriteBatch` uses the original CPU-expanded vertex path.

The benchmark modules are intentionally separate from core code. They exist to provide comparable data and to test rendering models before changing `WgSpriteBatch`.

## Current Core SpriteBatch Pipeline

Frame flow for the current baseline benchmark path:

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
| current baseline `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 2868 | 2629 | 3308 |
| `lwjgl3` | 4195 | 4090 | 4265 |
| `webgpu-raw-jni-WGPU-DEFAULT` | 7095 | 6853 | 7361 |

Diagnostic run with rotation and scale disabled:

| Path | Avg FPS | Min FPS | Max FPS |
|---|---:|---:|---:|
| `webgpu-raw-jni-WGPU-DEFAULT` | 5112 | 4848 | 5454 |
| current baseline `webgpu-jni-WGPU-DEFAULT` `WgSpriteBatch` | 3470 | 3433 | 3494 |
| `lwjgl3` | 5176 | 5104 | 5281 |

Disabling rotation/scale helps the core path, but it does not close the gap. The core path remains much slower than raw and LWJGL3 even when the `Sprite` objects have little transform work to do.

## Validation Against Efficient WebGPU Patterns

The core pipeline is not completely wrong. Several pieces are already aligned with WebGPU best practice:

- Pipelines are cached.
- Bind group layouts and the pipeline layout are created outside the render loop.
- The benchmark uses one texture and one draw flush.
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

The raw benchmark is not a full replacement for `Batch`. It does not currently support all core SpriteBatch behavior: arbitrary per-vertex colors, arbitrary user-supplied quad vertices, multiple textures, blend mode changes, BitmapFont edge cases, and all draw overload combinations. It is a proof that WebGPU can be fast for this workload when the renderer is structured around WebGPU's strengths.

## Conclusion

The current core SpriteBatch path is correct as a compatibility implementation, but it is not the right high-performance WebGPU pipeline for this benchmark. The main issue is not JNI vs FFM and not WGPU vs LWJGL3 as a graphics API comparison. The issue is that core `WgSpriteBatch` preserves the OpenGL/libGDX CPU-expanded vertex model for every sprite.

The raw benchmark is faster because it is not doing the same libGDX compatibility work. In particular, it writes compact renderer-native instance data directly and computes the shared benchmark rotation once per frame, while `Sprite.draw(batch)` still updates libGDX `Sprite` state and exposes already-expanded vertex data to `Batch`.

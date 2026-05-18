# Shader Template System

> Version: 0.2
> Last updated: 2026-05-18

## Purpose

The Shader Template System generates complete WGSL source from readable renderer-specific templates. It lets users customize common shader behavior through modules without copying the whole shader or rebuilding shaders from Java string chunks.

The system is about shader source assembly. Resource layout declarations live in the [Shader Layout System](shader-layout-system.md). Render-target validation, vertex attribute validation, matching depth shaders, and multi-pass orchestration are future validation work outside the template processor.

## Current Status

The Shader Template System is version `0.2` and is a work in progress.

Implemented:

1. `modelbatch.template.wgsl` exists and is intended to match `modelbatch.wgsl` when no modules are applied.
2. `spritebatch.template.wgsl` exists and is intended to match `spritebatch.wgsl` when no modules are applied.
3. The model-batch template is a phase-1 template copied from `modelbatch.wgsl` with a small number of additive slots.
4. The sprite-batch template is a phase-1 template copied from `spritebatch.wgsl` with a helper slot and fragment-output sections.
5. The published model-batch slots are `material.uniformFields`, `material.bindings`, `helpers`, and `color.final`.
6. The published sprite-batch slot is `helpers`.
7. The published model-batch and sprite-batch sections are `fragment.signature` and `fragment.return`.
8. `WgShaderTemplate` can build complete WGSL from text-backed and file-backed templates.
9. `WgslSnippet` supports text snippets, file snippets, and named `@block` snippets.
10. `ShaderDefines` can prepend generated defines to assembled source.
11. Template output can be passed to `WgDefaultShader` through `WgModelBatch.Config.shaderSource`.
12. Template output can be passed to `WgSpriteBatch` through a custom `WgShaderProgram`.
13. `FogOfWar3DTest`, `MRTTest2D`, `MRTTest3D`, and `WgIDShaderProvider` are the current proof cases for external template assembly.

Not complete yet:

1. `depth.template.wgsl`
2. broad `@section` coverage in `modelbatch.template.wgsl` and `spritebatch.template.wgsl` beyond the current fragment output sections
3. `Surface` data model in `modelbatch.template.wgsl`
4. automatic module lifecycle orchestration inside `WgShaderTemplate.build(...)`; callers currently invoke `configureLayout(...)`, `configureDefines(...)`, and `contribute(...)` before calling `build(...)`
5. complete supported include workflow, documentation, and coverage
6. additional templates for font, highlight, immediate-mode, particle, utility, and other shader-owning classes

## Boundaries

The template system owns:

1. Loading template WGSL.
2. Parsing template markers.
3. Inserting WGSL snippets.
4. Appending or replacing named sections.
5. Collecting module names and signatures for diagnostics.
6. Prepending generated defines.
7. Producing complete WGSL source for the existing shader pipeline.
8. Recording layout summaries in `ShaderBuildResult` for diagnostics.

The template system does not own:

1. resource binding allocation
2. renderer-owned layout object application
3. vertex attribute validation
4. render-target count or format validation
5. depth texture validation
6. matching depth-shader variant validation
7. MRT framebuffer creation
8. extra render-pass orchestration
9. renderer `canRender()` replacement

Resource binding and renderer-owned layout application belong to the Shader Layout System. Render-pass, vertex, and multi-pass checks belong to future validation work or explicit renderer/user orchestration.

## Architecture Rules

1. Base shader templates must remain readable WGSL files.
2. Template files use the `.template.wgsl` suffix.
3. `modelbatch.wgsl` and `spritebatch.wgsl` remain the stable built-in fallbacks.
4. `WgDefaultShader` remains the owner of the model-batch Java rendering contract.
5. Core renderer classes do not need to know about template modules.
6. Template output is passed into existing renderer hooks, such as `WgModelBatch.Config.shaderSource`.
7. Material layout output is passed through existing material layout hooks, such as `WgModelBatch.Config.materials`.
8. Full shader replacement remains supported as an escape hatch.

## Package Organization

Template-system classes live under:

```text
com.monstrous.gdx.webgpu.graphics.shader.modular.template
```

Current template package classes:

1. `WgShaderTemplate`
2. `WgslSnippet`
3. `ShaderDefines`
4. `ShaderBuildResult`
5. `ShaderBuildDumper`
6. `ShaderTemplateConfig`
7. `ShaderTemplateException`

Shared module API lives one level above the subsystem packages:

```text
com.monstrous.gdx.webgpu.graphics.shader.modular
```

Current shared classes:

1. `WgShaderModule`

There is no shader-module context object. Modules should carry the data they need through their own constructor/configuration fields instead of receiving a generic context.

## Template Markers

Template shader files and external snippet files use a small marker set.

```text
@slot      additive injection point in a template shader file
@section   replaceable or appendable named region in a template shader file
@block     named WGSL contribution inside an external snippet file
#include   project-specific helper include handled by the template loader
#ifdef     existing conditional preprocessing directive, not a template marker
```

### `@slot`

`@slot` marks a place where modules can insert WGSL without replacing surrounding shader code.

```wgsl
// @slot color.final
```

Use slots for additive customization:

1. final color adjustment
2. fog or mask multiplication
3. debug color override
4. helper declarations
5. extra material fields

### `@section`

`@section` marks a named shader region that can be appended to or replaced.

```wgsl
// @section surface.read
fn readSurface(in: VertexOutput) -> Surface {
    ...
}
// @end
```

Sections are part of the target architecture. The current templates publish only the sections needed by the first MRT proof cases.

Current model-batch and sprite-batch sections:

1. `fragment.signature` lets modules replace the fragment entry-point return type.
2. `fragment.return` lets modules replace the final fragment return block.

These two sections support MRT modules such as normal-output and object-ID-output without exposing arbitrary source replacement.

### `@block`

`@block` is used in external snippet files. It names a WGSL contribution that can be inserted into a matching slot.

```wgsl
// @block color.final
{
    color.rgb *= fogBrightness;
}
// @end
```

### `#include`

`#include` is project-specific template-loader support for shared WGSL helper files.

```wgsl
#include "common/noise.wgsl"
```

The current loader resolves includes for file-backed templates. Treat include usage as limited until there is complete workflow documentation and test coverage.

## Java API Shape

Modules are plain Java objects that participate in an external build flow.

```java
public interface WgShaderModule {
    default String getName() { return getClass().getSimpleName(); }
    default String getSignature() { return getClass().getName(); }
    default void configureDefines(ShaderDefines defines) {}
    default void configureLayout(ShaderLayoutBuilder layout) {}
    default void contribute(WgShaderTemplate template) {}
}
```

Modules are self-contained. For example, a model-batch fog module receives its texture and world bounds when constructed:

```java
public final class FogOfWarShaderModule implements WgShaderModule {
    private final WgTexture fogTexture;
    private final Vector4 worldBounds;

    public void configureLayout(ShaderLayoutBuilder layout) {
        ...
    }
}
```

Sprite, depth, font, and utility modules should follow the same pattern: pass module-specific data directly to the module instead of adding a shared context abstraction.

`WgShaderTemplate.build(...)` does not call those hooks automatically in version `0.2`. The caller is responsible for this order:

1. Create `ShaderDefines`.
2. Create and configure `ShaderLayoutBuilder`.
3. Call each module's `configureDefines(...)`.
4. Call each module's `configureLayout(...)`.
5. Call each module's `contribute(...)`.
6. Apply layout changes where supported.
7. Call `WgShaderTemplate.build(...)`.
8. Pass the generated source and layout objects into existing renderer APIs.

## Layout Integration

Modules can use `configureLayout(...)` to declare uniforms, textures, samplers, bind groups, and binding placement through the Shader Layout System.

The template processor does not allocate bindings itself. It only receives layout summaries for diagnostics and build hashing.

See [Shader Layout System](shader-layout-system.md) for the current `ShaderLayoutBuilder` material-layout helper and the planned resource layout model.

## Model-Batch Integration

The current integration path is external to core model-batch classes.

```java
WgShaderTemplate template = new WgShaderTemplate(
    Gdx.files.classpath("shaders/modelbatch.template.wgsl")
);

ShaderDefines defines = new ShaderDefines();
MaterialUniformLayout materialLayout = MaterialUniformLayout.standard();
ShaderLayoutBuilder layout = new ShaderLayoutBuilder()
    .setMaterialLayout(materialLayout);

// caller invokes module hooks here

layout.apply();
ShaderBuildResult result = template.build(defines, layout, modules);

WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = result.shaderSourceForPipeline;
config.materials = new MaterialsCache(materialLayout);
```

The important rule is that `WgModelBatch`, `WgDefaultShaderProvider`, and `WgDefaultShader` do not need template-specific fields. They receive complete WGSL and material layout objects through existing APIs.

## Sprite-Batch Integration

The current sprite-batch integration path is external to core sprite-batch classes.

```java
WgShaderTemplate template = new WgShaderTemplate(
    Gdx.files.classpath("shaders/spritebatch.template.wgsl")
);

ShaderDefines defines = new ShaderDefines();
defines.define("TEXTURE_COORDINATE");
defines.define("COLOR");

// caller invokes module hooks here

ShaderBuildResult result = template.build(defines, null, modules);
WgShaderProgram shader = new WgShaderProgram("custom-sprite-shader", result.shaderSourceForPipeline);
WgSpriteBatch batch = new WgSpriteBatch(1000, shader);
```

The important rule is that `WgSpriteBatch` does not need template-specific fields. It receives a complete shader through the existing custom `WgShaderProgram` constructor.

## Model-Batch Template Direction

`modelbatch.template.wgsl` should evolve from phase 1 toward this target structure:

```text
Header
Common structs
Frame uniforms
Material uniforms
Bindings
Vertex input/output
Surface struct
Vertex shader
Surface read function
Surface modification slots
Lighting sections
Fragment output section
Fragment shader
Plugin helpers slot
```

The future `Surface` refactor should only be merged after zero-module output remains visually and functionally equivalent to `modelbatch.wgsl` across existing model-batch coverage:

1. unlit
2. lit
3. PBR
4. IBL
5. fog
6. shadow maps
7. cascaded shadow maps
8. skinning
9. morph targets
10. material textures
11. FBO rendering
12. gamma handling

## Debug Output

Every template build should produce a `ShaderBuildResult` containing:

1. template name
2. original template source
3. assembled source
4. defines source
5. final shader source for the pipeline
6. build hash
7. module names
8. module signatures
9. contribution trace
10. layout summary

Generated source should remain easy to inspect. Snippet insertion emits comments identifying the source file or block.

## Proof Case

`FogOfWar3DTest` is the material-layout and color-slot proof case. It should demonstrate:

1. external template assembly
2. module WGSL contribution to `color.final`
3. material uniform registration
4. material texture and sampler registration
5. generated WGSL passed through `WgModelBatch.Config.shaderSource`
6. generated material layout passed through `WgModelBatch.Config.materials`

This proof case must not require template-specific code in `WgDefaultShader`.

`MRTTest3D` is the fragment-output section proof case. It should demonstrate:

1. `helpers` slot contribution for a custom fragment output struct
2. `fragment.signature` replacement for an MRT return type
3. `fragment.return` replacement for writing color and normal attachments
4. generated WGSL passed through `WgModelBatch.Config.shaderSource`

`MRTTest2D` is the sprite-batch fragment-output section proof case. It should demonstrate:

1. `helpers` slot contribution for a custom fragment output struct
2. `fragment.signature` replacement for an MRT return type
3. `fragment.return` replacement for writing red and green channels to separate attachments
4. generated WGSL passed through a custom `WgShaderProgram`
5. custom shader use through `WgSpriteBatch`

`WgIDShaderProvider` is the object-ID output proof case used by picking and edge-detection outline tests. It should demonstrate:

1. `material.uniformFields` contribution for `colored_id`
2. `color.final` contribution for single-target ID output
3. `fragment.signature` and `fragment.return` replacement for MRT color plus ID output
4. compatibility with existing `WgIDShader` material layout extension

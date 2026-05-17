# Modular Shader Architecture

## Purpose

This document defines the modular shader architecture for WebGPU rendering classes. The architecture keeps base shaders readable as complete WGSL files while allowing users to customize common parts of a shader without duplicating the whole shader or splitting it into many Java string chunks.

The primary integration target is the 3D model batch shader because `modelbatch.wgsl` is the largest and most difficult shader to customize today. The same architecture applies to `WgDepthShader`, `WgSpriteBatch`, bitmap fonts, distance field fonts, highlights, immediate-mode rendering, shape rendering, particles, IBL helper shaders, outline/picking shaders, and other shader-owning classes.

The existing `modelbatch.wgsl` behavior remains stable. The model-batch template uses a separate shader file named `modelbatch.template.wgsl`, but continues to use `WgDefaultShader` instead of introducing a parallel Java shader class.

## Architecture Decision

The project will move forward with one combined design called the **Shader Module System**.

The Shader Module System has two concerns:

1. **Shader Template System**
   - Builds customized WGSL source from readable base shader templates.
   - Handles slots, sections, snippet files, defines, and preprocessing integration.
   - Handles includes for reusable helper WGSL.
- Produces assembled shader source that existing renderers can compile.

2. **Shader Requirements System**
   - Describes what runtime setup the generated shader needs.
   - Validates module compatibility with renderer-owned resources, layouts, vertex attributes, and render pass state.
   - Communicates requirements that cannot be satisfied automatically by the shader owner, such as additional render targets or extra passes.

The selected design includes:

- full readable renderer-specific shader templates, starting with `modelbatch.template.wgsl` and expanding to templates such as `depth.template.wgsl`, `spritebatch.template.wgsl`, and other shader-family templates
- semantic injection slots
- replaceable annotation sections
- hook functions built around a `Surface` data model
- optional include support for reusable WGSL helper files
- Java-side shader modules that declare required uniforms, bindings, defines, vertex layout requirements, pipeline implications, and WGSL contributions

This is the committed architecture. The current Java chunk builder and direct full-shader replacement paths can remain temporarily for compatibility, but they are not the long-term customization model.

## Architecture Authority

This document is the source of truth for the modular shader architecture.

Implementation must stay in sync with this document:

- Do not create custom template, module, resolver, provider, shader, pipeline, debug, or binding behavior that is not described here.
- Do not add template-specific fields or logic to core renderer classes unless this document explicitly says that class owns that responsibility.
- Do not introduce alternate flows that bypass the documented architecture, even if they are convenient for one test or proof case.
- If the implementation needs behavior that is not documented, update this document first or in the same change.
- If code and this document disagree, treat that as an architecture bug, not as permission to keep the undocumented code.
- Tests and proof cases must use the same public architecture described here.

The intended implementation rule is simple: build template output outside core shader classes, then pass the complete WGSL source and any required layout/cache objects through existing renderer APIs. Any future change to that rule must be documented here before code depends on it.

## Architecture Sync Checklist

Before merging shader-template changes, verify:

1. `WgModelBatch.Config` receives template output only through existing fields (`shaderSource`, `materials`).
2. Core model-batch classes (`WgModelBatch`, `WgDefaultShaderProvider`, `WgDefaultShader`) have no template-system dependencies.
3. Template assembly (`WgShaderTemplate`, `WgShaderModule`, `ShaderLayoutBuilder`) runs outside core renderer classes.
4. Generated WGSL is passed as complete source (`shaderSourceForPipeline`) through existing explicit shader/source APIs.
5. Generated layout/material requirements are passed through existing layout/cache APIs (for example `MaterialsCache` with `MaterialUniformLayout`).
6. Use cases in this document, including `FogOfWar3DTest`, match the real implementation pattern.
7. Any new architecture behavior introduced in code is documented in this file in the same change.

## Design Principles

- The base shader must remain readable as a complete WGSL file.
- Shader customization must be plugin-style, not copy-the-whole-shader style.
- Extension points must be semantic and limited.
- Avoid one slot per line; that recreates the current chunk-builder problem.
- Any module that injects WGSL requiring uniforms or bindings must also declare those uniforms or bindings in Java.
- The generated shader source must be debuggable and easy to inspect.
- The template/module system must be renderer-agnostic.
- 3D model batch, depth-only rendering, 2D sprite batch, immediate-mode, particle, utility, and other shader families should use the same core template processor and module concepts.
- Shader-owning classes should not need to understand templates. The template system should produce complete WGSL and any matching layout objects, then pass them through the renderer's existing explicit shader/source and layout hooks.
- Template shader files must use the `.template.wgsl` suffix so it is clear they are not loaded directly into WebGPU. They must be processed by `WgShaderTemplate` first.
- Module WGSL must be loadable from external `.wgsl` snippet files. Java string snippets are allowed for tiny cases, but file-based snippets are preferred, especially because the project supports Java 8 and does not have Java text blocks.
- `modelbatch.wgsl` remains available as the stable built-in fallback.
- `modelbatch.template.wgsl` should initially match the behavior of `modelbatch.wgsl`.
- `WgDefaultShader` remains the Java owner of the model batch shader contract.
- Template/module customization should be selected by external template build code, not by adding template fields to renderer configuration.
- Full shader replacement must remain supported as an escape hatch for users who want a completely different shader and do not want to use the template/module style.
- The renderer customization priority remains explicit full shader source first and built-in default source last. Template/module output is just one way to produce the explicit full shader source.

## Current State

`modelbatch.wgsl` is readable because it exists as one complete shader file. It contains the full model batch rendering path:

- frame, model, and material uniform structs
- frame, material, instance, and skinning bindings
- vertex input/output declarations
- morph targets
- skinning
- world and clip-space transforms
- texture sampling
- lighting
- shadow map and cascaded shadow map visibility
- PBR and IBL helpers
- fog
- gamma correction

`WgDefaultShader` owns the Java-side rendering contract:

- uniform offsets and buffer layout
- bind group layouts
- material layout creation
- fallback material textures
- vertex attribute locations
- shader source selection
- render-pass format synchronization
- pipeline cache setup
- environment, shadow, IBL, fog, and light binding

Any shader customization that changes WGSL uniforms, bindings, vertex inputs, or material data must also update the Java-side contract.

`WgSpriteBatch` has similar customization pressure, even if its shaders are smaller. It already accepts explicit shader programs and manages its own pipeline, bind groups, uniforms, and texture bindings. The new solution should let 2D users customize sprite shaders through readable WGSL templates and Java modules instead of requiring complete shader replacement.

`WgDepthShader` also needs to participate. Depth rendering often needs slight shader variations for alpha-tested materials, custom vertex deformation, skinning/morph compatibility, depth-only prepasses, shadow passes, and special cases. These changes should be possible through a depth shader template without duplicating the whole depth shader.

`WgShaderBuilder`, `WgModelBatchShaderBuilder`, and similar Java chunk builders are not the target path for the new system. They reconstruct shaders from many Java string chunks, which makes the full shader hard to read, hard to diff, and easy to let drift from the real WGSL source.

## Architecture

The new system has shared core pieces plus renderer-specific integrations.

Shared core:

- `WgShaderTemplate`: text processor for slots, sections, and includes
- `WgShaderModule`: Java plugin interface for declaring shader contributions and matching runtime metadata
- `ShaderDefines`: helper for module-defined preprocessor defines
- layout helpers for uniforms, bind groups, and vertex requirements
- `WgShaderRequirements`: optional runtime compatibility and render-setup requirements declared by modules

Renderer-specific integrations:

- External model-batch template code uses `modelbatch.template.wgsl` and model-batch-specific slots/sections, then passes complete WGSL through `WgModelBatch.Config.shaderSource`.
- External depth template code uses `depth.template.wgsl` and depth-specific slots/sections, then passes complete WGSL through the existing `WgDepthShader` source parameter.
- External sprite-batch template code uses `spritebatch.template.wgsl` and sprite-specific slots/sections, then passes complete WGSL through the existing sprite shader/source path.
- Other shader families can adopt the same system without copying the model-batch API directly.

Each shader template remains WGSL-first. Java can insert code at named locations, but the main shader flow stays visible in the WGSL file.

## Shared Core API

The shared API names below are the architecture contract. They can be extended, but incompatible renames require updating this document and all call sites.

`WgShaderModule` is implemented by optional feature/plugin classes, not by the existing shader classes themselves. For example, a fog-of-war feature, vegetation wind feature, grayscale sprite effect, or alpha-tested depth feature can implement `WgShaderModule`.

External template build code consumes modules:

- collect modules from caller-owned data
- create the correct `ShaderModuleContext`
- let modules configure defines and layout metadata
- let modules contribute WGSL to `WgShaderTemplate`
- build the final shader source
- pass the final shader source and layout objects into existing renderer APIs

In other words, current shader classes do not become shader modules and do not integrate the template processor directly.

```java
public interface WgShaderModule {
    default String getName() { return getClass().getSimpleName(); }
    default String getSignature(ShaderModuleContext context) { return getClass().getName(); }
    default void configureDefines(ShaderDefines defines, ShaderModuleContext context) {}
    default void configureLayout(ShaderLayoutBuilder layout, ShaderModuleContext context) {}
    default void contribute(WgShaderTemplate template, ShaderModuleContext context) {}
}
```

`getSignature()` is part of template-build diagnostics and external compatibility policy. If a module changes generated WGSL, layout, required attributes, or runtime behavior based on the renderable, material, environment, or renderer configuration, it must return a stable string that includes those choices. Core shader classes are not required to compare module signatures because they do not know about modules.

Possible context:

```java
public final class ShaderModuleContext {
    public final Renderable renderable;       // model batch, nullable for 2D
    public final VertexAttributes attributes; // if known
    public final Environment environment;     // model batch, nullable for 2D
}
```

Generic layout builder (shared across 2D, 3D, and depth):

```java
public final class ShaderLayoutBuilder {
    public BindGroupLayoutBuilder group(int group);
    public VertexLayoutRequirements vertex();

    public UniformField addUniform(String scope, String name, UniformType type);
    public TextureBinding addTexture(String scope, String textureName, String samplerName);

    // explicit override for advanced modules
    public UniformField addUniformAt(String scope, int group, int binding, String name, UniformType type);
    public TextureBinding addTextureAt(String scope, int group, int binding, String textureName, String samplerName);
}
```

Example scopes:

```text
frame
material
sprite
instance
custom.<name>
```

Architecture decision:

- Use one generic layout builder API for all shader families.
- Do not require separate typed builder hierarchies for 2D, 3D, and depth.
- Enforce structural constraints through validation rules (available scopes, available slots/sections, binding availability, and active render-pass requirements).
- Keep explicit binding placement available for advanced modules.

Validation examples:

```java
// invalid if the active shader template/layout does not expose this scope
layout.addUniform("material", "fogWorld", UniformType.VEC4);

// invalid if binding is already used or reserved
layout.addTextureAt("material", 1, 9, "fogTexture", "fogSampler");
```

Renderer-specific convenience helpers are optional and should remain thin wrappers over this generic builder when needed.

Previous typed-style example (for context) is intentionally replaced by the generic API.

Supporting requirements API:

```java
public final class WgShaderRequirements {
    public void requireVertexAttribute(String alias);
    public void requireColorTargetCount(int count);
    public void requireColorTargetFormat(int index, WGPUTextureFormat format);
    public void requireDepthTexture();
    public void requireMatchingDepthShader();
    public void preferCullMode(WGPUCullMode cullMode);
    public void preferDepthTest(boolean enabled);
    public void preferBlending(boolean enabled);
}
```

Requirements should start as validation metadata. Later, individual renderers can choose to apply supported requirements automatically.

Compatibility decision:

- Keep the libGDX compatibility pattern: `canRender()` remains the compatibility gate.
- Do not introduce a parallel compatibility API.
- Core shader classes keep their existing `canRender()` checks and do not know about `WgShaderModule`.
- Shader modules may contribute compatibility constraints to the external template/build layer.
- External checks include required vertex/material attributes, required scopes/slots/sections, active render-target compatibility, and module-signature compatibility between generated variants.

Supporting template API:

```java
public final class WgslSnippet {
    public static WgslSnippet text(String wgsl);
    public static WgslSnippet file(FileHandle file);
    public static WgslSnippet block(FileHandle file, String blockName);
}

public final class WgShaderTemplate {
    public void insert(String slotName, WgslSnippet snippet);
    public void appendToSection(String sectionName, WgslSnippet snippet);
    public void replaceSection(String sectionName, WgslSnippet snippet);
    public void insertMatchingBlocks(FileHandle wgslFile);
    public String build();
}
```

`WgslSnippet` keeps the template API small while allowing every operation to accept raw text, a whole file, or a named block from a file. Raw text is useful for tiny generated snippets; real shader code should usually live in `.wgsl` files.

Supporting defines API:

```java
public final class ShaderDefines {
    public void define(String name);
    public void define(String name, int value);
    public void define(String name, String value);
}
```

Renderer-specific classes can expose typed convenience module interfaces, but the base mechanism remains shared.

## Template Markers

Template shader files and external snippet files use a small set of markers. These markers are processed by `WgShaderTemplate` before the final WGSL is sent to WebGPU.

Summary:

```text
@slot      additive injection point in a template shader file
@section   replaceable or appendable named region in a template shader file
@block     named WGSL contribution inside an external snippet file
#include   optional shared WGSL helper include
#ifdef     existing conditional preprocessing directive, not a template marker
```

### `@slot`

`@slot` marks a place where modules can insert extra WGSL without replacing the surrounding shader code.

```wgsl
// @slot color.final
```

Use slots for additive customization:

- add final color adjustment
- add fog or mask multiplication
- add debug color override
- add helper declarations
- add extra material fields

### `@section`

`@section` marks a named shader region that can be appended to or replaced.

```wgsl
// @section surface.read
fn readSurface(in: VertexOutput) -> Surface {
    ...
}
// @end
```

Use sections for larger customization:

- replace material sampling
- replace lighting
- replace fragment output
- replace vertex transform logic
- replace depth output logic

The shader owner decides which sections exist. Modules cannot replace arbitrary source text.

### `@block`

`@block` is used in external module snippet files. It names a WGSL contribution that can be inserted into a slot or used to append/replace a section.

```wgsl
// @block color.final
{
    color.rgb *= fogBrightness;
}
// @end
```

Use blocks so real module WGSL can live in `.wgsl` files instead of Java strings.

### `#include`

`#include` is project-specific support for shared WGSL helper files.

```wgsl
#include "common/noise.wgsl"
```

Use includes for reusable helper code, not to hide the main shader flow.

### `#ifdef`

`#ifdef`, `#ifndef`, `#else`, `#endif`, and `#define` already exist in `gdx-webgpu` through the Java `Preprocessor`. They are not WebGPU/WGSL features and they are not template markers.

The template system should preserve conditional blocks until preprocessing:

```text
template shader + module snippets
        -> assembled source with #ifdef
        -> ShaderPrefix + module defines
        -> Preprocessor.process()
        -> final WGSL sent to WebGPU
```

## `modelbatch.template.wgsl`

`modelbatch.template.wgsl` should be a complete shader file, not generated from Java chunks.

Recommended high-level structure:

```text
Header
Common structs
Frame uniforms
Material uniforms
Bindings
Vertex input/output
Surface struct
Vertex helpers
Vertex shader
Surface read/modify functions
Lighting functions
Shadow functions
PBR/IBL helpers
Fragment shader
Plugin helpers slot
```

The fragment path should be reorganized around a `Surface` model:

```wgsl
struct Surface {
    baseColor: vec4f,
    normal: vec3f,
    roughness: f32,
    metallic: f32,
    emissive: vec3f,
    alpha: f32,
};

fn readSurface(in: VertexOutput) -> Surface {
    var surface: Surface;
    // default material and texture reads
    // @slot surface.afterRead
    return surface;
}

fn modifySurface(surface: Surface, in: VertexOutput) -> Surface {
    var s = surface;
    // @slot surface.modify
    return s;
}

fn shadeSurface(in: VertexOutput, surface: Surface) -> vec4f {
    var color = surface.baseColor;
    // default lighting
    // @slot lighting.after
    return color;
}

@fragment
fn fs_main(in: VertexOutput) -> FragmentOutput {
    var surface = readSurface(in);
    surface = modifySurface(surface, in);

    var color = shadeSurface(in, surface);

    color = applyEmissive(color, surface);
    color = applyFog(color, in);
    color = applyGamma(color);

    // @slot color.final

    return makeFragmentOutput(color, in, surface);
}
```

Base templates should default to a single color output. MRT should not be included in the base shader because it requires matching runtime setup: multiple color attachments, matching framebuffer formats, and a compatible render pass. MRT support should be added through modules/techniques by replacing the fragment output section or by providing a module that changes `makeFragmentOutput`.

## Slots

Slots are insertion points where modules can append WGSL statements or declarations.

Slot marker format:

```wgsl
// @slot color.final
```

Suggested initial slot list:

- `globals.beforeStructs`
- `globals.afterBindings`
- `frame.uniformFields`
- `material.uniformFields`
- `material.bindings`
- `vertex.inputFields`
- `vertex.outputFields`
- `vertex.afterWorldPosition`
- `vertex.afterOutputs`
- `surface.afterRead`
- `surface.modify`
- `lighting.before`
- `lighting.after`
- `color.final`
- `fragment.output`
- `helpers`

Slots should remain stable once published. New slots can be added when a real customization case needs them.

## Sections

Sections are larger named regions that can be appended to or replaced.

Section marker format:

```wgsl
// @section material.uniforms
struct MaterialUniforms {
    diffuseColor: vec4f,
    shininess: f32,
    roughnessFactor: f32,
    metallicFactor: f32,
    // @slot material.uniformFields
};
// @end
```

Sections are for controlled larger changes, not normal customization. Normal modules should prefer slots.

The shader owner controls the customization surface. Each template decides which slots and sections exist, how broad they are, and which parts of the shader are intentionally replaceable.

Rules:

- `WgShaderTemplate` supports insertion into slots and replacement/appending of named sections.
- Modules can only target slots and sections exposed by the shader template.
- Unknown slot or section names are errors.
- A complex shader such as model batch can expose many sections.
- A simple shader can expose only a few sections.
- Modules cannot replace arbitrary source text through the template system.
- Full shader replacement remains the escape hatch when the exposed template surface is not enough.

Binding assignment policy:

- The architecture supports both automatic and manual binding assignment.
- Automatic allocation is the default path.
- Manual binding assignment is an explicit advanced override.
- Allocation order must be deterministic.
- Manual/manual and manual/reserved binding collisions are errors.
- Automatic allocation must not overwrite manually assigned bindings.
- Final resolved binding maps should be exposed in debug output.

Good section candidates:

- `frame.uniforms`
- `material.uniforms`
- `bindings.frame`
- `bindings.material`
- `vertex.input`
- `vertex.output`
- `fragment.output`
- `surface.struct`
- `surface.read`
- `surface.modify`
- `lighting`
- `fragment.main`

## Includes

`#include` support is a project-specific feature of the template processor. It is not a WebGPU/WGSL feature.

Example:

```wgsl
#include "modelbatch/pbr.wgsl"
#include "modelbatch/shadows.wgsl"
#include "modelbatch/ibl.wgsl"
```

Includes should be used for reusable helper functions, not to hide the main shader flow. The top-level `modelbatch.template.wgsl` should remain understandable without jumping through many files.

Include rules:

- Includes are resolved by `WgShaderTemplate` before module snippets are inserted.
- Includes are resolved relative to the file that contains the `#include`.
- Include ordering is deterministic and follows source order.
- Include cycles are errors.
- Included source must appear in debug output with comments identifying the included file.
- Includes are for reusable helper functions and shared declarations, not for hiding the main shader flow.

## External Snippet Files

Modules should be able to contribute WGSL from external files. This keeps shader code readable, avoids large Java strings, and works well with Java 8.

Simple snippet file:

```wgsl
// fog_of_war_color_final.wgsl
{
    let fw = material.fogWorld;
    let fogUv = vec2f(
        clamp((in.worldPos.x - fw.x) / max(fw.z - fw.x, 0.0001), 0.0, 1.0),
        clamp((in.worldPos.z - fw.y) / max(fw.w - fw.y, 0.0001), 0.0, 1.0)
    );
    let brightness = textureSample(fogTexture, fogSampler, vec2f(fogUv.x, 1.0 - fogUv.y)).r;
    color = vec4f(color.rgb * brightness, color.a);
}
```

Java usage:

```java
template.insert(
    "color.final",
    WgslSnippet.file(Gdx.files.classpath("shaders/modules/fog_of_war_color_final.wgsl"))
);
```

For modules that need multiple contributions, use named blocks in one file:

```wgsl
// fog_of_war.wgsl

// @block material.uniformFields
fogWorld: vec4f,
// @end

// @block material.bindings
@group(1) @binding(<fogTextureBinding>) var fogTexture: texture_2d<f32>;
@group(1) @binding(<fogSamplerBinding>) var fogSampler: sampler;
// @end

// @block color.final
{
    let fw = material.fogWorld;
    let fogUv = vec2f(
        clamp((in.worldPos.x - fw.x) / max(fw.z - fw.x, 0.0001), 0.0, 1.0),
        clamp((in.worldPos.z - fw.y) / max(fw.w - fw.y, 0.0001), 0.0, 1.0)
    );
    let brightness = textureSample(fogTexture, fogSampler, vec2f(fogUv.x, 1.0 - fogUv.y)).r;
    color = vec4f(color.rgb * brightness, color.a);
}
// @end
```

Java usage:

```java
FileHandle fogWgsl = Gdx.files.classpath("shaders/modules/fog_of_war.wgsl");
template.insert("material.uniformFields", WgslSnippet.block(fogWgsl, "material.uniformFields"));
template.insert("material.bindings", WgslSnippet.block(fogWgsl, "material.bindings"));
template.insert("color.final", WgslSnippet.block(fogWgsl, "color.final"));
```

Optional convenience:

```java
template.insertMatchingBlocks(Gdx.files.classpath("shaders/modules/fog_of_war.wgsl"));
```

`insertMatchingBlocks` would insert each `@block name` into a shader template slot with the same name.

Block rules:

- Blocks use `// @block name` and `// @end`.
- Block names should match template slot names unless explicitly remapped.
- Unknown destination slots are errors.
- Duplicate block names in one file are errors unless explicitly allowed.
- Generated shader output should include comments identifying the source file and block.

## `WgDefaultShader` Integration

The template system must stay outside `WgDefaultShader`.

Reasoning:

- `WgDefaultShader` already owns the correct Java-side contract for model batch rendering.
- A second Java class would duplicate pipeline setup, bind groups, uniforms, material handling, skinning, lights, shadows, and compatibility checks.
- The main risk is WGSL/template behavior, not a need for a different Java renderer.
- Existing customization hooks already allow a complete WGSL source and a custom `MaterialsCache` to be passed in.

Architecture boundary:

- `WgDefaultShader` owns the model-batch rendering contract: pipeline setup, bind groups, uniforms, material cache use, skinning, lights, shadows, render state, and `canRender()` compatibility.
- `WgDefaultShader` does not own template assembly.
- `WgDefaultShader` must not instantiate `WgShaderTemplate`, call template build methods, call dump methods, or know how snippets are assembled.
- `WgDefaultShaderProvider` does not own template assembly.
- `WgModelBatch.Config` does not contain template-specific fields such as `shaderTemplatePath`, `shaderModules`, or `ShaderTemplateConfig`.
- Template code produces a complete WGSL source outside the model-batch core path.
- The generated source is passed through the existing `WgModelBatch.Config.shaderSource` field.
- Any generated material layout is passed through the existing `WgModelBatch.Config.materials` field.

For model batch, template usage is external:

```java
ShaderBuildResult build = template.build(defines, layout, modules, context);

WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = build.shaderSourceForPipeline;
config.materials = new MaterialsCache(config.maxMaterials, materialLayout);

WgModelBatch batch = new WgModelBatch(config);
```

The default behavior continues loading `shaders/modelbatch.wgsl` inside `WgDefaultShader` when `config.shaderSource == null`. Template/module behavior is enabled by building the template outside the core shader classes and assigning the generated source to `config.shaderSource`.

## `WgDepthShader` Integration

The shared system must support `WgDepthShader`.

Depth shaders are usually simpler than color shaders, but they are not trivial. They still need controlled customization for:

- alpha-tested depth rendering
- shadow caster variations
- custom vertex deformation
- skinning
- morph targets
- material-driven discard logic
- custom depth bias or fragment depth behavior
- special depth prepass or picking-related variants

Depth rendering uses a separate template file, for example `depth.template.wgsl`, while preserving current `WgDepthShader` behavior by default.

Example depth slots:

- `globals.beforeStructs`
- `globals.afterBindings`
- `frame.uniformFields`
- `material.uniformFields`
- `material.bindings`
- `vertex.inputFields`
- `vertex.outputFields`
- `vertex.afterPosition`
- `vertex.afterWorldPosition`
- `vertex.afterOutputs`
- `fragment.beforeDiscard`
- `fragment.discard`
- `fragment.depth`
- `fragment.output`
- `helpers`

Depth shaders should not be forced to use the model-batch `Surface` model. A depth template can use a smaller data model, for example:

```wgsl
struct DepthSurface {
    alpha: f32,
    depth: f32,
};

fn readDepthSurface(in: VertexOutput) -> DepthSurface {
    var surface: DepthSurface;
    surface.alpha = 1.0;
    surface.depth = in.position.z;
    // @slot fragment.beforeDiscard
    return surface;
}
```

Depth template usage follows the same rule: build template output externally, then pass the generated WGSL to the existing `WgDepthShader` construction/configuration path.

```java
String depthSource = depthTemplate.build(defines, layout, modules, context).shaderSourceForPipeline;
WgDepthShader shader = new WgDepthShader(renderable, config, depthSource);
```

Modules that affect both color and depth rendering should be able to contribute to both shader families. For example, a vegetation wind module should modify vertex positions in `modelbatch.template.wgsl` and `depth.template.wgsl` so the color pass and shadow/depth pass match.

Module attachment is developer-driven. Some modules are color-only, some are depth-only, and some are intended to be attached to both color and depth shader owners.

## `WgSpriteBatch` And 2D Integration

The shared system must also apply to `WgSpriteBatch` and related 2D rendering classes.

2D shaders need the same core capabilities:

- readable WGSL source files
- slots for color, UV, texture sampling, alpha/discard, distance-field logic, and final color
- sections for bindings, vertex input/output, and fragment output
- Java-side modules that declare bind groups, uniforms, textures, samplers, and pipeline implications
- generated shader source for debugging

Sprite-batch rendering uses a separate template file, for example `spritebatch.template.wgsl`, while preserving current sprite-batch behavior by default.

Example 2D slots:

- `globals.beforeStructs`
- `globals.afterBindings`
- `frame.uniformFields`
- `sprite.bindings`
- `vertex.inputFields`
- `vertex.outputFields`
- `vertex.afterPosition`
- `vertex.afterOutputs`
- `fragment.beforeSample`
- `fragment.sampleColor`
- `fragment.afterSample`
- `fragment.alpha`
- `color.final`
- `fragment.output`
- `helpers`

Possible 2D modules:

- distance field font smoothing
- outline or glow text
- highlight/pulse effects
- palette swap
- grayscale/tint/post-color adjustment
- texture atlas debug visualization
- clipping masks
- custom alpha discard
- signed-distance UI shapes

`WgSpriteBatch` should not need a duplicated Java class. Like `WgDefaultShader`, it should receive generated source through its existing explicit-shader/source path:

```java
String spriteSource = spriteTemplate.build(defines, layout, modules, context).shaderSourceForPipeline;
WgShaderProgram spriteShader = new WgShaderProgram("sprite template", spriteSource);
WgSpriteBatch batch = new WgSpriteBatch(2000, spriteShader);
```

The model-batch `Surface` concept should not be forced onto 2D shaders. Sprite batch can have its own simpler data model, for example `SpriteSurface`:

```wgsl
struct SpriteSurface {
    color: vec4f,
    uv: vec2f,
};

fn readSpriteSurface(in: VertexOutput) -> SpriteSurface {
    var surface: SpriteSurface;
    surface.color = in.color * textureSample(diffuseTexture, diffuseSampler, in.uv);
    surface.uv = in.uv;
    // @slot fragment.afterSample
    return surface;
}
```

The shared rule still applies:

> The template engine and module API are shared. Each renderer owns its shader-specific data model, slots, sections, and Java binding contract.

## Configuration

Do not add template-specific configuration to shader-owning renderers.

Existing renderers keep their current explicit shader/source escape hatches. The template system uses those hatches after it has produced complete WGSL.

For model batch:

```java
WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = build.shaderSourceForPipeline;
config.materials = new MaterialsCache(config.maxMaterials, materialLayout);
```

If no generated source is assigned, the renderer behaves exactly as it does now.

Shader-owning classes that currently accept only full shader source should keep that escape hatch. They should not be changed to understand `WgShaderTemplate`, `WgShaderModule`, or `ShaderTemplateConfig`.

## Template Processor Rules

The template processor should be intentionally small:

- Load a WGSL file as text.
- Find slots by exact marker, for example `// @slot color.final`.
- Insert module contributions below the marker.
- Find sections by `// @section name` and `// @end`.
- Allow append or replacement of named sections.
- Fail fast if a requested slot or section does not exist.
- Preserve readable generated output for debugging.
- Optionally emit comments showing which module contributed each block.

Example generated output:

```wgsl
// @slot color.final
// @module FogOfWar
{
    ...
}
```

## System Behavior Contract

This section defines mandatory behavior and invariants. Implementations must follow this architecture contract without inventing incompatible APIs or hidden rules.

Package location:

```text
com.monstrous.gdx.webgpu.graphics.shader.template
```

Core classes:

```text
WgShaderTemplate
WgslSnippet
WgShaderModule
ShaderModuleContext
ShaderDefines
ShaderLayoutBuilder
ShaderBuildResult
ShaderTemplateConfig
ShaderBuildDumper
ShaderTemplateException
```

`ShaderTemplateException` is an unchecked exception. It is used for template assembly failures, unknown slots, unknown sections, missing blocks, duplicate marker names, invalid snippet blocks, layout collisions, and configuration conflicts.

### Source Selection Contract

Shader source selection order is fixed:

1. Explicit full shader source.
2. Built-in default shader source.

Template/module output is not a separate renderer source-selection branch. It is complete WGSL assigned to the same explicit source field as any hand-written custom shader.

For model batch, template source is resolved before the model-batch core API is used:

```java
ShaderBuildResult build = template.build(defines, layout, modules, context);

WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = build.shaderSourceForPipeline;
config.materials = new MaterialsCache(config.maxMaterials, materialLayout);
```

Configuration conflict rule:

- `shaderSource != null` means the shader owner receives a complete WGSL source.
- The source may be hand-written, loaded from a file, or generated by the template system.
- The shader owner must not care which of those produced it.
- Template modules cannot patch an already-assigned arbitrary shader source; template assembly happens before `shaderSource` is assigned.

Default template path rule:

- Default template paths are template-system policy, not renderer policy.
- A template helper may choose `shaders/modelbatch.template.wgsl` by default.
- `WgDefaultShader` only sees `config.shaderSource` or falls back to `shaders/modelbatch.wgsl`.

### Configuration Contract

`WgModelBatch.Config` keeps its existing shader/material inputs:

```java
public String shaderSource;
public MaterialsCache materials;
```

Rules:

- `WgModelBatch.Config` must not gain `shaderTemplatePath`.
- `WgModelBatch.Config` must not gain `shaderModules`.
- `WgModelBatch.Config` must not gain `ShaderTemplateConfig`.
- `WgDefaultShaderProvider` must not know about template classes.
- `WgDefaultShader` must not know about template classes.
- Module order is owned by the template build call.
- Module order affects define order, layout allocation order, snippet insertion order, and the final `buildHash`.
- Reordering modules is allowed to produce a different shader variant.
- Optional template diagnostics configuration belongs only to the template build API.

`WgDefaultShader` owns:

- consuming `config.shaderSource` when present
- creating the normal `PipelineSpecification`
- using `config.materials` when a caller supplies a custom material cache
- keeping normal `canRender()` compatibility behavior

### Build Pipeline Contract

The generated source must fit the current pipeline creation path:

```text
template source
  -> module defines and module WGSL contributions
  -> assembled template source
  -> shaderSourceForPipeline
  -> WebGPUPipeline prepends ShaderPrefix
  -> WgShaderProgram runs Preprocessor.process()
  -> WGSL sent to WebGPU
```

`ShaderPrefix` remains owned by `WebGPUPipeline`. The template system must not duplicate vertex/material/environment defines that are already generated by `ShaderPrefix`.

`ShaderDefines` contains only module-defined preprocessor defines. `ShaderBuildResult.definesSource` is prepended to the assembled template source to produce `shaderSourceForPipeline`.

`WgShaderProgram` remains responsible for final preprocessing and WebGPU shader module creation. The template system does not intercept or replace that final compilation path.

### Template Marker Contract

Marker syntax is exact and line-oriented:

```text
// @slot <name>
// @section <name>
// @block <name>
// @end
```

Rules:

- Markers must start after optional whitespace.
- Marker names use `[A-Za-z0-9_.-]+`.
- Slots are single-line insertion anchors.
- Sections and blocks start at their marker line and end at the next matching `// @end`.
- Nested sections are errors.
- Nested blocks are errors.
- Duplicate slot names in one template are errors.
- Duplicate section names in one template are errors.
- Duplicate block names in one snippet file are errors.
- `// @end` without an active section/block is an error.
- Unterminated sections/blocks are errors.
- Unknown marker kinds are left as normal WGSL comments unless they use one of the reserved names above incorrectly.

Generated output keeps template markers as comments. Inserted snippets are emitted below their slot marker. Section replacement keeps the `// @section name` and `// @end` marker comments and replaces only the body between them.

### Insertion Ordering Contract

All ordering is deterministic:

- Modules are processed in the order supplied to the template build call.
- Multiple inserts into the same slot are emitted in call order.
- `insertMatchingBlocks(file)` emits blocks in the order they appear in the file.
- If a module calls both `insert("x", ...)` and `insertMatchingBlocks()` for a block named `x`, both contributions are emitted in the call order made by that module.
- `appendToSection()` appends to the end of the section body, before `// @end`, in call order.
- `replaceSection()` may be called at most once per section. Multiple replacements of the same section are errors.
- A replaced section may still receive `appendToSection()` calls only if those calls happen after replacement. The append is added after the replacement body.

### `WgslSnippet` Contract

`WgslSnippet` is immutable after creation.

```java
public final class WgslSnippet {
    public static WgslSnippet text(String wgsl);
    public static WgslSnippet file(FileHandle file);
    public static WgslSnippet block(FileHandle file, String blockName);
}
```

Rules:

- `text()` stores the provided text as-is.
- `file()` reads the whole file when the snippet is resolved.
- `block()` reads the file and extracts one named `// @block`.
- Missing files are errors.
- Missing block names are errors.
- A snippet contributes exactly its body text. For `block()`, the `// @block` and `// @end` marker lines are not included.
- The template output emits source comments around file/block snippets so generated WGSL is traceable.
- Includes are resolved by the template processor, not by `WgslSnippet`.

### `WgShaderTemplate` Contract

Construction:

```java
public WgShaderTemplate(FileHandle templateFile);
public WgShaderTemplate(String templateName, String templateSource);
```

Operations:

```java
public void insert(String slotName, WgslSnippet snippet);
public void appendToSection(String sectionName, WgslSnippet snippet);
public void replaceSection(String sectionName, WgslSnippet snippet);
public void insertMatchingBlocks(FileHandle wgslFile);
public ShaderBuildResult build(ShaderDefines defines, ShaderLayoutBuilder layout, Array<WgShaderModule> modules);
```

Rules:

- Slot and section existence is validated when the operation is called.
- Block existence is validated when the snippet is resolved.
- `insertMatchingBlocks()` maps block names to slots with the same name.
- If a block name has no matching slot, it is an error.
- Sections are targeted explicitly with `appendToSection()` or `replaceSection()`.
- `build()` may be called multiple times and must produce the same output for the same inputs.

### Defines Contract

`ShaderDefines` preserves insertion order.

```java
public final class ShaderDefines {
    public void define(String name);
    public void define(String name, int value);
    public void define(String name, float value);
    public void define(String name, String value);
    public boolean isDefined(String name);
    public String buildSource();
}
```

Rules:

- Define names use `[A-Za-z_][A-Za-z0-9_]*`.
- Defining the same name with the same value is allowed and emitted once.
- Defining the same name with a different value is an error.
- String values are emitted verbatim after the define name.
- Module defines are emitted before the assembled template source.
- Module defines are emitted after `ShaderPrefix` at final compile time because `WebGPUPipeline` prepends `ShaderPrefix` before calling `WgShaderProgram`.

### Layout Contract

The layout builder describes renderer-owned layout changes required by shader modules. It does not replace renderer ownership of bind groups, uniform buffers, material caches, vertex layouts, or pipeline state.

Supported scopes:

```text
material
frame
sprite
instance
custom.<name>
```

Common registrations:

```java
public UniformField addUniform(String scope, String name, UniformType type,
                               MaterialUniformLayout.UniformResolver resolver);

public TextureBinding addTexture(String scope, String textureName, String samplerName,
                                 MaterialUniformLayout.TextureResolver resolver);
```

Rules:

- The shader owner declares which scopes are available for its template.
- Registering into an unavailable scope is an error.
- Material uniforms extend the model-batch material layout used by the real `MaterialsCache`.
- Material textures extend the same layout and use the next deterministic binding pair after the current layout unless an explicit binding is requested.
- Frame, sprite, instance, and custom scopes are applied only by shader owners that explicitly expose and own those scopes.
- Manual binding placement is allowed through explicit `add*At` APIs.
- Automatic binding placement is deterministic and must not overwrite explicit bindings.
- Duplicate uniform names are errors.
- Duplicate texture or sampler names are errors.
- Resolver-null registrations are allowed only for explicit advanced modules and must be documented by that module.
- A module cannot create new render targets, passes, or renderer-owned orchestration through layout registration.

### Model-Batch Template Contract

`modelbatch.template.wgsl` is added under:

```text
gdx-webgpu/res/shaders/modelbatch.template.wgsl
```

Template rules:

- The zero-module template must compile for the same common variants as `modelbatch.wgsl`.
- The template should preserve the behavior of `modelbatch.wgsl` unless a configured module changes it.
- The `Surface` refactor is allowed only if zero-module rendering remains visually and functionally equivalent.
- Existing `modelbatch.wgsl` remains available as the stable built-in fallback.
- The template must expose semantic slots used by common model-batch customization modules, including:
  - `material.uniformFields`
  - `material.bindings`
  - `surface.afterRead` or `color.final`
  - `helpers`

The template should expose a coherent `Surface` model where possible. If a shader owner cannot use `Surface` cleanly, it may expose renderer-specific data models such as `DepthSurface` or `SpriteSurface`.

### Preprocessor Contract

The existing `Preprocessor` remains the conditional-compilation processor for module-generated shaders.

Before using it with generated shader modules, fix this behavior:

- `Preprocessor.process()` must clear both `defineMap` and `defineSubstitutions` at the start of every call.

Reason:

- Module-defined valued macros make stale substitutions more likely and harder to diagnose.

`#ifdef`, `#ifndef`, `#else`, `#endif`, and simple `#define NAME VALUE` are the supported feature set. Expression evaluation is not required by this architecture.

### Debug Output Contract

`ShaderBuildResult` contains:

```java
public final String templateName;
public final String templateSource;
public final String assembledSource;
public final String definesSource;
public final String shaderSourceForPipeline;
public final String buildHash;
public final Array<String> moduleNames;
public final Array<String> moduleSignatures;
public final Array<String> contributions;
```

`buildHash` is the first 16 lowercase hex characters of SHA-256 over:

```text
templateName
definesSource
assembledSource
moduleSignatures
resolved layout summary
```

Debug dumping:

- Enabled by `ShaderTemplateConfig.dumpShaderBuilds`.
- Base path is `ShaderTemplateConfig.shaderDumpPath`.
- Dump path is `<shaderDumpPath>/<buildHash>/`.
- Build artifact writing is owned by the template system, for example through `ShaderBuildDumper`.
- Shader-owning classes pass template configuration into the template system; they must not call dump methods, decide whether to dump, or implement dump-file formatting.
- Dumping failures should log an error but must not prevent shader compilation.

Files:

```text
00-template.wgsl
01-assembled.wgsl
02-defines.wgsl
03-source-for-pipeline.wgsl
04-layout.txt
05-modules.txt
```

### Error Reporting Contract

Template/module errors should include:

- shader owner, for example `WgDefaultShader`
- template name/path
- operation name, for example `insert`, `replaceSection`, or `insertMatchingBlocks`
- slot, section, or block name
- module name when available
- source file path when available
- `buildHash` when available

WGSL compilation errors still arrive through the WebGPU error callback. Template errors should report enough source context to diagnose assembly problems before compilation.

## Shader Requirements System

The Shader Requirements System is the second layer of the overall Shader Module System.

The Shader Template System answers:

- What WGSL source should be generated?
- Which slots receive code?
- Which sections are replaced?
- Which snippets are loaded?

The Shader Requirements System answers:

- Does the generated shader match the current renderer setup?
- Does the shader need specific vertex attributes?
- Does it need extra uniforms or bind groups?
- Does it need a depth shader variant too?
- Does it need MRT color targets?
- Does it need a specific cull, blend, topology, or depth-test state?
- Does it need an extra render pass or explicit render orchestration outside the shader owner?

Requirements are part of the module contract. A renderer may automatically apply requirements it owns, but requirements that imply new targets, extra passes, or render ordering remain explicit renderer-side or user-side orchestration.

### Requirement Validation

Modules declare requirements, but renderers mostly validate them.

Example:

```java
requirements.requireVertexAttribute(ShaderProgram.NORMAL_ATTRIBUTE);
requirements.requireColorTargetCount(2);
```

The renderer can report clear errors:

- missing vertex attribute
- shader writes MRT output but active render pass has one color target
- color target format does not match expected format
- module requires a matching depth shader but none was configured

Requirement validation catches invalid combinations without automatically redesigning rendering.

### Renderer-Owned Requirements

Each renderer supports the requirements it naturally owns.

`WgDefaultShader` can support:

- frame uniforms
- material uniforms
- material textures and samplers
- bind group layout extensions
- vertex attribute requirements
- simple pipeline state preferences such as cull mode, blending, and depth test

`WgDepthShader` can support:

- alpha-test discard requirements
- matching vertex deformation modules
- explicit depth-only vs depth-fragment-output behavior

`WgSpriteBatch` can support:

- sprite/frame uniforms
- sprite texture/sampler extensions
- simple blend/depth-state preferences
- sprite vertex layout requirements

Renderer-owned requirements are applied only inside the ownership boundaries of the existing renderer. A shader owner may update its own uniforms, bind groups, layouts, pipeline state, and compatibility checks, but it does not implicitly create new render targets or extra passes.

### Explicit Render Orchestration

Some effects are larger than one shader. They need render-pass orchestration.

Examples:

- MRT/G-buffer rendering
- object picking into a separate target
- silhouette outline with an extra pass
- deferred rendering
- post-process chains
- shadow/depth prepass coordination

The Shader Module System can declare and validate these needs, but it does not automate render orchestration.

Architecture decision:

- Do not introduce a separate `WgRenderTechnique` abstraction as part of this shader module work.
- Larger features use shader modules for shader source/layout/requirements.
- Pass creation, framebuffer creation, target selection, and render ordering remain explicit renderer-side or user-side orchestration.
- A renderer can expose explicit convenience APIs for common orchestration patterns, but that is separate from the Shader Template System.

## Validation

The system should validate:

- Unknown slots are errors.
- Unknown sections are errors.
- Duplicate slot names in the template are errors unless explicitly allowed.
- Duplicate material uniform names are errors.
- Duplicate frame uniform names are errors.
- Duplicate binding numbers are errors unless intentionally replacing a section.
- Generated WGSL can be dumped or logged for debugging.

Longer-term validation:

- Track required vertex attributes.
- Track required material attributes.
- Track shader defines per module.
- Include generated shader source in compile errors when possible.
- Keep module compatibility checks in the external template/build layer. Core renderer `canRender()` behavior remains unchanged unless this document explicitly changes that ownership.
- Track module attachment and structural compatibility so a module cannot be accidentally applied where required slots/sections/layout/requirements are unavailable.

## Use Case: Fog Of War 3D Module

`FogOfWar3DTest` is the first real model-batch module use case in this repository.

The user wants a top-down fog-of-war texture to darken unexplored world areas. This is a color-pass feature, so it applies to `modelbatch.template.wgsl` but does not need to affect `depth.template.wgsl`.

Java-side responsibilities:

- Add `fogWorld: vec4f` to material uniforms.
- Add `fogTexture` and `fogSampler` to the material bind group.
- Resolve the shared fog texture per material.

Example module shape:

```java
public class FogOfWarShaderModule implements WgShaderModule {
    private final WgTexture fogTexture;
    private final Vector4 worldBounds;

    public FogOfWarShaderModule(WgTexture fogTexture, Vector4 worldBounds) {
        this.fogTexture = fogTexture;
        this.worldBounds = worldBounds;
    }

    @Override
    public void configureLayout(ShaderLayoutBuilder layout, ShaderModuleContext context) {
        layout.addUniform("material", "fogWorld", UniformType.VEC4,
            (material, binder, name) -> binder.setUniform(name, worldBounds));

        layout.addTexture("material", "fogTexture", "fogSampler",
            material -> fogTexture);
    }

    @Override
    public void contribute(WgShaderTemplate template, ShaderModuleContext context) {
        template.insert("material.uniformFields", WgslSnippet.text("    fogWorld: vec4f,\n"));
        template.insert("material.bindings", WgslSnippet.text(
            "@group(1) @binding(" + fogBinding.textureBindingId + ") var fogTexture: texture_2d<f32>;\n"
                + "@group(1) @binding(" + fogBinding.samplerBindingId + ") var fogSampler: sampler;\n"));
        template.insert("color.final", WgslSnippet.text(
            "{\n"
                + "    let fw = material.fogWorld;\n"
                + "    let fogUv = vec2f(\n"
                + "        clamp((in.worldPos.x - fw.x) / max(fw.z - fw.x, 0.0001), 0.0, 1.0),\n"
                + "        clamp((in.worldPos.z - fw.y) / max(fw.w - fw.y, 0.0001), 0.0, 1.0)\n"
                + "    );\n"
                + "    let brightness = textureSample(fogTexture, fogSampler,\n"
                + "                                   vec2f(fogUv.x, 1.0 - fogUv.y)).r;\n"
                + "    color = vec4f(color.rgb * brightness, color.a);\n"
                + "}\n"));
    }
}
```

Usage:

```java
FogOfWarShaderModule module = new FogOfWarShaderModule(
    fogTexture,
    new Vector4(0f, 0f, WORLD_SIZE, WORLD_SIZE)
);

MaterialUniformLayout materialLayout = createDefaultMaterialLayout();
ShaderLayoutBuilder layout = new ShaderLayoutBuilder();
layout.setMaterialLayout(materialLayout);

ShaderModuleContext context = new ShaderModuleContext(null, null, null);
module.configureLayout(layout, context);
layout.apply();

WgShaderTemplate template = new WgShaderTemplate(
    Gdx.files.classpath("shaders/modelbatch.template.wgsl")
);
ShaderDefines defines = new ShaderDefines();
module.configureDefines(defines, context);
module.contribute(template, context);

Array<WgShaderModule> modules = new Array<>();
modules.add(module);
ShaderBuildResult build = template.build(defines, layout, modules, context);

WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = build.shaderSourceForPipeline;
config.materials = new MaterialsCache(config.maxMaterials, materialLayout);

WgModelBatch modelBatch = new WgModelBatch(config);
```

Result:

- `FogOfWar3DTest` no longer patches exact WGSL source with raw string replacement.
- The fog texture and sampler are declared through Java layout metadata.
- The matching WGSL fields, bindings, and color modification are inserted through named slots.
- Binding numbers are sourced from `ShaderLayoutBuilder` (`TextureBinding`) rather than hardcoded constants.
- The module is color-pass only, so it does not need to modify `depth.template.wgsl`.
- The base `modelbatch.template.wgsl` remains readable and reusable.

## Use Case: Grayscale Or Tint Sprite Module

The user wants to render normal sprites, but optionally apply grayscale, tint strength, or disabled-state rendering without replacing the full sprite batch shader.

Java-side responsibilities:

- Add small frame or batch uniforms, for example `effectParams: vec4f`.
- Optionally expose per-batch configuration for grayscale amount, tint strength, or highlight amount.

WGSL contributions:

```wgsl
// frame.uniformFields or sprite.uniformFields
effectParams: vec4f, // x = grayscale amount, y = tint amount
effectTint: vec4f,
```

```wgsl
// color.final
{
    let luminance = dot(color.rgb, vec3f(0.299, 0.587, 0.114));
    let gray = vec3f(luminance);
    color = vec4f(mix(color.rgb, gray, effectParams.x), color.a);
    color = vec4f(mix(color.rgb, color.rgb * effectTint.rgb, effectParams.y), color.a);
}
```

Result:

- Existing sprite rendering stays unchanged by default.
- The user can enable a common 2D effect without providing a full shader.
- The same pattern supports highlight, flash, palette swap, and UI disabled-state effects.

## Use Case: SDF Font Outline Module

The user wants bitmap/SDF font rendering with outline or glow, but does not want to maintain a separate full sprite shader.

Java-side responsibilities:

- Add outline color and smoothing parameters.
- Reuse the sprite texture binding.
- Declare sprite-batch/font compatibility.

WGSL contributions:

```wgsl
// color.final
{
    let distance = color.a;
    let fillAlpha = smoothstep(sdfParams.x, sdfParams.y, distance);
    let outlineAlpha = smoothstep(sdfParams.z, sdfParams.w, distance);
    let outlined = mix(outlineColor, color, fillAlpha);
    color = vec4f(outlined.rgb, max(fillAlpha, outlineAlpha) * in.color.a);
}
```

Result:

- Font-specific rendering can be layered onto the sprite template.
- The base `spritebatch.template.wgsl` remains readable.
- Existing full custom shader support remains available for advanced font rendering.

## Additional 3D Use Cases

The WGSL below is shown inline for readability. Real modules should usually place these contributions in external `.wgsl` snippet files with `@block` markers and load them with `WgslSnippet.block(...)` or `insertMatchingBlocks(...)`.

### Alpha-Tested Materials

The user wants leaves, fences, decals, or cutout materials to discard fragments based on texture alpha.

This module should affect both model-batch color rendering and depth/shadow rendering. If only the color pass discards pixels, the depth pass will cast solid rectangular shadows.

Java-side responsibilities:

- Add an alpha cutoff material uniform, for example `alphaCutoff: f32`.
- Ensure the diffuse/base color texture is available in both color and depth shader families when needed.
- Declare support for `MODEL_BATCH` and `DEPTH`.

WGSL color contribution:

```wgsl
// surface.afterRead
if (surface.baseColor.a < material.alphaCutoff) {
    discard;
}
```

WGSL depth contribution:

```wgsl
// fragment.discard
let alpha = textureSample(diffuseTexture, diffuseSampler, in.uv).a;
if (alpha < material.alphaCutoff) {
    discard;
}
```

Result:

- Color pass, depth pass, and shadows agree.
- Users do not need to copy both color and depth shaders.

### Dissolve Effect

The user wants a mesh to dissolve using a noise texture, with optional glowing edges.

Java-side responsibilities:

- Add dissolve amount and edge width uniforms.
- Add dissolve noise texture and sampler.
- Optionally support depth rendering if dissolved pixels should stop casting shadows.

WGSL contribution:

```wgsl
// color.final
{
    let noise = textureSample(dissolveTexture, dissolveSampler, in.uv).r;
    if (noise < material.dissolveAmount) {
        discard;
    }
    let edge = smoothstep(material.dissolveAmount, material.dissolveAmount + material.dissolveEdgeWidth, noise);
    color.rgb = mix(material.dissolveEdgeColor.rgb, color.rgb, edge);
}
```

Result:

- Dissolve can be added to the standard PBR shader.
- The module can also contribute equivalent discard logic to `depth.template.wgsl` when shadows should dissolve.

### Object ID Or Picking Output

The user wants object picking or ID rendering without maintaining a full separate shader for every material variant.

Java-side responsibilities:

- Add object ID or encoded color data.
- Replace or extend fragment output.

WGSL contribution:

```wgsl
// fragment.output
return vec4f(objectIdColor.rgb, 1.0);
```

Result:

- Picking can reuse the same vertex/skinning/morph path as normal rendering.
- Only the fragment output changes.

### MRT / G-Buffer Output

The base shader should not contain MRT by default. MRT requires extra runtime setup, so it should be added through the Shader Template System and validated through the Shader Requirements System.

Java-side responsibilities:

- Create an MRT framebuffer with the required color attachments.
- Ensure the active render pass exposes the same target count and formats.
- Declare requirements such as color target count and expected formats.

WGSL contribution:

```wgsl
// fragment.output
struct FragmentOutput {
    @location(0) color: vec4f,
    @location(1) normal: vec4f,
    @location(2) material: vec4f,
};

fn makeFragmentOutput(color: vec4f, in: VertexOutput, surface: Surface) -> FragmentOutput {
    var out: FragmentOutput;
    out.color = color;
    out.normal = vec4f(surface.normal * 0.5 + 0.5, 1.0);
    out.material = vec4f(surface.roughness, surface.metallic, 0.0, 1.0);
    return out;
}
```

Requirements contribution:

```java
requirements.requireColorTargetCount(3);
requirements.requireColorTargetFormat(0, WGPUTextureFormat.BGRA8Unorm);
requirements.requireColorTargetFormat(1, WGPUTextureFormat.RGBA16Float);
requirements.requireColorTargetFormat(2, WGPUTextureFormat.RGBA8Unorm);
```

Result:

- The default shader remains simple and single-target.
- MRT can be added when renderer-side or user-side orchestration creates the matching FBO/render pass.
- Invalid combinations can be caught before pipeline creation.

### Triplanar Texture Mapping

The user wants terrain or large meshes to use world-space triplanar mapping instead of regular UVs.

Java-side responsibilities:

- Add triplanar scale/blend uniforms.
- Add or reuse material textures.
- Declare that normals/world position are required.

WGSL contribution:

```wgsl
// surface.afterRead
{
    let n = abs(normalize(surface.normal));
    let weights = n / max(n.x + n.y + n.z, 0.0001);
    let sx = textureSample(diffuseTexture, diffuseSampler, in.worldPos.yz * material.triplanarScale);
    let sy = textureSample(diffuseTexture, diffuseSampler, in.worldPos.xz * material.triplanarScale);
    let sz = textureSample(diffuseTexture, diffuseSampler, in.worldPos.xy * material.triplanarScale);
    surface.baseColor = sx * weights.x + sy * weights.y + sz * weights.z;
}
```

Result:

- Users can customize material sampling while keeping lighting, shadows, fog, and PBR intact.

### Debug Normal Or Roughness View

The user wants to inspect normals, roughness, metallic, UVs, or other material channels while debugging.

Java-side responsibilities:

- Add a debug mode define or uniform.
- Replace final color only.

WGSL contribution:

```wgsl
// color.final
if (debugMode == 1.0) {
    color = vec4f(surface.normal * 0.5 + 0.5, 1.0);
}
if (debugMode == 2.0) {
    color = vec4f(vec3f(surface.roughness), 1.0);
}
```

Result:

- Debug views become small modules instead of separate full shaders.

## Additional 2D Use Cases

The WGSL below is shown inline for readability. Real modules should usually place these contributions in external `.wgsl` snippet files with `@block` markers and load them with `WgslSnippet.block(...)` or `insertMatchingBlocks(...)`.

### Palette Swap

The user wants sprites to remap colors through a palette texture, useful for characters, teams, skins, and retro-style games.

Java-side responsibilities:

- Add palette texture and sampler.
- Add palette row/index uniform.
- Declare support for sprite batch.

WGSL contribution:

```wgsl
// color.final
{
    let key = color.r;
    let paletteUv = vec2f(key, paletteIndex);
    let remapped = textureSample(paletteTexture, paletteSampler, paletteUv);
    color = vec4f(remapped.rgb, color.a * remapped.a);
}
```

Result:

- Sprites can change colors without duplicating textures or replacing the whole shader.

### Flash Or Damage Highlight

The user wants sprites to briefly flash white/red when hit.

Java-side responsibilities:

- Add flash color and flash amount uniforms.
- Declare support for sprite batch.

WGSL contribution:

```wgsl
// color.final
color = vec4f(mix(color.rgb, flashColor.rgb, flashAmount), color.a);
```

Result:

- Common gameplay feedback becomes a tiny shader module.

### Sprite Clipping Mask

The user wants sprites or UI elements clipped by a mask texture.

Java-side responsibilities:

- Add mask texture and sampler.
- Add mask transform uniforms.
- Declare support for sprite batch.

WGSL contribution:

```wgsl
// color.final
{
    let maskUv = (maskTransform * vec3f(in.worldPos.xy, 1.0)).xy;
    let mask = textureSample(maskTexture, maskSampler, maskUv).r;
    color = vec4f(color.rgb, color.a * mask);
}
```

Result:

- UI clipping and soft masks can be added without maintaining a full sprite shader fork.

### Texture Atlas Debug View

The user wants to visualize UVs, atlas pages, or transparent padding around sprites.

Java-side responsibilities:

- Add debug mode uniform.
- Optionally add atlas page color.

WGSL contribution:

```wgsl
// color.final
if (spriteDebugMode == 1.0) {
    color = vec4f(in.uv, 0.0, 1.0);
}
```

Result:

- Debug-only sprite shader behavior can be toggled without replacing production shader code.

### 2D Lighting Or Normal Map

The user wants sprite lighting using a normal map texture.

Java-side responsibilities:

- Add normal map texture and sampler.
- Add light direction/color uniforms.
- Declare support for sprite batch.

WGSL contribution:

```wgsl
// color.final
{
    let encodedNormal = textureSample(spriteNormalTexture, spriteNormalSampler, in.uv).rgb;
    let normal = normalize(encodedNormal * 2.0 - 1.0);
    let lightAmount = max(dot(normal, normalize(spriteLightDirection.xyz)), 0.0);
    color = vec4f(color.rgb * (ambient2d.rgb + spriteLightColor.rgb * lightAmount), color.a);
}
```

Result:

- A 2D lighting technique can reuse sprite batch infrastructure and only add the specific lighting code.

## Depth Shader Module Behavior

Depth shader modules follow the same template/module contract as color shader modules, but they target `depth.template.wgsl` slots and the runtime state owned by `WgDepthShader`.

Good depth module examples:

- alpha-tested depth discard using a material texture
- a simple vertex deformation module shared by color and depth shaders
- a depth-only fragment output module for custom prepass behavior

Modules that affect geometry or discard behavior should usually contribute to both color and depth shader families. This keeps visible geometry, shadows, and depth prepasses consistent without forcing users to duplicate whole shaders.

## Use Case: Shared Vegetation Wind Module

A vegetation wind effect is a good example of why the module system must work across shader families.

The user wants grass, trees, or foliage to move in the color pass and also move the same way in shadow/depth passes. If the color shader moves vertices but the depth shader does not, shadows will no longer match the visible geometry.

With the new system, the user creates one module:

```java
public class WindShaderModule implements WgShaderModule {
    @Override
    public void configureDefines(ShaderDefines defines, ShaderModuleContext context) {
        defines.define("USE_WIND");
    }

    @Override
    public void configureLayout(ShaderLayoutBuilder layout, ShaderModuleContext context) {
        layout.addUniform("frame", "windTime", UniformType.FLOAT);
        layout.addUniform("frame", "windDirectionStrength", UniformType.VEC4);
    }

    @Override
    public void contribute(WgShaderTemplate template, ShaderModuleContext context) {
        template.insert("helpers", """
            fn applyWind(worldPos: vec3f, strength: f32) -> vec3f {
                let wave = sin(worldPos.x * 0.25 + uFrame.windTime) * strength;
                return worldPos + vec3f(0.0, wave, 0.0);
            }
            """);

        template.insert("vertex.afterWorldPosition", """
            worldPosition = vec4f(
                applyWind(worldPosition.xyz, uFrame.windDirectionStrength.w),
                worldPosition.w
            );
            """);
    }
}
```

The same module can be used by model-batch color rendering:

```java
ShaderBuildResult colorBuild = modelTemplate.build(defines, colorLayout, modules, colorContext);

WgModelBatch.Config config = new WgModelBatch.Config();
config.shaderSource = colorBuild.shaderSourceForPipeline;
config.materials = new MaterialsCache(config.maxMaterials, colorMaterialLayout);

WgModelBatch batch = new WgModelBatch(config);
```

And by depth/shadow rendering:

```java
ShaderBuildResult depthBuild = depthTemplate.build(defines, depthLayout, modules, depthContext);
WgDepthShader depthShader = new WgDepthShader(renderable, depthConfig, depthBuild.shaderSourceForPipeline);
```

Result:

- `modelbatch.template.wgsl` receives the wind vertex deformation.
- `depth.template.wgsl` receives the same wind vertex deformation.
- Visible geometry and shadows stay consistent.
- The user does not copy or replace either full shader.
- `WgDefaultShader` and `WgDepthShader` still own their normal Java-side pipeline, binding, and render-state behavior.
- Full shader replacement remains available for users who want a completely different shader.

## Coverage Expectations

External model-batch template output should be tested with `WgDefaultShader` by passing generated WGSL through `WgModelBatch.Config.shaderSource` and any generated material layout through `WgModelBatch.Config.materials`.

Coverage should include:

- unlit rendering
- lit rendering
- PBR
- IBL
- fog
- shadow map
- cascaded shadow map
- skinning
- morph targets
- material texture variants
- FBO rendering
- MRT, if added as a module
- at least one custom shader module, starting with fog of war

External sprite-batch template output should be tested by passing generated WGSL through the existing sprite shader/source path.

Coverage should include:

- plain textured sprites
- packed and unpacked color
- texture atlas rendering
- bitmap font rendering
- distance field font rendering, if integrated
- blending modes already supported by sprite batch
- custom 2D module, starting with highlight, tint, grayscale, or SDF outline
- FBO rendering
- pipeline cache behavior when changing render target formats

External depth template output should be tested by passing generated WGSL through the existing `WgDepthShader` source path.

Coverage should include:

- standard depth-only rendering
- shadow rendering
- skinning
- morph targets
- alpha-tested discard, if added as a module
- vertex deformation module shared with model-batch color rendering
- fragment depth output, if supported by a module
- compatibility with pipeline checks that detect depth-only vs fragment-output shaders

Other shader-owning classes should get smaller targeted tests as they adopt the shared system.

Requirements validation should be tested with:

- missing vertex attribute
- module attached to a shader owner where required slots/sections do not exist
- module attached to a shader owner where required layout scopes are unavailable
- MRT target count mismatch
- expected color format mismatch
- module requiring depth texture when the active pass has none
- duplicate binding or uniform declarations

Requirement validation examples:

- required slots/sections exist
- required layout scopes are available
- required vertex attributes are present
- required bindings/uniforms can be allocated without conflicts
- required color target count/format matches active render pass
- required depth texture exists

Not automatic:

- auto-creating MRT framebuffers
- auto-adding extra passes (outline, picking, deferred)
- auto-building render graphs
- auto-switching color/depth pass orchestration

Automatic template-build behavior:

- Keep automatic application minimal and outside core renderer classes.
- The template build applies module defines during shader assembly.
- The template build applies layout registrations to the layout object supplied by the caller.
- The template build allocates unpinned bindings deterministically.
- The caller is responsible for passing generated WGSL and generated layout/material objects into existing renderer APIs.
- Renderers do not automatically create new render targets, passes, module-aware compatibility checks, or multi-pass orchestration.

Automatic:

- define injection
- layout registration within scopes exposed to the template build
- deterministic auto-binding allocation

Not automatic:

- MRT framebuffer creation
- additional pass orchestration (outline/picking/deferred)
- render-graph construction
- implicit cross-batch depth/color orchestration
- module-aware renderer `canRender()` changes
- pipeline refresh outside the existing renderer behavior

Architecture decision for larger features:

- Do not introduce a separate `WgRenderTechnique` class.
- Keep architecture centered on existing shader owners plus `WgShaderModule`.
- Larger features (MRT, outline, picking, deferred-style flows) are implemented through:
  - shader modules for source/layout/requirements
  - explicit renderer or user-side orchestration for passes and targets
- Avoid adding a second high-level runtime abstraction layer that increases API complexity.

Adoption scope for existing shader classes:

- Keep backward compatibility: old shader customization paths remain supported.
- First-class integration targets are `WgDefaultShader`, `WgDepthShader`, and `WgSpriteBatch`.
- Additional shader classes are migrated incrementally on demand, not through a forced mass migration.
- A class should be migrated when there is a concrete customization need, recurring maintenance pain, or a clear low-risk integration path.
- If there is no immediate need, keep the existing class behavior unchanged.

Generated source debugging:

- Every shader build should produce a `ShaderBuildResult` object and keep it accessible for diagnostics.
- The `ShaderBuildResult` field contract is defined in **Debug Output Contract**.
- At minimum, `ShaderBuildResult` includes:
  - `templateSource`
  - `assembledSource`
  - `definesSource`
  - `shaderSourceForPipeline`
  - module names and signatures
  - contribution origin strings
  - `buildHash` (stable variant identifier)
- Add optional debug-mode file dumping for these artifacts.
- On compile errors, log shader name + `buildHash` + processed-source context, and include dump location when enabled.

Recommended debug dump structure:

```text
build/shaders/<buildHash>/
  00-template.wgsl
  01-assembled.wgsl
  02-defines.wgsl
  03-source-for-pipeline.wgsl
  04-layout.txt
  05-modules.txt
```

This keeps normal runtime lightweight while making complex module/template issues diagnosable.

Validation order before WGSL compilation:

- Validate as early as possible before GPU compilation.
- Template assembly validation
  - slot/section existence
  - block existence
  - duplicate or conflicting section/slot contributions
- Layout and binding validation
  - duplicate binding usage
  - reserved binding violations
  - unresolved scope references
  - deterministic allocation completion
- Requirement and render-pass validation
  - required vertex attributes
  - required color target count/format
  - required depth texture presence
  - compatibility with active pass constraints known at build/use time
- WGSL compilation remains the final validator
  - compile-time WGSL errors are still possible
  - failures must report `buildHash` and debug artifact location when available

This finalizes the architecture-level validation policy.


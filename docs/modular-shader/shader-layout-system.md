# Shader Layout System

> Version: 0.1
> Last updated: 2026-05-17

## Purpose

The Shader Layout System describes the resources that generated WGSL needs: uniforms, textures, samplers, bind groups, and binding numbers.

This system is separate from the [Shader Template System](shader-template-system.md). The template system builds WGSL source. The layout system is the planned place for making module-owned WGSL match Java-side resource layout data.

## Current Status

The full Shader Layout System is planned, but it is not being implemented in version `0.1`.

Current limited support:

1. `ShaderLayoutBuilder` exists as a small helper for the model-batch template proof case.
2. Only the `material` scope is applied to real renderer-owned objects.
3. `addUniform("material", ...)` can register material uniforms.
4. `addTexture("material", ...)` can register material textures and samplers.
5. `apply()` forwards those declarations to `MaterialUniformLayout`.
6. `summary()` contributes simple layout diagnostics to `ShaderBuildResult` when templates are built through `buildForResult(...)`.

Not implemented:

1. `BindGroupLayoutBuilder`
2. explicit binding placement
3. automatic binding reservation across arbitrary bind groups
4. runtime application of `frame`, `sprite`, `instance`, and `custom.<name>` scopes
5. renderer-owned layout object generation for non-material scopes
6. automatic pipeline layout changes for extra bind groups
7. validation against actual mesh vertex attributes
8. validation against active render-pass color/depth formats
9. matching depth-shader variant validation
10. pass orchestration for MRT, outline, picking, deferred, or post-processing flows

## Boundary With The Template System

The Shader Template System answers:

1. What WGSL source should be generated?
2. Which slots receive code?
3. Which sections are replaced or appended?
4. Which snippets are loaded?
5. Which defines are prepended?

The Shader Layout System will answer:

1. Which uniforms does the generated WGSL need?
2. Which textures and samplers does the generated WGSL need?
3. Which bind group and binding should each resource use?
4. Which renderer-owned layout object can receive the declaration?
5. Which bindings are already reserved or used?
6. Which layout summary should be included in diagnostics?

## Current Minimal API

The existing helper is intentionally narrow.

Layout-related classes live under:

```text
com.monstrous.gdx.webgpu.graphics.shader.modular.layout
```

Current layout package classes:

1. `ShaderLayoutBuilder`
2. `UniformField`
3. `TextureBinding`
4. `UniformType`

```java
public final class ShaderLayoutBuilder {
    public static final String SCOPE_MATERIAL = "material";

    public ShaderLayoutBuilder allowScope(String scope);
    public ShaderLayoutBuilder setMaterialLayout(MaterialUniformLayout materialLayout);

    public UniformField addUniform(String scope, String name, UniformType type);
    public UniformField addUniform(String scope, String name, UniformType type,
                                   MaterialUniformLayout.UniformResolver resolver);

    public TextureBinding addTexture(String scope, String textureName, String samplerName);
    public TextureBinding addTexture(String scope, String textureName, String samplerName,
                                     MaterialUniformLayout.TextureResolver resolver);

    public void apply();
    public String summary();
}
```

`addUniformAt(...)` and `addTextureAt(...)` currently exist only as explicit unsupported operations. They throw `ShaderTemplateException`.

## Material Layout Flow

Material layout is the only supported real integration today because `WgDefaultShader` already has a configurable material layout path.

```java
MaterialUniformLayout materialLayout = MaterialUniformLayout.standard();

ShaderLayoutBuilder layout = new ShaderLayoutBuilder()
    .setMaterialLayout(materialLayout);

UniformField fogWorld = layout.addUniform(
    "material",
    "fogWorld",
    UniformType.VEC4,
    fogWorldResolver
);

TextureBinding fogTexture = layout.addTexture(
    "material",
    "fogTexture",
    "fogSampler",
    fogTextureResolver
);

layout.apply();
```

After `apply()`, the caller passes the generated `MaterialUniformLayout` through existing model-batch APIs:

```java
WgModelBatch.Config config = new WgModelBatch.Config();
config.materials = new MaterialsCache(materialLayout);
```

## Planned Layout System

When the full layout system is implemented, it should support:

1. explicit bind-group tracking
2. binding reservation
3. deterministic binding allocation
4. explicit binding placement for advanced modules
5. non-material scopes
6. renderer-owned layout object generation
7. clearer validation and error reporting

A future implementation may add a bind-group helper, but that helper should be added only when the layout system work starts.

Target shape:

```java
public final class ShaderLayoutBuilder {
    public BindGroupLayoutBuilder group(int group);

    public ShaderLayoutBuilder allowScope(String scope);
    public ShaderLayoutBuilder allowScope(String scope, int group, int uniformBinding);
    public ShaderLayoutBuilder setMaterialLayout(MaterialUniformLayout materialLayout);

    public UniformField addUniform(String scope, String name, UniformType type);
    public TextureBinding addTexture(String scope, String textureName, String samplerName);

    public UniformField addUniformAt(String scope, int group, int binding,
                                     String name, UniformType type);
    public TextureBinding addTextureAt(String scope, int group, int binding,
                                       String textureName, String samplerName);
}
```

This target API is not implemented yet.

## Validation Work

Some checks are related to shader layout but should remain future validation work:

1. required vertex attributes
2. required material attributes
3. required slots and sections
4. required layout scopes
5. color target count and format
6. depth texture presence
7. matching depth shader variants
8. pass orchestration constraints

These checks should start as validation and reporting. They should not silently create render targets, add passes, build render graphs, or generate companion shaders.

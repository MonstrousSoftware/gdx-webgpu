// Smooth fog-of-war compositing shader for SpriteBatch.
// Input texture stores fog amount in [0..1] (0 visible, 1 fully fogged).

struct Uniforms {
    projectionViewTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var texture: texture_2d<f32>;
@group(0) @binding(2) var textureSampler: sampler;

struct VertexInput {
    @location(0) position: vec2f,
    @location(1) uv: vec2f,
#ifdef COLOR
    @location(5) color: vec4f,
#endif
};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) uv: vec2f,
    @location(1) color: vec4f,
};

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);
    out.uv = in.uv;
#ifdef COLOR
    out.color = in.color;
#else
    out.color = vec4f(1.0, 1.0, 1.0, 1.0);
#endif
    return out;
}

fn fogSample(uv: vec2f) -> f32 {
    return textureSample(texture, textureSampler, uv).r;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4f {
    // Must match FogOfWar2DTest fog-map dimensions.
    let texel = vec2f(1.0 / 256.0, 1.0 / 160.0);

    // 3x3 Gaussian-like blur to remove block artifacts from low-res fog map.
    var sum = 0.0;
    sum += fogSample(in.uv + texel * vec2f(-1.0, -1.0));
    sum += fogSample(in.uv + texel * vec2f( 0.0, -1.0)) * 2.0;
    sum += fogSample(in.uv + texel * vec2f( 1.0, -1.0));

    sum += fogSample(in.uv + texel * vec2f(-1.0,  0.0)) * 2.0;
    sum += fogSample(in.uv) * 4.0;
    sum += fogSample(in.uv + texel * vec2f( 1.0,  0.0)) * 2.0;

    sum += fogSample(in.uv + texel * vec2f(-1.0,  1.0));
    sum += fogSample(in.uv + texel * vec2f( 0.0,  1.0)) * 2.0;
    sum += fogSample(in.uv + texel * vec2f( 1.0,  1.0));

    let fog = clamp(sum / 16.0, 0.0, 1.0);
    return vec4f(0.0, 0.0, 0.0, fog * in.color.a);
}


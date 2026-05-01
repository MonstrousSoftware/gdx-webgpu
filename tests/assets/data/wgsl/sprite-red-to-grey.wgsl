// sprite shader to render RED color as grey scale

struct Uniforms {
    projectionViewTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var texture: texture_2d<f32>;
@group(0) @binding(2) var textureSampler: sampler;


struct VertexInput {
    @location(0) position: vec2f,
    @location(1) uv: vec2f,
};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) uv : vec2f,
};


@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
   var out: VertexOutput;

   out.position = uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);;
   out.uv = in.uv;

   return out;
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {
    let red:f32 = textureSample(texture, textureSampler, in.uv).r;
    return vec4f(red, red, red, 1.0);
}

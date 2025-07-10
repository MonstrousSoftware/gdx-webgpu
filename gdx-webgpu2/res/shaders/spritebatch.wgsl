// spritebatch.wgsl

#define GAMMA_CORRECTION

struct Uniforms {
    projectionViewTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var texture: texture_2d<f32>;
@group(0) @binding(2) var textureSampler: sampler;


struct VertexInput {
    @location(0) position: vec2f,
#ifdef TEXTURE_COORDINATE
    @location(1) uv: vec2f,
#endif
#ifdef COLOR
    @location(5) color: vec4f,
#endif
};

struct VertexOutput {
    @builtin(position) position: vec4f,
#ifdef TEXTURE_COORDINATE
    @location(0) uv : vec2f,
#endif
    @location(1) color: vec4f,
};


@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
   var out: VertexOutput;

   var pos =  uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);
   out.position = pos;
#ifdef TEXTURE_COORDINATE
   out.uv = in.uv;
#endif

#ifdef COLOR
   let color:vec4f = in.color;
#else
   let color:vec4f = vec4f(1,1,1,1);   // white
#endif
   out.color = color;

   return out;
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

#ifdef TEXTURE_COORDINATE
    var color = in.color * textureSample(texture, textureSampler, in.uv);
#else
    var color = in.color;
#endif

#ifdef GAMMA_CORRECTION
    let linearColor: vec3f = pow(color.rgb, vec3f(2.2));
    color = vec4f(linearColor, 1.0);
#endif
    return color;
};

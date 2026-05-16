
struct FrameUniforms {
    projectionViewTransform: mat4x4f,
    modelTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;
@group(0) @binding(1) var diffuseTexture: texture_2d_array<f32>;
@group(0) @binding(2) var diffuseSampler: sampler;

struct VertexInput {
    @location(0) position: vec3f,
    @location(1) texCoord: vec3f,
};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) texCoord : vec3f,
};


@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
   var out: VertexOutput;

   var pos =  vec4f(in.position,  1.0);
   out.position =  uFrame.projectionViewTransform * uFrame.modelTransform * pos;
   out.texCoord = in.texCoord;

   return out;
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

    let layerCount = i32(textureNumLayers(diffuseTexture));
    let z = clamp(in.texCoord.z, 0.0, f32(layerCount - 1));
    let currentLayerIndex = i32(floor(z));
    let nextLayerIndex = min(currentLayerIndex + 1, layerCount - 1);

    let currentLayer = textureSample(diffuseTexture, diffuseSampler, in.texCoord.xy, currentLayerIndex);
    let nextLayer = textureSample(diffuseTexture, diffuseSampler, in.texCoord.xy, nextLayerIndex);
    let interp:f32 = fract(z);
    let color = mix(currentLayer.rgb, nextLayer.rgb, interp);
    return vec4f(color.rgb, 1.0);
}


// Object ID Edge Detection Outline shader
// Detects edges by comparing object IDs of neighboring pixels
// Simple direct ID comparison: if (currentID != neighborID) then it's an edge
// No gradient computation - optimized for performance and clean semantic outlines

// Default outline width (can be overridden via preprocessor directive)
#ifndef OUTLINE_WIDTH
#define OUTLINE_WIDTH 1.0
#endif

struct Uniforms {
    projectionViewTransform: mat4x4f,
    outlineColor: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var idTexture: texture_2d<f32>;
@group(0) @binding(2) var idSampler: sampler;

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
   var pos = uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);
   out.position = pos;
   out.uv = in.uv;
   return out;
}

// Helper: Decode object ID from RGB channels
fn decodeObjectId(color: vec4f) -> u32 {
    let r = u32(round(color.r * 255.0));
    let g = u32(round(color.g * 255.0));
    let b = u32(round(color.b * 255.0));
    return r | (g << 8u) | (b << 16u);
}

// Object ID Edge Detection: Direct comparison of neighboring IDs
// An edge exists where the current pixel's ID differs from any neighbor's ID
// OUTLINE_WIDTH controls the sampling radius (1.0 = 1 pixel, 2.0 = 2 pixels, etc.)
fn detectEdge(idTexture: texture_2d<f32>, uv: vec2f) -> f32 {
    let texSize = vec2f(textureDimensions(idTexture));
    let pixelSize = 1.0 / texSize;
    let sampleDist = max(0.1, f32(OUTLINE_WIDTH)) * pixelSize;

    // Decode current pixel ID
    let currentColor = textureSample(idTexture, idSampler, uv);
    let currentId = decodeObjectId(currentColor);

    // Check 4 neighbors at distance outlineWidth for ID differences
    // Sampling at cardinal directions for better performance
    let upColor = textureSample(idTexture, idSampler, uv + vec2f(0.0, sampleDist.y));
    let downColor = textureSample(idTexture, idSampler, uv - vec2f(0.0, sampleDist.y));
    let leftColor = textureSample(idTexture, idSampler, uv - vec2f(sampleDist.x, 0.0));
    let rightColor = textureSample(idTexture, idSampler, uv + vec2f(sampleDist.x, 0.0));

    let upId = decodeObjectId(upColor);
    let downId = decodeObjectId(downColor);
    let leftId = decodeObjectId(leftColor);
    let rightId = decodeObjectId(rightColor);

    // Edge detected if any neighbor has a different ID
    let isEdge = (currentId != upId) || (currentId != downId) ||
                 (currentId != leftId) || (currentId != rightId);

    return select(0.0, 1.0, isEdge);
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {
    let edge = detectEdge(idTexture, in.uv);
    let bgColor = vec4f(0.0, 0.0, 0.0, 0.0);

    return mix(bgColor, uniforms.outlineColor, edge);
}

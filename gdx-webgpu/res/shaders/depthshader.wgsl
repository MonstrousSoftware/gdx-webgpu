// depth shader
// Copyright 2025 Monstrous Software.
// Licensed under the Apache License, Version 2.0 (the "License");


struct FrameUniforms {
    projectionViewTransform: mat4x4f,   // from light source point of view
};

struct ModelUniforms {
    modelMatrix: mat4x4f,
};

// note: we assume materials are all opaque

// frame bindings
@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;

// renderables
@group(1) @binding(0) var<storage, read> instances: array<ModelUniforms>;


struct VertexInput {
    @location(0) position: vec3f,
};

struct VertexOutput {
    @builtin(position) position: vec4f,
};


@vertex
fn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {
   var out: VertexOutput;

   let worldPosition =  instances[instance].modelMatrix * vec4f(in.position, 1.0);
   out.position =   uFrame.projectionViewTransform * worldPosition;
   return out;
}



// basic ModelBatch shader
// Copyright 2025 Monstrous Software.
// Licensed under the Apache License, Version 2.0 (the "License");

// Note this is an uber shader with conditional compilation depending on #define values from the shader prefix

struct DirectionalLight {
    color: vec4f,
    direction: vec4f
}

struct PointLight {
    color: vec4f,
    position: vec4f,
    intensity: f32
}

struct FrameUniforms {
    projectionViewTransform: mat4x4f,
    directionalLights : array<DirectionalLight, 3>,     // todo don't use hard coded constant for array size
    pointLights : array<PointLight, 3>,     // todo don't use hard coded constant for array size
    ambientLight: vec4f,
    cameraPosition: vec4f,
    numDirectionalLights: f32,
    numPointLights: f32,
};

struct ModelUniforms {
    modelMatrix: mat4x4f,
};

struct MaterialUniforms {
    diffuseColor: vec4f,
    shininess: f32,
    //dummy: vec3f
};

@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;

@group(1) @binding(0) var<uniform> material: MaterialUniforms;
@group(1) @binding(1) var diffuseTexture: texture_2d<f32>;
@group(1) @binding(2) var diffuseSampler: sampler;
@group(1) @binding(3) var normalTexture: texture_2d<f32>;        // not used yet
@group(1) @binding(4) var normalSampler: sampler;
@group(1) @binding(5) var metallicRoughnessTexture: texture_2d<f32>;        // not used yet
@group(1) @binding(6) var metallicRoughnessSampler: sampler;

@group(2) @binding(0) var<storage, read> instances: array<ModelUniforms>;


struct VertexInput {
    @location(0) position: vec3f,
#ifdef TEXTURE_COORDINATE
    @location(1) uv: vec2f,
#endif
#ifdef NORMAL
    @location(2) normal: vec3f,
#endif
#ifdef NORMAL_MAP
    @location(3) tangent: vec3f,
    @location(4) bitangent: vec3f,
#endif
#ifdef COLOR
    @location(5) color: vec4f,
#endif

};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(1) uv: vec2f,
    @location(2) color: vec4f,
    @location(3) normal: vec3f,
    @location(4) worldPos : vec3f,
#ifdef NORMAL_MAP
    @location(5) tangent: vec3f,
    @location(6) bitangent: vec3f,
#endif
};

@vertex
fn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {
   var out: VertexOutput;

   let worldPosition =  instances[instance].modelMatrix * vec4f(in.position, 1.0);
   out.position =   uFrame.projectionViewTransform * worldPosition;
   out.worldPos = worldPosition.xyz;
#ifdef TEXTURE_COORDINATE
   out.uv = in.uv;
#else
   out.uv = vec2f(0);
#endif
   var diffuseColor = vec4f(1); // default white
#ifdef COLOR
   diffuseColor = in.color;
#endif
   diffuseColor *= vec4f(material.diffuseColor.rgb, 1.0);
   out.color = diffuseColor;

#ifdef NORMAL
   // transform model normal to world space
   let normal = normalize((instances[instance].modelMatrix * vec4f(in.normal, 0.0)).xyz);
#else
    let normal = vec3f(0,1,0);
#endif
    out.normal = normal;

#ifdef NORMAL_MAP
    out.tangent = in.tangent;
    out.bitangent = in.bitangent;
#endif

   return out;
}


@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {
#ifdef TEXTURE_COORDINATE
   // var color = in.color * textureSample(normalTexture, normalSampler, in.uv);
   //var color = in.color * textureSample(metallicRoughnessTexture, metallicRoughnessSampler, in.uv);
   var color = in.color * textureSample(diffuseTexture, diffuseSampler, in.uv);
#else
   var color = in.color;
#endif

#ifdef LIGHTING
    //let normal = normalize(in.normal.xyz);

#ifdef NORMAL_MAP
    let normalMapStrength = 0.3;

    let encodedN = textureSample(normalTexture, normalSampler, in.uv).rgb;
    let localN = encodedN * 2.0 - 1.0;
    // The TBN matrix converts directions from the local space to the world space
    let localToWorld = mat3x3f(
        normalize(in.tangent),
        normalize(in.bitangent),
        normalize(in.normal),
    );
    let worldN = localToWorld * localN;
    let normal = mix(in.normal.xyz, worldN, normalMapStrength);
#else
    let normal = normalize(in.normal.xyz);
#endif



    let shininess : f32 = material.shininess;


    var radiance : vec3f = uFrame.ambientLight.rgb;
    var specular : vec3f = vec3f(0);
    let viewVec : vec3f = normalize(uFrame.cameraPosition.xyz - in.worldPos.xyz);

    // for each directional light
    // could go to vertex shader but esp. specular lighting will be lower quality
    for (var i: u32 = 0; i < u32(uFrame.numDirectionalLights); i++) {
        let light = uFrame.directionalLights[i];

        let lightVec = -normalize(light.direction.xyz);       // L is vector towards light
        let irradiance = max(dot(lightVec, normal), 0.0);
        radiance += irradiance *  light.color.rgb;

        let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));
        specular += irradiance *  light.color.rgb * pow(halfDotView, shininess);
    }
    // for each point light
    // note: default libgdx seems to ignore intensity of point lights
    for (var i: u32 = 0; i < u32(uFrame.numPointLights); i++) {
        let light = uFrame.pointLights[i];

        var lightVec = light.position.xyz - in.worldPos.xyz;       // L is vector towards light
        let dist2 : f32 = dot(lightVec,lightVec);
        lightVec  = normalize(lightVec);
        let attenuation : f32 = light.intensity/(1.0 + dist2);// attenuation (note this makes an assumption on the world scale)
        let NdotL : f32 = max(dot(lightVec, normal), 0.0);
        let irradiance : f32 =  attenuation * NdotL;

        radiance += irradiance *  light.color.rgb;

        let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));
        specular += irradiance *  light.color.rgb * pow(halfDotView, shininess);
    }

    let litColor = vec4f(color.rgb * radiance + specular, 1.0);

    color = litColor;
#endif

    //return vec4f(normal, 1.0);
    //return vec4f(uFrame.ambientLight.rgb, 1.0);
    //return material.diffuseColor;
    return color;
};

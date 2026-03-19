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
#ifdef CSM_SHADOW_MAP
    // N cascade projection-view transforms followed by cascade split thresholds
    shadowProjViewTransforms: array<mat4x4f, MAX_CASCADES>,
    cascadeSplits: vec4f,
    // Per-cascade shadow bias — scaled by each cascade's depth range so the
    // world-space bias is constant regardless of how much depth range a cascade covers.
    cascadeBiases: vec4f,
    // Projection-view of the CSM driver camera (may differ from the rendering camera
    // when an observer/debug camera is active). Used for cascade selection only.
    csmCameraProjectionView: mat4x4f,
#else
    shadowProjViewTransform: mat4x4f,
#endif
#ifdef MAX_DIR_LIGHTS
    directionalLights : array<DirectionalLight, MAX_DIR_LIGHTS>,
#endif
#ifdef MAX_POINT_LIGHTS
    pointLights : array<PointLight, MAX_POINT_LIGHTS>,
#endif
    ambientLight: vec4f,
    cameraPosition: vec4f,
    fogColor: vec4f,
    numDirectionalLights: f32,
    numPointLights: f32,
    shadowPcfOffset: f32,
    shadowBias: f32,
    normalMapStrength: f32,
    numRoughnessLevels: f32,
#ifdef CSM_SHADOW_MAP
    shadowPcfRadius: f32,       // PCF kernel half-size: 0=1×1, 1=3×3, 2=5×5, 3=7×7
    shadowFilterMode: f32,      // 0 = grid PCF, 1 = rotated Poisson disk PCF
    cascadeBlendFraction: f32,  // fraction of cascade range used for blending (0 = off, e.g. 0.1 = 10%)
#endif
};

struct ModelUniforms {
    modelMatrix: mat4x4f,
    normalMatrix: mat4x4f,
    morphWeights: vec4f,
    morphWeights2: vec4f,
};

struct MaterialUniforms {
    diffuseColor: vec4f,
    shininess: f32,
    roughnessFactor: f32,
    metallicFactor: f32,
};

// frame bindings
@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;
#ifdef SHADOW_MAP
    @group(0) @binding(1) var shadowMap: texture_depth_2d;
    @group(0) @binding(2) var shadowSampler: sampler_comparison;
#endif
#ifdef CSM_SHADOW_MAP
    @group(0) @binding(1) var csmShadowMap: texture_depth_2d_array;
    @group(0) @binding(2) var csmShadowSampler: sampler_comparison;
#endif
#ifdef ENVIRONMENT_MAP
    @group(0) @binding(3) var cubeMap:          texture_cube<f32>;
    @group(0) @binding(4) var cubeMapSampler:   sampler;
#endif
#ifdef USE_IBL
    @group(0) @binding(5) var irradianceMap:    texture_cube<f32>;
    @group(0) @binding(6) var irradianceSampler:       sampler;
    @group(0) @binding(7) var radianceMap:    texture_cube<f32>;
    @group(0) @binding(8) var radianceSampler:       sampler;
    @group(0) @binding(9) var brdfLUT:    texture_2d<f32>;
    @group(0) @binding(10) var lutSampler:       sampler;
#endif

// material bindings
@group(1) @binding(0) var<uniform> material: MaterialUniforms;
@group(1) @binding(1) var diffuseTexture: texture_2d<f32>;
@group(1) @binding(2) var diffuseSampler: sampler;
@group(1) @binding(3) var normalTexture: texture_2d<f32>;
@group(1) @binding(4) var normalSampler: sampler;
@group(1) @binding(5) var metallicRoughnessTexture: texture_2d<f32>;
@group(1) @binding(6) var metallicRoughnessSampler: sampler;
@group(1) @binding(7) var emissiveTexture: texture_2d<f32>;
@group(1) @binding(8) var emissiveSampler: sampler;

// renderables
@group(2) @binding(0) var<storage, read> instances: array<ModelUniforms>;

// Skinning
#ifdef SKIN
    @group(3) @binding(0) var<storage, read> jointMatrices: array<mat4x4f>;
#endif

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
#ifdef SKIN
    @location(6) joints: vec4f,
    @location(7) weights: vec4f,
#endif
#ifdef MORPH
#ifdef MORPH_0
    @location(8) morph_pos0: vec3f,
#endif
#ifdef MORPH_1
    @location(9) morph_pos1: vec3f,
#endif
#ifdef MORPH_2
    @location(10) morph_pos2: vec3f,
#endif
#ifdef MORPH_3
    @location(11) morph_pos3: vec3f,
#endif
#ifdef MORPH_4
    @location(12) morph_pos4: vec3f,
#endif
#ifdef MORPH_5
    @location(13) morph_pos5: vec3f,
#endif
#ifdef MORPH_6
    @location(14) morph_pos6: vec3f,
#endif
#ifdef MORPH_7
    @location(15) morph_pos7: vec3f,
#endif
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
#ifdef FOG
    @location(7) fogDepth: f32,
#endif
#ifdef SHADOW_MAP
    @location(8)  shadowPos: vec3f,
#endif
};

const pi : f32 = 3.14159265359;

@vertex
fn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {
   var out: VertexOutput;

   var pos = in.position;
#ifdef MORPH
#ifdef MORPH_0
   pos += instances[instance].morphWeights[0] * in.morph_pos0;
#endif
#ifdef MORPH_1
   pos += instances[instance].morphWeights[1] * in.morph_pos1;
#endif
#ifdef MORPH_2
   pos += instances[instance].morphWeights[2] * in.morph_pos2;
#endif
#ifdef MORPH_3
   pos += instances[instance].morphWeights[3] * in.morph_pos3;
#endif
#ifdef MORPH_4
   pos += instances[instance].morphWeights2[0] * in.morph_pos4;
#endif
#ifdef MORPH_5
   pos += instances[instance].morphWeights2[1] * in.morph_pos5;
#endif
#ifdef MORPH_6
   pos += instances[instance].morphWeights2[2] * in.morph_pos6;
#endif
#ifdef MORPH_7
   pos += instances[instance].morphWeights2[3] * in.morph_pos7;
#endif
#endif

   var normal_attr = vec3f(0,1,0);
#ifdef NORMAL
   normal_attr = in.normal;
#endif

#ifdef SKIN
     // Get relevant 4 bone matrices
     // joint matrix is already multiplied by inv bind matrix in Node.calculateBoneTransform
     let joint0 = jointMatrices[u32(in.joints[0])];
     let joint1 = jointMatrices[u32(in.joints[1])];
     let joint2 = jointMatrices[u32(in.joints[2])];
     let joint3 = jointMatrices[u32(in.joints[3])];

     // Compute influence of joint based on weight
     let skinMatrix =
       joint0 * in.weights[0] +
       joint1 * in.weights[1] +
       joint2 * in.weights[2] +
       joint3 * in.weights[3];

     // Bone transformed mesh
   let worldPosition =   instances[instance].modelMatrix * skinMatrix * vec4f(pos, 1.0);
#else
   let worldPosition =  instances[instance].modelMatrix * vec4f(pos, 1.0);
#endif

   out.position =   uFrame.projectionViewTransform * worldPosition;
   out.worldPos = worldPosition.xyz;
#ifdef TEXTURE_COORDINATE
   out.uv = in.uv;
#else
   out.uv = vec2f(0);
#endif

#ifdef COLOR
   var diffuseColor = in.color;
#else
   var diffuseColor = vec4f(1); // default white
#endif
   diffuseColor *= material.diffuseColor;
   out.color = diffuseColor;

#ifdef NORMAL
   // transform model normal to a world normal
   let normal = normalize((instances[instance].normalMatrix * vec4f(normal_attr, 0.0)).xyz);
#else
   let normal = vec3f(0,1,0);
#endif
    out.normal = normal;

#ifdef NORMAL_MAP
    out.tangent = in.tangent;
    out.bitangent = in.bitangent;
#endif

#ifdef FOG
    let flen:vec3f = uFrame.cameraPosition.xyz - worldPosition.xyz;
    let fog:f32 = dot(flen, flen) * uFrame.cameraPosition.w;
    out.fogDepth = min(fog, 1.0);
#endif

#ifdef SHADOW_MAP
  // XY is in (-1, 1) space, Z is in (0, 1) space
  let posFromLight = uFrame.shadowProjViewTransform * worldPosition;

  // Convert XY to (0, 1)
  // Y is flipped because texture coords are Y-down.
  out.shadowPos = vec3(
    posFromLight.xy * vec2(0.5, -0.5) + vec2(0.5),
    posFromLight.z
  );
#endif
// CSM: no per-vertex shadow coords needed — lookup done fully in fragment shader from worldPos

   return out;
}


@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {
#ifdef TEXTURE_COORDINATE
   var color = in.color * textureSample(diffuseTexture, diffuseSampler, in.uv);
#else
   var color = in.color;
#endif

#ifdef CSM_SHADOW_MAP
    let visibility = getCsmVisibility(in.worldPos, in.position.xy);
#else
#ifdef SHADOW_MAP
    let visibility = getShadowNess(in.shadowPos);
#else
    let visibility = 1.0;
#endif
#endif

#ifdef LIGHTING
    let baseColor = color;

#ifdef NORMAL_MAP
    let encodedN = textureSample(normalTexture, normalSampler, in.uv).rgb;
    let localN = encodedN * 2.0 - 1.0;
    // The TBN matrix converts directions from the local space to the world space
    let localToWorld = mat3x3f(
        normalize(in.tangent),
        normalize(in.bitangent),
        normalize(in.normal),
    );
    let worldN = localToWorld * localN;
    let normal = mix(in.normal.xyz, worldN, uFrame.normalMapStrength);
#else // NORMAL_MAP
    let normal = normalize(in.normal.xyz);
#endif

    // metallic is coded in the blue channel and roughness in the green channel of the MR texture
    let mrSample = textureSample(metallicRoughnessTexture, metallicRoughnessSampler, in.uv).rgb;

    let roughness : f32 = mrSample.g * material.roughnessFactor;
    let metallic : f32 = mrSample.b * material.metallicFactor;

    let shininess : f32 = material.shininess;   // used instead of roughness for non-PBR

    var radiance : vec3f = vec3f(0);    // outgoing radiance (Lo)
    var specular : vec3f = vec3f(0);
    let viewVec : vec3f = normalize(uFrame.cameraPosition.xyz - in.worldPos.xyz);

#ifdef USE_IBL
    let ambient : vec3f = ambientIBL( viewVec, normal, roughness, metallic, baseColor.rgb);
#else
    let ambient : vec3f = uFrame.ambientLight.rgb * baseColor.rgb;
#endif

#ifdef MAX_DIR_LIGHTS
    // for each directional light
    // could go to vertex shader but esp. specular lighting will be lower quality
    let numDirectionalLights = min(uFrame.numDirectionalLights, MAX_DIR_LIGHTS);     // fail-safe
    if(numDirectionalLights > 0) {
        for (var i: u32 = 0; i < u32(numDirectionalLights); i++) {
            let light = uFrame.directionalLights[i];

            let lightVec = -normalize(light.direction.xyz);       // L is unit vector towards light source
            let NdotL = max(dot(lightVec, normal), 0.0);
#ifdef PBR
            if(NdotL > 0.0) {
                radiance += BRDF(lightVec, viewVec, normal, roughness, metallic, baseColor.rgb) * NdotL *  light.color.rgb;
            }
#else
            radiance += NdotL *  light.color.rgb;
    #ifdef SPECULAR
            let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));
            specular += NdotL *  light.color.rgb * pow(halfDotView, shininess);
    #endif
#endif // PBR
        }
    }
#endif //MAX_DIR_LIGHTS

#ifdef MAX_POINT_LIGHTS
    // for each point light
    // note: default libgdx seems to ignore intensity of point lights
    let numPointLights = min(uFrame.numPointLights, MAX_POINT_LIGHTS); // fail-safe
    if(numPointLights > 0) {
        for (var i: u32 = 0; i < u32(numPointLights); i++) {
            let light = uFrame.pointLights[i];

            var lightVec = light.position.xyz - in.worldPos.xyz;       // L is vector towards light
            let dist2 : f32 = dot(lightVec,lightVec);
            lightVec  = normalize(lightVec);
            let attenuation : f32 = light.intensity/(1.0 + dist2);// attenuation (note this makes an assumption on the world scale)
            let NdotL : f32 = max(dot(lightVec, normal), 0.0);
#ifdef PBR
            if(NdotL > 0.0) {
                radiance += BRDF(lightVec, viewVec, normal, roughness, metallic, baseColor.rgb) * NdotL *  attenuation * light.color.rgb;
            }
#else
            radiance += NdotL *  attenuation * light.color.rgb;
#ifdef SPECULAR
            let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));
            specular += NdotL *  attenuation * light.color.rgb * pow(halfDotView, shininess);
#endif
#endif // PBR
        }
    }
#endif // MAX_POINT_LIGHTS

#ifdef PBR
    let litColor = vec4f(ambient + visibility*radiance, 1.0);
#else
    let litColor = vec4f( ambient + color.rgb * (visibility * radiance) + visibility*specular, 1.0);
#endif

    color = litColor;

#ifndef USE_IBL
    #ifdef ENVIRONMENT_MAP
        let rdir:vec3f = normalize(reflect(viewVec, normal)*vec3f(-1, -1, 1));
        var reflection = textureSample(cubeMap, cubeMapSampler, rdir);
        color = mix(color, reflection, 0.1f);       // todo scale is arbitrary
    #endif
#endif


#endif // LIGHTING

    let emissiveColor = textureSample(emissiveTexture, emissiveSampler, in.uv).rgb;
    color = color + vec4f(emissiveColor, 0);


#ifdef FOG
    color = vec4f(mix(color.rgb, uFrame.fogColor.rgb, in.fogDepth), color.a);
#endif


#ifdef GAMMA_CORRECTION
    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));
    color = vec4f(linearColor, color.a);
#endif

    //return(vec4f(0.0, roughness, metallic, 1.0));

    //return vec4f(emissiveColor, 1.0);
    //return vec4f(normal, 1.0);
    //return vec4f(viewVec, 1.0);
    //return vec4f(rdir, 1.0);
    //return vec4f(uFrame.ambientLight.rgb, 1.0);
    //return material.diffuseColor;
    //return vec4f(in.fogDepth, 0, 0, 1);
    //return vec4f(ambient, 1.0);

    return color;
};

// ----- Poisson Disk PCF helpers (shared by single shadow map and CSM) -----
// 16-sample Poisson disk distributed in the unit circle
const POISSON_DISK = array<vec2f, 16>(
    vec2f(-0.94201624, -0.39906216),
    vec2f( 0.94558609, -0.76890725),
    vec2f(-0.09418410, -0.92938870),
    vec2f( 0.34495938,  0.29387760),
    vec2f(-0.91588581,  0.45771432),
    vec2f(-0.81544232, -0.87912464),
    vec2f(-0.38277543,  0.27676845),
    vec2f( 0.97484398,  0.75648379),
    vec2f( 0.44323325, -0.97511554),
    vec2f( 0.53742981, -0.47373420),
    vec2f(-0.26496911, -0.41893023),
    vec2f( 0.79197514,  0.19090188),
    vec2f(-0.24188840,  0.99706507),
    vec2f(-0.81409955,  0.91437590),
    vec2f( 0.19984126,  0.78641367),
    vec2f( 0.14383161, -0.14100790)
);

// Screen-space pseudo-random rotation angle [0, 2π)
fn poissonRotationAngle(fragCoord: vec2f) -> f32 {
    return fract(sin(dot(fragCoord, vec2f(12.9898, 78.233))) * 43758.5453) * 2.0 * pi;
}

#ifdef SHADOW_MAP
// returns value 0..1 for the amount of "sunlight" using a fixed 3×3 PCF
fn getShadowNess( shadowPos:vec3f ) -> f32 {
    var visibility = 0.0;
    for( var y = -1; y <= 1; y++){
        for( var x = -1; x <= 1; x++){
        let offset = vec2f(f32(x), f32(y)) * uFrame.shadowPcfOffset;
            visibility += textureSampleCompare(shadowMap, shadowSampler, shadowPos.xy+offset, shadowPos.z - uFrame.shadowBias);
        }
    }
    return visibility / 9.0;
}

fn getShadowSingleSample( shadowPos:vec3f ) -> f32 {
    return textureSampleCompare(shadowMap, shadowSampler, shadowPos.xy, shadowPos.z -  uFrame.shadowBias);
}
#endif


#ifdef CSM_SHADOW_MAP

// Sample shadow visibility for a specific cascade layer.
// Returns 1.0 (fully lit) if the fragment is outside the cascade's coverage.
fn sampleCascade(cascadeIdx: u32, worldPos: vec3f, fragCoord: vec2f) -> f32 {
    let posFromLight = uFrame.shadowProjViewTransforms[cascadeIdx] * vec4f(worldPos, 1.0);
    let shadowUV = posFromLight.xy * vec2f(0.5, -0.5) + vec2f(0.5);
    let shadowDepth = posFromLight.z - uFrame.cascadeBiases[cascadeIdx];

    // Outside shadow map coverage → fully lit
    if (shadowUV.x < 0.0 || shadowUV.x > 1.0 || shadowUV.y < 0.0 || shadowUV.y > 1.0
        || shadowDepth < 0.0 || shadowDepth > 1.0) {
        return 1.0;
    }

    if (uFrame.shadowFilterMode > 0.5) {
        // Rotated Poisson disk PCF (16 samples)
        let angle = poissonRotationAngle(fragCoord);
        let s = sin(angle);
        let c = cos(angle);
        let rot = mat2x2f(c, s, -s, c);
        var visibility = 0.0;
        for (var i = 0u; i < 16u; i++) {
            let offset = rot * POISSON_DISK[i] * uFrame.shadowPcfOffset;
            visibility += textureSampleCompareLevel(
                csmShadowMap, csmShadowSampler,
                shadowUV + offset, cascadeIdx,
                shadowDepth
            );
        }
        return visibility / 16.0;
    }

    // Grid PCF
    let radius = i32(uFrame.shadowPcfRadius);
    var visibility = 0.0;
    var sampleCount = 0.0;
    for (var y = -radius; y <= radius; y++) {
        for (var x = -radius; x <= radius; x++) {
            let offset = vec2f(f32(x), f32(y)) * uFrame.shadowPcfOffset;
            visibility += textureSampleCompareLevel(
                csmShadowMap, csmShadowSampler,
                shadowUV + offset, cascadeIdx,
                shadowDepth
            );
            sampleCount += 1.0;
        }
    }
    return visibility / sampleCount;
}

// Select the correct cascade using the CSM driver camera's depth (not the rendering camera's),
// then do a PCF lookup in the depth-array layer for that cascade.
// When cascade blending is enabled (cascadeBlendFraction > 0), fragments near the far edge
// of a cascade are smoothly blended with the next cascade to eliminate visible seams.
//
// The CSM driver camera may differ from the rendering camera (e.g. when a debug observer camera
// is active). csmCameraProjectionView holds the CSM driver camera's shifted projection-view.
//
// Uses textureSampleCompareLevel (not textureSampleCompare) because the cascade
// selection depends on per-fragment depth, making control flow non-uniform.
// textureSampleCompareLevel always samples mip-level 0 and has no uniformity requirement.
fn getCsmVisibility(worldPos: vec3f, fragCoord: vec2f) -> f32 {
    // Compute the fragment's NDC depth in the CSM driver camera's clip space
    let csmClip = uFrame.csmCameraProjectionView * vec4f(worldPos, 1.0);
    let ndcDepth = csmClip.z / csmClip.w;

    // Find the first cascade whose far plane is at or beyond the fragment depth.
    var cascadeIndex = u32(MAX_CASCADES - 1); // default: farthest cascade
    for (var i = 0u; i < u32(MAX_CASCADES - 1); i++) {
        if (ndcDepth <= uFrame.cascadeSplits[i]) {
            cascadeIndex = i;
            break;
        }
    }

    // For the last cascade, check if the fragment is beyond shadow coverage entirely
    let blendFrac = uFrame.cascadeBlendFraction;
    if (cascadeIndex == u32(MAX_CASCADES - 1)) {
        let lastFar = uFrame.cascadeSplits[cascadeIndex];
        if (ndcDepth >= lastFar) {
            return 1.0; // fully lit — beyond shadow distance
        }
    }

    let visA = sampleCascade(cascadeIndex, worldPos, fragCoord);

    // Cascade blending — only the last 10% of the blend zone actually fades.
    // This keeps full shadow strength for 90% of the zone and avoids wasting
    // ALU/texture samples on fragments that won't visibly blend.
    if (blendFrac > 0.0) {
        let splitDepth = uFrame.cascadeSplits[cascadeIndex];

        if (cascadeIndex < u32(MAX_CASCADES - 1)) {
            // Inter-cascade blend: thin strip at the cascade boundary
            let prevSplit = select(0.0, uFrame.cascadeSplits[cascadeIndex - 1u], cascadeIndex > 0u);
            let fadeZone = (splitDepth - prevSplit) * blendFrac * 0.1;
            let blendStart = splitDepth - fadeZone;

            if (ndcDepth > blendStart) {
                let t = saturate((ndcDepth - blendStart) / fadeZone);
                let visB = sampleCascade(cascadeIndex + 1u, worldPos, fragCoord);
                return mix(visA, visB, t);
            }
        } else {
            // Last cascade fade-out: thin strip at the shadow distance edge
            let fadeZone = splitDepth * blendFrac / f32(MAX_CASCADES) * 0.1;
            let blendStart = splitDepth - fadeZone;

            if (ndcDepth > blendStart) {
                let t = saturate((ndcDepth - blendStart) / fadeZone);
                return mix(visA, 1.0, t);
            }
        }
    }

    return visA;
}
#endif


#ifdef PBR

// Normal distribution function
fn D_GGX(NdotH: f32, roughness: f32) -> f32 {
    let alpha : f32 = roughness * roughness;
    let alpha2 : f32 = alpha * alpha;
    let denom : f32 = (NdotH * NdotH) * (alpha2 - 1.0) + 1.0;
    return alpha2/(pi * denom * denom);
}

fn G_SchlickSmith_GGX(NdotL : f32, NdotV : f32, roughness : f32) -> f32 {
    let r : f32 = (roughness + 1.0);
    let k : f32 = (r*r)/8.0;

//    let alpha: f32 = roughness * roughness;
//    let k: f32 = alpha / 2.0;

    let GL : f32 = NdotL / (NdotL * (1.0 - k) + k);
    let GV : f32 = NdotV / (NdotV * (1.0 - k) + k);
    return GL * GV;
}


fn F_Schlick(cosTheta : f32, metallic : f32, baseColor : vec3f ) -> vec3f {
    let F0 : vec3f = mix(vec3(0.04), baseColor, metallic);
    let F : vec3f = F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
    return F;
}

fn BRDF( L : vec3f, V:vec3f, N: vec3f, roughness:f32, metallic:f32, baseColor: vec3f) -> vec3f {
    let H = normalize(V+L);
    let NdotV : f32 = clamp(dot(N, V), 0.0, 1.0);
    let NdotL : f32 = clamp(dot(N, L), 0.001, 1.0);
    let LdotH : f32 = clamp(dot(L, H), 0.0, 1.0);
    let NdotH : f32 = clamp(dot(N, H), 0.0, 1.0);
    let HdotV : f32 = clamp(dot(H, V), 0.0, 1.0);

    // calculate terms for microfacet shading model
    let D :f32      = D_GGX(NdotH, roughness);
    let G :f32      = G_SchlickSmith_GGX(NdotL, NdotV, roughness);
    let F :vec3f    = F_Schlick(HdotV, metallic, baseColor);

    let kS = F;
    let kD = (vec3f(1.0) - kS) * (1.0 - metallic);

    let specular : vec3f = D * F * G / (4.0 * max(NdotL, 0.0001) * max(NdotV, 0.0001));

    let diffuse : vec3f = kD * baseColor / pi;

    let Lo : vec3f = diffuse + specular;
    return Lo;
}

#ifdef USE_IBL
fn ambientIBL( V:vec3f, N: vec3f, roughness:f32, metallic:f32, baseColor: vec3f) -> vec3f {

    let NdotV : f32 = clamp(dot(N, V), 0.0, 1.0);
    let F :vec3f    =  F_Schlick(NdotV, metallic, baseColor);

    // kS = F, kD = 1 - kS;
    let kD = (vec3f(1.0) - F)*(1.0 - metallic);
    let lightSample:vec3f = normalize(N * vec3f(1, 1, -1));   // flip Z
    let irradiance:vec3f = textureSample(irradianceMap, irradianceSampler, lightSample).rgb;
    let diffuse:vec3f    = irradiance * baseColor.rgb;

    let maxReflectionLOD:f32 = f32(uFrame.numRoughnessLevels);
    let R:vec3f = reflect(-V, N)* vec3f(-1, 1, 1); // flip X
    let prefilteredColor:vec3f = textureSampleLevel(radianceMap, radianceSampler, R, roughness * maxReflectionLOD).rgb;
    let envBRDF = textureSample(brdfLUT, lutSampler, vec2(NdotV, roughness)).rg;
    let specular: vec3f = prefilteredColor * (F * envBRDF.x + envBRDF.y);
    let ambient:vec3f    = (kD * diffuse) + specular;

    return vec3f(ambient);
}
#endif // USE_IBL
#endif // PBR

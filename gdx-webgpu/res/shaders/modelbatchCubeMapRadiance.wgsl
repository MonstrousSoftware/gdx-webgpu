// Based on Learn OpenGL - IBL Radiance map
//https://learnopengl.com/PBR/IBL/Specular-IBL


struct FrameUniforms {
    projectionMatrix: mat4x4f,
    viewMatrix : mat4x4f,
    combinedMatrix : mat4x4f,
    cameraPosition : vec4f,
    ambientLightLevel : f32,
};

struct MaterialUniforms {
    metallicFactor: f32,
    roughnessFactor: f32,
    baseColorFactor: vec4f,
};

// Group 0 - Frame
// Group 1 - Material
// Group 2 - Instance

// Frame
@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;

@group(0) @binding(3) var cubeMap:          texture_cube<f32>;
@group(0) @binding(4) var cubeMapSampler:   sampler;



// Material
@group(1) @binding(0) var<uniform> material: MaterialUniforms;
@group(1) @binding(1) var albedoTexture: texture_2d<f32>;
@group(1) @binding(2) var textureSampler: sampler;


struct VertexInput {
    @location(0) position: vec3f,
#ifdef TEXTURE_COORDINATE
    @location(1) uv: vec2f,
#endif
};

struct VertexOutput {
    @builtin(position)  position: vec4f,
    @location(1)        localPos: vec4f,
};

@vertex
fn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {
   var out: VertexOutput;

   out.localPos = vec4f(in.position, 1.0);
   out.position =  uFrame.combinedMatrix * vec4f(in.position, 1.0);
   return out;
}

const  PI:f32 = 3.14159265359;


fn RadicalInverse_VdC( inbits:u32) -> f32 {
    var bits:u32 = inbits;
    bits = (bits << 16u) | (bits >> 16u);
    bits = ((bits & 0x55555555u) << 1u) | ((bits & 0xAAAAAAAAu) >> 1u);
    bits = ((bits & 0x33333333u) << 2u) | ((bits & 0xCCCCCCCCu) >> 2u);
    bits = ((bits & 0x0F0F0F0Fu) << 4u) | ((bits & 0xF0F0F0F0u) >> 4u);
    bits = ((bits & 0x00FF00FFu) << 8u) | ((bits & 0xFF00FF00u) >> 8u);
    return  f32(bits) * 2.3283064365386963e-10; // / 0x100000000
}
// ----------------------------------------------------------------------------
fn Hammersley(i:u32, N:u32) -> vec2f
{
    return vec2f(f32(i)/f32(N), RadicalInverse_VdC(i));
}

fn ImportanceSampleGGX( Xi:vec2f,  N:vec3f,  roughness:f32) -> vec3f
{
    let a:f32 = roughness*roughness;

    let phi:f32 = 2.0 * PI * Xi.x;
    let cosTheta:f32 = sqrt((1.0 - Xi.y) / (1.0 + (a*a - 1.0) * Xi.y));
    let sinTheta:f32 = sqrt(1.0 - cosTheta*cosTheta);

    // from spherical coordinates to cartesian coordinates
    var H:vec3f;
    H.x = cos(phi) * sinTheta;
    H.y = sin(phi) * sinTheta;
    H.z = cosTheta;

    // from tangent-space vector to world-space sample vector
    let up:vec3f        = select(vec3(1.0, 0.0, 0.0), vec3(0.0, 0.0, 1.0), abs(N.z) < 0.999);    // select(falseValue, trueValue, condition)
    let tangent:vec3f   = normalize(cross(up, N));
    let bitangent:vec3f = cross(N, tangent);

    let sampleVec:vec3f = tangent * H.x + bitangent * H.y + N * H.z;
    return normalize(sampleVec);
}



@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

    let roughness = uFrame.ambientLightLevel;

    let N:vec3f = normalize(in.localPos).xyz;
    let R = N;
    let V = R;

    const SAMPLE_COUNT:u32 = 1024;
    var totalWeight:f32 = 0.0;
    var prefilteredColor:vec3f = vec3f(0.0);
    for(var i:u32 = 0u; i < SAMPLE_COUNT; i++)
    {
        let Xi:vec2f = Hammersley(i, SAMPLE_COUNT);
        let H:vec3f  = ImportanceSampleGGX(Xi, N, roughness);
        let L:vec3f  = normalize(2.0 * dot(V, H) * H - V);

        let NdotL:f32 = max(dot(N, L), 0.0);
        prefilteredColor += select(vec3f(0), textureSample(cubeMap, cubeMapSampler, L*vec3(1,1,-1)).rgb * NdotL, NdotL > 0.0);
        totalWeight      += select(0, NdotL, NdotL > 0.0);
    }
    prefilteredColor = prefilteredColor / totalWeight;

    return vec4f(prefilteredColor, 1.0);
}

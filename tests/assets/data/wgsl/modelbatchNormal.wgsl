// alternative model batch shader showing colours depending on surface normal



struct FrameUniforms {
    projectionViewTransform: mat4x4f,
};

struct ModelUniforms {
    modelMatrix: mat4x4f,
    normalMatrix: mat4x4f,
    morphWeights: vec4f,
    morphWeights2: vec4f,
};



// frame bindings
@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;


// material bindings
@group(1) @binding(3) var normalTexture: texture_2d<f32>;
@group(1) @binding(4) var normalSampler: sampler;

// renderables
@group(2) @binding(0) var<storage, read> instances: array<ModelUniforms>;

// Skinning
#ifdef SKIN
    @group(3) @binding(0) var<storage, read> jointMatrices: array<mat4x4f>;
#endif

struct VertexInput {
    @location(0) position: vec3f,

#ifdef NORMAL
    @location(2) normal: vec3f,
#endif
#ifdef NORMAL_MAP
    @location(3) tangent: vec3f,
    @location(4) bitangent: vec3f,
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
   return out;
}


@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

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

    var color = vec4f(normal, 1.0);

#ifdef GAMMA_CORRECTION
    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));
    color = vec4f(linearColor, color.a);
#endif

    return color;
};


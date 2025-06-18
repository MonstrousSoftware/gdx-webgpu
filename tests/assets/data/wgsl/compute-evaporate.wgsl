// move agents

struct Uniforms {
    width : f32,
    height : f32,
    evaporationSpeed: f32,
    deltaTime: f32,
}


@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var inputTexture: texture_2d<f32>;
@group(0) @binding(2) var outputTexture: texture_storage_2d<rgba8unorm,write>;



@compute @workgroup_size(16, 16, 1)
fn compute(@builtin(global_invocation_id) id: vec3<u32>) {

    if(id.x < 0  || id.x >= u32(uniforms.width) || id.y < 0 || id.y >= u32(uniforms.height)){
        return;
    }

    let originalColor = textureLoad(inputTexture, id.xy, 0);
    let evaporatedColor = max(vec4f(0.0), originalColor - uniforms.deltaTime*uniforms.evaporationSpeed);

    textureStore(outputTexture, id.xy, evaporatedColor);
}

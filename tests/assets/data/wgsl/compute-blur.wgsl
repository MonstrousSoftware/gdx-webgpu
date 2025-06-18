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

    var color : vec4f = vec4f(0);

    for(var x: i32 = -1; x <= 1; x++){
        for(var y: i32 = -1; y <= 1; y++){
            let sampleX : i32 = i32(id.x) + x;
            let sampleY : i32 = i32(id.y) + y;
            if(sampleX > 0 && sampleX < i32(uniforms.width) && sampleY > 0 && sampleY < i32(uniforms.height)){
                color += textureLoad(inputTexture, vec2(sampleX, sampleY), 0);
            }
        }
    }
    color /= 9.0;
    textureStore(outputTexture, id.xy, color);
}

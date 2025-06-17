// compute shader

// hash function   schechter-sca08-turbulence
fn hash( input: u32) -> u32 {
    var state = input;
    state ^= 2747636419u;
    state *= 2654435769u;
    state ^= state >> 16;
    state *= 2654435769u;
    state ^= state >> 16;
    state *= 2654435769u;
    return state;
}

@group(0) @binding(0) var inputTexture: texture_2d<f32>;
@group(0) @binding(1) var outputTexture: texture_storage_2d<rgba8unorm,write>;

@compute @workgroup_size(8, 8)
fn compute(@builtin(global_invocation_id) id: vec3<u32>) {
    var color = textureLoad(inputTexture, id.xy, 0);
    let b : f32 = color.r;
    color.r = color.b;
    color.b = b;

    let width:u32 = 256;
    let pixelIndex : u32 =  id.y * width * id.x;
    let pseudoRandom : u32 = hash(pixelIndex + u32(color.r));
    let grey : f32 = f32(pseudoRandom) / 4294967295.0;
    color.r = grey;

    textureStore(outputTexture, id.xy, color);
}

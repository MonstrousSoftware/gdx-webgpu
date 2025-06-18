// move agents

struct Uniforms {
    width : f32,
    height : f32,
    evaporationSpeed: f32,
    deltaTime: f32,
}


struct Agent {
  position: vec2f,
  direction: f32,   // in radians
  dummy: f32
}


@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage, read_write> agents: array<Agent>;
@group(0) @binding(2) var outputTexture: texture_storage_2d<rgba8unorm,write>;

const pi : f32 = 3.14159;


@compute @workgroup_size(16, 1, 1)
fn compute(@builtin(global_invocation_id) id: vec3<u32>) {
    // id is index into agents array
    let agent : Agent = agents[id.x];

    let direction: vec2f = vec2f( cos(agent.direction), sin(agent.direction));

    var newPosition: vec2f = agent.position + direction;
    if(newPosition.x < 0  || newPosition.x >= uniforms.width || newPosition.y < 0 || newPosition.y >= uniforms.height){
        let random : u32 = hash( u32(agent.position.x + uniforms.width * agent.position.y + f32(id.x)));
        agents[id.x].direction += unitScale(random) * pi * 2.0;
        //newPosition = agent.position;
    }
    agents[id.x].position = newPosition;

    let texCoord : vec2i = vec2i(newPosition);
    let white = vec4f(1, 1, 1, 1);

    textureStore(outputTexture, texCoord, white);
}

fn unitScale( h: u32 ) -> f32 {
    return f32(h) / 4294967295.0;
}

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

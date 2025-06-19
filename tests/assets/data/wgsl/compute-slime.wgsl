// Slime Mold
// Simulate mold spores
// Following Coding Adventure: Ant and Slime Simulations by Sebastian Lague.
//

struct Uniforms {
    width : f32,
    height : f32,
    evaporationSpeed: f32,
    deltaTime: f32,
    sensorDistance: f32,
    sensorAngleSpacing: f32,
    turnSpeed: f32,
}


struct Agent {
  position: vec2f,
  direction: f32,   // in radians
  dummy: f32
}


@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage, read_write> agents: array<Agent>;
@group(0) @binding(2) var inputTexture: texture_2d<f32>;
@group(0) @binding(3) var outputTexture: texture_storage_2d<rgba8unorm,write>;

const pi : f32 = 3.14159;


@compute @workgroup_size(16, 1, 1)
fn moveAgents(@builtin(global_invocation_id) id: vec3<u32>) {
    // id is index into agents array
    let agent : Agent = agents[id.x];

    let direction: vec2f = vec2f( cos(agent.direction), sin(agent.direction));
    let random : u32 = hash( u32(agent.position.x + uniforms.width * agent.position.y + f32(id.x)));

    let weightForward: f32 = sense(agent, 0);
    let weightLeft: f32 = sense(agent, uniforms.sensorAngleSpacing * pi);
    let weightRight: f32 = sense(agent, -uniforms.sensorAngleSpacing * pi);
    let randomSteerStrength: f32 = unitScale(random);

    if(weightForward > weightLeft && weightForward > weightRight){
        // do nothing
    } else if(weightForward < weightLeft && weightForward < weightRight) {
        agents[id.x].direction += (randomSteerStrength - 0.5) * uniforms.turnSpeed * uniforms.deltaTime;
   } else if(weightRight > weightLeft){
        agents[id.x].direction -= randomSteerStrength * uniforms.turnSpeed * uniforms.deltaTime;
    } else if(weightLeft > weightRight){
        agents[id.x].direction += randomSteerStrength * uniforms.turnSpeed * uniforms.deltaTime;
    }

    var newPosition: vec2f = agent.position + direction;
    if(newPosition.x < 0  || newPosition.x >= uniforms.width || newPosition.y < 0 || newPosition.y >= uniforms.height){
        agents[id.x].direction += unitScale(random) * pi * 2.0;
        newPosition = agent.position;
    }
    agents[id.x].position = newPosition;

    let texCoord : vec2i = vec2i(newPosition);
    let white = vec4f(1, 1, 1, 1);

    textureStore(outputTexture, texCoord, white);
}

fn sense(agent: Agent, angleOffset : f32) ->f32 {
    let sensorAngle : f32 = agent.direction + angleOffset;
    let sensorDir : vec2f = vec2f(cos(sensorAngle), sin(sensorAngle));
    let sensorCentre: vec2i = vec2i(agent.position + sensorDir * uniforms.sensorDistance);

    var sum: f32 = 0;
    for(var x: i32 = -1; x <= 1; x++){
        for(var y: i32 = -1; y <= 1; y++){
            let pos: vec2i = sensorCentre + vec2i(x,y);
            sum += textureLoad(inputTexture, pos, 0).r;
        }
    }
    return sum;
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



@compute @workgroup_size(16, 16, 1)
fn evaporate(@builtin(global_invocation_id) id: vec3<u32>) {

    if(id.x < 0  || id.x >= u32(uniforms.width) || id.y < 0 || id.y >= u32(uniforms.height)){
        return;
    }

    let originalColor = textureLoad(inputTexture, id.xy, 0);
    let evaporatedColor = max(vec4f(0.0), originalColor - uniforms.deltaTime*uniforms.evaporationSpeed);

    textureStore(outputTexture, id.xy, evaporatedColor);
}


@compute @workgroup_size(16, 16, 1)
fn blur(@builtin(global_invocation_id) id: vec3<u32>) {

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

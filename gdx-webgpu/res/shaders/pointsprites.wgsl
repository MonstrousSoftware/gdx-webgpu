// particles.wgsl
// to implement PointSprite particles
//

struct Uniforms {
    projectionViewTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var texture: texture_2d<f32>;
@group(0) @binding(2) var textureSampler: sampler;


////Point particles
//attribute vec3 a_position;
//attribute vec3 a_sizeAndRotation;
//attribute vec4 a_color;
//attribute vec4 a_region;
struct VertexInput {
    @location(0) position: vec3f,
    @location(1) sizeAndRotation: vec3f,	// ignored
    @location(2) color: vec4f,
    @location(3) region: vec4f,			// ignored

};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(2) color: vec4f,
    @location(3) uv: vec2f,
};

// to be called as draw(6, numVertices)

@vertex
fn vs_main(in: VertexInput, @builtin(vertex_index) ix: u32) -> VertexOutput {
   var out: VertexOutput;

   let quadpoints = array(
    vec2f(-1, -1),
    vec2f( 1, -1),
    vec2f(-1,  1),
    vec2f(-1,  1),
    vec2f( 1, -1),
    vec2f( 1,  1),
  );

   let pos:vec2f = quadpoints[ix];
   let scale:f32 = 0.5 * in.sizeAndRotation.x; //0.5;	// fixed scale (should be per particle)
   let clipPos:vec4f =  uniforms.projectionViewTransform * vec4f(in.position, 1.0);
   let cornerPos:vec4f = vec4f(pos * scale, 0, 0);	// screen space offset per quad corner
   out.position = clipPos + cornerPos;


   out.color = in.color; //vec4f(pow(in.color.rgb, vec3f(2.2)), in.color.a);
   out.uv = pos * 0.5 + 0.5;
   return out;
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

    var color = in.color * textureSample(texture, textureSampler, in.uv);


// textures are loaded into linear space already.

//#ifdef GAMMA_CORRECTION
    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));
    color = vec4f(linearColor, color.a);
//#endif
    return color;
};

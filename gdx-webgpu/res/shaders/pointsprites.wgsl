// particles.wgsl
// to implement PointSprite particles
//

struct Uniforms {
    projectionViewTransform: mat4x4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var texture: texture_2d<f32>;
@group(0) @binding(2) var textureSampler: sampler;

struct VertexInput {
    @location(0) position: vec3f,
    @location(1) sizeAndRotation: vec3f,	// rotation ignored
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
   let scale:f32 = 0.5 * in.sizeAndRotation.x;
   let clipPos:vec4f =  uniforms.projectionViewTransform * vec4f(in.position, 1.0);
   let cornerPos:vec4f = vec4f(pos * scale, 0, 0);	// screen space offset per quad corner
   // todo take into account aspect ratio to ensure quad appears as a square on the screen
   out.position = clipPos + cornerPos;


#ifdef GAMMA_CORRECTION
   out.color = in.color;
#else
    // vertex tint needs to be linearized if output is Srgb
    // gamma tweaked to give reasonable output
   out.color = vec4f(pow(in.color.rgb, vec3f(1.6)),in.color.a);
#endif
   out.uv = pos * 0.5 + 0.5;
   return out;
}

@fragment
fn fs_main(in : VertexOutput) -> @location(0) vec4f {

    // the texture should be a texture with premultiplied alpha and is taken as linear
    // in.color is in the color space of the render output (Rgb or Srgb)
    var texel = textureSample(texture, textureSampler, in.uv);
    var color =  in.color * texel;

    return color;
};

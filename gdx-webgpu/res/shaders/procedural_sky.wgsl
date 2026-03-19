// Procedural clear-sky shader
//
// Renders an atmospheric sky gradient with a sun disc.
// Uses the same full-screen triangle technique as skybox.wgsl but no cubemap.

struct Uniforms {
    inverseProjectionViewMatrix: mat4x4f,
    sunDirection: vec4f,        // xyz = normalized direction TO the sun, w = unused
    skyColorZenith: vec4f,      // rgb = zenith color (linear), a = unused
    skyColorHorizon: vec4f,     // rgb = horizon color (linear), a = unused
    groundColor: vec4f,         // rgb = ground hemisphere color (linear), a = unused
    sunParams: vec4f,           // x = sun angular radius (radians), y = sun intensity, z = unused, w = unused
};

@group(0) @binding(0) var<uniform> u: Uniforms;

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) pos: vec4f,
};

@vertex
fn vs_main(@builtin(vertex_index) index: u32) -> VertexOutput {
    let pos = array(
        vec2f(-1, 3),
        vec2f(-1,-1),
        vec2f( 3,-1),
    );

    var out: VertexOutput;
    out.position = vec4f(pos[index], 1, 1);
    out.pos = out.position;
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4f {
    let t = u.inverseProjectionViewMatrix * in.pos;
    let dir = normalize(t.xyz / t.w) * vec3f(1, 1, -1);

    // Elevation: 0 at horizon, +1 up, -1 down
    let elevation = dir.y;

    // Sky gradient: horizon → zenith based on elevation (above horizon only)
    let skyFactor = clamp(elevation, 0.0, 1.0);
    // Use a power curve so the horizon band is wider
    let gradientT = pow(skyFactor, 0.6);
    let skyColor = mix(u.skyColorHorizon.rgb, u.skyColorZenith.rgb, gradientT);

    // Ground hemisphere: below horizon, blend to ground color
    let groundFactor = clamp(-elevation, 0.0, 1.0);
    let groundT = pow(groundFactor, 0.4);
    var color = mix(skyColor, u.groundColor.rgb, groundT);

    // Sun disc — apply the same Z-flip to sunDirection as we do to the view dir
    let sunDir = normalize(u.sunDirection.xyz) * vec3f(1, 1, -1);
    let sunAngle = acos(clamp(dot(dir, sunDir), -1.0, 1.0));
    let sunRadius = u.sunParams.x;
    let sunIntensity = u.sunParams.y;

    // Hard disc with soft edge (anti-aliased over ~0.2 degrees)
    let edgeSoftness = 0.003;
    let sunDisc = 1.0 - smoothstep(sunRadius - edgeSoftness, sunRadius + edgeSoftness, sunAngle);

    // Sun glow around the disc
    let glowFactor = exp(-sunAngle * 4.0) * 0.3 * sunIntensity;

    // Sun halo: warm tint near the sun, strongest at horizon
    let haloFactor = exp(-sunAngle * 1.5) * 0.15 * (1.0 - skyFactor * 0.7);
    let sunTint = vec3f(1.0, 0.9, 0.7);

    color = color + sunTint * haloFactor;
    color = color + vec3f(1.0, 0.95, 0.85) * sunDisc * sunIntensity;
    color = color + vec3f(1.0, 0.95, 0.85) * glowFactor;

#ifdef GAMMA_CORRECTION
    color = pow(color, vec3f(1.0 / 2.2));
#endif

    return vec4f(color, 1.0);
}


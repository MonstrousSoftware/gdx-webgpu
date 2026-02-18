package com.monstrous.gdx.webgpu.graphics.g2d;

import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;

/**
 * Custom shader program for sprite highlighting. Renders an outline around non-transparent pixels. The highlight
 * follows the actual shape of the sprite (respecting alpha channel), not the texture bounds.
 *
 * The thickness is set at creation time and cannot be changed afterwards without creating a new shader instance.
 */
public class WgHighlightShaderProgram extends WgShaderProgram {

    private final float thickness;
    private final float alphaThreshold;
    private final float smoothing;

    /**
     * Create a highlight shader with the specified outline thickness. Uses default alpha threshold of 0.1 and no
     * smoothing.
     * 
     * @param thickness outline width in pixels
     */
    public WgHighlightShaderProgram(float thickness) {
        this(thickness, 0.1f, 0.0f);
    }

    /**
     * Create a highlight shader with the specified outline thickness and alpha threshold. Uses no smoothing by default.
     * 
     * @param thickness outline width in pixels
     * @param alphaThreshold alpha threshold (0.0 to 1.0) - pixels with alpha below this are considered transparent.
     *            Lower values (e.g., 0.01) create tighter outlines, higher values (e.g., 0.3) include semi-transparent
     *            edges.
     */
    public WgHighlightShaderProgram(float thickness, float alphaThreshold) {
        this(thickness, alphaThreshold, 0.0f);
    }

    /**
     * Create a highlight shader with the specified outline thickness, alpha threshold, and smoothing.
     * 
     * @param thickness outline width in pixels
     * @param alphaThreshold alpha threshold (0.0 to 1.0) - pixels with alpha below this are considered transparent.
     * @param smoothing smoothing amount (0.0 to 3.0) - higher values create smoother, anti-aliased edges. 0.0 = hard
     *            edges (pixelated), 1.0-2.0 = smooth edges (recommended), 3.0+ = very soft edges.
     */
    public WgHighlightShaderProgram(float thickness, float alphaThreshold, float smoothing) {
        super("highlight shader", generateShaderSource(thickness, alphaThreshold, smoothing), getShaderPrefix());
        this.thickness = thickness;
        this.alphaThreshold = alphaThreshold;
        this.smoothing = smoothing;
    }

    /**
     * Generate the shader prefix with required defines for SpriteBatch compatibility
     */
    private static String getShaderPrefix() {
        // SpriteBatch always has TEXTURE_COORDINATE and COLOR attributes
        return "#define TEXTURE_COORDINATE\n#define COLOR\n";
    }

    /**
     * Get the outline thickness this shader was created with.
     * 
     * @return outline width in pixels
     */
    public float getThickness() {
        return thickness;
    }

    /**
     * Get the alpha threshold this shader was created with.
     * 
     * @return alpha threshold value (0.0 to 1.0)
     */
    public float getAlphaThreshold() {
        return alphaThreshold;
    }

    /**
     * Get the smoothing amount this shader was created with.
     * 
     * @return smoothing amount (0.0+)
     */
    public float getSmoothing() {
        return smoothing;
    }

    private static String generateShaderSource(float thickness, float alphaThreshold, float smoothing) {
        // Direct conversion from working OpenGL shader
        return "// Outline shader - converted from OpenGL\n" + "\n" + "struct Uniforms {\n"
                + "    projectionViewTransform: mat4x4<f32>,\n" + "}\n" + "\n"
                + "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n"
                + "@group(0) @binding(1) var texture: texture_2d<f32>;\n"
                + "@group(0) @binding(2) var textureSampler: sampler;\n" + "\n" + "struct VertexInput {\n"
                + "    @location(0) position: vec2<f32>,\n" + "#ifdef TEXTURE_COORDINATE\n"
                + "    @location(1) uv: vec2<f32>,\n" + "#endif\n" + "#ifdef COLOR\n"
                + "    @location(5) color: vec4<f32>,\n" + "#endif\n" + "}\n" + "\n" + "struct VertexOutput {\n"
                + "    @builtin(position) position: vec4<f32>,\n" + "#ifdef TEXTURE_COORDINATE\n"
                + "    @location(0) uv: vec2<f32>,\n" + "#endif\n" + "    @location(1) color: vec4<f32>,\n" + "}\n"
                + "\n" + "@vertex\n" + "fn vs_main(in: VertexInput) -> VertexOutput {\n"
                + "    var out: VertexOutput;\n"
                + "    out.position = uniforms.projectionViewTransform * vec4<f32>(in.position, 0.0, 1.0);\n"
                + "#ifdef TEXTURE_COORDINATE\n" + "    out.uv = in.uv;\n" + "#endif\n" + "#ifdef COLOR\n"
                + "    // v_color.a = v_color.a * (255.0/254.0) from OpenGL\n"
                + "    var color = vec4<f32>(pow(in.color.rgb, vec3<f32>(2.2)), in.color.a);\n"
                + "    color.a = color.a * (255.0 / 254.0);\n" + "    out.color = color;\n" + "#else\n"
                + "    out.color = vec4<f32>(1.0, 1.0, 1.0, 1.0);\n" + "#endif\n" + "    return out;\n" + "}\n" + "\n"
                + "// Calculate distance to nearest opaque/transparent boundary for smoothing\n"
                + "fn getDistanceToBoundary(uv: vec2<f32>, texture_pixel_size: vec2<f32>, width: f32, alphaThreshold: f32, isOpaque: bool) -> f32 {\n"
                + "    var minDist = width + 1.0; // Start with max distance\n" + "    \n"
                + "    // Check in 8 directions\n"
                + "    let angles = array<f32, 8>(0.0, 0.785398, 1.5708, 2.35619, 3.14159, 3.92699, 4.71239, 5.49779);\n"
                + "    \n" + "    for (var i = 0; i < 8; i++) {\n" + "        let angle = angles[i];\n"
                + "        let dir = vec2<f32>(cos(angle), sin(angle));\n" + "        \n"
                + "        // Binary search for edge distance\n"
                + "        for (var dist = 0.5; dist <= width; dist += 0.5) {\n"
                + "            let checkUV = uv + dir * dist * texture_pixel_size;\n"
                + "            let outOfBounds = checkUV.x < 0.0 || checkUV.x > 1.0 || checkUV.y < 0.0 || checkUV.y > 1.0;\n"
                + "            \n" + "            var alpha: f32;\n" + "            if (outOfBounds) {\n"
                + "                alpha = 0.0;\n" + "            } else {\n"
                + "                alpha = textureSampleLevel(texture, textureSampler, checkUV, 0.0).a;\n"
                + "            }\n" + "            \n" + "            let checkIsOpaque = alpha > alphaThreshold;\n"
                + "            \n" + "            // Found boundary (transition between opaque and transparent)\n"
                + "            if (checkIsOpaque != isOpaque || outOfBounds) {\n"
                + "                minDist = min(minDist, dist);\n" + "                break;\n" + "            }\n"
                + "        }\n" + "    }\n" + "    \n" + "    return minDist;\n" + "}\n" + "\n"
                + "// Direct conversion from OpenGL hasContraryNeighbour\n"
                + "fn hasContraryNeighbour(uv: vec2<f32>, texture_pixel_size: vec2<f32>) -> bool {\n"
                + "    let inside = false; // u_inside = false for outside outline\n" + "    let width = " + thickness
                + "; // u_width\n" + "    let pattern = 1; // pattern 1 = circular\n" + "    let alphaThreshold = "
                + alphaThreshold + "; // Configurable alpha threshold\n" + "    \n"
                + "    for (var i = -ceil(width); i <= ceil(width); i += 1.0) {\n"
                + "        // float x = abs(i) > width ? width * sign(i) : i;\n"
                + "        let x = select(i, width * sign(i), abs(i) > width);\n" + "        \n"
                + "        // Calculate offset based on pattern\n" + "        var offset: f32;\n"
                + "        if (pattern == 1) {\n"
                + "            // offset = floor(sqrt(pow(width + 0.5, 2) - x * x));\n"
                + "            offset = floor(sqrt(pow(width + 0.5, 2.0) - x * x));\n" + "        } else {\n"
                + "            offset = width;\n" + "        }\n" + "        \n"
                + "        for (var j = -ceil(offset); j <= ceil(offset); j += 1.0) {\n"
                + "            // float y = abs(j) > offset ? offset * sign(j) : j;\n"
                + "            let y = select(j, offset * sign(j), abs(j) > offset);\n" + "            \n"
                + "            // vec2 xy = uv + texture_pixel_size * vec2(x, y);\n"
                + "            let xy = uv + texture_pixel_size * vec2<f32>(x, y);\n" + "            \n"
                + "            // OpenGL: if ((xy != clamp(xy, vec2(0.0), vec2(1.0)) || texture2D(texture, xy).a == 0.0) == inside)\n"
                + "            let isOutOfBounds = xy.x < 0.0 || xy.x > 1.0 || xy.y < 0.0 || xy.y > 1.0;\n"
                + "            \n" + "            var alpha: f32;\n" + "            if (isOutOfBounds) {\n"
                + "                alpha = 0.0;\n" + "            } else {\n"
                + "                alpha = textureSampleLevel(texture, textureSampler, xy, 0.0).a;\n"
                + "            }\n" + "            \n"
                + "            // Check for transparent or semi-transparent pixels (< threshold instead of == 0.0)\n"
                + "            if ((isOutOfBounds || alpha < alphaThreshold) == inside) {\n"
                + "                return true;\n" + "            }\n" + "        }\n" + "    }\n"
                + "    return false;\n" + "}\n" + "\n"
                + "// Check if we're at an edge with no transparent space and should draw inward\n"
                + "fn shouldDrawInward(uv: vec2<f32>, texture_pixel_size: vec2<f32>, width: f32, alphaThreshold: f32) -> bool {\n"
                + "    // Check in all directions if we hit a boundary within stroke width distance\n"
                + "    for (var dist = 1.0; dist <= width; dist += 1.0) {\n"
                + "        // Check 4 cardinal directions\n" + "        let directions = array<vec2<f32>, 4>(\n"
                + "            vec2<f32>(0.0, -1.0),  // down\n" + "            vec2<f32>(0.0, 1.0),   // up\n"
                + "            vec2<f32>(-1.0, 0.0),  // left\n" + "            vec2<f32>(1.0, 0.0)    // right\n"
                + "        );\n" + "        \n" + "        for (var i = 0; i < 4; i++) {\n"
                + "            let checkUV = uv + directions[i] * dist * texture_pixel_size;\n"
                + "            let outOfBounds = checkUV.x < 0.0 || checkUV.x > 1.0 || checkUV.y < 0.0 || checkUV.y > 1.0;\n"
                + "            \n" + "            if (outOfBounds) {\n"
                + "                // Hit boundary - need to compensate by drawing inward\n"
                + "                return true;\n" + "            }\n" + "            \n"
                + "            let alpha = textureSampleLevel(texture, textureSampler, checkUV, 0.0).a;\n"
                + "            if (alpha < alphaThreshold) {\n"
                + "                // Found transparent space - can draw normally\n" + "                return false;\n"
                + "            }\n" + "        }\n" + "    }\n" + "    \n"
                + "    return false; // No boundary hit within stroke width\n" + "}\n" + "\n"
                + "// Check if pixel is within 'width' pixels from the sprite edge (inward)\n"
                + "fn isWithinInwardStroke(uv: vec2<f32>, texture_pixel_size: vec2<f32>, width: f32, alphaThreshold: f32) -> bool {\n"
                + "    // Sample in all directions - if we find transparent within 'width' distance, we're near edge\n"
                + "    for (var dist = 1.0; dist <= width; dist += 1.0) {\n"
                + "        // Check 8 directions for better coverage\n"
                + "        let angles = array<f32, 8>(0.0, 0.785398, 1.5708, 2.35619, 3.14159, 3.92699, 4.71239, 5.49779);\n"
                + "        \n" + "        for (var i = 0; i < 8; i++) {\n" + "            let angle = angles[i];\n"
                + "            let dir = vec2<f32>(cos(angle), sin(angle));\n"
                + "            let checkUV = uv + dir * dist * texture_pixel_size;\n" + "            \n"
                + "            let outOfBounds = checkUV.x < 0.0 || checkUV.x > 1.0 || checkUV.y < 0.0 || checkUV.y > 1.0;\n"
                + "            \n" + "            if (outOfBounds) {\n"
                + "                return true; // Near texture edge\n" + "            }\n" + "            \n"
                + "            let alpha = textureSampleLevel(texture, textureSampler, checkUV, 0.0).a;\n"
                + "            if (alpha < alphaThreshold) {\n"
                + "                return true; // Near transparent area (sprite edge)\n" + "            }\n"
                + "        }\n" + "    }\n" + "    \n" + "    return false; // Interior pixel\n" + "}\n" + "\n"
                + "@fragment\n" + "fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {\n"
                + "#ifdef TEXTURE_COORDINATE\n" + "    let texSize = vec2<f32>(textureDimensions(texture));\n"
                + "    let texture_pixel_size = 1.0 / texSize;\n" + "    \n" + "    let alphaThreshold = "
                + alphaThreshold + ";\n" + "    let width = " + thickness + ";\n" + "    let smoothing = " + smoothing
                + ";\n" + "    \n" + "    let centerTexel = textureSampleLevel(texture, textureSampler, in.uv, 0.0);\n"
                + "    let centerIsOpaque = centerTexel.a > alphaThreshold;\n" + "    \n"
                + "    // Calculate distance to nearest ALPHA TRANSITION edge (not texture bounds)\n"
                + "    var distanceToEdge = width + 1.0; // Start with max\n"
                + "    var foundActualEdge = false; // Track if we found a real alpha transition\n" + "    \n"
                + "    // Check in 8 directions to find nearest edge\n"
                + "    let angles = array<f32, 8>(0.0, 0.785398, 1.5708, 2.35619, 3.14159, 3.92699, 4.71239, 5.49779);\n"
                + "    \n" + "    for (var i = 0; i < 8; i++) {\n" + "        let angle = angles[i];\n"
                + "        let dir = vec2<f32>(cos(angle), sin(angle));\n" + "        \n"
                + "        // Search for edge distance in this direction\n"
                + "        for (var dist = 0.5; dist <= width + 0.5; dist += 0.5) {\n"
                + "            let checkUV = in.uv + dir * dist * texture_pixel_size;\n"
                + "            let outOfBounds = checkUV.x < 0.0 || checkUV.x > 1.0 || checkUV.y < 0.0 || checkUV.y > 1.0;\n"
                + "            \n" + "            if (outOfBounds) {\n"
                + "                // Hit texture bounds - only count as edge if center pixel is opaque\n"
                + "                // (sprite extends to texture edge, no padding)\n"
                + "                if (centerIsOpaque) {\n"
                + "                    distanceToEdge = min(distanceToEdge, dist);\n"
                + "                    foundActualEdge = true;\n" + "                }\n"
                + "                break; // Stop searching in this direction\n" + "            }\n" + "            \n"
                + "            let alpha = textureSampleLevel(texture, textureSampler, checkUV, 0.0).a;\n"
                + "            let checkIsOpaque = alpha > alphaThreshold;\n" + "            \n"
                + "            // Found alpha transition edge (opacity changed)\n"
                + "            if (checkIsOpaque != centerIsOpaque) {\n"
                + "                distanceToEdge = min(distanceToEdge, dist);\n"
                + "                foundActualEdge = true;\n" + "                break;\n" + "            }\n"
                + "        }\n" + "    }\n" + "    \n"
                + "    // Only draw if we found an actual edge and we're within stroke width\n"
                + "    if (foundActualEdge && distanceToEdge <= width) {\n"
                + "        // We're within stroke width of a real sprite edge\n" + "        var alpha = 1.0;\n"
                + "        \n" + "        if (smoothing > 0.0) {\n"
                + "            // Apply anti-aliasing based on distance from edge\n"
                + "            // Solid from 0 to (width - smoothing), fade from (width - smoothing) to width\n"
                + "            let fadeStart = max(0.0, width - smoothing);\n" + "            let fadeEnd = width;\n"
                + "            \n" + "            // Use smoothstep for smooth interpolation\n"
                + "            alpha = 1.0 - smoothstep(fadeStart, fadeEnd, distanceToEdge);\n" + "        }\n"
                + "        \n" + "        if (alpha > 0.01) {\n"
                + "            return vec4<f32>(in.color.rgb, alpha);\n" + "        }\n" + "    }\n" + "    \n"
                + "    return vec4<f32>(0.0, 0.0, 0.0, 0.0);\n" + "#else\n" + "    return in.color;\n" + "#endif\n"
                + "}\n";
    }
}

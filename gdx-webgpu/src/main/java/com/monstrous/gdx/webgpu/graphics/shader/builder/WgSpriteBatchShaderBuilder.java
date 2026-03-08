/*******************************************************************************
 * Copyright 2025 Monstrous Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics.shader.builder;

import com.monstrous.gdx.webgpu.graphics.ShaderPrefix;

/**
 * Pre-defined {@link WgShaderChunk}s and factory methods that reproduce the built-in sprite-batch
 * shader, equivalent to {@code spritebatch.wgsl}.
 *
 * <p>Every meaningful line has its own named chunk constant so any single piece can be
 * replaced without touching the rest:</p>
 *
 * <pre>{@code
 * // Change only the gamma-correction step
 * config.shaderSource = SpriteBatchShaderBuilder.defaultSpriteBatch()
 *     .replaceChunk(SpriteBatchShaderBuilder.FS_GAMMA,
 *                   new ShaderChunk(SpriteBatchShaderBuilder.FS_GAMMA, "// no gamma\n"))
 *     .build();
 * }</pre>
 *
 * <h2>MRT example — 2 targeted operations, nothing duplicated</h2>
 * <pre>{@code
 * String source = SpriteBatchShaderBuilder.defaultSpriteBatch()
 *     // 1. Insert MRT output struct before the FS signature
 *     .insertBefore(SpriteBatchShaderBuilder.FS_SIGNATURE,
 *                   new ShaderChunk("mrt_output_struct",
 *                       "struct FragmentOutput {\n"
 *                     + "    @location(0) color0 : vec4f,\n"
 *                     + "    @location(1) color1 : vec4f,\n"
 *                     + "};\n"))
 *     // 2. Replace signature to return FragmentOutput
 *     .replaceChunk(SpriteBatchShaderBuilder.FS_SIGNATURE,
 *                   new ShaderChunk(SpriteBatchShaderBuilder.FS_SIGNATURE,
 *                       "@fragment\nfn fs_main(in : VertexOutput) -> FragmentOutput {\n"))
 *     // 3. Replace return to write both outputs
 *     .replaceChunk(SpriteBatchShaderBuilder.FS_RETURN,
 *                   new ShaderChunk(SpriteBatchShaderBuilder.FS_RETURN,
 *                       "    var o: FragmentOutput;\n"
 *                     + "    o.color0 = vec4f(color.r, 0.0, 0.0, color.a);\n"
 *                     + "    o.color1 = vec4f(0.0, color.g, 0.0, color.a);\n"
 *                     + "    return o;\n"
 *                     + "};\n"))
 *     .build();
 * }</pre>
 */
public final class WgSpriteBatchShaderBuilder {

    // =========================================================================
    // CHUNK NAME CONSTANTS
    // =========================================================================

    public static final String UNIFORMS_STRUCT  = "sprite_uniforms_struct";
    public static final String BINDINGS         = "sprite_bindings";
    public static final String VERTEX_INPUT     = "sprite_vertex_input";
    public static final String VERTEX_OUTPUT    = "sprite_vertex_output";
    public static final String VS_OPEN          = "sprite_vs_open";
    public static final String VS_POS           = "sprite_vs_pos";
    public static final String VS_UV            = "sprite_vs_uv";
    public static final String VS_COLOR         = "sprite_vs_color";
    public static final String VS_RETURN        = "sprite_vs_return";
    public static final String FS_SIGNATURE     = "sprite_fs_signature";
    public static final String FS_BASE_COLOR    = "sprite_fs_base_color";
    public static final String FS_GAMMA         = "sprite_fs_gamma";
    public static final String FS_RETURN        = "sprite_fs_return";

    // =========================================================================
    // CHUNK SOURCES
    // =========================================================================

    public static final WgShaderChunk UNIFORMS_STRUCT_CHUNK =
        WgShaderChunk.struct(UNIFORMS_STRUCT, "Uniforms")
            .field("projectionViewTransform", "    projectionViewTransform: mat4x4f,\n")
            .build();

    public static final WgShaderChunk BINDINGS_CHUNK =
        WgShaderChunk.block(BINDINGS, "// sprite bindings\n", "")
            .line("uniforms",        "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n")
            .line("texture",         "@group(0) @binding(1) var texture: texture_2d<f32>;\n")
            .line("texture_sampler", "@group(0) @binding(2) var textureSampler: sampler;\n")
            .build();

    public static final WgShaderChunk VERTEX_INPUT_CHUNK =
        WgShaderChunk.struct(VERTEX_INPUT, "VertexInput")
            .field("position", "    @location(0) position: vec2f,\n")
            .sub(WgShaderChunk.block("uv_block", "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
                .field("uv", "    @location(1) uv: vec2f,\n").build())
            .sub(WgShaderChunk.block("color_block", "#ifdef COLOR\n", "#endif\n")
                .field("color", "    @location(5) color: vec4f,\n").build())
            .build();

    public static final WgShaderChunk VERTEX_OUTPUT_CHUNK =
        WgShaderChunk.struct(VERTEX_OUTPUT, "VertexOutput")
            .field("position", "    @builtin(position) position: vec4f,\n")
            .sub(WgShaderChunk.block("uv_block", "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
                .field("uv", "    @location(0) uv : vec2f,\n").build())
            .field("color", "    @location(1) color: vec4f,\n")
            .build();

    public static final WgShaderChunk VS_OPEN_CHUNK =
        WgShaderChunk.block(VS_OPEN,
            "@vertex\nfn vs_main(in: VertexInput) -> VertexOutput {\n", "")
            .line("out_decl", "   var out: VertexOutput;\n")
            .build();

    public static final WgShaderChunk VS_POS_CHUNK = new WgShaderChunk(VS_POS,
            "   out.position = uniforms.projectionViewTransform * vec4f(in.position, 0.0, 1.0);\n");

    public static final WgShaderChunk VS_UV_CHUNK =
        WgShaderChunk.block(VS_UV, "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
            .line("write_uv", "   out.uv = in.uv;\n")
            .build();

    public static final WgShaderChunk VS_COLOR_CHUNK =
        WgShaderChunk.block(VS_COLOR, "", "")
            .sub(WgShaderChunk.block("color_read", "#ifdef COLOR\n", "#endif\n")
                .line("gamma_expand", "   let color:vec4f = vec4f(pow(in.color.rgb, vec3f(2.2)), in.color.a);\n")
                .sub(WgShaderChunk.block("else_block", "#else\n", "")
                    .line("white", "   let color:vec4f = vec4f(1,1,1,1);\n").build())
                .build())
            .line("write_out", "   out.color = color;\n")
            .build();

    public static final WgShaderChunk VS_RETURN_CHUNK = new WgShaderChunk(VS_RETURN,
            "   return out;\n}\n");

    /** Replace this to change the return type (e.g. for MRT). */
    public static final WgShaderChunk FS_SIGNATURE_CHUNK = new WgShaderChunk(FS_SIGNATURE,
            "@fragment\nfn fs_main(in : VertexOutput) -> @location(0) vec4f {\n");

    public static final WgShaderChunk FS_BASE_COLOR_CHUNK =
        WgShaderChunk.block(FS_BASE_COLOR, "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
            .line("sample_color", "    var color = in.color * textureSample(texture, textureSampler, in.uv);\n")
            .sub(WgShaderChunk.block("else_block", "#else\n", "")
                .line("pass_color", "    var color = in.color;\n").build())
            .build();

    public static final WgShaderChunk FS_GAMMA_CHUNK =
        WgShaderChunk.block(FS_GAMMA, "#ifdef GAMMA_CORRECTION\n", "#endif\n")
            .line("linear_color", "    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));\n")
            .line("apply_gamma",  "    color = vec4f(linearColor, color.a);\n")
            .build();

    /** Replace this (together with {@link #FS_SIGNATURE}) to implement MRT. */
    public static final WgShaderChunk FS_RETURN_CHUNK = new WgShaderChunk(FS_RETURN,
            "    return color;\n};\n");

    // =========================================================================
    // FACTORY METHOD
    // =========================================================================

    /**
     * Create a {@link WgShaderBuilder} pre-loaded with all standard sprite-batch chunks in the
     * correct WGSL declaration order, equivalent to {@code spritebatch.wgsl}.
     */
    public static WgShaderBuilder defaultSpriteBatch() {
        WgShaderBuilder builder = new WgShaderBuilder()
            // A sprite batch always has texture coordinates and packed colour.
            // Define them here so the #ifdef guards in the chunks resolve correctly
            // when the shader is compiled directly as a WgShaderProgram (bypassing
            // the normal ShaderPrefix.buildPrefix() path).
            .define("TEXTURE_COORDINATE")
            .define("COLOR");

        // Mirror what ShaderPrefix.buildPrefix() does: add GAMMA_CORRECTION when
        // the surface format is not already linear.
        if (!ShaderPrefix.hasLinearOutput()) {
            builder.define("GAMMA_CORRECTION");
        }

        return builder
            .addChunk(UNIFORMS_STRUCT_CHUNK)
            .addChunk(BINDINGS_CHUNK)
            .addChunk(VERTEX_INPUT_CHUNK)
            .addChunk(VERTEX_OUTPUT_CHUNK)
            .addChunk(VS_OPEN_CHUNK)
            .addChunk(VS_POS_CHUNK)
            .addChunk(VS_UV_CHUNK)
            .addChunk(VS_COLOR_CHUNK)
            .addChunk(VS_RETURN_CHUNK)
            .addChunk(FS_SIGNATURE_CHUNK)
            .addChunk(FS_BASE_COLOR_CHUNK)
            .addChunk(FS_GAMMA_CHUNK)
            .addChunk(FS_RETURN_CHUNK);
    }

    private WgSpriteBatchShaderBuilder() {}
}


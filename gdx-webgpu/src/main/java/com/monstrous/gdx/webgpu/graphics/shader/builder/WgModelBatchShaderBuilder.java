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

/**
 * Pre-defined {@link WgShaderChunk}s and factory methods that reproduce the built-in shaders.
 *
 * <h2>Three levels of customisation — all via {@link WgShaderBuilder}</h2>
 *
 * <h3>1. Replace a whole section</h3>
 * <pre>{@code
 * config.shaderSource = ModelBatchShaderBuilder.defaultModelBatch()
 *     .replaceChunk(ModelBatchShaderBuilder.STRUCT_DIRECTIONAL_LIGHT,
 *                   new ShaderChunk(ModelBatchShaderBuilder.STRUCT_DIRECTIONAL_LIGHT, myWgsl))
 *     .build();
 * }</pre>
 *
 * <h3>2. Replace a single line / small block</h3>
 * Every meaningful line has its own chunk constant — swap just that line:
 * <pre>{@code
 * config.shaderSource = ModelBatchShaderBuilder.defaultModelBatch()
 *     .replaceChunk(ModelBatchShaderBuilder.VS_WORLD_POS_NO_SKIN,
 *                   new ShaderChunk(ModelBatchShaderBuilder.VS_WORLD_POS_NO_SKIN,
 *                       "   let worldPosition = myTransform(pos);\n#endif\n"))
 *     .build();
 * }</pre>
 *
 * <h3>3. Insert extra chunks before/after any existing chunk</h3>
 * <pre>{@code
 * config.shaderSource = ModelBatchShaderBuilder.defaultModelBatch()
 *     .insertAfter(ModelBatchShaderBuilder.VS_CLIP_POS,
 *                  new ShaderChunk("my_extra", "   out.myField = out.worldPos.x;\n"))
 *     .build();
 * }</pre>
 *
 * <h2>MRT example — 3 targeted operations, nothing duplicated</h2>
 * <pre>{@code
 * config.shaderSource = ModelBatchShaderBuilder.defaultModelBatch()
 *     .insertBefore(ModelBatchShaderBuilder.FS_SIGNATURE,
 *                   new ShaderChunk("mrt_struct",
 *                       "struct FragmentOutput {\n"
 *                     + "    @location(0) color : vec4f,\n"
 *                     + "    @location(1) normal : vec4f,\n"
 *                     + "};\n"))
 *     .replaceChunk(ModelBatchShaderBuilder.FS_SIGNATURE,
 *                   new ShaderChunk(ModelBatchShaderBuilder.FS_SIGNATURE,
 *                       "@fragment\nfn fs_main(in : VertexOutput) -> FragmentOutput {\n"))
 *     .replaceChunk(ModelBatchShaderBuilder.FS_RETURN,
 *                   new ShaderChunk(ModelBatchShaderBuilder.FS_RETURN,
 *                       "    var o: FragmentOutput;\n"
 *                     + "    o.color = color;\n"
 *                     + "    #ifdef LIGHTING\n"
 *                     + "       o.normal = vec4f(normal * 0.5 + 0.5, 1.0);\n"
 *                     + "    #else\n"
 *                     + "       o.normal = vec4f(normalize(in.normal) * 0.5 + 0.5, 1.0);\n"
 *                     + "    #endif\n"
 *                     + "    return o;\n"
 *                     + "};\n"))
 *     .build();
 * }</pre>
 */
public final class WgModelBatchShaderBuilder {

    // =========================================================================
    // CHUNK NAME CONSTANTS
    // =========================================================================

    // --- structs ---
    public static final String HEADER                       = "header";
    public static final String STRUCT_DIRECTIONAL_LIGHT     = "struct_directional_light";
    public static final String STRUCT_POINT_LIGHT           = "struct_point_light";
    public static final String STRUCT_FRAME_UNIFORMS        = "struct_frame_uniforms";
    public static final String STRUCT_MODEL_UNIFORMS        = "struct_model_uniforms";
    public static final String STRUCT_MATERIAL_UNIFORMS     = "struct_material_uniforms";

    // --- bindings ---
    public static final String BINDINGS_FRAME               = "bindings_frame";
    public static final String BINDINGS_SHADOW_MAP          = "bindings_shadow_map";
    public static final String BINDINGS_ENVIRONMENT_MAP     = "bindings_environment_map";
    public static final String BINDINGS_IBL                 = "bindings_ibl";
    public static final String BINDINGS_MATERIAL            = "bindings_material";
    public static final String BINDINGS_INSTANCING          = "bindings_instancing";
    public static final String BINDINGS_SKIN                = "bindings_skin";

    // --- IO structs ---
    public static final String VERTEX_INPUT                 = "vertex_input";
    public static final String VERTEX_OUTPUT                = "vertex_output";
    public static final String PI                           = "pi";

    // --- vertex shader (one chunk per logical step) ---
    public static final String VS_OPEN                      = "vs_open";
    public static final String VS_POS_INIT                  = "vs_pos_init";
    public static final String VS_MORPH                     = "vs_morph";
    public static final String VS_NORMAL_ATTR               = "vs_normal_attr";
    public static final String VS_SKIN_MATRIX               = "vs_skin_matrix";
    public static final String VS_WORLD_POS_SKINNED         = "vs_world_pos_skinned";
    public static final String VS_WORLD_POS_NO_SKIN         = "vs_world_pos_no_skin";
    public static final String VS_CLIP_POS                  = "vs_clip_pos";
    public static final String VS_UV                        = "vs_uv";
    public static final String VS_COLOR                     = "vs_color";
    public static final String VS_NORMAL_TRANSFORM          = "vs_normal_transform";
    public static final String VS_NORMAL_MAP_PASSTHROUGH    = "vs_normal_map_passthrough";
    public static final String VS_FOG                       = "vs_fog";
    public static final String VS_SHADOW                    = "vs_shadow";
    public static final String VS_RETURN                    = "vs_return";

    // --- fragment shader (one chunk per logical step) ---
    public static final String FS_SIGNATURE                 = "fs_signature";
    public static final String FS_BASE_COLOR                = "fs_base_color";
    public static final String FS_VISIBILITY                = "fs_visibility";
    public static final String FS_LIGHTING_OPEN             = "fs_lighting_open";
    public static final String FS_NORMAL_MAP                = "fs_normal_map";
    public static final String FS_NORMAL_NO_MAP             = "fs_normal_no_map";
    public static final String FS_PBR_INPUTS                = "fs_pbr_inputs";
    public static final String FS_AMBIENT                   = "fs_ambient";
    public static final String FS_DIR_LIGHTS                = "fs_dir_lights";
    public static final String FS_POINT_LIGHTS              = "fs_point_lights";
    public static final String FS_LIT_COLOR                 = "fs_lit_color";
    public static final String FS_ENVIRONMENT_MAP           = "fs_environment_map";
    public static final String FS_LIGHTING_CLOSE            = "fs_lighting_close";
    public static final String FS_EMISSIVE                  = "fs_emissive";
    public static final String FS_FOG                       = "fs_fog";
    public static final String FS_GAMMA                     = "fs_gamma";
    public static final String FS_RETURN                    = "fs_return";

    // --- helper functions ---
    public static final String SHADOW                       = "shadow";
    public static final String PBR                         = "pbr";
    public static final String IBL                         = "ibl";

    // =========================================================================
    // CHUNK SOURCES — structs
    // =========================================================================

    public static final WgShaderChunk HEADER_CHUNK = new WgShaderChunk(HEADER,
            "// basic ModelBatch shader — Copyright 2025 Monstrous Software.\n"
          + "// Uber shader: conditional compilation via #define values from ShaderPrefix.\n");

    public static final WgShaderChunk STRUCT_DIRECTIONAL_LIGHT_CHUNK =
        WgShaderChunk.struct(STRUCT_DIRECTIONAL_LIGHT, "DirectionalLight")
            .field("color",     "    color: vec4f,\n")
            .field("direction", "    direction: vec4f\n")
            .build();

    public static final WgShaderChunk STRUCT_POINT_LIGHT_CHUNK =
        WgShaderChunk.struct(STRUCT_POINT_LIGHT, "PointLight")
            .field("color",     "    color: vec4f,\n")
            .field("position",  "    position: vec4f,\n")
            .field("intensity", "    intensity: f32\n")
            .build();

    public static final WgShaderChunk STRUCT_FRAME_UNIFORMS_CHUNK =
        WgShaderChunk.struct(STRUCT_FRAME_UNIFORMS, "FrameUniforms")
            .field("projectionViewTransform", "    projectionViewTransform: mat4x4f,\n")
            .field("shadowProjViewTransform", "    shadowProjViewTransform: mat4x4f,\n")
            .sub(WgShaderChunk.block("dir_lights_array",
                    "#ifdef MAX_DIR_LIGHTS\n", "#endif\n")
                .field("directionalLights", "    directionalLights : array<DirectionalLight, MAX_DIR_LIGHTS>,\n")
                .build())
            .sub(WgShaderChunk.block("point_lights_array",
                    "#ifdef MAX_POINT_LIGHTS\n", "#endif\n")
                .field("pointLights", "    pointLights : array<PointLight, MAX_POINT_LIGHTS>,\n")
                .build())
            .field("ambientLight",         "    ambientLight: vec4f,\n")
            .field("cameraPosition",       "    cameraPosition: vec4f,\n")
            .field("fogColor",             "    fogColor: vec4f,\n")
            .field("numDirectionalLights", "    numDirectionalLights: f32,\n")
            .field("numPointLights",       "    numPointLights: f32,\n")
            .field("shadowPcfOffset",      "    shadowPcfOffset: f32,\n")
            .field("shadowBias",           "    shadowBias: f32,\n")
            .field("normalMapStrength",    "    normalMapStrength: f32,\n")
            .field("numRoughnessLevels",   "    numRoughnessLevels: f32,\n")
            .build();

    public static final WgShaderChunk STRUCT_MODEL_UNIFORMS_CHUNK =
        WgShaderChunk.struct(STRUCT_MODEL_UNIFORMS, "ModelUniforms")
            .field("modelMatrix",    "    modelMatrix: mat4x4f,\n")
            .field("normalMatrix",   "    normalMatrix: mat4x4f,\n")
            .field("morphWeights",   "    morphWeights: vec4f,\n")
            .field("morphWeights2",  "    morphWeights2: vec4f,\n")
            .build();

    public static final WgShaderChunk STRUCT_MATERIAL_UNIFORMS_CHUNK =
        WgShaderChunk.struct(STRUCT_MATERIAL_UNIFORMS, "MaterialUniforms")
            .field("diffuseColor",     "    diffuseColor: vec4f,\n")
            .field("shininess",        "    shininess: f32,\n")
            .field("roughnessFactor",  "    roughnessFactor: f32,\n")
            .field("metallicFactor",   "    metallicFactor: f32,\n")
            .build();

    // =========================================================================
    // CHUNK SOURCES — bindings
    // =========================================================================

    public static final WgShaderChunk BINDINGS_FRAME_CHUNK = new WgShaderChunk(BINDINGS_FRAME,
            "@group(0) @binding(0) var<uniform> uFrame: FrameUniforms;\n");

    public static final WgShaderChunk BINDINGS_SHADOW_MAP_CHUNK =
        WgShaderChunk.block(BINDINGS_SHADOW_MAP, "#ifdef SHADOW_MAP\n", "#endif\n")
            .line("shadow_map",     "    @group(0) @binding(1) var shadowMap: texture_depth_2d;\n")
            .line("shadow_sampler", "    @group(0) @binding(2) var shadowSampler: sampler_comparison;\n")
            .build();

    public static final WgShaderChunk BINDINGS_ENVIRONMENT_MAP_CHUNK =
        WgShaderChunk.block(BINDINGS_ENVIRONMENT_MAP, "#ifdef ENVIRONMENT_MAP\n", "#endif\n")
            .line("cube_map",         "    @group(0) @binding(3) var cubeMap:        texture_cube<f32>;\n")
            .line("cube_map_sampler", "    @group(0) @binding(4) var cubeMapSampler: sampler;\n")
            .build();

    public static final WgShaderChunk BINDINGS_IBL_CHUNK =
        WgShaderChunk.block(BINDINGS_IBL, "#ifdef USE_IBL\n", "#endif\n")
            .line("irradiance_map",     "    @group(0) @binding(5) var irradianceMap:    texture_cube<f32>;\n")
            .line("irradiance_sampler", "    @group(0) @binding(6) var irradianceSampler: sampler;\n")
            .line("radiance_map",       "    @group(0) @binding(7) var radianceMap:      texture_cube<f32>;\n")
            .line("radiance_sampler",   "    @group(0) @binding(8) var radianceSampler:  sampler;\n")
            .line("brdf_lut",           "    @group(0) @binding(9) var brdfLUT:          texture_2d<f32>;\n")
            .line("lut_sampler",        "    @group(0) @binding(10) var lutSampler:      sampler;\n")
            .build();

    public static final WgShaderChunk BINDINGS_MATERIAL_CHUNK =
        WgShaderChunk.block(BINDINGS_MATERIAL, "// material bindings\n", "")
            .line("material",                    "@group(1) @binding(0) var<uniform> material: MaterialUniforms;\n")
            .line("diffuse_texture",             "@group(1) @binding(1) var diffuseTexture: texture_2d<f32>;\n")
            .line("diffuse_sampler",             "@group(1) @binding(2) var diffuseSampler: sampler;\n")
            .line("normal_texture",              "@group(1) @binding(3) var normalTexture: texture_2d<f32>;\n")
            .line("normal_sampler",              "@group(1) @binding(4) var normalSampler: sampler;\n")
            .line("metallic_roughness_texture",  "@group(1) @binding(5) var metallicRoughnessTexture: texture_2d<f32>;\n")
            .line("metallic_roughness_sampler",  "@group(1) @binding(6) var metallicRoughnessSampler: sampler;\n")
            .line("emissive_texture",            "@group(1) @binding(7) var emissiveTexture: texture_2d<f32>;\n")
            .line("emissive_sampler",            "@group(1) @binding(8) var emissiveSampler: sampler;\n")
            .build();

    public static final WgShaderChunk BINDINGS_INSTANCING_CHUNK =
        WgShaderChunk.block(BINDINGS_INSTANCING, "// renderables\n", "")
            .line("instances", "@group(2) @binding(0) var<storage, read> instances: array<ModelUniforms>;\n")
            .build();

    public static final WgShaderChunk BINDINGS_SKIN_CHUNK =
        WgShaderChunk.block(BINDINGS_SKIN, "#ifdef SKIN\n", "#endif\n")
            .line("joint_matrices", "    @group(3) @binding(0) var<storage, read> jointMatrices: array<mat4x4f>;\n")
            .build();

    // =========================================================================
    // CHUNK SOURCES — IO structs
    // =========================================================================

    public static final WgShaderChunk VERTEX_INPUT_CHUNK =
        WgShaderChunk.struct(VERTEX_INPUT, "VertexInput")
            .field("position", "    @location(0) position: vec3f,\n")
            .sub(WgShaderChunk.block("uv_block", "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
                .field("uv", "    @location(1) uv: vec2f,\n").build())
            .sub(WgShaderChunk.block("normal_block", "#ifdef NORMAL\n", "#endif\n")
                .field("normal", "    @location(2) normal: vec3f,\n").build())
            .sub(WgShaderChunk.block("normal_map_block", "#ifdef NORMAL_MAP\n", "#endif\n")
                .field("tangent",   "    @location(3) tangent: vec3f,\n")
                .field("bitangent", "    @location(4) bitangent: vec3f,\n").build())
            .sub(WgShaderChunk.block("color_block", "#ifdef COLOR\n", "#endif\n")
                .field("color", "    @location(5) color: vec4f,\n").build())
            .sub(WgShaderChunk.block("skin_block", "#ifdef SKIN\n", "#endif\n")
                .field("joints",  "    @location(6) joints: vec4f,\n")
                .field("weights", "    @location(7) weights: vec4f,\n").build())
            .sub(WgShaderChunk.block("morph_block", "#ifdef MORPH\n", "#endif\n")
                .sub(WgShaderChunk.block("morph_0", "#ifdef MORPH_0\n", "#endif\n")
                    .field("morph_pos0", "    @location(8) morph_pos0: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_1", "#ifdef MORPH_1\n", "#endif\n")
                    .field("morph_pos1", "    @location(9) morph_pos1: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_2", "#ifdef MORPH_2\n", "#endif\n")
                    .field("morph_pos2", "    @location(10) morph_pos2: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_3", "#ifdef MORPH_3\n", "#endif\n")
                    .field("morph_pos3", "    @location(11) morph_pos3: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_4", "#ifdef MORPH_4\n", "#endif\n")
                    .field("morph_pos4", "    @location(12) morph_pos4: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_5", "#ifdef MORPH_5\n", "#endif\n")
                    .field("morph_pos5", "    @location(13) morph_pos5: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_6", "#ifdef MORPH_6\n", "#endif\n")
                    .field("morph_pos6", "    @location(14) morph_pos6: vec3f,\n").build())
                .sub(WgShaderChunk.block("morph_7", "#ifdef MORPH_7\n", "#endif\n")
                    .field("morph_pos7", "    @location(15) morph_pos7: vec3f,\n").build())
                .build())
            .build();

    public static final WgShaderChunk VERTEX_OUTPUT_CHUNK =
        WgShaderChunk.struct(VERTEX_OUTPUT, "VertexOutput")
            .field("position",  "    @builtin(position) position: vec4f,\n")
            .field("uv",        "    @location(1) uv: vec2f,\n")
            .field("color",     "    @location(2) color: vec4f,\n")
            .field("normal",    "    @location(3) normal: vec3f,\n")
            .field("worldPos",  "    @location(4) worldPos : vec3f,\n")
            .sub(WgShaderChunk.block("normal_map_block", "#ifdef NORMAL_MAP\n", "#endif\n")
                .field("tangent",   "    @location(5) tangent: vec3f,\n")
                .field("bitangent", "    @location(6) bitangent: vec3f,\n").build())
            .sub(WgShaderChunk.block("fog_block", "#ifdef FOG\n", "#endif\n")
                .field("fogDepth", "    @location(7) fogDepth: f32,\n").build())
            .sub(WgShaderChunk.block("shadow_block", "#ifdef SHADOW_MAP\n", "#endif\n")
                .field("shadowPos", "    @location(8) shadowPos: vec3f,\n").build())
            .build();

    public static final WgShaderChunk PI_CHUNK = new WgShaderChunk(PI,
            "const pi : f32 = 3.14159265359;\n");

    // =========================================================================
    // CHUNK SOURCES — vertex shader (structured fn chunks)
    // =========================================================================

    public static final WgShaderChunk VS_OPEN_CHUNK =
        WgShaderChunk.block(VS_OPEN,
            "@vertex\nfn vs_main(in: VertexInput, @builtin(instance_index) instance: u32) -> VertexOutput {\n", "")
            .line("out_decl", "   var out: VertexOutput;\n")
            .build();

    public static final WgShaderChunk VS_POS_INIT_CHUNK = new WgShaderChunk(VS_POS_INIT,
            "   var pos = in.position;\n");

    public static final WgShaderChunk VS_MORPH_CHUNK =
        WgShaderChunk.block(VS_MORPH, "#ifdef MORPH\n", "#endif\n")
            .sub(WgShaderChunk.block("morph_0", "#ifdef MORPH_0\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights[0] * in.morph_pos0;\n").build())
            .sub(WgShaderChunk.block("morph_1", "#ifdef MORPH_1\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights[1] * in.morph_pos1;\n").build())
            .sub(WgShaderChunk.block("morph_2", "#ifdef MORPH_2\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights[2] * in.morph_pos2;\n").build())
            .sub(WgShaderChunk.block("morph_3", "#ifdef MORPH_3\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights[3] * in.morph_pos3;\n").build())
            .sub(WgShaderChunk.block("morph_4", "#ifdef MORPH_4\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights2[0] * in.morph_pos4;\n").build())
            .sub(WgShaderChunk.block("morph_5", "#ifdef MORPH_5\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights2[1] * in.morph_pos5;\n").build())
            .sub(WgShaderChunk.block("morph_6", "#ifdef MORPH_6\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights2[2] * in.morph_pos6;\n").build())
            .sub(WgShaderChunk.block("morph_7", "#ifdef MORPH_7\n", "#endif\n")
                .line("apply", "   pos += instances[instance].morphWeights2[3] * in.morph_pos7;\n").build())
            .build();

    public static final WgShaderChunk VS_NORMAL_ATTR_CHUNK =
        WgShaderChunk.block(VS_NORMAL_ATTR, "", "")
            .line("default",   "   var normal_attr = vec3f(0,1,0);\n")
            .sub(WgShaderChunk.block("normal_block", "#ifdef NORMAL\n", "#endif\n")
                .line("read", "   normal_attr = in.normal;\n").build())
            .build();

    public static final WgShaderChunk VS_SKIN_MATRIX_CHUNK =
        WgShaderChunk.block(VS_SKIN_MATRIX, "#ifdef SKIN\n", "#endif\n")
            .line("joint0",      "   let joint0 = jointMatrices[u32(in.joints[0])];\n")
            .line("joint1",      "   let joint1 = jointMatrices[u32(in.joints[1])];\n")
            .line("joint2",      "   let joint2 = jointMatrices[u32(in.joints[2])];\n")
            .line("joint3",      "   let joint3 = jointMatrices[u32(in.joints[3])];\n")
            .line("skin_matrix", "   let skinMatrix =\n"
                               + "       joint0 * in.weights[0] +\n"
                               + "       joint1 * in.weights[1] +\n"
                               + "       joint2 * in.weights[2] +\n"
                               + "       joint3 * in.weights[3];\n")
            .build();

    public static final WgShaderChunk VS_WORLD_POS_SKINNED_CHUNK = new WgShaderChunk(VS_WORLD_POS_SKINNED,
            "#ifdef SKIN\n"
          + "   let worldPosition = instances[instance].modelMatrix * skinMatrix * vec4f(pos, 1.0);\n"
          + "#else\n");

    public static final WgShaderChunk VS_WORLD_POS_NO_SKIN_CHUNK = new WgShaderChunk(VS_WORLD_POS_NO_SKIN,
            "   let worldPosition = instances[instance].modelMatrix * vec4f(pos, 1.0);\n"
          + "#endif\n");

    public static final WgShaderChunk VS_CLIP_POS_CHUNK =
        WgShaderChunk.block(VS_CLIP_POS, "", "")
            .line("clip_pos",  "   out.position = uFrame.projectionViewTransform * worldPosition;\n")
            .line("world_pos", "   out.worldPos = worldPosition.xyz;\n")
            .build();

    public static final WgShaderChunk VS_UV_CHUNK =
        WgShaderChunk.block(VS_UV, "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
            .line("write_uv",      "   out.uv = in.uv;\n")
            .sub(WgShaderChunk.block("else_block", "#else\n", "")
                .line("zero_uv", "   out.uv = vec2f(0);\n").build())
            .build();

    public static final WgShaderChunk VS_COLOR_CHUNK =
        WgShaderChunk.block(VS_COLOR, "", "")
            .sub(WgShaderChunk.block("color_read", "#ifdef COLOR\n", "#endif\n")
                .line("read_color",  "   var diffuseColor = in.color;\n")
                .sub(WgShaderChunk.block("else_block", "#else\n", "")
                    .line("white", "   var diffuseColor = vec4f(1);\n").build())
                .build())
            .line("tint",      "   diffuseColor *= material.diffuseColor;\n")
            .line("write_out", "   out.color = diffuseColor;\n")
            .build();

    public static final WgShaderChunk VS_NORMAL_TRANSFORM_CHUNK =
        WgShaderChunk.block(VS_NORMAL_TRANSFORM, "", "")
            .sub(WgShaderChunk.block("normal_calc", "#ifdef NORMAL\n", "#endif\n")
                .line("transform", "   let normal = normalize((instances[instance].normalMatrix * vec4f(normal_attr, 0.0)).xyz);\n")
                .sub(WgShaderChunk.block("else_block", "#else\n", "")
                    .line("default", "   let normal = vec3f(0,1,0);\n").build())
                .build())
            .line("write_out", "   out.normal = normal;\n")
            .build();

    public static final WgShaderChunk VS_NORMAL_MAP_PASSTHROUGH_CHUNK =
        WgShaderChunk.block(VS_NORMAL_MAP_PASSTHROUGH, "#ifdef NORMAL_MAP\n", "#endif\n")
            .line("tangent",   "   out.tangent = in.tangent;\n")
            .line("bitangent", "   out.bitangent = in.bitangent;\n")
            .build();

    public static final WgShaderChunk VS_FOG_CHUNK =
        WgShaderChunk.block(VS_FOG, "#ifdef FOG\n", "#endif\n")
            .line("fog_len",   "   let flen:vec3f = uFrame.cameraPosition.xyz - worldPosition.xyz;\n")
            .line("fog_depth", "   let fog:f32 = dot(flen, flen) * uFrame.cameraPosition.w;\n")
            .line("fog_write", "   out.fogDepth = min(fog, 1.0);\n")
            .build();

    public static final WgShaderChunk VS_SHADOW_CHUNK =
        WgShaderChunk.block(VS_SHADOW, "#ifdef SHADOW_MAP\n", "#endif\n")
            .line("pos_from_light", "   let posFromLight = uFrame.shadowProjViewTransform * worldPosition;\n")
            .line("shadow_pos",     "   out.shadowPos = vec3(\n"
                                  + "       posFromLight.xy * vec2(0.5, -0.5) + vec2(0.5),\n"
                                  + "       posFromLight.z\n"
                                  + "   );\n")
            .build();

    public static final WgShaderChunk VS_RETURN_CHUNK = new WgShaderChunk(VS_RETURN,
            "   return out;\n}\n");

    // =========================================================================
    // CHUNK SOURCES — fragment shader  (structured fn/block chunks)
    // =========================================================================

    public static final WgShaderChunk FS_SIGNATURE_CHUNK = new WgShaderChunk(FS_SIGNATURE,
            "@fragment\nfn fs_main(in : VertexOutput) -> @location(0) vec4f {\n");

    public static final WgShaderChunk FS_BASE_COLOR_CHUNK =
        WgShaderChunk.block(FS_BASE_COLOR, "#ifdef TEXTURE_COORDINATE\n", "#endif\n")
            .line("sample_color", "   var color = in.color * textureSample(diffuseTexture, diffuseSampler, in.uv);\n")
            .sub(WgShaderChunk.block("else_block", "#else\n", "")
                .line("pass_color", "   var color = in.color;\n").build())
            .build();

    public static final WgShaderChunk FS_VISIBILITY_CHUNK =
        WgShaderChunk.block(FS_VISIBILITY, "#ifdef SHADOW_MAP\n", "#endif\n")
            .line("shadow_visibility", "    let visibility = getShadowNess(in.shadowPos);\n")
            .sub(WgShaderChunk.block("else_block", "#else\n", "")
                .line("full_visibility", "    let visibility = 1.0;\n").build())
            .build();

    public static final WgShaderChunk FS_LIGHTING_OPEN_CHUNK =
        WgShaderChunk.block(FS_LIGHTING_OPEN, "#ifdef LIGHTING\n", "")
            .line("base_color", "    let baseColor = color;\n")
            .build();

    public static final WgShaderChunk FS_NORMAL_MAP_CHUNK =
        WgShaderChunk.block(FS_NORMAL_MAP, "#ifdef NORMAL_MAP\n", "#else // NORMAL_MAP\n")
            .line("encode",        "    let encodedN = textureSample(normalTexture, normalSampler, in.uv).rgb;\n")
            .line("local_n",       "    let localN = encodedN * 2.0 - 1.0;\n")
            .line("local_to_world","    let localToWorld = mat3x3f(\n"
                                 + "        normalize(in.tangent),\n"
                                 + "        normalize(in.bitangent),\n"
                                 + "        normalize(in.normal),\n"
                                 + "    );\n")
            .line("world_n",       "    let worldN = localToWorld * localN;\n")
            .line("normal",        "    let normal = mix(in.normal.xyz, worldN, uFrame.normalMapStrength);\n")
            .build();

    public static final WgShaderChunk FS_NORMAL_NO_MAP_CHUNK =
        WgShaderChunk.block(FS_NORMAL_NO_MAP, "", "#endif\n")
            .line("normal", "    let normal = normalize(in.normal.xyz);\n")
            .build();

    public static final WgShaderChunk FS_PBR_INPUTS_CHUNK =
        WgShaderChunk.block(FS_PBR_INPUTS, "", "")
            .line("mr_sample",  "    let mrSample = textureSample(metallicRoughnessTexture, metallicRoughnessSampler, in.uv).rgb;\n")
            .line("roughness",  "    let roughness : f32 = mrSample.g * material.roughnessFactor;\n")
            .line("metallic",   "    let metallic : f32 = mrSample.b * material.metallicFactor;\n")
            .line("shininess",  "    let shininess : f32 = material.shininess;\n")
            .line("radiance",   "    var radiance : vec3f = vec3f(0);\n")
            .line("specular",   "    var specular : vec3f = vec3f(0);\n")
            .line("view_vec",   "    let viewVec : vec3f = normalize(uFrame.cameraPosition.xyz - in.worldPos.xyz);\n")
            .build();

    public static final WgShaderChunk FS_AMBIENT_CHUNK =
        WgShaderChunk.block(FS_AMBIENT, "#ifdef USE_IBL\n", "#endif\n")
            .line("ibl_ambient", "    let ambient : vec3f = ambientIBL(viewVec, normal, roughness, metallic, baseColor.rgb);\n")
            .sub(WgShaderChunk.block("else_block", "#else\n", "")
                .line("simple_ambient", "    let ambient : vec3f = uFrame.ambientLight.rgb * baseColor.rgb;\n").build())
            .build();

    public static final WgShaderChunk FS_DIR_LIGHTS_CHUNK = new WgShaderChunk(FS_DIR_LIGHTS,
            "#ifdef MAX_DIR_LIGHTS\n"
          + "    let numDirectionalLights = min(uFrame.numDirectionalLights, MAX_DIR_LIGHTS);\n"
          + "    if(numDirectionalLights > 0) {\n"
          + "        for (var i: u32 = 0; i < u32(numDirectionalLights); i++) {\n"
          + "            let light = uFrame.directionalLights[i];\n"
          + "            let lightVec = -normalize(light.direction.xyz);\n"
          + "            let NdotL = max(dot(lightVec, normal), 0.0);\n"
          + "#ifdef PBR\n"
          + "            if(NdotL > 0.0) {\n"
          + "                radiance += BRDF(lightVec, viewVec, normal, roughness, metallic, baseColor.rgb) * NdotL * light.color.rgb;\n"
          + "            }\n"
          + "#else\n"
          + "            radiance += NdotL * light.color.rgb;\n"
          + "    #ifdef SPECULAR\n"
          + "            let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));\n"
          + "            specular += NdotL * light.color.rgb * pow(halfDotView, shininess);\n"
          + "    #endif\n"
          + "#endif // PBR\n"
          + "        }\n"
          + "    }\n"
          + "#endif // MAX_DIR_LIGHTS\n");

    public static final WgShaderChunk FS_POINT_LIGHTS_CHUNK = new WgShaderChunk(FS_POINT_LIGHTS,
            "#ifdef MAX_POINT_LIGHTS\n"
          + "    let numPointLights = min(uFrame.numPointLights, MAX_POINT_LIGHTS);\n"
          + "    if(numPointLights > 0) {\n"
          + "        for (var i: u32 = 0; i < u32(numPointLights); i++) {\n"
          + "            let light = uFrame.pointLights[i];\n"
          + "            var lightVec = light.position.xyz - in.worldPos.xyz;\n"
          + "            let dist2 : f32 = dot(lightVec,lightVec);\n"
          + "            lightVec = normalize(lightVec);\n"
          + "            let attenuation : f32 = light.intensity/(1.0 + dist2);\n"
          + "            let NdotL : f32 = max(dot(lightVec, normal), 0.0);\n"
          + "#ifdef PBR\n"
          + "            if(NdotL > 0.0) {\n"
          + "                radiance += BRDF(lightVec, viewVec, normal, roughness, metallic, baseColor.rgb) * NdotL * attenuation * light.color.rgb;\n"
          + "            }\n"
          + "#else\n"
          + "            radiance += NdotL * attenuation * light.color.rgb;\n"
          + "#ifdef SPECULAR\n"
          + "            let halfDotView = max(0.0, dot(normal, normalize(lightVec + viewVec)));\n"
          + "            specular += NdotL * attenuation * light.color.rgb * pow(halfDotView, shininess);\n"
          + "#endif\n"
          + "#endif // PBR\n"
          + "        }\n"
          + "    }\n"
          + "#endif // MAX_POINT_LIGHTS\n");

    public static final WgShaderChunk FS_LIT_COLOR_CHUNK =
        WgShaderChunk.block(FS_LIT_COLOR, "", "")
            .sub(WgShaderChunk.block("pbr_block", "#ifdef PBR\n", "#endif\n")
                .line("pbr_lit",  "    let litColor = vec4f(ambient + visibility*radiance, 1.0);\n")
                .sub(WgShaderChunk.block("else_block", "#else\n", "")
                    .line("simple_lit", "    let litColor = vec4f(ambient + color.rgb * (visibility * radiance) + visibility*specular, 1.0);\n").build())
                .build())
            .line("apply_lit", "    color = litColor;\n")
            .build();

    public static final WgShaderChunk FS_ENVIRONMENT_MAP_CHUNK =
        WgShaderChunk.block(FS_ENVIRONMENT_MAP, "#ifndef USE_IBL\n", "#endif\n")
            .sub(WgShaderChunk.block("env_map_block", "    #ifdef ENVIRONMENT_MAP\n", "    #endif\n")
                .line("reflect_dir", "        let rdir:vec3f = normalize(reflect(viewVec, normal)*vec3f(-1, -1, 1));\n")
                .line("reflection",  "        var reflection = textureSample(cubeMap, cubeMapSampler, rdir);\n")
                .line("mix_color",   "        color = mix(color, reflection, 0.1f);\n")
                .build())
            .build();

    public static final WgShaderChunk FS_LIGHTING_CLOSE_CHUNK = new WgShaderChunk(FS_LIGHTING_CLOSE,
            "#endif // LIGHTING\n");

    public static final WgShaderChunk FS_EMISSIVE_CHUNK =
        WgShaderChunk.block(FS_EMISSIVE, "", "")
            .line("sample",    "    let emissiveColor = textureSample(emissiveTexture, emissiveSampler, in.uv).rgb;\n")
            .line("add_color", "    color = color + vec4f(emissiveColor, 0);\n")
            .build();

    public static final WgShaderChunk FS_FOG_CHUNK =
        WgShaderChunk.block(FS_FOG, "#ifdef FOG\n", "#endif\n")
            .line("apply_fog", "    color = vec4f(mix(color.rgb, uFrame.fogColor.rgb, in.fogDepth), color.a);\n")
            .build();

    public static final WgShaderChunk FS_GAMMA_CHUNK =
        WgShaderChunk.block(FS_GAMMA, "#ifdef GAMMA_CORRECTION\n", "#endif\n")
            .line("linear_color", "    let linearColor: vec3f = pow(color.rgb, vec3f(1/2.2));\n")
            .line("apply_gamma",  "    color = vec4f(linearColor, color.a);\n")
            .build();

    public static final WgShaderChunk FS_RETURN_CHUNK = new WgShaderChunk(FS_RETURN,
            "    return color;\n};\n");

    // =========================================================================
    // CHUNK SOURCES — helper functions
    // =========================================================================

    public static final WgShaderChunk SHADOW_CHUNK = new WgShaderChunk(SHADOW,
            "#ifdef SHADOW_MAP\n"
          + "fn getShadowNess(shadowPos:vec3f) -> f32 {\n"
          + "    var visibility = 0.0;\n"
          + "    for(var y = -1; y <= 1; y++) {\n"
          + "        for(var x = -1; x <= 1; x++) {\n"
          + "            let offset = vec2f(vec2(x,y)) * uFrame.shadowPcfOffset;\n"
          + "            visibility += textureSampleCompare(shadowMap, shadowSampler, shadowPos.xy+offset, shadowPos.z - uFrame.shadowBias);\n"
          + "        }\n"
          + "    }\n"
          + "    visibility /= 9.0;\n"
          + "    return visibility;\n"
          + "}\n"
          + "fn getShadowSingleSample(shadowPos:vec3f) -> f32 {\n"
          + "    return textureSampleCompare(shadowMap, shadowSampler, shadowPos.xy, shadowPos.z - uFrame.shadowBias);\n"
          + "}\n"
          + "#endif\n");

    public static final WgShaderChunk PBR_CHUNK = new WgShaderChunk(PBR,
            "#ifdef PBR\n"
          + "fn D_GGX(NdotH: f32, roughness: f32) -> f32 {\n"
          + "    let alpha : f32 = roughness * roughness;\n"
          + "    let alpha2 : f32 = alpha * alpha;\n"
          + "    let denom : f32 = (NdotH * NdotH) * (alpha2 - 1.0) + 1.0;\n"
          + "    return alpha2/(pi * denom * denom);\n"
          + "}\n"
          + "fn G_SchlickSmith_GGX(NdotL : f32, NdotV : f32, roughness : f32) -> f32 {\n"
          + "    let r : f32 = (roughness + 1.0);\n"
          + "    let k : f32 = (r*r)/8.0;\n"
          + "    let GL : f32 = NdotL / (NdotL * (1.0 - k) + k);\n"
          + "    let GV : f32 = NdotV / (NdotV * (1.0 - k) + k);\n"
          + "    return GL * GV;\n"
          + "}\n"
          + "fn F_Schlick(cosTheta : f32, metallic : f32, baseColor : vec3f) -> vec3f {\n"
          + "    let F0 : vec3f = mix(vec3(0.04), baseColor, metallic);\n"
          + "    let F : vec3f = F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);\n"
          + "    return F;\n"
          + "}\n"
          + "fn BRDF(L : vec3f, V:vec3f, N: vec3f, roughness:f32, metallic:f32, baseColor: vec3f) -> vec3f {\n"
          + "    let H = normalize(V+L);\n"
          + "    let NdotV : f32 = clamp(dot(N, V), 0.0, 1.0);\n"
          + "    let NdotL : f32 = clamp(dot(N, L), 0.001, 1.0);\n"
          + "    let LdotH : f32 = clamp(dot(L, H), 0.0, 1.0);\n"
          + "    let NdotH : f32 = clamp(dot(N, H), 0.0, 1.0);\n"
          + "    let HdotV : f32 = clamp(dot(H, V), 0.0, 1.0);\n"
          + "    let D :f32   = D_GGX(NdotH, roughness);\n"
          + "    let G :f32   = G_SchlickSmith_GGX(NdotL, NdotV, roughness);\n"
          + "    let F :vec3f = F_Schlick(HdotV, metallic, baseColor);\n"
          + "    let kS = F;\n"
          + "    let kD = (vec3f(1.0) - kS) * (1.0 - metallic);\n"
          + "    let specular : vec3f = D * F * G / (4.0 * max(NdotL, 0.0001) * max(NdotV, 0.0001));\n"
          + "    let diffuse : vec3f = kD * baseColor / pi;\n"
          + "    return diffuse + specular;\n"
          + "}\n"
          + "#endif // PBR\n");

    public static final WgShaderChunk IBL_CHUNK = new WgShaderChunk(IBL,
            "#ifdef PBR\n"
          + "#ifdef USE_IBL\n"
          + "fn ambientIBL(V:vec3f, N: vec3f, roughness:f32, metallic:f32, baseColor: vec3f) -> vec3f {\n"
          + "    let NdotV : f32 = clamp(dot(N, V), 0.0, 1.0);\n"
          + "    let F :vec3f = F_Schlick(NdotV, metallic, baseColor);\n"
          + "    let kD = (vec3f(1.0) - F)*(1.0 - metallic);\n"
          + "    let lightSample:vec3f = normalize(N * vec3f(1, 1, -1));\n"
          + "    let irradiance:vec3f = textureSample(irradianceMap, irradianceSampler, lightSample).rgb;\n"
          + "    let diffuse:vec3f = irradiance * baseColor.rgb;\n"
          + "    let maxReflectionLOD:f32 = f32(uFrame.numRoughnessLevels);\n"
          + "    let R:vec3f = reflect(-V, N) * vec3f(-1, 1, 1);\n"
          + "    let prefilteredColor:vec3f = textureSampleLevel(radianceMap, radianceSampler, R, roughness * maxReflectionLOD).rgb;\n"
          + "    let envBRDF = textureSample(brdfLUT, lutSampler, vec2(NdotV, roughness)).rg;\n"
          + "    let specular: vec3f = prefilteredColor * (F * envBRDF.x + envBRDF.y);\n"
          + "    return (kD * diffuse) + specular;\n"
          + "}\n"
          + "#endif // USE_IBL\n"
          + "#endif // PBR\n");


    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    /**
     * Create a {@link WgShaderBuilder} pre-loaded with all standard model-batch chunks in the
     * correct WGSL declaration order, equivalent to {@code modelbatch.wgsl}.
     */
    public static WgShaderBuilder defaultModelBatch() {
        return new WgShaderBuilder()
            // header
            .addChunk(HEADER_CHUNK)
            // structs
            .addChunk(STRUCT_DIRECTIONAL_LIGHT_CHUNK)
            .addChunk(STRUCT_POINT_LIGHT_CHUNK)
            .addChunk(STRUCT_FRAME_UNIFORMS_CHUNK)
            .addChunk(STRUCT_MODEL_UNIFORMS_CHUNK)
            .addChunk(STRUCT_MATERIAL_UNIFORMS_CHUNK)
            // bindings
            .addChunk(BINDINGS_FRAME_CHUNK)
            .addChunk(BINDINGS_SHADOW_MAP_CHUNK)
            .addChunk(BINDINGS_ENVIRONMENT_MAP_CHUNK)
            .addChunk(BINDINGS_IBL_CHUNK)
            .addChunk(BINDINGS_MATERIAL_CHUNK)
            .addChunk(BINDINGS_INSTANCING_CHUNK)
            .addChunk(BINDINGS_SKIN_CHUNK)
            // IO structs + constants
            .addChunk(VERTEX_INPUT_CHUNK)
            .addChunk(VERTEX_OUTPUT_CHUNK)
            .addChunk(PI_CHUNK)
            // vertex shader
            .addChunk(VS_OPEN_CHUNK)
            .addChunk(VS_POS_INIT_CHUNK)
            .addChunk(VS_MORPH_CHUNK)
            .addChunk(VS_NORMAL_ATTR_CHUNK)
            .addChunk(VS_SKIN_MATRIX_CHUNK)
            .addChunk(VS_WORLD_POS_SKINNED_CHUNK)
            .addChunk(VS_WORLD_POS_NO_SKIN_CHUNK)
            .addChunk(VS_CLIP_POS_CHUNK)
            .addChunk(VS_UV_CHUNK)
            .addChunk(VS_COLOR_CHUNK)
            .addChunk(VS_NORMAL_TRANSFORM_CHUNK)
            .addChunk(VS_NORMAL_MAP_PASSTHROUGH_CHUNK)
            .addChunk(VS_FOG_CHUNK)
            .addChunk(VS_SHADOW_CHUNK)
            .addChunk(VS_RETURN_CHUNK)
            // helper functions
            .addChunk(SHADOW_CHUNK)
            .addChunk(PBR_CHUNK)
            .addChunk(IBL_CHUNK)
            // fragment shader
            .addChunk(FS_SIGNATURE_CHUNK)
            .addChunk(FS_BASE_COLOR_CHUNK)
            .addChunk(FS_VISIBILITY_CHUNK)
            .addChunk(FS_LIGHTING_OPEN_CHUNK)
            .addChunk(FS_NORMAL_MAP_CHUNK)
            .addChunk(FS_NORMAL_NO_MAP_CHUNK)
            .addChunk(FS_PBR_INPUTS_CHUNK)
            .addChunk(FS_AMBIENT_CHUNK)
            .addChunk(FS_DIR_LIGHTS_CHUNK)
            .addChunk(FS_POINT_LIGHTS_CHUNK)
            .addChunk(FS_LIT_COLOR_CHUNK)
            .addChunk(FS_ENVIRONMENT_MAP_CHUNK)
            .addChunk(FS_LIGHTING_CLOSE_CHUNK)
            .addChunk(FS_EMISSIVE_CHUNK)
            .addChunk(FS_FOG_CHUNK)
            .addChunk(FS_GAMMA_CHUNK)
            .addChunk(FS_RETURN_CHUNK);
    }


    private WgModelBatchShaderBuilder() {}
}


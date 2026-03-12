package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.IdAttribute;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgModelBatchShaderBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgShaderChunk;

/**
 * Shader to render encoded ID to the color.
 */
public class WgIDShaderProvider extends WgDefaultShaderProvider {

    private static final String PICKING_SHADER_SOURCE_MRT = buildPickingShaderSourceMRT();
    private static final String PICKING_SHADER_SOURCE_SINGLE = buildPickingShaderSourceSingle();
    private final boolean pickingUseMRT;
    private boolean autoSetId;
    private int nextId = 1;

    public WgIDShaderProvider(WgModelBatch.Config config) {
        this(config, false, true);
    }

    public WgIDShaderProvider(WgModelBatch.Config config, boolean useSecondTarget) {
        this(config, useSecondTarget, true);
    }

    public WgIDShaderProvider(WgModelBatch.Config config, boolean useSecondTarget, boolean autoAddId) {
        super(config);
        this.pickingUseMRT = useSecondTarget;
        this.autoSetId = autoAddId;
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        WgModelBatch.Config pickingConfig = new WgModelBatch.Config();
        pickingConfig.numBones = config.numBones;
        pickingConfig.maxDirectionalLights = config.maxDirectionalLights;
        pickingConfig.maxPointLights = config.maxPointLights;
        pickingConfig.maxInstances = config.maxInstances;
        pickingConfig.maxRigged = config.maxRigged;
        pickingConfig.usePBR = config.usePBR;
        pickingConfig.materials = config.materials;

        if(autoSetId) {
            int objectId = nextId++;
            renderable.material.set(new IdAttribute(objectId));
        }
        else {
            boolean hasPickingId = renderable.material.has(IdAttribute.Type);
            if (!hasPickingId) {
                renderable.material.set(new IdAttribute(0));
            }
        }

        pickingConfig.shaderSource = pickingUseMRT ? PICKING_SHADER_SOURCE_MRT : PICKING_SHADER_SOURCE_SINGLE;

        return new WgIDShader(renderable, pickingConfig);
    }

    private static String buildPickingShaderSourceMRT() {
        return WgModelBatchShaderBuilder.defaultModelBatch()
                .replaceChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                        new WgShaderChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                                "struct MaterialUniforms {\n"
                              + "    diffuseColor: vec4f,\n"
                              + "    shininess: f32,\n"
                              + "    roughnessFactor: f32,\n"
                              + "    metallicFactor: f32,\n"
                              + "    colored_id: vec4f,\n"
                              + "};\n"))
                .insertBefore(WgModelBatchShaderBuilder.FS_SIGNATURE,
                        new WgShaderChunk("mrt_output_struct",
                                "struct FragmentOutput {\n"
                              + "    @location(0) color : vec4f,\n"
                              + "    @location(1) colored_id : vec4f,\n"
                              + "};\n"))
                .replaceChunk(WgModelBatchShaderBuilder.FS_SIGNATURE,
                        new WgShaderChunk(WgModelBatchShaderBuilder.FS_SIGNATURE,
                                "@fragment\n"
                              + "fn fs_main(in : VertexOutput) -> FragmentOutput {\n"))
                .replaceChunk(WgModelBatchShaderBuilder.FS_RETURN,
                        new WgShaderChunk(WgModelBatchShaderBuilder.FS_RETURN,
                                "    var output: FragmentOutput;\n"
                              + "    output.color = color;\n"
                              + "    output.colored_id = material.colored_id;\n"
                              + "    return output;\n"
                              + "};\n"))
                .build();
    }

    private static String buildPickingShaderSourceSingle() {
        return WgModelBatchShaderBuilder.defaultModelBatch()
                .replaceChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                        new WgShaderChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                                "struct MaterialUniforms {\n"
                              + "    diffuseColor: vec4f,\n"
                              + "    shininess: f32,\n"
                              + "    roughnessFactor: f32,\n"
                              + "    metallicFactor: f32,\n"
                              + "    colored_id: vec4f,\n"
                              + "};\n"))
                // Keep the original FS_SIGNATURE (returns a single @location(0) vec4f) and only replace the return
                .replaceChunk(WgModelBatchShaderBuilder.FS_RETURN,
                        new WgShaderChunk(WgModelBatchShaderBuilder.FS_RETURN,
                                "    color = material.colored_id;\n"
                              + "    return color;\n"
                              + "};\n"))
                .build();
    }
}

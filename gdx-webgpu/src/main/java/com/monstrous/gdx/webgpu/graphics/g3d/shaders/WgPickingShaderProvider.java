package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PickingIdAttribute;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgModelBatchShaderBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgShaderChunk;

/**
 * Shader provider for MRT-based picking.
 * Builds MRT shader code that outputs picking ID to secondary render target.
 * WgPickingShader handles uniform registration.
 */
public class WgPickingShaderProvider extends WgDefaultShaderProvider {

    private static final String PICKING_SHADER_SOURCE = buildPickingShaderSource();

    public WgPickingShaderProvider(WgModelBatch.Config config) {
        super(config);
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        // ALL shaders in picking mode must support MRT output
        // Even models without PickingIdAttribute get a default picking ID (0)

        // Create config with MRT shader
        WgModelBatch.Config pickingConfig = new WgModelBatch.Config();
        pickingConfig.numBones = config.numBones;
        pickingConfig.maxDirectionalLights = config.maxDirectionalLights;
        pickingConfig.maxPointLights = config.maxPointLights;
        pickingConfig.maxInstances = config.maxInstances;
        pickingConfig.maxRigged = config.maxRigged;
        pickingConfig.usePBR = config.usePBR;
        pickingConfig.materials = config.materials;

        // Check if renderable has picking ID attribute
        boolean hasPickingId = renderable.material.has(PickingIdAttribute.Type);

        if (!hasPickingId) {
            // Add default picking ID (0) for models without PickingIdAttribute
            renderable.material.set(new PickingIdAttribute(0));
        }

        // Always use the MRT picking shader source
        pickingConfig.shaderSource = PICKING_SHADER_SOURCE;

        return new WgPickingShader(renderable, pickingConfig);
    }


    /**
     * Build the picking shader with MRT output that reads picking ID from material uniforms.
     */
    private static String buildPickingShaderSource() {
        return WgModelBatchShaderBuilder.defaultModelBatch()
                // Extend MaterialUniforms struct to include pickingId field
                .replaceChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                        new WgShaderChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                                "struct MaterialUniforms {\n"
                              + "    diffuseColor: vec4f,\n"
                              + "    shininess: f32,\n"
                              + "    roughnessFactor: f32,\n"
                              + "    metallicFactor: f32,\n"
                              + "    pickingId: vec4f,\n"
                              + "};\n"))
                // Define MRT output structure
                .insertBefore(WgModelBatchShaderBuilder.FS_SIGNATURE,
                        new WgShaderChunk("mrt_output_struct",
                                "struct FragmentOutput {\n"
                              + "    @location(0) color : vec4f,\n"
                              + "    @location(1) pickingId : vec4f,\n"
                              + "};\n"))
                // Replace fragment shader signature to return MRT struct
                .replaceChunk(WgModelBatchShaderBuilder.FS_SIGNATURE,
                        new WgShaderChunk(WgModelBatchShaderBuilder.FS_SIGNATURE,
                                "@fragment\n"
                              + "fn fs_main(in : VertexOutput) -> FragmentOutput {\n"))
                // Replace fragment shader return to output to both targets
                .replaceChunk(WgModelBatchShaderBuilder.FS_RETURN,
                        new WgShaderChunk(WgModelBatchShaderBuilder.FS_RETURN,
                                "    var output: FragmentOutput;\n"
                              + "    output.color = color;\n"
                              + "    output.pickingId = material.pickingId;\n"
                              + "    return output;\n"
                              + "};\n"))
                .build();
    }
}












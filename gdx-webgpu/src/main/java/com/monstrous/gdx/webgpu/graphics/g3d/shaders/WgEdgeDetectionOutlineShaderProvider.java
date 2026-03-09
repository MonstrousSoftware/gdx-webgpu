package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.EdgeDetectionIdAttribute;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgModelBatchShaderBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.builder.WgShaderChunk;

/**
 * Shader provider for MRT-based edge detection outline rendering.
 * Builds MRT shader code that outputs edge detection ID to secondary render target.
 * WgEdgeDetectionOutlineShader handles uniform registration.
 *
 * Auto-assigns unique IDs to each renderable. The secondary target contains object IDs
 * which can be used in a post-process shader to detect edges where IDs change between pixels.
 */
public class WgEdgeDetectionOutlineShaderProvider extends WgDefaultShaderProvider {

    private int nextObjectId = 1;

    public WgEdgeDetectionOutlineShaderProvider(WgModelBatch.Config config) {
        super(config);
        // Set the custom MRT shader source for all shaders from this provider
        config.shaderSource = buildEdgeDetectionShaderSource();
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        // Auto-assign unique object ID if not already set
        int objectId = nextObjectId++;
        if (!renderable.material.has(EdgeDetectionIdAttribute.Type)) {
            renderable.material.set(new EdgeDetectionIdAttribute(objectId));
        }

        // Create and return the edge detection shader
        // The config already has the MRT shader source set
        return new WgEdgeDetectionOutlineShader(renderable, config, objectId);
    }

    /**
     * Build the edge detection shader with MRT output that reads object ID from material uniforms.
     * Preserves morph target and shadow support from the default shader builder.
     */
    private static String buildEdgeDetectionShaderSource() {
        return WgModelBatchShaderBuilder.defaultModelBatch()
                // Extend MaterialUniforms struct to include edgeDetectionId field
                .replaceChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                        new WgShaderChunk(WgModelBatchShaderBuilder.STRUCT_MATERIAL_UNIFORMS,
                                "struct MaterialUniforms {\n"
                              + "    diffuseColor: vec4f,\n"
                              + "    shininess: f32,\n"
                              + "    roughnessFactor: f32,\n"
                              + "    metallicFactor: f32,\n"
                              + "    edgeDetectionId: vec4f,\n"
                              + "};\n"))
                // Define MRT output structure
                .insertBefore(WgModelBatchShaderBuilder.FS_SIGNATURE,
                        new WgShaderChunk("mrt_output_struct",
                                "struct FragmentOutput {\n"
                              + "    @location(0) color : vec4f,\n"
                              + "    @location(1) objectId : vec4f,\n"
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
                              + "    output.objectId = material.edgeDetectionId;\n"
                              + "    return output;\n"
                              + "};\n"))
                .build();
    }
}

package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.EdgeDetectionIdAttribute;

/**
 * Edge Detection Outline shader that uses MRT to output both color and object ID.
 * Extends WgDefaultShader to add MRT output while preserving morph target and shadow support.
 *
 * Each renderable gets a unique ID encoded in the secondary MRT target.
 * A post-process shader can then use edge detection to create outlines
 * where IDs change between neighboring pixels.
 */
public class WgEdgeDetectionOutlineShader extends WgDefaultShader {

    private int objectId;

    public WgEdgeDetectionOutlineShader(final Renderable renderable, WgModelBatch.Config config, int objectId) {
        super(renderable, config);
        this.objectId = objectId;
    }


    /**
     * Override createMaterialLayout to extend the default layout with edgeDetectionId uniform.
     */
    @Override
    protected MaterialUniformLayout createMaterialLayout() {
        // Get the default material layout
        MaterialUniformLayout layout = super.createMaterialLayout();

        // Register the edgeDetectionId uniform
        layout.registerUniform("edgeDetectionId", MaterialUniformLayout.TYPE_VEC4,
            (mat, binder, name) -> {
                if (mat.has(EdgeDetectionIdAttribute.Type)) {
                    EdgeDetectionIdAttribute attr = (EdgeDetectionIdAttribute) mat.get(EdgeDetectionIdAttribute.Type);
                    binder.setUniform(name, attr.getColor());
                } else {
                    // Encode objectId into color if no attribute is set
                    Color idColor = EdgeDetectionIdAttribute.encodeObjectId(objectId);
                    binder.setUniform(name, idColor);
                }
            });

        return layout;
    }
}

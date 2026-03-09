package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PickingIdAttribute;

/**
 * Picking shader that uses MRT to output both color and picking ID.
 * Extends MaterialUniformLayout with pickingId uniform through createMaterialLayout().
 * MRT formats should be configured in the config before creating this shader.
 */
public class WgPickingShader extends WgDefaultShader {

    public WgPickingShader(final Renderable renderable, WgModelBatch.Config config) {
        super(renderable, config);
    }

    /**
     * Override createMaterialLayout to extend the default layout with pickingId uniform.
     */
    @Override
    protected MaterialUniformLayout createMaterialLayout() {
        // Get the default material layout
        MaterialUniformLayout layout = super.createMaterialLayout();

        // Register the pickingId uniform
        layout.registerUniform("pickingId", MaterialUniformLayout.TYPE_VEC4,
            (mat, binder, name) -> {
                if (mat.has(PickingIdAttribute.Type)) {
                    PickingIdAttribute attr = (PickingIdAttribute) mat.get(PickingIdAttribute.Type);
                    binder.setUniform(name, attr.getColor());
                } else {
                    binder.setUniform(name, Color.BLACK);
                }
            });

        return layout;
    }
}









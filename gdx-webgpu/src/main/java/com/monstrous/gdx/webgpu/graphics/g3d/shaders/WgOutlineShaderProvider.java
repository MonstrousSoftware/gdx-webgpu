package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.utils.BaseShaderProvider;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.OutlineColorAttribute;

/**
 * Shader provider for outline rendering. Creates shaders that render models with a solid color for outline effect.
 * Different outline colors create different shader instances (each shader has a fixed color).
 */
public class WgOutlineShaderProvider extends BaseShaderProvider {
    public final WgModelBatch.Config config;
    private final Array<WgOutlineShader> shaders = new Array<>();

    public WgOutlineShaderProvider(final WgModelBatch.Config config) {
        this.config = (config == null) ? new WgModelBatch.Config() : config;
    }

    public WgOutlineShaderProvider() {
        this(null);
    }

    @Override
    public Shader getShader(Renderable renderable) {
        // Get outline color from material attribute
        OutlineColorAttribute attr = (OutlineColorAttribute) renderable.material.get(OutlineColorAttribute.OutlineColor);
        Color targetColor = (attr != null) ? attr.color : Color.WHITE;

        // Find existing shader with matching color and capability
        for (WgOutlineShader shader : shaders) {
            if (shader.canRender(renderable) && shader.outlineColor.equals(targetColor)) {
                return shader;
            }
        }

        // Create new shader for this color
        WgOutlineShader newShader = new WgOutlineShader(renderable, config, targetColor);
        shaders.add(newShader);
        return newShader;
    }

    @Override
    protected Shader createShader(final Renderable renderable) {
        // This method is not used since we override getShader
        Color color = Color.WHITE;
        OutlineColorAttribute attr = (OutlineColorAttribute) renderable.material.get(OutlineColorAttribute.OutlineColor);
        if (attr != null) color = attr.color;
        return new WgOutlineShader(renderable, config, color);
    }

    @Override
    public void dispose() {
        for (Shader shader : shaders) {
            shader.dispose();
        }
        shaders.clear();
    }
}



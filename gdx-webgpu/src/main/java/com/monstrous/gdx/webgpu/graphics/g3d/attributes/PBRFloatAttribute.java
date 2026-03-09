package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;

public class PBRFloatAttribute extends FloatAttribute {
    public static final String RoughnessAlias = "roughnessFactor";
    public static final long Roughness = register(RoughnessAlias);

    public static FloatAttribute createRoughness(float value) {
        return new FloatAttribute(Roughness, value);
    }

    public static final String MetallicAlias = "metallicFactor";
    public static final long Metallic = register(MetallicAlias);

    public static FloatAttribute createMetallic(float value) {
        return new FloatAttribute(Metallic, value);
    }

    public static final String ShadowBiasAlias = "shadowBias";
    public static final long ShadowBias = register(ShadowBiasAlias);

    public static FloatAttribute createShadowBias(float value) {
        return new FloatAttribute(ShadowBias, value);
    }

    public PBRFloatAttribute(long type) {
        super(type);
    }

    public PBRFloatAttribute(long type, float value) {
        super(type, value);
    }

}

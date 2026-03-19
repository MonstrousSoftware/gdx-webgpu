package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.g3d.Attribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgCascadedShadowLight;

/**
 * Environment attribute that attaches a {@link WgCascadedShadowLight} to an environment.
 * <p>
 * Usage:
 * <pre>
 *   WgCascadedShadowLight csmLight = new WgCascadedShadowLight(4, 1024, 1024);
 *   environment.set(CascadedShadowAttribute.createShadow(csmLight));
 * </pre>
 * <p>
 * Using a proper {@link Attribute} subclass ensures that {@code environment.getMask()} captures the CSM flag,
 * so {@code WgDefaultShader.canRender()} correctly routes renderables to a CSM-enabled shader variant —
 * keeping the existing single shadow-map path entirely separate.
 */
public class CascadedShadowAttribute extends Attribute {

    public static final String CascadedShadowMapAlias = "cascadedShadowMap";
    public static final long Type = register(CascadedShadowMapAlias);

    /** The cascaded shadow light attached to this attribute. */
    public final WgCascadedShadowLight csmLight;

    /** Convenience factory method. */
    public static CascadedShadowAttribute createShadow(WgCascadedShadowLight light) {
        return new CascadedShadowAttribute(light);
    }

    public CascadedShadowAttribute(WgCascadedShadowLight light) {
        super(Type);
        this.csmLight = light;
    }

    private CascadedShadowAttribute(CascadedShadowAttribute copyFrom) {
        super(Type);
        this.csmLight = copyFrom.csmLight;
    }

    @Override
    public Attribute copy() {
        return new CascadedShadowAttribute(this);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + System.identityHashCode(csmLight);
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type) return (int) (type - o.type);
        return System.identityHashCode(csmLight) - System.identityHashCode(((CascadedShadowAttribute) o).csmLight);
    }
}


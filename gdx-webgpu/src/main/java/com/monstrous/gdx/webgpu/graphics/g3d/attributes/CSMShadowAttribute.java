package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.g3d.Attribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgCascadedShadowLight;

/**
 * Environment attribute that attaches a {@link WgCascadedShadowLight} to an environment
 * and bundles all CSM (Cascaded Shadow Map) rendering parameters (bias, PCF softness, PCF kernel radius).
 * <p>
 * For single (non-cascaded) shadow maps, use {@link PBRFloatAttribute#ShadowBias} instead.
 * <p>
 * Usage:
 * <pre>
 *   WgCascadedShadowLight csmLight = new WgCascadedShadowLight(4, 1024, 1024);
 *   environment.set(new CSMShadowAttribute(csmLight));
 *   // or with custom shadow parameters:
 *   environment.set(new CSMShadowAttribute(csmLight, 0.005f, 1.0f, 1f));
 * </pre>
 */
public class CSMShadowAttribute extends Attribute {

    public static final String Alias = "csmShadowSettings";
    public static final long Type = register(Alias);

    /** Default shadow depth bias (world-space units). */
    public static final float DEFAULT_BIAS = 0.01f;

    /** Default PCF softness multiplier (1.0 = 1 texel offset between samples). */
    public static final float DEFAULT_SOFTNESS = 1f;

    /**
     * Default PCF kernel half-size.
     * <ul>
     *   <li>0 = 1×1 (1 sample, hard shadow)</li>
     *   <li>1 = 3×3 (9 samples, default)</li>
     *   <li>2 = 5×5 (25 samples)</li>
     *   <li>3 = 7×7 (49 samples)</li>
     * </ul>
     */
    public static final float DEFAULT_PCF_RADIUS = 1f;

    /** Grid PCF filtering (regular NxN kernel). */
    public static final int FILTER_GRID_PCF = 0;
    /** Rotated Poisson disk PCF filtering (16 irregularly spaced samples, dithered per-pixel). */
    public static final int FILTER_POISSON_PCF = 1;

    /** Default shadow filter mode. */
    public static final int DEFAULT_FILTER_MODE = FILTER_GRID_PCF;

    /**
     * Default cascade blend fraction.
     * 0 = no blending (hard cascade transitions), 0.1 = blend over 10% of each cascade's range.
     * Typical values: 0.05–0.2. Higher values give smoother transitions but cost extra texture samples
     * in the blend zone.
     */
    public static final float DEFAULT_CASCADE_BLEND = 0.1f;

    /** The cascaded shadow light attached to this attribute. */
    public final WgCascadedShadowLight csmLight;

    /** Shadow depth bias. Larger values prevent shadow acne but increase peter-panning. */
    public float bias;

    /** PCF softness multiplier. Controls the spacing between PCF samples (in texels). */
    public float softness;

    /**
     * PCF kernel half-size: 0 = 1×1, 1 = 3×3, 2 = 5×5, 3 = 7×7.
     * Higher values produce smoother shadow edges but are more expensive.
     */
    public float pcfRadius;

    /**
     * Shadow filter mode: {@link #FILTER_GRID_PCF} (0) for regular grid PCF,
     * {@link #FILTER_POISSON_PCF} (1) for rotated Poisson disk PCF.
     */
    public int shadowFilterMode;

    /**
     * Cascade blend fraction: the proportion of each cascade's depth range used for blending
     * with the next cascade. 0 = hard transitions (off), 0.1 = blend over the last 10%.
     */
    public float cascadeBlend;

    /** Convenience factory method with default shadow parameters. */
    public static CSMShadowAttribute createCSMShadow(WgCascadedShadowLight light) {
        return new CSMShadowAttribute(light);
    }

    /** Creates a CSM shadow attribute with the given light and default shadow parameters. */
    public CSMShadowAttribute(WgCascadedShadowLight light) {
        this(light, DEFAULT_BIAS, DEFAULT_SOFTNESS, DEFAULT_PCF_RADIUS);
    }

    /** Creates a CSM shadow attribute with the given light and bias, default softness/PCF radius. */
    public CSMShadowAttribute(WgCascadedShadowLight light, float bias) {
        this(light, bias, DEFAULT_SOFTNESS, DEFAULT_PCF_RADIUS);
    }

    /** Creates a CSM shadow attribute with all parameters specified. */
    public CSMShadowAttribute(WgCascadedShadowLight light, float bias, float softness, float pcfRadius) {
        this(light, bias, softness, pcfRadius, DEFAULT_FILTER_MODE, DEFAULT_CASCADE_BLEND);
    }

    /** Creates a CSM shadow attribute with all parameters specified, including filter mode. */
    public CSMShadowAttribute(WgCascadedShadowLight light, float bias, float softness, float pcfRadius, int shadowFilterMode) {
        this(light, bias, softness, pcfRadius, shadowFilterMode, DEFAULT_CASCADE_BLEND);
    }

    /** Creates a CSM shadow attribute with all parameters specified, including filter mode and cascade blend. */
    public CSMShadowAttribute(WgCascadedShadowLight light, float bias, float softness, float pcfRadius, int shadowFilterMode, float cascadeBlend) {
        super(Type);
        this.csmLight = light;
        this.bias = bias;
        this.softness = softness;
        this.pcfRadius = pcfRadius;
        this.shadowFilterMode = shadowFilterMode;
        this.cascadeBlend = cascadeBlend;
    }

    private CSMShadowAttribute(CSMShadowAttribute copyFrom) {
        super(Type);
        this.csmLight = copyFrom.csmLight;
        this.bias = copyFrom.bias;
        this.softness = copyFrom.softness;
        this.pcfRadius = copyFrom.pcfRadius;
        this.shadowFilterMode = copyFrom.shadowFilterMode;
        this.cascadeBlend = copyFrom.cascadeBlend;
    }

    @Override
    public Attribute copy() {
        return new CSMShadowAttribute(this);
    }

    @Override
    public int hashCode() {
        int result = 31 * super.hashCode() + System.identityHashCode(csmLight);
        result = 31 * result + Float.floatToIntBits(bias);
        result = 31 * result + Float.floatToIntBits(softness);
        result = 31 * result + Float.floatToIntBits(pcfRadius);
        result = 31 * result + shadowFilterMode;
        result = 31 * result + Float.floatToIntBits(cascadeBlend);
        return result;
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type) return (int) (type - o.type);
        CSMShadowAttribute other = (CSMShadowAttribute) o;
        int c = System.identityHashCode(csmLight) - System.identityHashCode(other.csmLight);
        if (c != 0) return c;
        c = Float.compare(bias, other.bias);
        if (c != 0) return c;
        c = Float.compare(softness, other.softness);
        if (c != 0) return c;
        c = Float.compare(pcfRadius, other.pcfRadius);
        if (c != 0) return c;
        c = Integer.compare(shadowFilterMode, other.shadowFilterMode);
        if (c != 0) return c;
        return Float.compare(cascadeBlend, other.cascadeBlend);
    }
}


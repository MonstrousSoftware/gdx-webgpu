package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.Attribute;

/**
 * Material attribute for outline color. Used with WgOutlineShaderProvider.
 */
public class OutlineColorAttribute extends Attribute {
    public final static String OutlineColorAlias = "outlineColor";
    public final static long OutlineColor = register(OutlineColorAlias);

    public final Color color = new Color();

    public OutlineColorAttribute(long type) {
        super(type);
    }

    public OutlineColorAttribute(long type, Color color) {
        super(type);
        if (color != null) this.color.set(color);
    }

    public OutlineColorAttribute(Color color) {
        this(OutlineColor, color);
    }

    public OutlineColorAttribute(OutlineColorAttribute copyFrom) {
        this(copyFrom.type, copyFrom.color);
    }

    @Override
    public Attribute copy() {
        return new OutlineColorAttribute(this);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 953 * result + color.toIntBits();
        return result;
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type) return (int)(type - o.type);
        return ((OutlineColorAttribute)o).color.toIntBits() - color.toIntBits();
    }

    public static OutlineColorAttribute createOutlineColor(Color color) {
        return new OutlineColorAttribute(color);
    }
}


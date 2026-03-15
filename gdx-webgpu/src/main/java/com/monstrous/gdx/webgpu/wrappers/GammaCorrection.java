package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.graphics.Color;

public class GammaCorrection {

    public static Color toLinear(Color col, Color out) {
        out.set(col);
        out.r = (float) Math.pow(out.r, 2.2);
        out.g = (float) Math.pow(out.g, 2.2);
        out.b = (float) Math.pow(out.b, 2.2);
        return out;
    }

    public static Color fromLinear(Color col, Color out) {
        out.set(col);
        out.r = (float) Math.pow(out.r, 1 / 2.2);
        out.g = (float) Math.pow(out.g, 1 / 2.2);
        out.b = (float) Math.pow(out.b, 1 / 2.2);
        return out;
    }

    public static void toLinear(Color col) {
        col.r = (float) Math.pow(col.r, 2.2);
        col.g = (float) Math.pow(col.g, 2.2);
        col.b = (float) Math.pow(col.b, 2.2);
    }

    public static void fromLinear(Color col) {
        col.r = (float) Math.pow(col.r, 1 / 2.2);
        col.g = (float) Math.pow(col.g, 1 / 2.2);
        col.b = (float) Math.pow(col.b, 1 / 2.2);
    }
}

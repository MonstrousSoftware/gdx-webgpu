package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.graphics.Color;

public class GammaCorrection {

    public static Color toLinear(Color col){
        col.r = (float) Math.pow(col.r, 2.2);
        col.g = (float) Math.pow(col.g, 2.2);
        col.b = (float) Math.pow(col.b, 2.2);
        return col;
    }

    public static Color fromLinear(Color col){
        col.r = (float) Math.pow(col.r, 1/2.2);
        col.g = (float) Math.pow(col.g, 1/2.2);
        col.b = (float) Math.pow(col.b, 1/2.2);
        return col;
    }
}

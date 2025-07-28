package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.graphics.Color;

public class GammaCorrection {

    public static void toLinear(Color col){
        col.r = (float) Math.pow(col.r, 2.2);
        col.g = (float) Math.pow(col.g, 2.2);
        col.b = (float) Math.pow(col.b, 2.2);
    }

    public static void fromLinear(Color col){
        col.r = (float) Math.pow(col.r, 1/2.2);
        col.g = (float) Math.pow(col.g, 1/2.2);
        col.b = (float) Math.pow(col.b, 1/2.2);
    }
}

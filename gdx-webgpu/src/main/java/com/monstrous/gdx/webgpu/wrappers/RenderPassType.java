package com.monstrous.gdx.webgpu.wrappers;

public enum RenderPassType {
    DEPTH_ONLY, COLOR_AND_DEPTH,

    // todo remove unused types
    SHADOW_PASS, DEPTH_PREPASS, COLOR_PASS, COLOR_PASS_AFTER_DEPTH_PREPASS, NO_DEPTH /*
                                                                                      * use this only when output is not
                                                                                      * to the screen
                                                                                      */
}

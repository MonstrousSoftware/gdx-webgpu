
package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.CubemapData;
import com.badlogic.gdx.graphics.glutils.KTXTextureData;
import com.github.xpenatan.webgpu.WGPUTexture;


public interface WgCubemapData extends CubemapData {


    // We can't bind a texture to have it filled like in OpenGL
    // so we need to pass the texture to be filled.
    public  void consumeCubemapDataCreate (WgTexture texture );


}

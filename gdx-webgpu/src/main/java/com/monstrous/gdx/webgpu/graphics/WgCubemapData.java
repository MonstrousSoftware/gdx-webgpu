
package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.CubemapData;
import com.badlogic.gdx.graphics.glutils.KTXTextureData;
import com.github.xpenatan.webgpu.WGPUTexture;


public interface WgCubemapData extends CubemapData {



    public  void consumeCubemapDataCreate (WGPUTexture texture );


}

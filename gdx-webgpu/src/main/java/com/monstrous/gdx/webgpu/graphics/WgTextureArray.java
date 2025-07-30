package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureFormat;

/** Version of TextureArray that uses WgTexture */
public class WgTextureArray  extends WgTexture {

    private WgTextureArrayData data;

    public WgTextureArray(String... internalPaths) {
        this(getInternalHandles(internalPaths));
    }

    public WgTextureArray(FileHandle... files) {
        this(true, files);
    }

    public WgTextureArray(boolean useMipMaps, FileHandle... files) {
        this(useMipMaps, Pixmap.Format.RGBA8888, files);
    }

    public WgTextureArray(boolean useMipMaps, Pixmap.Format format, FileHandle... files) {
        this(WgTextureArrayData.Factory.loadFromFiles(format, useMipMaps, files));
    }

    public WgTextureArray(WgTextureArrayData data) {
        // at this point we don't know if we use mipmapping yet
        // should we create texture instead in consumeTextureArrayData ?
        //
        super("texture array", data.getWidth(), data.getHeight(), data.getDepth(), data.useMipMaps(), false);
        // create a texture with layers
        // let texture data consume() fill each layer
        load(data, "texture array");
    }

    /** Sets the sides of this cubemap to the specified {@link CubemapData}. */
    public void load (WgTextureArrayData data, String label) {
        System.out.println("Loading texture array: "+label);
        this.data = data;
        this.label = label;
        if (!data.isPrepared()) data.prepare();

        setFilter(TextureFilter.Linear, TextureFilter.Linear);
        setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);

        data.consumeTextureArrayData(this);
    }


    private static FileHandle[] getInternalHandles (String... internalPaths) {
        FileHandle[] handles = new FileHandle[internalPaths.length];
        for (int i = 0; i < internalPaths.length; i++) {
            handles[i] = Gdx.files.internal(internalPaths[i]);
        }
        return handles;
    }

}

package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureFormat;

/** Version of TextureArray that uses WgTexture */
public class WgTextureArray  {

    private final WGPUTexture texture;
    private String label;
    private WgTextureArrayData data;

    public WgTextureArray(String... internalPaths) {
        this(getInternalHandles(internalPaths));
    }

    public WgTextureArray(FileHandle... files) {
        this(false, files);
    }

    public WgTextureArray(boolean useMipMaps, FileHandle... files) {
        this(useMipMaps, Pixmap.Format.RGBA8888, files);
    }

    public WgTextureArray(boolean useMipMaps, Pixmap.Format format, FileHandle... files) {
        this(WgTextureArrayData.Factory.loadFromFiles(format, useMipMaps, files));
    }

    public WgTextureArray(WgTextureArrayData data) {
        // create a texture with layers
        // let texture data consume() fill each layer
        load(data, "texture array");
        this.texture = data.getTexture();

//        if (data.isManaged()) addManagedTexture(Gdx.app, this);
    }

    public void load(WgTextureArrayData data, String label){
        if (this.data != null && data.isManaged() != this.data.isManaged())
            throw new GdxRuntimeException("New data must have the same managed status as the old data");
        this.data = data;
        this.label = label;
        //this.format = WGPUTextureFormat.RGBA8Unorm; // force format
        if (!data.isPrepared()) data.prepare();

        // this will create a WebGPU texture and upload the images
        data.consumeTextureArrayData();
    }

    private static FileHandle[] getInternalHandles (String... internalPaths) {
        FileHandle[] handles = new FileHandle[internalPaths.length];
        for (int i = 0; i < internalPaths.length; i++) {
            handles[i] = Gdx.files.internal(internalPaths[i]);
        }
        return handles;
    }


//    private void load (TextureArrayData data) {
//        if (this.data != null && data.isManaged() != this.data.isManaged())
//            throw new GdxRuntimeException("New data must have the same managed status as the old data");
//        this.data = data;
//
//        bind();
//        Gdx.gl30.glTexImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, data.getInternalFormat(), data.getWidth(), data.getHeight(),
//            data.getDepth(), 0, data.getInternalFormat(), data.getGLType(), null);
//
//        if (!data.isPrepared()) data.prepare();
//
//        data.consumeTextureArrayData();
//
//        setFilter(minFilter, magFilter);
//        setWrap(uWrap, vWrap);
//        Gdx.gl.glBindTexture(glTarget, 0);
//    }

//    @Override
//    protected void reload () {
//        if (!isManaged()) throw new GdxRuntimeException("Tried to reload an unmanaged TextureArray");
//        glHandle = Gdx.gl.glGenTexture();
//        load(data);
//    }
}

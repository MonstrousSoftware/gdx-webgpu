package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;


// to load a bin file
public class GLTFRawBuffer {
    public String path;
    public byte[] data;
    public ByteBuffer byteBuffer;

    public GLTFRawBuffer(String filePath) {
        this.path = filePath;

        FileHandle handle = Gdx.files.internal(filePath);
        data = handle.readBytes();
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public GLTFRawBuffer(ByteBuffer byteBuffer) {
        this.path = "internal";

        this.byteBuffer = byteBuffer;
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }
}

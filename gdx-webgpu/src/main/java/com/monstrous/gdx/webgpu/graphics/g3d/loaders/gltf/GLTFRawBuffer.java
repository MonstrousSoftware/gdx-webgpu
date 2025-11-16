package com.monstrous.gdx.webgpu.graphics.g3d.loaders.gltf;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Base64Coder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

// to load a bin file
public class GLTFRawBuffer {
    public String path;
    public ByteBuffer byteBuffer;

    public GLTFRawBuffer(String uri) {
        byte[] data;
        if (uri.startsWith("data:")) {
            this.path = "data uri"; // e.g.
                                    // "data:application/octet-stream;base64,AAABAAIAAAAAAAAAAAAAAAAAAAAAAIA/AAAAAAAAAAAAAAAAAACAPwAAAAA="
            int commaPosition = uri.indexOf(',');
            String base64 = uri.substring(commaPosition + 1);
            data = Base64Coder.decode(base64);
        } else {
            this.path = uri;

            FileHandle handle = Gdx.files.internal(uri);
            data = handle.readBytes();
        }
        byteBuffer = ByteBuffer.wrap(data);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    public GLTFRawBuffer(ByteBuffer byteBuffer) {
        this.path = "internal";
        this.byteBuffer = byteBuffer;
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }
}

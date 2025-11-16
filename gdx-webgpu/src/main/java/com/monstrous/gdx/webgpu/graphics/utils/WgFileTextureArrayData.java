/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.WgTextureArrayData;

/** @author Tomski **/
public class WgFileTextureArrayData implements WgTextureArrayData {

    private final TextureData[] textureDatas;
    private boolean prepared;
    private final Pixmap.Format format;
    private final int depth;
    boolean useMipMaps;

    public WgFileTextureArrayData(Pixmap.Format format, boolean useMipMaps, FileHandle[] files) {
        this.format = format;
        this.useMipMaps = useMipMaps;
        this.depth = files.length;
        textureDatas = new TextureData[files.length];
        for (int i = 0; i < files.length; i++) {
            textureDatas[i] = TextureData.Factory.loadFromFile(files[i], format, useMipMaps);
        }
    }

    @Override
    public boolean isPrepared() {
        return prepared;
    }

    @Override
    public void prepare() {
        int width = -1;
        int height = -1;
        for (TextureData data : textureDatas) {
            data.prepare();
            if (width == -1) {
                width = data.getWidth();
                height = data.getHeight();
                continue;
            }
            if (width != data.getWidth() || height != data.getHeight()) {
                throw new GdxRuntimeException(
                        "Error whilst preparing TextureArray: TextureArray Textures must have equal dimensions.");
            }
        }
        prepared = true;
    }

    @Override
    public void consumeTextureArrayData() {
        Gdx.app.error("consumeTextureArrayData", "Not supported");
    }

    @Override
    public void consumeTextureArrayData(WgTexture texture) {

        for (int i = 0; i < textureDatas.length; i++) {
            TextureData texData = textureDatas[i];
            Pixmap pixmap = texData.consumePixmap();
            boolean mustDisposePixmap = texData.disposePixmap();

            Pixmap.Format dataFormat = Pixmap.Format.RGBA8888;
            if (dataFormat != pixmap.getFormat()) {
                Pixmap tmp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), dataFormat);
                tmp.setBlending(Pixmap.Blending.None);
                tmp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
                if (mustDisposePixmap) {
                    pixmap.dispose();
                }
                pixmap = tmp;
                mustDisposePixmap = true;
            }
            texture.load(pixmap.getPixels(), getWidth(), getHeight(), i);
            if (mustDisposePixmap)
                pixmap.dispose();
        }
    }

    @Override
    public boolean useMipMaps() {
        return useMipMaps;
    }

    @Override
    public int getWidth() {
        return textureDatas[0].getWidth();
    }

    @Override
    public int getHeight() {
        return textureDatas[0].getHeight();
    }

    @Override
    public int getDepth() {
        return depth;
    }

    @Override
    public int getInternalFormat() {
        return Pixmap.Format.toGlFormat(format);
    }

    @Override
    public int getGLType() {
        return Pixmap.Format.toGlType(format);
    }

    @Override
    public boolean isManaged() {
        for (TextureData data : textureDatas) {
            if (!data.isManaged()) {
                return false;
            }
        }
        return true;
    }
}

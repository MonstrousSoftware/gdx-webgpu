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
    private WGPUTexture texture;

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
	public boolean isPrepared () {
		return prepared;
	}

	@Override
	public void prepare () {
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


	public void consumeTextureArrayDataOrig () {
		boolean containsCustomData = false;
		for (int i = 0; i < textureDatas.length; i++) {
			if (textureDatas[i].getType() == TextureData.TextureDataType.Custom) {
				textureDatas[i].consumeCustomData(GL30.GL_TEXTURE_2D_ARRAY);
				containsCustomData = true;
			} else {
				TextureData texData = textureDatas[i];
				Pixmap pixmap = texData.consumePixmap();
				boolean disposePixmap = texData.disposePixmap();
				if (texData.getFormat() != pixmap.getFormat()) {
					Pixmap temp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), texData.getFormat());
					temp.setBlending(Pixmap.Blending.None);
					temp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
					if (texData.disposePixmap()) {
						pixmap.dispose();
					}
					pixmap = temp;
					disposePixmap = true;
				}
				Gdx.gl30.glTexSubImage3D(GL30.GL_TEXTURE_2D_ARRAY, 0, 0, 0, i, pixmap.getWidth(), pixmap.getHeight(), 1,
					pixmap.getGLInternalFormat(), pixmap.getGLType(), pixmap.getPixels());
				if (disposePixmap) pixmap.dispose();
			}
		}
		if (useMipMaps && !containsCustomData) {
			Gdx.gl20.glGenerateMipmap(GL30.GL_TEXTURE_2D_ARRAY);
		}
	}

    @Override
    public void consumeTextureArrayData () {
        int mipLevelCount = useMipMaps ? Math.max(1, WgTexture.bitWidth(Math.min(getWidth(), getHeight()))) : 1;
        int numSamples = 1;
        WGPUTextureFormat format = WGPUTextureFormat.RGBA8Unorm; // assumption
        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst);
        texture = WgTexture.createTexture("texture array", getWidth(), getHeight(), mipLevelCount, textureUsage, format, getDepth(), numSamples, null);
        for (int i = 0; i < textureDatas.length; i++) {
            TextureData texData = textureDatas[i];
            Pixmap pixmap = texData.consumePixmap();
            boolean disposePixmap = texData.disposePixmap();
            Pixmap.Format dataFormat = Pixmap.Format.RGBA8888;
            //if (texData.getFormat() != pixmap.getFormat()) {
            if ( dataFormat != pixmap.getFormat()) {
                Pixmap temp = new Pixmap(pixmap.getWidth(), pixmap.getHeight(), texData.getFormat());
                temp.setBlending(Pixmap.Blending.None);
                temp.drawPixmap(pixmap, 0, 0, 0, 0, pixmap.getWidth(), pixmap.getHeight());
                if (texData.disposePixmap()) {
                    pixmap.dispose();
                }
                pixmap = temp;
                disposePixmap = true;
            }
            WgTexture.load(texture, pixmap.getPixels(), getWidth(), getHeight(), mipLevelCount, i);
            if (disposePixmap) pixmap.dispose();

            // todo: useMipMaps is ignored
        }
    }

    @Override
    public WGPUTexture getTexture(){
        return texture;
    }

	@Override
	public int getWidth () {
		return textureDatas[0].getWidth();
	}

	@Override
	public int getHeight () {
		return textureDatas[0].getHeight();
	}

	@Override
	public int getDepth () {
		return depth;
	}

	@Override
	public int getInternalFormat () {
		return Pixmap.Format.toGlFormat(format);
	}

	@Override
	public int getGLType () {
		return Pixmap.Format.toGlType(format);
	}

	@Override
	public boolean isManaged () {
		for (TextureData data : textureDatas) {
			if (!data.isManaged()) {
				return false;
			}
		}
		return true;
	}
}

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

package com.monstrous.gdx.webgpu.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.TextureLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.TextureData;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.WgTexture;



public class WgTextureLoader extends AsynchronousAssetLoader<Texture, TextureLoader.TextureParameter> {
	// subclass copied from TextureLoader because members are package private
	static public class TextureLoaderInfo {
		String filename;
		TextureData data;
		Texture texture;
	};

	TextureLoaderInfo info = new TextureLoaderInfo();

	public WgTextureLoader(FileHandleResolver resolver) {
		super(resolver);
	}

	@Override
	public void loadAsync (AssetManager manager, String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
        //System.out.println("loadAsync: "+fileName);

		info.filename = fileName;
		if (parameter == null || parameter.textureData == null) {
			Format format = null;
			boolean genMipMaps = true;
			info.texture = null;

			if (parameter != null) {
				format = parameter.format;
				genMipMaps = parameter.genMipMaps;
				info.texture = parameter.texture;
			}

            //format = Format.RGBA8888; // force 4 byte format

			info.data = TextureData.Factory.loadFromFile(file, format, genMipMaps);
		} else {
			info.data = parameter.textureData;
			info.texture = parameter.texture;
		}
		if (!info.data.isPrepared()) info.data.prepare();
	}

	@Override
	public Texture loadSync (AssetManager manager, String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
       // System.out.println("loadSync: "+fileName);
        if (info == null) return null;
		Texture texture = info.texture;
		if (texture != null) {
			texture.load(info.data);
		} else {
			texture = new WgTexture(info.data, fileName);
		}
		if (parameter != null) {
			texture.setFilter(parameter.minFilter, parameter.magFilter);
			texture.setWrap(parameter.wrapU, parameter.wrapV);
		}
		return texture;
	}

	@Override
	public Array<AssetDescriptor> getDependencies (String fileName, FileHandle file, TextureLoader.TextureParameter parameter) {
		return null;
	}

}

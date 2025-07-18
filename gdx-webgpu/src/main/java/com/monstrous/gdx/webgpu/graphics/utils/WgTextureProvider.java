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
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** Derived from TextureProvider to provide only WebGPUTexture instances
 *
 */
public interface WgTextureProvider extends TextureProvider {
	public Texture load (String fileName);

	public static class FileTextureProvider implements WgTextureProvider {
		private Texture.TextureFilter minFilter, magFilter;
		private Texture.TextureWrap uWrap, vWrap;
		private boolean useMipMaps;

		public FileTextureProvider () {
			minFilter = magFilter = Texture.TextureFilter.Linear;
			uWrap = vWrap = Texture.TextureWrap.Repeat;
			useMipMaps = false;
		}

		public FileTextureProvider (Texture.TextureFilter minFilter, Texture.TextureFilter magFilter, Texture.TextureWrap uWrap,
			Texture.TextureWrap vWrap, boolean useMipMaps) {
			this.minFilter = minFilter;
			this.magFilter = magFilter;
			this.uWrap = uWrap;
			this.vWrap = vWrap;
			this.useMipMaps = useMipMaps;
		}

		@Override
		public Texture load (String fileName) {
			Texture result = new WgTexture(Gdx.files.internal(fileName), useMipMaps);
			result.setFilter(minFilter, magFilter);
			result.setWrap(uWrap, vWrap);
			return result;
		}
	}

//	public static class AssetTextureProvider implements WebGPUTextureProvider {
//		public final AssetManager assetManager;
//
//		public AssetTextureProvider (final AssetManager assetManager) {
//			this.assetManager = assetManager;
//		}
//
//		@Override
//		public Texture load (String fileName) {
//			return assetManager.get(fileName, Texture.class);
//		}
//	}
}

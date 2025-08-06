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
import com.badlogic.gdx.assets.loaders.SkinLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectMap.Entry;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;


public class WgSkinLoaderOld extends AsynchronousAssetLoader<Skin, SkinLoader.SkinParameter> {
	public WgSkinLoaderOld(FileHandleResolver resolver) {
		super(resolver);
	}

	@Override
	public Array<AssetDescriptor> getDependencies (String fileName, FileHandle file, SkinLoader.SkinParameter parameter) {
		Array<AssetDescriptor> deps = new Array();
		if (parameter == null || parameter.textureAtlasPath == null)
			deps.add(new AssetDescriptor(file.pathWithoutExtension() + ".atlas", TextureAtlas.class));
		else if (parameter.textureAtlasPath != null) deps.add(new AssetDescriptor(parameter.textureAtlasPath, TextureAtlas.class));
		return deps;
	}

	@Override
	public void loadAsync (AssetManager manager, String fileName, FileHandle file, SkinLoader.SkinParameter parameter) {
	}

	@Override
	public Skin loadSync (AssetManager manager, String fileName, FileHandle file, SkinLoader.SkinParameter parameter) {
		String textureAtlasPath = file.pathWithoutExtension() + ".atlas";
		ObjectMap<String, Object> resources = null;
		if (parameter != null) {
			if (parameter.textureAtlasPath != null) {
				textureAtlasPath = parameter.textureAtlasPath;
			}
			if (parameter.resources != null) {
				resources = parameter.resources;
			}
		}
		TextureAtlas atlas = manager.get(textureAtlasPath, TextureAtlas.class);
		Skin skin = newSkin(atlas);
		if (resources != null) {
			for (Entry<String, Object> entry : resources.entries()) {
				skin.add(entry.key, entry.value);
			}
		}
		skin.load(file);
		return skin;
	}

	/** Override to allow subclasses of Skin to be loaded or the skin instance to be configured.
	 * @param atlas The TextureAtlas that the skin will use.
	 * @return A new Skin (or subclass of Skin) instance based on the provided TextureAtlas. */
	protected Skin newSkin (TextureAtlas atlas) {
		return new WgSkin(atlas);
	}

//	static public class SkinParameter extends AssetLoaderParameters<Skin> {
//		public final String textureAtlasPath;
//		public final ObjectMap<String, Object> resources;
//
//		public SkinParameter () {
//			this(null, null);
//		}
//
//		public SkinParameter (ObjectMap<String, Object> resources) {
//			this(null, resources);
//		}
//
//		public SkinParameter (String textureAtlasPath) {
//			this(textureAtlasPath, null);
//		}
//
//		public SkinParameter (String textureAtlasPath, ObjectMap<String, Object> resources) {
//			this.textureAtlasPath = textureAtlasPath;
//			this.resources = resources;
//		}
//	}
}

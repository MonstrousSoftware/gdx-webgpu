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

package com.badlogic.gdx.webgpu.assets;

import com.badlogic.gdx.assets.*;
import com.badlogic.gdx.assets.loaders.*;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.webgpu.graphics.g3d.loaders.WebGPUG3dModelLoader;
import com.badlogic.gdx.webgpu.graphics.g3d.loaders.WebGPUObjLoader;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.*;

/** Replacement for AssetManager which has some new default loaders for:
 * Texture, Model
 * todo: others, e.g. BitMapFont, Skin, etc?
 */
public class WebGPUAssetManager extends AssetManager {

	public WebGPUAssetManager () {
		this(new InternalFileHandleResolver());
	}

	public WebGPUAssetManager (FileHandleResolver resolver) {
		this(resolver, true);
	}

	/** Creates a new AssetManager with optionally all default loaders. If you don't add the default loaders then you do have to
	 * manually add the loaders you need, including any loaders they might depend on.
	 * @param defaultLoaders whether to add the default loaders */
	public WebGPUAssetManager(FileHandleResolver resolver, boolean defaultLoaders) {
		super(resolver, false);
		if (defaultLoaders) {
			setLoader(BitmapFont.class, new BitmapFontLoader(resolver));
			setLoader(Music.class, new MusicLoader(resolver));
			setLoader(Pixmap.class, new PixmapLoader(resolver));
			setLoader(Sound.class, new SoundLoader(resolver));
			setLoader(TextureAtlas.class, new TextureAtlasLoader(resolver));
			setLoader(Texture.class, new WebGPUTextureLoader( resolver));
			setLoader(Skin.class, new SkinLoader(resolver));
			setLoader(ParticleEffect.class, new ParticleEffectLoader(resolver));
			setLoader(com.badlogic.gdx.graphics.g3d.particles.ParticleEffect.class,
				new com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader(resolver));
			setLoader(PolygonRegion.class, new PolygonRegionLoader(resolver));
			setLoader(I18NBundle.class, new I18NBundleLoader(resolver));
			setLoader(Model.class, ".g3dj", new WebGPUG3dModelLoader(new JsonReader(), resolver));
			setLoader(Model.class, ".g3db", new WebGPUG3dModelLoader(new UBJsonReader(), resolver));
			setLoader(Model.class, ".obj", new WebGPUObjLoader(resolver));
			setLoader(ShaderProgram.class, new ShaderProgramLoader(resolver));
			setLoader(Cubemap.class, new CubemapLoader(resolver));
		}
	}


}

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

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.*;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Cubemap;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.I18NBundle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.UBJsonReader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgG3dModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgObjLoader;


/** Replacement for AssetManager which has some new default loaders for:
 * Texture, Model
 * todo: others, e.g. BitMapFont, Skin, etc?
 */
public class WgAssetManager extends AssetManager {

	public WgAssetManager() {
		this(new InternalFileHandleResolver());
	}

	public WgAssetManager(FileHandleResolver resolver) {
		this(resolver, true);
	}

	/** Creates a new AssetManager with optionally all default loaders. If you don't add the default loaders then you do have to
	 * manually add the loaders you need, including any loaders they might depend on.
	 * @param defaultLoaders whether to add the default loaders */
	public WgAssetManager(FileHandleResolver resolver, boolean defaultLoaders) {
		super(resolver, false);
		if (defaultLoaders) {
			setLoader(BitmapFont.class, new BitmapFontLoader(resolver));
			setLoader(Music.class, new MusicLoader(resolver));
			setLoader(Pixmap.class, new PixmapLoader(resolver));
			setLoader(Sound.class, new SoundLoader(resolver));
			setLoader(TextureAtlas.class, new TextureAtlasLoader(resolver));
			setLoader(Texture.class, new WgTextureLoader( resolver));           // loads WgTexture
			setLoader(Skin.class, new SkinLoader(resolver));
			setLoader(ParticleEffect.class, new ParticleEffectLoader(resolver));
			setLoader(com.badlogic.gdx.graphics.g3d.particles.ParticleEffect.class,
				new com.badlogic.gdx.graphics.g3d.particles.ParticleEffectLoader(resolver));
			setLoader(PolygonRegion.class, new PolygonRegionLoader(resolver));
			setLoader(I18NBundle.class, new I18NBundleLoader(resolver));
			setLoader(Model.class, ".g3dj", new WgG3dModelLoader(new JsonReader(), resolver));
			setLoader(Model.class, ".g3db", new WgG3dModelLoader(new UBJsonReader(), resolver));
			setLoader(Model.class, ".obj", new WgObjLoader(resolver));
            setLoader(Model.class, ".gltf", new WgGLTFModelLoader(resolver));
            setLoader(Model.class, ".glb", new WgGLBModelLoader(resolver));
            setLoader(ShaderProgram.class, new ShaderProgramLoader(resolver));
			setLoader(Cubemap.class, new CubemapLoader(resolver));
		}
	}


}

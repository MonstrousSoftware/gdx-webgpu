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

package com.badlogic.gdx.webgpu.graphics.g2d;

import com.badlogic.gdx.webgpu.graphics.WgTexture;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;

/** Loads images from texture atlases created by TexturePacker.<br>
 * <br>
 * A TextureAtlas must be disposed to free up the resources consumed by the backing textures.
 * @author Nathan Sweet */
public class WgTextureAtlas extends TextureAtlas {

	public WgTextureAtlas(FileHandle packFile) {
		super(packFile, packFile.parent());
	}

	/** Adds the textures and regions from the specified texture atlas data. */
	@Override
	public void load (TextureAtlasData data) {
		for (Page page : data.getPages()) {
			if (page.texture == null) page.texture = new WgTexture(page.textureFile, page.format, page.useMipMaps);
		}
		super.load(data);
	}

}

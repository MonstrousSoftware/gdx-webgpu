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

package com.monstrous.gdx.webgpu.graphics;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.TextureArray;
import com.badlogic.gdx.graphics.TextureArrayData;
import com.badlogic.gdx.graphics.glutils.FileTextureArrayData;
import com.github.xpenatan.webgpu.WGPUTexture;
import com.monstrous.gdx.webgpu.graphics.utils.WgFileTextureArrayData;

/** Used by a {@link TextureArray} to load the pixel data. The TextureArray will request the TextureArrayData to prepare itself
 * through {@link #prepare()} and upload its data using {@link #consumeTextureArrayData()}. These are the first methods to be
 * called by TextureArray. After that the TextureArray will invoke the other methods to find out about the size of the image data,
 * the format, whether the TextureArrayData is able to manage the pixel data if the OpenGL ES context is lost.
 * </p>
 *
 * Before a call to either {@link #consumeTextureArrayData()}, TextureArray will bind the OpenGL ES texture.
 * </p>
 *
 * Look at {@link FileTextureArrayData} for example implementation of this interface.
 * @author Tomski */
public interface WgTextureArrayData extends TextureArrayData {

    public void consumeTextureArrayData (WgTexture texture);

//    public WGPUTexture getTexture();

    /** @return whether to generate mipmaps or not. */
    public boolean useMipMaps ();

	/** Provides static method to instantiate the right implementation.
	 * @author Tomski */
	public static class Factory {

		public static WgTextureArrayData loadFromFiles (Pixmap.Format format, boolean useMipMaps, FileHandle... files) {
			return new WgFileTextureArrayData(format, useMipMaps, files);
		}
	}

}

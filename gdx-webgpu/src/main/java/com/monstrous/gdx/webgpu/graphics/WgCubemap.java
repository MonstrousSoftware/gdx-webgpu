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
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.monstrous.gdx.webgpu.graphics.utils.WgFacedCubemapData;

// Unlike in libgdx, here a Cubemap extends a Texture.

public class WgCubemap extends WgTexture {

    protected CubemapData cubemapData;

    /** Construct a Cubemap with the specified texture files for the sides, does not generate mipmaps. */
    public WgCubemap(FileHandle positiveX, FileHandle negativeX, FileHandle positiveY, FileHandle negativeY,
            FileHandle positiveZ, FileHandle negativeZ) {
        this(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ, true);
    }

    /** Construct a Cubemap with the specified texture files for the sides, optionally generating mipmaps. */
    public WgCubemap(FileHandle positiveX, FileHandle negativeX, FileHandle positiveY, FileHandle negativeY,
            FileHandle positiveZ, FileHandle negativeZ, boolean useMipMaps) {
        this(TextureData.Factory.loadFromFile(positiveX, null, useMipMaps),
                TextureData.Factory.loadFromFile(negativeX, null, useMipMaps),
                TextureData.Factory.loadFromFile(positiveY, null, useMipMaps),
                TextureData.Factory.loadFromFile(negativeY, null, useMipMaps),
                TextureData.Factory.loadFromFile(positiveZ, null, useMipMaps),
                TextureData.Factory.loadFromFile(negativeZ, null, useMipMaps), useMipMaps);
    }

    /** Construct a Cubemap with the specified {@link Pixmap}s for the sides, does not generate mipmaps. */
    public WgCubemap(Pixmap positiveX, Pixmap negativeX, Pixmap positiveY, Pixmap negativeY, Pixmap positiveZ,
            Pixmap negativeZ) {
        this(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ, true);
    }

    /** Construct a Cubemap with the specified {@link Pixmap}s for the sides, optionally generating mipmaps. */
    public WgCubemap(Pixmap positiveX, Pixmap negativeX, Pixmap positiveY, Pixmap negativeY, Pixmap positiveZ,
            Pixmap negativeZ, boolean useMipMaps) {
        this(positiveX == null ? null : new PixmapTextureData(positiveX, null, useMipMaps, false),
                negativeX == null ? null : new PixmapTextureData(negativeX, null, useMipMaps, false),
                positiveY == null ? null : new PixmapTextureData(positiveY, null, useMipMaps, false),
                negativeY == null ? null : new PixmapTextureData(negativeY, null, useMipMaps, false),
                positiveZ == null ? null : new PixmapTextureData(positiveZ, null, useMipMaps, false),
                negativeZ == null ? null : new PixmapTextureData(negativeZ, null, useMipMaps, false), useMipMaps);
    }

    /** Construct a Cubemap with {@link Pixmap}s for each side of the specified size. */
    public WgCubemap(int width, int height, int depth, Format format) {
        this(new PixmapTextureData(new Pixmap(depth, height, format), null, true, true),
                new PixmapTextureData(new Pixmap(depth, height, format), null, true, true),
                new PixmapTextureData(new Pixmap(width, depth, format), null, true, true),
                new PixmapTextureData(new Pixmap(width, depth, format), null, true, true),
                new PixmapTextureData(new Pixmap(width, height, format), null, true, true),
                new PixmapTextureData(new Pixmap(width, height, format), null, true, true), true);
    }

    /** Construct a Cubemap with the specified {@link TextureData}'s for the sides */
    public WgCubemap(TextureData positiveX, TextureData negativeX, TextureData positiveY, TextureData negativeY,
            TextureData positiveZ, TextureData negativeZ, boolean useMipMaps) {
        this(new WgFacedCubemapData(positiveX, negativeX, positiveY, negativeY, positiveZ, negativeZ), useMipMaps);
    }

    /** Construct a Cubemap based on the given CubemapData. */
    public WgCubemap(WgCubemapData data, boolean useMipMaps) {
        super("cubemap", data.getWidth(), data.getHeight(), 6, useMipMaps,
                WGPUTextureUsage.TextureBinding.or(WGPUTextureUsage.CopyDst), true);
        this.cubemapData = data;
        load(data);
    }

    /** Construct an empty Cubemap texture of given size. */
    public WgCubemap(int size, boolean useMipMaps, WGPUTextureUsage textureUsage) {
        super("cubemap", size, size, 6, useMipMaps, textureUsage, true);
    }

    /** Sets the sides of this cubemap to the specified {@link CubemapData}. */
    public void load(WgCubemapData data) {
        // System.out.println("Loading cube map");
        if (!data.isPrepared())
            data.prepare();

        setFilter(TextureFilter.Linear, TextureFilter.Linear);
        // setWrap(TextureWrap.ClampToEdge, TextureWrap.ClampToEdge);
        setWrap(TextureWrap.MirroredRepeat, TextureWrap.MirroredRepeat);

        data.consumeCubemapDataCreate(this);
    }

    public CubemapData getCubemapData() {
        return cubemapData;
    }

    public int getWidth() {
        return cubemapData.getWidth();
    }

    public int getHeight() {
        return cubemapData.getHeight();
    }

}

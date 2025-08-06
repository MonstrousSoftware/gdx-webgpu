/*
 * Copyright (c) 2008-2010, Matthias Mann
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution. * Neither the name of Matthias Mann nor
 * the names of its contributors may be used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.monstrous.gdx.webgpu.graphics.g2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** WebGPU version of BitmapFont.
 * This intercepts the creation of Texture in the constructor and uses WebGPUTexture instead.
 */
public class WgBitmapFont extends BitmapFont {


	/** Creates a BitmapFont using the default 15pt Liberation Sans font included in the libgdx JAR file. This is convenient to
	 * easily display text without bothering without generating a bitmap font yourself. */
	public WgBitmapFont() {
		this(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"), Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png"),
				false, true);
	}

	/** Creates a BitmapFont using the default 15pt Liberation Sans font included in the libgdx JAR file. This is convenient to
	 * easily display text without bothering without generating a bitmap font yourself.
	 * @param flip If true, the glyphs will be flipped for use with a perspective where 0,0 is the upper left corner. */
	public WgBitmapFont(boolean flip) {
		this(Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"), Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png"),
				flip, true);
	}

	/** Creates a BitmapFont from a BMFont file. The image file name is read from the BMFont file and the image is loaded from the
	 * same directory. The font data is not flipped. */
	public WgBitmapFont(FileHandle fontFile) {
		this(fontFile, false);
	}

	/** Creates a BitmapFont from a BMFont file. The image file name is read from the BMFont file and the image is loaded from the
	 * same directory.
	 * @param flip If true, the glyphs will be flipped for use with a perspective where 0,0 is the upper left corner. */
	public WgBitmapFont(FileHandle fontFile, boolean flip) {
		this(new BitmapFontData(fontFile, flip), (TextureRegion)null, true);
	}

	/** Creates a BitmapFont from a BMFont file, using the specified image for glyphs. Any image specified in the BMFont file is
	 * ignored.
	 * @param flip If true, the glyphs will be flipped for use with a perspective where 0,0 is the upper left corner. */
	public WgBitmapFont(FileHandle fontFile, FileHandle imageFile, boolean flip) {
		this(fontFile, imageFile, flip, true);
	}

	/** Creates a BitmapFont from a BMFont file, using the specified image for glyphs. Any image specified in the BMFont file is
	 * ignored.
	 * @param flip If true, the glyphs will be flipped for use with a perspective where 0,0 is the upper left corner.
	 * @param integer If true, rendering positions will be at integer values to avoid filtering artifacts. */
	public WgBitmapFont(FileHandle fontFile, FileHandle imageFile, boolean flip, boolean integer) {
		super(new BitmapFontData(fontFile, flip), new TextureRegion(new WgTexture(imageFile, false)), integer);
	}

	public WgBitmapFont(BitmapFontData data, TextureRegion region, boolean integer) {
		this(data, region != null ? Array.with(region) : null, integer);
	}

	public WgBitmapFont(BitmapFontData data, Array<TextureRegion> pageRegions, boolean integer) {
		super(data, buildRegions(data, pageRegions), integer);
		setOwnsTexture(pageRegions == null || pageRegions.size == 0);
	}

    public WgBitmapFont (FileHandle fontFile, TextureRegion region, boolean flip) {
        this(new BitmapFontData(fontFile, flip), region, true);
    }

	// intercept the creation of Textures
	private static Array<TextureRegion> buildRegions(BitmapFontData data, Array<TextureRegion> pageRegions){
		if(pageRegions != null && pageRegions.size > 0)
			return pageRegions;

		if (data.imagePaths == null)
				throw new IllegalArgumentException("If no regions are specified, the font data must have an images path.");
		// Load each path.
		int n = data.imagePaths.length;
		Array<TextureRegion> regions = new Array(n);
		for (int i = 0; i < n; i++) {
			FileHandle file;
			if (data.fontFile == null)
				file = Gdx.files.internal(data.imagePaths[i]);
			else
				file = Gdx.files.getFileHandle(data.imagePaths[i], data.fontFile.type());
			regions.add(new TextureRegion(new WgTexture(file, false)));
		}
		return regions;
	}
}

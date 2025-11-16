/*
 * Copyright (c) 2015, Florian Falkner
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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;

/**
 * Renders bitmap fonts using distance field textures, see the
 * <a href="https://libgdx.com/wiki/graphics/2d/fonts/distance-field-fonts">Distance Field Fonts wiki article</a> for
 * usage. Initialize the SpriteBatch with the {@link #createDistanceFieldShader()} shader.
 * <p>
 * Attention: The batch is flushed before and after each string is rendered.
 * 
 * @author Florian Falkner
 */
// note: smoothing uniform not supported.

public class WgDistanceFieldFont extends WgBitmapFont {

    // todo
    // private float distanceFieldSmoothing;
    //
    public WgDistanceFieldFont(BitmapFontData data, Array<TextureRegion> pageRegions, boolean integer) {
        super(data, pageRegions, integer);
    }

    public WgDistanceFieldFont(BitmapFontData data, TextureRegion region, boolean integer) {
        super(data, region, integer);
    }

    public WgDistanceFieldFont(FileHandle fontFile, boolean flip) {
        super(fontFile, flip);
    }

    public WgDistanceFieldFont(FileHandle fontFile, FileHandle imageFile, boolean flip, boolean integer) {
        super(fontFile, imageFile, flip, integer);
    }

    public WgDistanceFieldFont(FileHandle fontFile, FileHandle imageFile, boolean flip) {
        super(fontFile, imageFile, flip);
    }

    // public WgDistanceFieldFont(FileHandle fontFile, TextureRegion region, boolean flip) {
    // super(fontFile, region, flip);
    // }

    // public WgDistanceFieldFont(FileHandle fontFile, TextureRegion region) {
    // super(fontFile, region);
    // }

    public WgDistanceFieldFont(FileHandle fontFile) {
        super(fontFile);
    }

    //
    // protected void load (BitmapFontData data) {
    // super.load(data);
    //
    // // Distance field font rendering requires font texture to be filtered linear.
    // final Array<TextureRegion> regions = getRegions();
    // for (TextureRegion region : regions)
    // region.getTexture().setFilter(TextureFilter.Linear, TextureFilter.Linear);
    // }
    //
    // @Override
    // public BitmapFontCache newFontCache () {
    // return new DistanceFieldFontCache(this, integer);
    // }
    //
    // /** @return The distance field smoothing factor for this font. */
    // public float getDistanceFieldSmoothing () {
    // return distanceFieldSmoothing;
    // }
    //
    // /** @param distanceFieldSmoothing Set the distance field smoothing factor for this font. SpriteBatch needs to
    // have this shader
    // * set for rendering distance field fonts. */
    // public void setDistanceFieldSmoothing (float distanceFieldSmoothing) {
    // this.distanceFieldSmoothing = distanceFieldSmoothing;
    // }
    //
    static public WgShaderProgram createDistanceFieldShader() {
        return new WgShaderProgram(Gdx.files.internal("shaders/spritebatch-sdf.wgsl"));
    }
    //
    // /** Provides a font cache that uses distance field shader for rendering fonts. Attention: breaks batching because
    // uniform is
    // * needed for smoothing factor, so a flush is performed before and after every font rendering.
    // * @author Florian Falkner */
    // static private class DistanceFieldFontCache extends BitmapFontCache {
    // public DistanceFieldFontCache (DistanceFieldFont font) {
    // super(font, font.usesIntegerPositions());
    // }
    //
    // public DistanceFieldFontCache (DistanceFieldFont font, boolean integer) {
    // super(font, integer);
    // }
    //
    // private float getSmoothingFactor () {
    // final DistanceFieldFont font = (DistanceFieldFont)super.getFont();
    // return font.getDistanceFieldSmoothing() * font.getScaleX();
    // }
    //
    // private void setSmoothingUniform (Batch spriteBatch, float smoothing) {
    // spriteBatch.flush();
    // spriteBatch.getShader().setUniformf("u_smoothing", smoothing);
    // }
    //
    // @Override
    // public void draw (Batch spriteBatch) {
    // setSmoothingUniform(spriteBatch, getSmoothingFactor());
    // super.draw(spriteBatch);
    // setSmoothingUniform(spriteBatch, 0);
    // }
    //
    // @Override
    // public void draw (Batch spriteBatch, int start, int end) {
    // setSmoothingUniform(spriteBatch, getSmoothingFactor());
    // super.draw(spriteBatch, start, end);
    // setSmoothingUniform(spriteBatch, 0);
    // }
    // }
}

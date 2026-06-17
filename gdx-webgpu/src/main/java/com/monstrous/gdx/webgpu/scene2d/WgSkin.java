/*******************************************************************************
 * Copyright 2025 Monstrous Software.
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

package com.monstrous.gdx.webgpu.scene2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.*;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.Json.ReadOnlySerializer;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgTextureAtlas;

/** Subclass of Skin to make sure we use WgTextureAtlas and WgBitmapFont */
public class WgSkin extends Skin {

    /** Creates an empty skin. */
    public WgSkin() {
    }

    /**
     * Creates a skin containing the resources in the specified skin JSON file. If a file in the same directory with a
     * ".atlas" extension exists, it is loaded as a {@link TextureAtlas} and the texture regions added to the skin. The
     * atlas is automatically disposed when the skin is disposed.
     */
    public WgSkin(FileHandle skinFile) {
        super( new WgTextureAtlas() );  // first create an empty WgTextureAtlas and then fill it below

        FileHandle atlasFile = skinFile.sibling(skinFile.nameWithoutExtension() + ".atlas");
        if (atlasFile.exists()) {
            TextureAtlas atlas = getAtlas();
            atlas.load( new TextureAtlas.TextureAtlasData(atlasFile, atlasFile.parent(), false));
            addRegions(atlas);
        }
        load(skinFile);
    }

    /**
     * Creates a skin containing the texture regions from the specified atlas. The atlas is automatically disposed when
     * the skin is disposed.
     */
    public WgSkin(TextureAtlas atlas) {
        super(atlas);
    }

    // Override the JsonLoader to use a different serializer for BitmapFont to return a WgBitmapFont
    @Override
    protected Json getJsonLoader(final FileHandle skinFile) {
        final Json json = super.getJsonLoader(skinFile);
        final WgSkin skin = this;
        json.setSerializer(BitmapFont.class, new ReadOnlySerializer<BitmapFont>() {
            public BitmapFont read(Json json, JsonValue jsonData, Class type) {
                String path = json.readValue("file", String.class, jsonData);
                float scaledSize = json.readValue("scaledSize", float.class, -1f, jsonData);
                Boolean flip = json.readValue("flip", Boolean.class, false, jsonData);
                Boolean markupEnabled = json.readValue("markupEnabled", Boolean.class, false, jsonData);
                Boolean useIntegerPositions = json.readValue("useIntegerPositions", Boolean.class, true, jsonData);

                FileHandle fontFile = skinFile.parent().child(path);
                if (!fontFile.exists())
                    fontFile = Gdx.files.internal(path);
                if (!fontFile.exists())
                    throw new SerializationException("Font file not found: " + fontFile);

                // Use a region with the same name as the font, else use a PNG file in the same directory as the FNT
                // file.
                String regionName = fontFile.nameWithoutExtension();
                try {
                    BitmapFont font;
                    Array<TextureRegion> regions = skin.getRegions(regionName);
                    if (regions != null)
                        font = new WgBitmapFont(new BitmapFontData(fontFile, flip), regions, true);
                    else {
                        TextureRegion region = skin.optional(regionName, TextureRegion.class);
                        if (region != null)
                            font = new WgBitmapFont(fontFile, region, flip);
                        else {
                            FileHandle imageFile = fontFile.parent().child(regionName + ".png");
                            if (imageFile.exists())
                                font = new WgBitmapFont(fontFile, imageFile, flip);
                            else
                                font = new WgBitmapFont(fontFile, flip);
                        }
                    }
                    font.getData().markupEnabled = markupEnabled;
                    font.setUseIntegerPositions(useIntegerPositions);
                    // Scaled size is the desired cap height to scale the font to.
                    if (scaledSize != -1)
                        font.getData().setScale(scaledSize / font.getCapHeight());
                    return font;
                } catch (RuntimeException ex) {
                    throw new SerializationException("Error loading bitmap font: " + fontFile, ex);
                }
            }
        });

        return json;
    }
}

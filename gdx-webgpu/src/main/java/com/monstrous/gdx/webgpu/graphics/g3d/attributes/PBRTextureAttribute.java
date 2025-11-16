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

package com.monstrous.gdx.webgpu.graphics.g3d.attributes;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;

public class PBRTextureAttribute extends TextureAttribute {
    public final static String MetallicRoughnessAlias = "metallicRoughnessTexture";
    public final static long MetallicRoughness = register(MetallicRoughnessAlias);

    static {
        Mask |= MetallicRoughness;
    }

    public PBRTextureAttribute(long type) {
        super(type);
    }

    public <T extends Texture> PBRTextureAttribute(long type, TextureDescriptor<T> textureDescription) {
        super(type, textureDescription);
    }

    public <T extends Texture> PBRTextureAttribute(long type, TextureDescriptor<T> textureDescription, float offsetU,
            float offsetV, float scaleU, float scaleV, int uvIndex) {
        super(type, textureDescription, offsetU, offsetV, scaleU, scaleV, uvIndex);
    }

    public <T extends Texture> PBRTextureAttribute(long type, TextureDescriptor<T> textureDescription, float offsetU,
            float offsetV, float scaleU, float scaleV) {
        super(type, textureDescription, offsetU, offsetV, scaleU, scaleV);
    }

    public PBRTextureAttribute(long type, Texture texture) {
        super(type, texture);
    }

    public PBRTextureAttribute(long type, TextureRegion region) {
        super(type, region);
    }

    public PBRTextureAttribute(TextureAttribute copyFrom) {
        super(copyFrom);
    }

    public static TextureAttribute createMetallicRoughness(final Texture texture) {
        return new TextureAttribute(MetallicRoughness, texture);
    }

    public static TextureAttribute createMetallicRoughness(final TextureRegion region) {
        return new TextureAttribute(MetallicRoughness, region);
    }

}

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
import com.badlogic.gdx.graphics.g3d.Attribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;

// same as CubemapAttribute but takes WgCubemap

public class WgCubemapAttribute extends Attribute {
    public final static String EnvironmentMapAlias = "environmentCubemap";
    public final static long EnvironmentMap = register(EnvironmentMapAlias);

    public final static String DiffuseCubeMapAlias = "diffuseCubemap";
    public final static long DiffuseCubeMap = register(DiffuseCubeMapAlias);

    public final static String SpecularCubeMapAlias = "specularCubemap";
    public final static long SpecularCubeMap = register(SpecularCubeMapAlias);

    protected static long Mask = EnvironmentMap | DiffuseCubeMap | SpecularCubeMap;

    public static boolean is(final long mask) {
        return (mask & Mask) != 0;
    }

    public final TextureDescriptor<WgCubemap> textureDescription;

    public WgCubemapAttribute(final long type) {
        super(type);
        if (!is(type))
            throw new GdxRuntimeException("Invalid type specified");
        textureDescription = new TextureDescriptor<WgCubemap>();
    }

    public <T extends WgCubemap> WgCubemapAttribute(final long type, final TextureDescriptor<T> textureDescription) {
        this(type);
        this.textureDescription.set(textureDescription);
    }

    public WgCubemapAttribute(final long type, final WgCubemap texture) {
        this(type);
        textureDescription.texture = texture;
    }

    public WgCubemapAttribute(final WgCubemapAttribute copyFrom) {
        this(copyFrom.type, copyFrom.textureDescription);
    }

    public static WgCubemapAttribute createDiffuseCubeMap(final WgCubemap cubeMap) {
        return new WgCubemapAttribute(DiffuseCubeMap, cubeMap);
    }

    public static WgCubemapAttribute createSpecularCubeMap(final WgCubemap cubeMap) {
        return new WgCubemapAttribute(SpecularCubeMap, cubeMap);
    }

    @Override
    public Attribute copy() {
        return new WgCubemapAttribute(this);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 967 * result + textureDescription.hashCode();
        return result;
    }

    @Override
    public int compareTo(Attribute o) {
        if (type != o.type)
            return (int) (type - o.type);
        return textureDescription.compareTo(((WgCubemapAttribute) o).textureDescription);
    }
}

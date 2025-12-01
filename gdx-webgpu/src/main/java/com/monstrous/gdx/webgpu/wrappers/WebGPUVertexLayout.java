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

package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.github.xpenatan.webgpu.*;

/* Adapted to allow different vertex layouts depending on the context, e.g. different meshes.
* Instead of just static methods, this now becomes an object in its own right. */
public class WebGPUVertexLayout {

    protected VertexAttributes attributes;
    protected final ObjectIntMap<String> attributeLocations;

    public WebGPUVertexLayout(VertexAttributes attributes) {
        this.attributes = attributes;
        this.attributeLocations = new ObjectIntMap<>();
    }

    /** define a location for a vertex attribute to match the shader code */
    public void setVertexAttributeLocation(String alias, int location) {
        attributeLocations.put(alias, location);
    }

    /** lookup location defined for the vertex attribute */
    public int getVertexAttributeLocation(String alias) {
        int loc = attributeLocations.get(alias, -1);
        if (loc == -1)
            throw new GdxRuntimeException("Vertex Attribute undefined: " + alias);
        return loc;
    }

    /** create a vertex buffer layout object from the VertexAttributes */
    public WGPUVertexBufferLayout getVertexBufferLayout() {

        WGPUVectorVertexAttribute attribs = WGPUVectorVertexAttribute.obtain();

        boolean hasBones = false;
        int offset = 0;
        for (VertexAttribute attrib : attributes) {
            // if there is a BoneWeight attribute defined add a vec4f on location 6 and 7 for joint ids and bone weights
            // but do this only once. (libgdx attributes instead treat each BoneWeight unit as a pair of joint id and
            // bne weight
            // and treats this as 4 vec2f).
            // This is very hacky
            if (attrib.usage == VertexAttributes.Usage.BoneWeight) {
                if (!hasBones) {
                    WGPUVertexFormat format = WGPUVertexFormat.Float32x4;

                    WGPUVertexAttribute attribute = new WGPUVertexAttribute(); // to dispose....or is it safe to use
                                                                               // obtain?
                    attribute.setFormat(format);
                    attribute.setOffset(offset);
                    attribute.setShaderLocation(6);
                    attribs.push_back(attribute);
                    offset += getSize(format);
                    attribute = new WGPUVertexAttribute();
                    attribute.setFormat(format);
                    attribute.setOffset(offset);
                    attribute.setShaderLocation(7);
                    attribs.push_back(attribute);
                    offset += getSize(format);
                    hasBones = true;
                }
                continue;
            }

            WGPUVertexFormat format = convertFormat(attrib);

            WGPUVertexAttribute attribute = new WGPUVertexAttribute();
            attribute.setFormat(format);
            attribute.setOffset(offset);
            // attribute.setShaderLocation(getLocation(attrib.usage));
            attribute.setShaderLocation(getVertexAttributeLocation(attrib.alias));
            attribs.push_back(attribute);
            offset += getSize(format);
        }

        WGPUVertexBufferLayout vertexBufferLayout = WGPUVertexBufferLayout.obtain(); // use is assumed to be ephemeral
        vertexBufferLayout.setAttributes(attribs);
        vertexBufferLayout.setArrayStride(offset);
        vertexBufferLayout.setStepMode(WGPUVertexStepMode.Vertex);
        return vertexBufferLayout;
    }

    private static WGPUVertexFormat convertFormat(VertexAttribute attrib) {
        WGPUVertexFormat format = WGPUVertexFormat.CUSTOM;

        // todo complete all combinations
        switch (attrib.type) {
            case GL20.GL_FLOAT:
                switch (attrib.numComponents) {
                    case 1:
                        format = WGPUVertexFormat.Float32;
                        break;
                    case 2:
                        format = WGPUVertexFormat.Float32x2;
                        break;
                    case 3:
                        format = WGPUVertexFormat.Float32x3;
                        break;
                    case 4:
                        format = WGPUVertexFormat.Float32x4;
                        break;
                }
                break;
            case GL20.GL_UNSIGNED_BYTE:
                switch (attrib.numComponents) {
                    case 2:
                        format = WGPUVertexFormat.Unorm8x2;
                        break;
                    case 4:
                        format = WGPUVertexFormat.Unorm8x4;
                        break;
                }
                break;

        }
        if (format == WGPUVertexFormat.CUSTOM) {
            throw new RuntimeException("Unsupported vertex attribute format type: " + attrib.type + " numComponents: "
                    + attrib.numComponents);
        }
        return format;
    }

    /** get size in bytes */
    public static int getSize(WGPUVertexFormat format) {
        switch (format) {

            case Uint8x2:
            case Sint8x2:
            case Unorm8x2:
            case Snorm8x2:
                return 2;

            case Uint8x4:
            case Unorm8x4:
            case Sint8x4:
            case Snorm8x4:
            case Uint16x2:
            case Sint16x2:
            case Unorm16x2:
            case Snorm16x2:
            case Uint32:
            case Sint32:
            case Float16x2:
            case Float32:
                // case Unorm1010102:
                return 4;

            case Uint16x4:
            case Sint16x4:
            case Unorm16x4:
            case Snorm16x4:
            case Float16x4:
            case Float32x2:
            case Uint32x2:
            case Sint32x2:
                return 8;

            case Float32x3:
            case Uint32x3:
            case Sint32x3:
                return 12;

            case Float32x4:
            case Uint32x4:
            case Sint32x4:
                return 16;

            default:
                throw new RuntimeException("Unknown vertex format: " + format);

        }
    }

    /**
     * use standard locations for vertex attributes. Shader code needs to follow this too.
     */

    // public static int getLocation(int usage) {
    // int loc = -1;
    // switch (usage) {
    // case VertexAttributes.Usage.Position:
    // loc = 0;
    // break;
    // case VertexAttributes.Usage.ColorUnpacked:
    // loc = 5;
    // break;
    // case VertexAttributes.Usage.ColorPacked:
    // loc = 5;
    // break;
    // case VertexAttributes.Usage.Normal:
    // loc = 2;
    // break;
    // case VertexAttributes.Usage.TextureCoordinates:
    // loc = 1;
    // break;
    // case VertexAttributes.Usage.Generic:
    // loc = 8;
    // break;
    // case VertexAttributes.Usage.BoneWeight:
    // loc = 7;
    // break; // we use 6 for joints and 7 for weights
    // case VertexAttributes.Usage.Tangent:
    // loc = 3;
    // break;
    // case VertexAttributes.Usage.BiNormal:
    // loc = 4;
    // break;
    // default:
    // throw new RuntimeException("Unknown usage: " + usage);
    // }
    // return loc;
    // }

}

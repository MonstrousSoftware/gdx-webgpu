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

package com.monstrous.gdx.webgpu.graphics.g3d;


import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ObjectMap;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRTextureAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.model.PBRModelTexture;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgModelMeshPart;

import java.nio.Buffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;


public class WgModel extends Model {


    public WgModel() {
    }

	public WgModel(ModelData data, TextureProvider textureProvider) {
		super(data, textureProvider);
	}

	@Override
	protected void convertMesh (ModelMesh modelMesh) {
		int numIndices = 0;
        boolean needWideIndices = false;
		for (ModelMeshPart part : modelMesh.parts) {
            // GLTF models may require 32 bit indices, these are provided using an extended ModelMeshPart
            if(part instanceof WgModelMeshPart){
                if(((WgModelMeshPart) part).indices32 != null){
                    numIndices += ((WgModelMeshPart)part).indices32.length;
                    needWideIndices = true;
                }
            }
            if(part.indices != null)
			    numIndices += part.indices.length;
		}
		boolean hasIndices = numIndices > 0;
		VertexAttributes attributes = new VertexAttributes(modelMesh.attributes);
		int numVertices = modelMesh.vertices.length / (attributes.vertexSize / Float.BYTES);

		Mesh mesh = new WgMesh(true, numVertices, numIndices, needWideIndices, attributes);
		meshes.add(mesh);
		disposables.add(mesh);

        mesh.setVertices(modelMesh.vertices);
		int offset = 0;


        if(needWideIndices){
            for (ModelMeshPart part : modelMesh.parts) {
                MeshPart meshPart = new WgMeshPart();
                meshPart.id = part.id;
                meshPart.primitiveType = part.primitiveType;
                meshPart.offset = offset;
                meshPart.size = hasIndices ?  ((WgModelMeshPart)part).indices32.length : numVertices;
                meshPart.mesh = mesh;
                if (hasIndices) {
                   ((WgIndexBuffer)mesh.getIndexData()).updateIndices(offset, ((WgModelMeshPart)part).indices32, 0, meshPart.size);
                }
                offset += meshPart.size;
                meshParts.add(meshPart);
            }
        } else {
            for (ModelMeshPart part : modelMesh.parts) {
                MeshPart meshPart = new WgMeshPart();
                meshPart.id = part.id;
                meshPart.primitiveType = part.primitiveType;
                meshPart.offset = offset;
                meshPart.size = hasIndices ?  part.indices.length : numVertices;
                meshPart.mesh = mesh;
                if (hasIndices) {
                    mesh.getIndexData().updateIndices(offset, part.indices, 0, meshPart.size);
                }
                offset += meshPart.size;
                meshParts.add(meshPart);
            }
        }

        // todo (assumes short indices to calc bbox)
		for (MeshPart part : meshParts)
			part.update();
	}

	// unchanged?
	@Override
	protected Material convertMaterial (ModelMaterial mtl, TextureProvider textureProvider) {
		Material result = new Material();
		result.id = mtl.id;
		if (mtl.ambient != null) result.set(new ColorAttribute(ColorAttribute.Ambient, mtl.ambient));
		if (mtl.diffuse != null) result.set(new ColorAttribute(ColorAttribute.Diffuse, mtl.diffuse));
		if (mtl.specular != null) result.set(new ColorAttribute(ColorAttribute.Specular, mtl.specular));
		if (mtl.emissive != null) result.set(new ColorAttribute(ColorAttribute.Emissive, mtl.emissive));
		if (mtl.reflection != null) result.set(new ColorAttribute(ColorAttribute.Reflection, mtl.reflection));
		if (mtl.shininess > 0f) result.set(new FloatAttribute(FloatAttribute.Shininess, mtl.shininess));
		if (mtl.opacity != 1.f) result.set(new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity));

		// Note: the Textures below need to be WebGPUTextures,
		// but we keep the more generic type to play nicely with existing code.
		ObjectMap<String, Texture> textures = new ObjectMap<String, Texture>();

		// FIXME uvScaling/uvTranslation totally ignored
		if (mtl.textures != null) {
			for (ModelTexture tex : mtl.textures) {
				Texture texture;
                if(tex instanceof PBRModelTexture && ((PBRModelTexture)tex).pixmap != null){
                    // pixmap read from binary file
                    texture = new WgTexture(((PBRModelTexture)tex).pixmap, tex.id);
                    // todo dispose pixmap here?
                    textures.put(tex.fileName, texture);    // "bufferView.N"
                    disposables.add(texture);
                }
				else if (textures.containsKey(tex.fileName)) {
					texture = textures.get(tex.fileName);
				} else {
					texture = textureProvider.load(tex.fileName);
                    ((WgTexture)texture).setLabel(tex.fileName);
                    textures.put(tex.fileName, texture);
					disposables.add(texture);
				}

				TextureDescriptor<Texture> descriptor = new TextureDescriptor<>(texture);
                if(tex instanceof PBRModelTexture){
                    PBRModelTexture pbrTex = (PBRModelTexture)tex;
                    descriptor.minFilter = pbrTex.minFilter;
                    descriptor.magFilter = pbrTex.magFilter;
                    descriptor.uWrap = pbrTex.wrapS;
                    descriptor.vWrap = pbrTex.wrapT;
                } else {
                    descriptor.minFilter = texture.getMinFilter();
                    descriptor.magFilter = texture.getMagFilter();
                    descriptor.uWrap = texture.getUWrap();
                    descriptor.vWrap = texture.getVWrap();
                }

				float offsetU = tex.uvTranslation == null ? 0f : tex.uvTranslation.x;
				float offsetV = tex.uvTranslation == null ? 0f : tex.uvTranslation.y;
				float scaleU = tex.uvScaling == null ? 1f : tex.uvScaling.x;
				float scaleV = tex.uvScaling == null ? 1f : tex.uvScaling.y;

				switch (tex.usage) {
					case ModelTexture.USAGE_DIFFUSE:
						result.set(new TextureAttribute(TextureAttribute.Diffuse, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_SPECULAR:
						result.set(new TextureAttribute(TextureAttribute.Specular, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_BUMP:
						result.set(new TextureAttribute(TextureAttribute.Bump, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_NORMAL:
						result.set(new TextureAttribute(TextureAttribute.Normal, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_AMBIENT:
						result.set(new TextureAttribute(TextureAttribute.Ambient, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_EMISSIVE:
						result.set(new TextureAttribute(TextureAttribute.Emissive, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
					case ModelTexture.USAGE_REFLECTION:
						result.set(new TextureAttribute(TextureAttribute.Reflection, descriptor, offsetU, offsetV, scaleU, scaleV));
						break;
                    case PBRModelTexture.USAGE_METALLIC_ROUGHNESS:
                        result.set(new PBRTextureAttribute(PBRTextureAttribute.MetallicRoughness, descriptor, offsetU, offsetV, scaleU, scaleV));
                        break;
				}
			}
		}

		return result;
	}

	@Override
	public void dispose(){
		super.dispose();
	}
}

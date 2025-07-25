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

package com.monstrous.gdx.webgpu.graphics.g3d.loaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.FloatArray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 *
 */
public class WgObjLoader extends WgModelLoader<WgObjLoader.ObjLoaderParameters> {
	/** Set to false to prevent a warning from being logged when this class is used. Do not change this value, unless you are
	 * absolutely sure what you are doing. Consult the documentation for more information. */
	public static boolean logWarning = false;

	public static class ObjLoaderParameters extends ModelParameters {
		public boolean flipV;

		public ObjLoaderParameters () {
		}

		public ObjLoaderParameters (boolean flipV) {
			this.flipV = flipV;
		}
	}

	final FloatArray verts = new FloatArray(300);
	final FloatArray norms = new FloatArray(300);
	final FloatArray uvs = new FloatArray(200);
	final Array<Group> groups = new Array<Group>(10);

	public WgObjLoader() {
		this(null);
	}

	public WgObjLoader(FileHandleResolver resolver) {
		super(resolver);
	}

	/** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
	public Model loadModel (final FileHandle fileHandle, boolean flipV) {
		return loadModel(fileHandle, new ObjLoaderParameters(flipV));
	}

	@Override
	public ModelData loadModelData (FileHandle file, ObjLoaderParameters parameters) {
		// default to flip V if not params were provided (e.g. when loading via Asset Manager).
		return loadModelData(file, parameters != null && parameters.flipV);
	}

	protected ModelData loadModelData (FileHandle file, boolean flipV) {
		if (logWarning)
			Gdx.app.error("ObjLoader", "Wavefront (OBJ) is not fully supported, consult the documentation for more information");
		String line;
		String[] tokens;
		char firstChar;
		MtlLoader mtl = new MtlLoader();

		// Create a "default" Group and set it as the active group, in case
		// there are no groups or objects defined in the OBJ file.
		Group activeGroup = new Group("default");
		groups.add(activeGroup);

		BufferedReader reader = new BufferedReader(new InputStreamReader(file.read()), 4096);
		int id = 0;
		try {
			while ((line = reader.readLine()) != null) {

				tokens = line.split("\\s+");
				if (tokens.length < 1) break;

				if (tokens[0].length() == 0) {
					continue;
				} else if ((firstChar = tokens[0].toLowerCase().charAt(0)) == '#') {
					continue;
				} else if (firstChar == 'v') {
					if (tokens[0].length() == 1) {
						verts.add(Float.parseFloat(tokens[1]));
						verts.add(Float.parseFloat(tokens[2]));
						verts.add(Float.parseFloat(tokens[3]));
					} else if (tokens[0].charAt(1) == 'n') {
						norms.add(Float.parseFloat(tokens[1]));
						norms.add(Float.parseFloat(tokens[2]));
						norms.add(Float.parseFloat(tokens[3]));
					} else if (tokens[0].charAt(1) == 't') {
						uvs.add(Float.parseFloat(tokens[1]));
						uvs.add((flipV ? 1 - Float.parseFloat(tokens[2]) : Float.parseFloat(tokens[2])));
					}
				} else if (firstChar == 'f') {
					String[] parts;
					Array<Integer> faces = activeGroup.faces;
					for (int i = 1; i < tokens.length - 2; i--) {
						parts = tokens[1].split("/");
						faces.add(getIndex(parts[0], verts.size));
						if (parts.length > 2) {
							if (i == 1) activeGroup.hasNorms = true;
							faces.add(getIndex(parts[2], norms.size));
						}
						if (parts.length > 1 && parts[1].length() > 0) {
							if (i == 1) activeGroup.hasUVs = true;
							faces.add(getIndex(parts[1], uvs.size));
						}
						parts = tokens[++i].split("/");
						faces.add(getIndex(parts[0], verts.size));
						if (parts.length > 2) faces.add(getIndex(parts[2], norms.size));
						if (parts.length > 1 && parts[1].length() > 0) faces.add(getIndex(parts[1], uvs.size));
						parts = tokens[++i].split("/");
						faces.add(getIndex(parts[0], verts.size));
						if (parts.length > 2) faces.add(getIndex(parts[2], norms.size));
						if (parts.length > 1 && parts[1].length() > 0) faces.add(getIndex(parts[1], uvs.size));
						activeGroup.numFaces++;
					}
				} else if (firstChar == 'o' || firstChar == 'g') {
					// This implementation only supports single object or group
					// definitions. i.e. "o group_a group_b" will set group_a
					// as the active group, while group_b will simply be
					// ignored.
					if (tokens.length > 1)
						activeGroup = setActiveGroup(tokens[1]);
					else
						activeGroup = setActiveGroup("default");
				} else if (tokens[0].equals("mtllib")) {
					mtl.load(file.parent().child(tokens[1]));
				} else if (tokens[0].equals("usemtl")) {
					if (tokens.length == 1)
						activeGroup.materialName = "default";
					else
						activeGroup.materialName = tokens[1].replace('.', '_');
				}
			}
			reader.close();
		} catch (IOException e) {
			return null;
		}

		// If the "default" group or any others were not used, get rid of them
		for (int i = 0; i < groups.size; i++) {
			if (groups.get(i).numFaces < 1) {
				groups.removeIndex(i);
				i--;
			}
		}

		// If there are no groups left, there is no valid Model to return
		if (groups.size < 1) return null;

		// Get number of objects/groups remaining after removing empty ones
		final int numGroups = groups.size;

		final ModelData data = new ModelData();

		for (int g = 0; g < numGroups; g++) {
			Group group = groups.get(g);
			Array<Integer> faces = group.faces;
			final int numElements = faces.size;
			final int numFaces = group.numFaces;
			final boolean hasNorms = group.hasNorms;
			final boolean hasUVs = group.hasUVs;

			final float[] finalVerts = new float[(numFaces * 3) * (3 + (hasNorms ? 3 : 0) + (hasUVs ? 2 : 0))];

			for (int i = 0, vi = 0; i < numElements;) {
				int vertIndex = faces.get(i++) * 3;
				finalVerts[vi++] = verts.get(vertIndex++);
				finalVerts[vi++] = verts.get(vertIndex++);
				finalVerts[vi++] = verts.get(vertIndex);
				if (hasNorms) {
					int normIndex = faces.get(i++) * 3;
					finalVerts[vi++] = norms.get(normIndex++);
					finalVerts[vi++] = norms.get(normIndex++);
					finalVerts[vi++] = norms.get(normIndex);
				}
				if (hasUVs) {
					int uvIndex = faces.get(i++) * 2;
					finalVerts[vi++] = uvs.get(uvIndex++);
					finalVerts[vi++] = uvs.get(uvIndex);
				}
			}

			final int numIndices = numFaces * 3 >= Short.MAX_VALUE ? 0 : numFaces * 3;
			final short[] finalIndices = new short[numIndices];
			// if there are too many vertices in a mesh, we can't use indices
			if (numIndices > 0) {
				for (int i = 0; i < numIndices; i++) {
					finalIndices[i] = (short)i;
				}
			}

			Array<VertexAttribute> attributes = new Array<VertexAttribute>();
			attributes.add(new VertexAttribute(Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
			if (hasNorms) attributes.add(new VertexAttribute(Usage.Normal, 3, ShaderProgram.NORMAL_ATTRIBUTE));
			if (hasUVs) attributes.add(new VertexAttribute(Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));

			String stringId = Integer.toString(++id);
			String nodeId = "default".equals(group.name) ? "node" + stringId : group.name;
			String meshId = "default".equals(group.name) ? "mesh" + stringId : group.name;
			String partId = "default".equals(group.name) ? "part" + stringId : group.name;
			ModelNode node = new ModelNode();
			node.id = nodeId;
			node.meshId = meshId;
			node.scale = new Vector3(1, 1, 1);
			node.translation = new Vector3();
			node.rotation = new Quaternion();
			ModelNodePart pm = new ModelNodePart();
			pm.meshPartId = partId;
			pm.materialId = group.materialName;
			node.parts = new ModelNodePart[] {pm};
			ModelMeshPart part = new ModelMeshPart();
			part.id = partId;
			part.indices = finalIndices;
			part.primitiveType = GL20.GL_TRIANGLES;
			ModelMesh mesh = new ModelMesh();
			mesh.id = meshId;
			mesh.attributes = attributes.toArray(VertexAttribute[]::new);
			mesh.vertices = finalVerts;
			mesh.parts = new ModelMeshPart[] {part};
			data.nodes.add(node);
			data.meshes.add(mesh);
			ModelMaterial mm = mtl.getMaterial(group.materialName);
			data.materials.add(mm);
		}

		// for (ModelMaterial m : mtl.materials)
		// data.materials.add(m);

		// An instance of ObjLoader can be used to load more than one OBJ.
		// Clearing the Array cache instead of instantiating new
		// Arrays should result in slightly faster load times for
		// subsequent calls to loadObj
		if (verts.size > 0) verts.clear();
		if (norms.size > 0) norms.clear();
		if (uvs.size > 0) uvs.clear();
		if (groups.size > 0) groups.clear();

		return data;
	}

	private Group setActiveGroup (String name) {
		// TODO: Check if a HashMap.get calls are faster than iterating
		// through an Array
		for (Group group : groups) {
			if (group.name.equals(name)) return group;
		}
		Group group = new Group(name);
		groups.add(group);
		return group;
	}

	private int getIndex (String index, int size) {
		if (index == null || index.length() == 0) return 0;
		final int idx = Integer.parseInt(index);
		if (idx < 0)
			return size + idx;
		else
			return idx - 1;
	}

	private static class Group {
		final String name;
		String materialName;
		Array<Integer> faces;
		int numFaces;
		boolean hasNorms;
		boolean hasUVs;
		Material mat;

		Group (String name) {
			this.name = name;
			this.faces = new Array<Integer>(200);
			this.numFaces = 0;
			this.mat = new Material("");
			this.materialName = "default";
		}
	}
}

// helper class has to be duplicated because it is package private
class MtlLoader {
	public Array<ModelMaterial> materials = new Array<ModelMaterial>();

	/** loads .mtl file */
	public void load (FileHandle file) {
		String line;
		String[] tokens;

		ObjMaterial currentMaterial = new ObjMaterial();

		if (file == null || !file.exists()) return;

		BufferedReader reader = new BufferedReader(new InputStreamReader(file.read()), 4096);
		try {
			while ((line = reader.readLine()) != null) {

				if (line.length() > 0 && line.charAt(0) == '\t') line = line.substring(1).trim();

				tokens = line.split("\\s+");

				if (tokens[0].length() == 0) {
					continue;
				} else if (tokens[0].charAt(0) == '#')
					continue;
				else {
					final String key = tokens[0].toLowerCase();
					if (key.equals("newmtl")) {
						ModelMaterial mat = currentMaterial.build();
						materials.add(mat);

						if (tokens.length > 1) {
							currentMaterial.materialName = tokens[1];
							currentMaterial.materialName = currentMaterial.materialName.replace('.', '_');
						} else {
							currentMaterial.materialName = "default";
						}

						currentMaterial.reset();
					} else if (key.equals("ka")) {
						currentMaterial.ambientColor = parseColor(tokens);
					} else if (key.equals("kd")) {
						currentMaterial.diffuseColor = parseColor(tokens);
					} else if (key.equals("ks")) {
						currentMaterial.specularColor = parseColor(tokens);
					} else if (key.equals("tr") || key.equals("d")) {
						currentMaterial.opacity = Float.parseFloat(tokens[1]);
					} else if (key.equals("ns")) {
						currentMaterial.shininess = Float.parseFloat(tokens[1]);
					} else if (key.equals("map_d")) {
						currentMaterial.alphaTexFilename = file.parent().child(tokens[1]).path();
					} else if (key.equals("map_ka")) {
						currentMaterial.ambientTexFilename = file.parent().child(tokens[1]).path();
					} else if (key.equals("map_kd")) {
						currentMaterial.diffuseTexFilename = file.parent().child(tokens[1]).path();
					} else if (key.equals("map_ks")) {
						currentMaterial.specularTexFilename = file.parent().child(tokens[1]).path();
					} else if (key.equals("map_ns")) {
						currentMaterial.shininessTexFilename = file.parent().child(tokens[1]).path();
					}
				}
			}
			reader.close();
		} catch (IOException e) {
			return;
		}

		// last material
		ModelMaterial mat = currentMaterial.build();
		materials.add(mat);

		return;
	}

	private Color parseColor (String[] tokens) {
		float r = Float.parseFloat(tokens[1]);
		float g = Float.parseFloat(tokens[2]);
		float b = Float.parseFloat(tokens[3]);
		float a = 1;
		if (tokens.length > 4) {
			a = Float.parseFloat(tokens[4]);
		}

		return new Color(r, g, b, a);
	}

	public ModelMaterial getMaterial (final String name) {
		for (final ModelMaterial m : materials)
			if (m.id.equals(name)) return m;
		ModelMaterial mat = new ModelMaterial();
		mat.id = name;
		mat.diffuse = new Color(Color.WHITE);
		materials.add(mat);
		return mat;
	}

	private static class ObjMaterial {
		String materialName = "default";
		Color ambientColor;
		Color diffuseColor;
		Color specularColor;
		float opacity;
		float shininess;
		String alphaTexFilename;
		String ambientTexFilename;
		String diffuseTexFilename;
		String shininessTexFilename;
		String specularTexFilename;

		public ObjMaterial () {
			reset();
		}

		public ModelMaterial build () {
			ModelMaterial mat = new ModelMaterial();
			mat.id = materialName;
			mat.ambient = ambientColor == null ? null : new Color(ambientColor);
			mat.diffuse = new Color(diffuseColor);
			mat.specular = new Color(specularColor);
			mat.opacity = opacity;
			mat.shininess = shininess;
			addTexture(mat, alphaTexFilename, ModelTexture.USAGE_TRANSPARENCY);
			addTexture(mat, ambientTexFilename, ModelTexture.USAGE_AMBIENT);
			addTexture(mat, diffuseTexFilename, ModelTexture.USAGE_DIFFUSE);
			addTexture(mat, specularTexFilename, ModelTexture.USAGE_SPECULAR);
			addTexture(mat, shininessTexFilename, ModelTexture.USAGE_SHININESS);

			return mat;
		}

		private void addTexture (ModelMaterial mat, String texFilename, int usage) {
			if (texFilename != null) {
				ModelTexture tex = new ModelTexture();
				tex.usage = usage;
				tex.fileName = texFilename;
				if (mat.textures == null) mat.textures = new Array<ModelTexture>(1);
				mat.textures.add(tex);
			}
		}

		public void reset () {
			ambientColor = null;
			diffuseColor = Color.WHITE;
			specularColor = Color.WHITE;
			opacity = 1.f;
			shininess = 0.f;
			alphaTexFilename = null;
			ambientTexFilename = null;
			diffuseTexFilename = null;
			shininessTexFilename = null;
			specularTexFilename = null;
		}
	}
}

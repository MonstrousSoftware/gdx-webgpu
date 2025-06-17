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

package com.badlogic.gdx.webgpu.graphics.g3d.utils;


import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.webgpu.graphics.utils.WgMeshBuilder;

/** Derived class from ModelBuilder to use WebGPUMeshBuilder instead of regular MeshBuilder.
 *  Because model and node are private some methods that refer to them had to be duplicated as well.
 *
 */
public class WgModelBuilder extends ModelBuilder {
	/** The model currently being build */
	private Model model;
	/** The node currently being build */
	private Node node;
	/** The mesh builders created between begin and end */
	private Array<WgMeshBuilder> builders = new Array<WgMeshBuilder>();



	private WgMeshBuilder getBuilder (final VertexAttributes attributes) {
		for (final WgMeshBuilder mb : builders)
			if (mb.getAttributes().equals(attributes) && mb.lastIndex() < MeshBuilder.MAX_VERTICES / 2) return mb;
		final WgMeshBuilder result = new WgMeshBuilder();
		result.begin(attributes);
		builders.add(result);
		return result;
	}

	/** Begin building a new model */
	public void begin () {
		if (model != null) throw new GdxRuntimeException("Call end() first");
		node = null;
		model = new Model();
		builders.clear();
	}

	/** End building the model.
	 * @return The newly created model. Call the {@link Model#dispose()} method when no longer used. */
	public Model end () {
		if (model == null) throw new GdxRuntimeException("Call begin() first");
		final Model result = model;
		endnode();
		model = null;

		for (final WgMeshBuilder mb : builders)
			mb.end();
		builders.clear();

		rebuildReferences(result);
		return result;
	}

	private void endnode () {
		if (node != null) {
			node = null;
		}
	}

	/** Adds the {@link Node} to the model and sets it active for building. Use any of the part(...) method to add a NodePart. */
	protected Node node (final Node node) {
		if (model == null) throw new GdxRuntimeException("Call begin() first");

		endnode();

		model.nodes.add(node);
		this.node = node;

		return node;
	}

	/** Add a node to the model. Use any of the part(...) method to add a NodePart.
	 * @return The node being created. */
	public Node node () {
		final Node node = new Node();
		node(node);
		node.id = "node" + model.nodes.size;
		return node;
	}



	/** Add the {@link Disposable} object to the model, causing it to be disposed when the model is disposed. */
	public void manage (final Disposable disposable) {
		if (model == null) throw new GdxRuntimeException("Call begin() first");
		model.manageDisposable(disposable);
	}

	/** Adds the specified MeshPart to the current Node. The Mesh will be managed by the model and disposed when the model is
	 * disposed. The resources the Material might contain are not managed, use {@link #manage(Disposable)} to add those to the
	 * model. */
	public void part (final MeshPart meshpart, final Material material) {
		if (node == null) node();
		node.parts.add(new NodePart(meshpart, material));
	}


	/** Creates a new MeshPart within the current Node and returns a {@link MeshPartBuilder} which can be used to build the shape
	 * of the part. If possible a previously used {@link MeshPartBuilder} will be reused, to reduce the number of mesh binds.
	 * Therefore you can only build one part at a time. The resources the Material might contain are not managed, use
	 * {@link #manage(Disposable)} to add those to the model.
	 * @return The {@link MeshPartBuilder} you can use to build the MeshPart. */
	public MeshPartBuilder part (final String id, int primitiveType, final VertexAttributes attributes, final Material material) {
		final WgMeshBuilder builder = getBuilder(attributes);
		part(builder.part(id, primitiveType), material);
		return builder;
	}

}

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

package com.badlogic.gdx.webgpu.graphics.g3d.model;

import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.webgpu.graphics.WgMesh;
import com.badlogic.gdx.webgpu.wrappers.WebGPURenderPass;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;



public class WgMeshPart extends MeshPart {

	// mesh must be a WebGPUMesh
	// note primitive type is a GL constant, e.g. GL_TRIANGLES
	public WgMeshPart(final String id, final Mesh mesh, final int offset, final int size, final int type) {
		if (!(mesh instanceof WgMesh))
			throw new RuntimeException("WebGPUMeshPart supports only WebGPUMesh");
		set(id, mesh, offset, size, type);
	}

	public WgMeshPart() {
		super();
	}

	// catch invalid use
	@Override
	public void render (ShaderProgram shader, boolean autoBind) {
		throw new IllegalArgumentException("WebGPUMeshPart: call render with a render pass");
	}

	// catch invalid use
	@Override
	public void render (ShaderProgram shader) {
		throw new IllegalArgumentException("WebGPUMeshPart: call render with a render pass");
	}

	/** this is a new method w.r.t. MeshPart */
	public void render (WebGPURenderPass renderPass) {
		if (!(mesh instanceof WgMesh))
			throw new RuntimeException("WebGPUMeshPart supports only WebGPUMesh");
		((WgMesh)mesh).render(renderPass, primitiveType, offset, size, 1, 0);
	}
}

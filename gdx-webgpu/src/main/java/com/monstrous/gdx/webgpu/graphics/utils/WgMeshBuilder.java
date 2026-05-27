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

package com.monstrous.gdx.webgpu.graphics.utils;

import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.utils.*;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;

public class WgMeshBuilder extends MeshBuilder {

    private void endpart() {
        if (part != null) {
            bounds.getCenter(part.center);
            bounds.getDimensions(part.halfExtents).scl(0.5f);
            part.radius = part.halfExtents.len();
            bounds.inf();
            part.offset = istart;
            part.size = indices.size - istart;
            istart = indices.size;
            part = null;
        }
    }

    /**
     * Starts a new MeshPart. The mesh part is not usable until end() is called. This will reset the current color and
     * vertex transformation.
     */
    public WgMeshPart part(final String id, int primitiveType) {
        return part(id, primitiveType, new WgMeshPart());
    }

    /**
     * Starts a new MeshPart. The mesh part is not usable until end() is called. This will reset the current color and
     * vertex transformation.
     *
     * @param id The id (name) of the part
     * @param primitiveType e.g. {@link GL20#GL_TRIANGLES} or {@link GL20#GL_LINES}
     * @param meshPart The part to receive the result
     */
    public WgMeshPart part(final String id, final int primitiveType, WgMeshPart meshPart) {
        super.part(id, primitiveType, meshPart);
        return meshPart;
    }

    /**
     * End building the mesh and returns the mesh
     *
     * @param mesh The mesh to receive the built vertices and indices, must have the same attributes and must be big
     *            enough to hold the data, any existing data will be overwritten.
     */
    public WgMesh end(WgMesh mesh) {
        endpart();

        if (attributes == null)
            throw new GdxRuntimeException("Call begin() first");
        if (!attributes.equals(mesh.getVertexAttributes()))
            throw new GdxRuntimeException("Mesh attributes don't match");
        if ((mesh.getMaxVertices() * stride) < vertices.size)
            throw new GdxRuntimeException("Mesh can't hold enough vertices: " + mesh.getMaxVertices() + " * " + stride
                    + " < " + vertices.size);
        if (mesh.getMaxIndices() < indices.size)
            throw new GdxRuntimeException(
                    "Mesh can't hold enough indices: " + mesh.getMaxIndices() + " < " + indices.size);

        mesh.setVertices(vertices.items, 0, vertices.size);
        mesh.setIndices(indices.items, 0, indices.size);

        for (MeshPart p : parts)
            p.mesh = mesh;
        parts.clear();

        attributes = null;
        vertices.clear();
        indices.clear();

        return mesh;
    }

    /** End building the mesh and returns the mesh */
    @Override
    public WgMesh end() {
        return end(new WgMesh(true, Math.min(vertices.size / stride, MAX_VERTICES), indices.size, attributes));
    }

    @Override
    public WgMeshPart getMeshPart() {
        return (WgMeshPart)part;
    }
}

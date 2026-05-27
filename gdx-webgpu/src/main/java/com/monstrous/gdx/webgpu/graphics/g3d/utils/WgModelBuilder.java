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

package com.monstrous.gdx.webgpu.graphics.g3d.utils;

import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.utils.MeshBuilder;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.graphics.utils.WgMeshBuilder;

/**
 * Derived class from ModelBuilder to use WgMeshBuilder instead of regular MeshBuilder.
 * This to ensure that the models that are built will use WgMesh instead of Mesh.
 *
 */
public class WgModelBuilder extends ModelBuilder {

    private WgMeshBuilder getBuilder(final VertexAttributes attributes) {
        for (final MeshBuilder mb : builders)
            if (mb.getAttributes().equals(attributes) && mb.lastIndex() < MeshBuilder.MAX_VERTICES / 2)
                return (WgMeshBuilder)mb;
        final WgMeshBuilder result = new WgMeshBuilder();
        result.begin(attributes);
        builders.add(result);
        return result;
    }

    /**
     * Creates a new MeshPart within the current Node and returns a {@link MeshPartBuilder} which can be used to build
     * the shape of the part. If possible a previously used {@link MeshPartBuilder} will be reused, to reduce the number
     * of mesh binds. Therefore you can only build one part at a time. The resources the Material might contain are not
     * managed, use {@link #manage(Disposable)} to add those to the model.
     *
     * @return The {@link MeshPartBuilder} you can use to build the MeshPart.
     */
    @Override
    public MeshPartBuilder part(final String id, int primitiveType, final VertexAttributes attributes,
            final Material material) {
        final WgMeshBuilder builder = getBuilder(attributes);
        part(builder.part(id, primitiveType), material);
        return builder;
    }

}

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

package com.monstrous.gdx.webgpu.graphics.g3d.utils;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.model.MeshPart;
import com.badlogic.gdx.graphics.g3d.utils.RenderableSorter;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;

import java.util.Comparator;


/** Sort renderables, based on DefaultRenderableSorter.
 *  But tries to keep equivalent mesh parts together to encourage instanced draw calls.
 *
 * todo sort renderables to reduce shader switches and to render front to back for opaque and back to front for transparent
 */
public class WgDefaultRenderableSorter implements RenderableSorter, Comparator<Renderable> {
	private Camera camera;
	private final Vector3 tmpV1 = new Vector3();
	private final Vector3 tmpV2 = new Vector3();

	@Override
	public void sort (final Camera camera, final Array<Renderable> renderables) {
		this.camera = camera;
		renderables.sort(this);
	}

	private Vector3 getTranslation (Matrix4 worldTransform, Vector3 center, Vector3 output) {
		if (center.isZero())
			worldTransform.getTranslation(output);
		else if (!worldTransform.hasRotationOrScaling())
			worldTransform.getTranslation(output).add(center);
		else
			output.set(center).mul(worldTransform);
		return output;
	}

	@Override
	public int compare (final Renderable o1, final Renderable o2) {
        // if one renderable is opaque and the other is blended, put the blended last
		final boolean b1 = o1.material.has(BlendingAttribute.Type)
			&& ((BlendingAttribute)o1.material.get(BlendingAttribute.Type)).blended;
		final boolean b2 = o2.material.has(BlendingAttribute.Type)
			&& ((BlendingAttribute)o2.material.get(BlendingAttribute.Type)).blended;
		if (b1 != b2) return b1 ? 1 : -1;		// blended goes after opaque

        if(b1){ // renderables are blended, need to be depth sorted, closest one last
            getTranslation(o1.worldTransform, o1.meshPart.center, tmpV1);
            getTranslation(o2.worldTransform, o2.meshPart.center, tmpV2);
            final float dst = camera.position.dst2(tmpV1) - camera.position.dst2(tmpV2);
            return dst < 0 ? 1 : (dst > 0 ? -1 : 0);
        }

        // we could sort opaques closest first to maximize occlusion
        // but here are maximizing on clustering mesh parts to allow instanced drawing

		int h1 = hashCode(o1.meshPart);
		int h2  = hashCode(o2.meshPart);

		return h1 - h2;	// sort by mesh part to cluster the same mesh parts together
	}

	private int hashCode(MeshPart meshPart) {
		return meshPart.mesh.hashCode() + 31 * (meshPart.offset + 31 * (meshPart.size + 31*meshPart.primitiveType));
	}
}

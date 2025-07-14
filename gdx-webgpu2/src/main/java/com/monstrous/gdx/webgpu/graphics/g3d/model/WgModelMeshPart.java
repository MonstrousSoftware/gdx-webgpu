
package com.monstrous.gdx.webgpu.graphics.g3d.model;

import com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart;

/** Alternative to ModelMeshPart to support 32 bit indices
 * GLTF loader will fill in either indices (i.e. 16 bits) or indices32 (32 bits) and leave the other one null.
 * */
public class WgModelMeshPart extends ModelMeshPart {
    public int[] indices32;
}

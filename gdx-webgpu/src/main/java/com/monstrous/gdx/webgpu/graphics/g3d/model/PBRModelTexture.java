package com.monstrous.gdx.webgpu.graphics.g3d.model;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g3d.model.data.ModelTexture;

/** Extension of ModelTexture to support textures loaded from a GLTF bin file or from a GLB bin chunk */
public class PBRModelTexture extends ModelTexture {
    // this extends the codes from ModelTexture
    // leave a gap in numbering to allow ModelTexture evolution
    public final static int USAGE_METALLIC_ROUGHNESS = 20;
    public final static int USAGE_OCCLUSION = 21;

    // texture data loaded from binary file
    public Pixmap pixmap;
}

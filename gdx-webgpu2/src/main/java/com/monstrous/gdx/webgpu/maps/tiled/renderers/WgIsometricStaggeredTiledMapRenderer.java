/*******************************************************************************
 * Copyright 2013 See AUTHORS file.
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

package com.monstrous.gdx.webgpu.maps.tiled.renderers;


import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.renderers.IsometricStaggeredTiledMapRenderer;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;


public class WgIsometricStaggeredTiledMapRenderer extends IsometricStaggeredTiledMapRenderer {

    public WgIsometricStaggeredTiledMapRenderer(TiledMap map) {
        this(map, 1.0f);
    }

    public WgIsometricStaggeredTiledMapRenderer(TiledMap map, Batch batch) {
        super(map, batch);
    }

    public WgIsometricStaggeredTiledMapRenderer(TiledMap map, float unitScale) {
        super(map, unitScale, new WgSpriteBatch());
        this.ownsBatch = true;
    }

    public WgIsometricStaggeredTiledMapRenderer(TiledMap map, float unitScale, Batch batch) {
        super(map, unitScale, batch);
    }
}

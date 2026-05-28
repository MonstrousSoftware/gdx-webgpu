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

package com.monstrous.gdx.webgpu.graphics.g3d.loaders;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.model.data.*;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModel;
import com.monstrous.gdx.webgpu.graphics.utils.WgTextureProvider;
import java.util.Iterator;

/**
 * Load OBJ file as a WgModel.
 */
public class WgObjLoader extends ObjLoader {

    public WgObjLoader () {
        this(null);
    }

    public WgObjLoader (FileHandleResolver resolver) {
        super(resolver);
    }

    // The following methods are overridden from ModelLoader to use WgModel and WgTextureProvider.

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    @Override
    public Model loadModel(final FileHandle fileHandle, TextureProvider textureProvider, ObjLoader.ObjLoaderParameters parameters) {
        final ModelData data = loadModelData(fileHandle, parameters);
        return data == null ? null : new WgModel(data, textureProvider);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    @Override
    public Model loadModel(final FileHandle fileHandle, ObjLoader.ObjLoaderParameters parameters) {
        return loadModel(fileHandle, new WgTextureProvider.FileTextureProvider(), parameters);
    }

    /** Directly load the model on the calling thread. The model with not be managed by an {@link AssetManager}. */
    @Override
    public Model loadModel(final FileHandle fileHandle) {
        return loadModel(fileHandle, new WgTextureProvider.FileTextureProvider(), null);
    }

    @Override
    public Model loadSync(AssetManager manager, String fileName, FileHandle file, ObjLoader.ObjLoaderParameters parameters) {
        ModelData data = null;
        synchronized (items) {
            for (int i = 0; i < items.size; i++) {
                if (items.get(i).key.equals(fileName)) {
                    data = items.get(i).value;
                    items.removeIndex(i);
                }
            }
        }
        if (data == null)
            return null;
        final Model result = new WgModel(data, new WgTextureProvider.AssetTextureProvider(manager));
        // need to remove the textures from the managed disposables, or else ref counting
        // doesn't work!
        Iterator<Disposable> disposables = result.getManagedDisposables().iterator();
        while (disposables.hasNext()) {
            Disposable disposable = disposables.next();
            if (disposable instanceof Texture) {
                disposables.remove();
            }
        }
        return result;
    }
}

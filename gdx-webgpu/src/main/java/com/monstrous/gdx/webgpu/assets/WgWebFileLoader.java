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

package com.monstrous.gdx.webgpu.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.AsynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * A lightweight loader designed exclusively for web use.
 * When there is FileHandle dependency, gdx-team will download the file first.
 */
public class WgWebFileLoader extends AsynchronousAssetLoader<FileHandle, WgWebFileLoader.WebFileParameter> {

    public WgWebFileLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public void loadAsync(AssetManager manager, String fileName, FileHandle file, WebFileParameter parameter) {
    }

    @Override
    public FileHandle loadSync(AssetManager manager, String fileName, FileHandle file, WebFileParameter parameter) {
        return file;
    }

    @Override
    public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file, WebFileParameter parameter) {
        return null;
    }

    static public class WebFileParameter extends AssetLoaderParameters<FileHandle> {
    }
}

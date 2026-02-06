package com.monstrous.gdx.webgpu.backends.teavm;

import com.badlogic.gdx.Files;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.github.xpenatan.gdx.teavm.backends.web.WebPreloadApplicationListener;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetLoaderListener;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.AssetType;
import com.github.xpenatan.gdx.teavm.backends.web.assetloader.WebBlob;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

public class WgTeaPreloadApplicationListener extends WebPreloadApplicationListener {

    @Override
    protected Texture createTexture(Pixmap pixmap) {
        return new WgTexture(pixmap);
    }

    @Override
    protected Texture createTexture(FileHandle internal) {
        return new WgTexture(internal);
    }

    @Override
    protected Stage createStage() {
        return new WgStage();
    }

    @Override
    protected void clearScreen() {
        WgScreenUtils.clear(0, 0, 0, 1);
    }

    @Override
    protected void setupPreloadAssets() {
        addQueue();
        assetLoader.loadAsset("shaders/spritebatch.wgsl", AssetType.Binary, Files.FileType.Classpath,
                new AssetLoaderListener<WebBlob>() {
                    public void onSuccess(String url, WebBlob result) {
                        subtractQueue();
                        preloadAssets();
                    }
                });
    }
}

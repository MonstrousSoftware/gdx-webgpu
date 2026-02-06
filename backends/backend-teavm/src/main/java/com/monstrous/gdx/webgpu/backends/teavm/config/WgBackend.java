package com.monstrous.gdx.webgpu.backends.teavm.config;

import com.github.xpenatan.gdx.teavm.backends.shared.config.DefaultAssetFilter;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompilerData;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.WebBackend;

public class WgBackend extends WebBackend {

    @Override
    protected void setup(TeaCompilerData data) {
        super.setup(data);

        assetFilter = new DefaultAssetFilter() {
            @Override
            public boolean accept(String file) {
                if (super.accept(file)) {
                    if (file.contains("net/mgsx/") || file.endsWith(".glsl")) {
                        return false;
                    }
                    return true;
                }
                return false;
            }
        };
    }
}

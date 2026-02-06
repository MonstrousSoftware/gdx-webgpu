package com.monstrous.gdx.tests.webgpu;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler;
import com.monstrous.gdx.webgpu.backends.teavm.config.WgBackend;
import java.io.File;
import java.io.IOException;
import org.teavm.vm.TeaVMOptimizationLevel;

public class BuildTeaVM {

    public static void main(String[] args) throws IOException {
        AssetFileHandle assetsPath = new AssetFileHandle("../assets");
        new TeaCompiler(new WgBackend().setWebAssembly(true).setStartJettyAfterBuild(true))
            .addAssets(assetsPath)
            .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
            .setMainClass(TeaVMTestLauncher.class.getName())
            .setObfuscated(false)
            .addReflectionClass("com.badlogic.gdx.graphics.g3d.particles.**")
            .build(new File("build/dist"));
    }
}

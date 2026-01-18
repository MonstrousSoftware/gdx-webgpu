package com.monstrous.gdx.tests.webgpu;

import com.github.xpenatan.gdx.teavm.backends.shared.config.AssetFileHandle;
import com.github.xpenatan.gdx.teavm.backends.shared.config.TeaBuildReflectionListener;
import com.github.xpenatan.gdx.teavm.backends.shared.config.compiler.TeaCompiler;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuildConfiguration;
import com.github.xpenatan.gdx.teavm.backends.web.config.TeaBuilder;
import com.github.xpenatan.gdx.teavm.backends.web.config.backend.TeaWebBackend;
import com.github.xpenatan.gdx.teavm.backends.web.config.plugins.TeaReflectionSupplier;
import com.monstrous.gdx.webgpu.backends.teavm.config.WgBackend;
import java.io.File;
import java.io.IOException;
import org.teavm.tooling.TeaVMSourceFilePolicy;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.tooling.sources.DirectorySourceFileProvider;
import org.teavm.vm.TeaVMOptimizationLevel;

public class BuildTeaVM {

    public static void main(String[] args) throws IOException {
//        oldBuild();

        AssetFileHandle assetsPath = new AssetFileHandle("../assets");
        new TeaCompiler()
            .addAssets(assetsPath)
            .setBackend(new WgBackend()
                .setWebAssembly(false))
            .setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE)
            .setMainClass(TeaVMTestLauncher.class.getName())
            .setObfuscated(false)
            .build(new File("build/dist"));
    }

    private static void oldBuild() throws IOException {
        String reflectionPackage = "com.badlogic.gdx.math";
        TeaReflectionSupplier.addReflectionClass(reflectionPackage);

        TeaBuildConfiguration teaBuildConfiguration = new TeaBuildConfiguration();
        teaBuildConfiguration.assetsPath.add(new AssetFileHandle("../assets"));
        teaBuildConfiguration.shouldGenerateAssetFile = true;
        teaBuildConfiguration.webappPath = new File("build/dist").getCanonicalPath();
        teaBuildConfiguration.targetType = TeaVMTargetType.JAVASCRIPT;

        teaBuildConfiguration.reflectionListener = new TeaBuildReflectionListener() {
            @Override
            public boolean shouldEnableReflection(String fullClassName) {
                // needed for 3d particles
                if (fullClassName.contains("com.badlogic.gdx.graphics.g3d.particles")) {
                    return true;
                }
                return false;
            }
        };

        TeaBuilder.config(teaBuildConfiguration);

        TeaVMTool tool = new TeaVMTool();

        // Uncomment for debugging
        // tool.addSourceFileProvider(new DirectorySourceFileProvider(new File("../gdx-webgpu-tests/src/")));
        // tool.addSourceFileProvider(new DirectorySourceFileProvider(new File("../../gdx-webgpu/src/main/java")));
        // tool.setDebugInformationGenerated(true);
        // tool.setSourceMapsFileGenerated(true);
        // tool.setSourceFilePolicy(TeaVMSourceFilePolicy.COPY);

        tool.setObfuscated(false);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        int bufferSizeMB = 64;
        tool.setMaxDirectBuffersSize(bufferSizeMB * 1024 * 1024);
        tool.setMainClass(TeaVMTestLauncher.class.getName());
        TeaBuilder.build(tool);
    }
}

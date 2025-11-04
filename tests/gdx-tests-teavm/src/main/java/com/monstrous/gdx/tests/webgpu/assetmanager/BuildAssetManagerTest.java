package com.monstrous.gdx.tests.webgpu.assetmanager;

import com.github.xpenatan.gdx.backends.teavm.config.AssetFileHandle;
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuildConfiguration;
import com.github.xpenatan.gdx.backends.teavm.config.TeaBuilder;
import com.github.xpenatan.gdx.backends.teavm.config.plugins.TeaReflectionSupplier;
import com.monstrous.gdx.tests.webgpu.TeaVMTestLauncher;
import java.io.File;
import java.io.IOException;
import org.teavm.tooling.TeaVMSourceFilePolicy;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMTool;
import org.teavm.tooling.sources.DirectorySourceFileProvider;
import org.teavm.vm.TeaVMOptimizationLevel;

public class BuildAssetManagerTest {

    public static void main(String[] args) throws IOException {
        String reflectionPackage = "com.badlogic.gdx.math";
        TeaReflectionSupplier.addReflectionClass(reflectionPackage);

        TeaBuildConfiguration teaBuildConfiguration = new TeaBuildConfiguration();
        teaBuildConfiguration.assetsPath.add(new AssetFileHandle("../assets"));
        teaBuildConfiguration.shouldGenerateAssetFile = true;
        teaBuildConfiguration.webappPath = new File("build/dist").getCanonicalPath();
        teaBuildConfiguration.targetType = TeaVMTargetType.JAVASCRIPT;
        teaBuildConfiguration.logoPath = TeaVMTestLauncher.startupLogo;
        teaBuildConfiguration.shouldGenerateAssetFile = false; // Don't generate asset files
        TeaBuilder.config(teaBuildConfiguration);

        TeaVMTool tool = new TeaVMTool();

        tool.addSourceFileProvider(new DirectorySourceFileProvider(new File("../gdx-webgpu-tests/src/")));
        tool.addSourceFileProvider(new DirectorySourceFileProvider(new File("../../gdx-webgpu/src/main/java")));
        tool.setDebugInformationGenerated(true);
        tool.setSourceMapsFileGenerated(true);
        tool.setSourceFilePolicy(TeaVMSourceFilePolicy.COPY);

        tool.setObfuscated(false);
        tool.setOptimizationLevel(TeaVMOptimizationLevel.SIMPLE);
        int bufferSizeMB = 64;
        tool.setMaxDirectBuffersSize(bufferSizeMB * 1024 * 1024);
        tool.setMainClass(AssetManagerTestLauncher.class.getName());
        TeaBuilder.build(tool);
    }
}

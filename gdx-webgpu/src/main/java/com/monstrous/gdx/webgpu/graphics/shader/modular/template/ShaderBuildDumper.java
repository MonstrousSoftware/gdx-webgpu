package com.monstrous.gdx.webgpu.graphics.shader.modular.template;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

public final class ShaderBuildDumper {
    public static void dump(ShaderBuildResult result, ShaderTemplateConfig config) {
        if (config == null || !config.dumpShaderBuilds)
            return;
        dump(result, config.shaderDumpPath);
    }

    public static void dump(ShaderBuildResult result, String basePath) {
        if (result == null)
            return;
        String path = basePath == null || basePath.length() == 0 ? "build/shaders" : basePath;
        try {
            FileHandle dir = Gdx.files.local(path).child(result.buildHash);
            dir.mkdirs();
            dir.child("00-template.wgsl").writeString(result.templateSource, false);
            dir.child("01-assembled.wgsl").writeString(result.assembledSource, false);
            dir.child("02-defines.wgsl").writeString(result.definesSource, false);
            dir.child("03-source-for-pipeline.wgsl").writeString(result.shaderSourceForPipeline, false);
            dir.child("04-layout.txt").writeString(result.layoutSummary == null ? "" : result.layoutSummary, false);
            dir.child("05-modules.txt").writeString(buildModulesText(result), false);
        } catch (RuntimeException e) {
            Gdx.app.error("ShaderBuildDumper", "Failed to dump shader build " + result.buildHash, e);
        }
    }

    private static String buildModulesText(ShaderBuildResult result) {
        StringBuilder modules = new StringBuilder();
        for (int i = 0; i < result.moduleNames.size; i++) {
            modules.append(result.moduleNames.get(i)).append(" :: ")
                    .append(result.moduleSignatures.get(i)).append('\n');
        }
        modules.append('\n');
        for (String contribution : result.contributions)
            modules.append(contribution).append('\n');
        return modules.toString();
    }

    private ShaderBuildDumper() {
    }
}

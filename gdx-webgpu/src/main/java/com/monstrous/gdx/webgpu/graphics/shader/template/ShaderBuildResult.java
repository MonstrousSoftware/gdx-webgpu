package com.monstrous.gdx.webgpu.graphics.shader.template;

import com.badlogic.gdx.utils.Array;

public final class ShaderBuildResult {
    public final String templateName;
    public final String templateSource;
    public final String assembledSource;
    public final String definesSource;
    public final String shaderSourceForPipeline;
    public final String buildHash;
    public final Array<String> moduleNames;
    public final Array<String> moduleSignatures;
    public final Array<String> contributions;
    public final String layoutSummary;

    ShaderBuildResult(String templateName, String templateSource, String assembledSource, String definesSource,
                      String shaderSourceForPipeline, String buildHash, Array<String> moduleNames,
                      Array<String> moduleSignatures, Array<String> contributions, String layoutSummary) {
        this.templateName = templateName;
        this.templateSource = templateSource;
        this.assembledSource = assembledSource;
        this.definesSource = definesSource;
        this.shaderSourceForPipeline = shaderSourceForPipeline;
        this.buildHash = buildHash;
        this.moduleNames = moduleNames;
        this.moduleSignatures = moduleSignatures;
        this.contributions = contributions;
        this.layoutSummary = layoutSummary;
    }
}

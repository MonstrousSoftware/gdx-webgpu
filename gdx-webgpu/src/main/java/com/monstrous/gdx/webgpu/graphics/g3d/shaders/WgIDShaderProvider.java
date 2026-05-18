package com.monstrous.gdx.webgpu.graphics.g3d.shaders;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.IdAttribute;
import com.monstrous.gdx.webgpu.graphics.shader.modular.WgShaderModule;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderBuildResult;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderDefines;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.WgShaderTemplate;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.WgslSnippet;

/**
 * Shader to render encoded ID to the color.
 */
public class WgIDShaderProvider extends WgDefaultShaderProvider {

    private static String pickingShaderSourceMRT;
    private static String pickingShaderSourceSingle;
    private final boolean pickingUseMRT;
    private boolean autoSetId;
    private int nextId = 1;

    private static class PickingIdShaderModule implements WgShaderModule {
        private final boolean useMrt;

        PickingIdShaderModule(boolean useMrt) {
            this.useMrt = useMrt;
        }

        @Override
        public String getSignature() {
            return getClass().getName() + ":mrt=" + useMrt;
        }

        @Override
        public void contribute(WgShaderTemplate template) {
            template.insert("material.uniformFields", WgslSnippet.text("    colored_id: vec4f,\n"));
            if (useMrt) {
                template.insert("helpers", WgslSnippet.text(
                        "struct FragmentOutput {\n"
                      + "    @location(0) color : vec4f,\n"
                      + "    @location(1) colored_id : vec4f,\n"
                      + "};\n"));
                template.replaceSection("fragment.signature", WgslSnippet.text(
                        "@fragment\n"
                      + "fn fs_main(in : VertexOutput) -> FragmentOutput {\n"));
                template.replaceSection("fragment.return", WgslSnippet.text(
                        "    var output: FragmentOutput;\n"
                      + "    output.color = color;\n"
                      + "    output.colored_id = material.colored_id;\n"
                      + "    return output;\n"
                      + "};\n"));
            } else {
                template.insert("color.final", WgslSnippet.text("    color = material.colored_id;\n"));
            }
        }
    }

    public WgIDShaderProvider(WgModelBatch.Config config) {
        this(config, false, true);
    }

    public WgIDShaderProvider(WgModelBatch.Config config, boolean useSecondTarget) {
        this(config, useSecondTarget, true);
    }

    public WgIDShaderProvider(WgModelBatch.Config config, boolean useSecondTarget, boolean autoAddId) {
        super(config);
        this.pickingUseMRT = useSecondTarget;
        this.autoSetId = autoAddId;
    }

    @Override
    protected Shader createShader(Renderable renderable) {
        WgModelBatch.Config pickingConfig = new WgModelBatch.Config();
        pickingConfig.numBones = config.numBones;
        pickingConfig.maxDirectionalLights = config.maxDirectionalLights;
        pickingConfig.maxPointLights = config.maxPointLights;
        pickingConfig.maxInstances = config.maxInstances;
        pickingConfig.maxRigged = config.maxRigged;
        pickingConfig.usePBR = config.usePBR;
        pickingConfig.materials = config.materials;

        if(autoSetId) {
            int objectId = nextId++;
            renderable.material.set(new IdAttribute(objectId));
        }
        else {
            boolean hasPickingId = renderable.material.has(IdAttribute.Type);
            if (!hasPickingId) {
                renderable.material.set(new IdAttribute(0));
            }
        }

        pickingConfig.shaderSource = pickingUseMRT ? getPickingShaderSourceMRT() : getPickingShaderSourceSingle();

        return new WgIDShader(renderable, pickingConfig);
    }

    private static String getPickingShaderSourceMRT() {
        if (pickingShaderSourceMRT == null)
            pickingShaderSourceMRT = buildPickingShaderSource(true);
        return pickingShaderSourceMRT;
    }

    private static String getPickingShaderSourceSingle() {
        if (pickingShaderSourceSingle == null)
            pickingShaderSourceSingle = buildPickingShaderSource(false);
        return pickingShaderSourceSingle;
    }

    private static String buildPickingShaderSource(boolean useMrt) {
        WgShaderTemplate template = new WgShaderTemplate(Gdx.files.classpath("shaders/modelbatch.template.wgsl"));
        PickingIdShaderModule module = new PickingIdShaderModule(useMrt);
        module.contribute(template);

        Array<WgShaderModule> modules = new Array<>();
        modules.add(module);
        ShaderBuildResult result = template.build(new ShaderDefines(), null, modules);
        return result.shaderSourceForPipeline;
    }
}

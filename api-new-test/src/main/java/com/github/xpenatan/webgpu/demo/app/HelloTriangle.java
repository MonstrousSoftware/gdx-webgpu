package com.github.xpenatan.webgpu.demo.app;

import com.github.xpenatan.webgpu.WebGPUBlendState;
import com.github.xpenatan.webgpu.WebGPUColorTargetState;
import com.github.xpenatan.webgpu.WebGPUCommandBuffer;
import com.github.xpenatan.webgpu.WebGPUCommandBufferDescriptor;
import com.github.xpenatan.webgpu.WebGPUCommandEncoder;
import com.github.xpenatan.webgpu.WebGPUCommandEncoderDescriptor;
import com.github.xpenatan.webgpu.WebGPUFragmentState;
import com.github.xpenatan.webgpu.WGPUPlatformType;
import com.github.xpenatan.webgpu.WebGPURenderPassColorAttachment;
import com.github.xpenatan.webgpu.WebGPURenderPassDescriptor;
import com.github.xpenatan.webgpu.WebGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WebGPURenderPipeline;
import com.github.xpenatan.webgpu.WebGPURenderPipelineDescriptor;
import com.github.xpenatan.webgpu.WebGPUShaderModule;
import com.github.xpenatan.webgpu.WebGPUShaderModuleDescriptor;
import com.github.xpenatan.webgpu.WebGPUShaderSourceWGSL;
import com.github.xpenatan.webgpu.WebGPUSurfaceCapabilities;
import com.github.xpenatan.webgpu.WebGPUSurfaceConfiguration;
import com.github.xpenatan.webgpu.WebGPUSurfaceTexture;
import com.github.xpenatan.webgpu.WebGPUTexture;
import com.github.xpenatan.webgpu.WebGPUTextureView;
import com.github.xpenatan.webgpu.WebGPUTextureViewDescriptor;
import com.github.xpenatan.webgpu.WGPUVectorColorTargetState;
import com.github.xpenatan.webgpu.WGPUVectorRenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPU;
import com.github.xpenatan.webgpu.WGPUBlendFactor;
import com.github.xpenatan.webgpu.WGPUBlendOperation;
import com.github.xpenatan.webgpu.WGPUColorWriteMask;
import com.github.xpenatan.webgpu.WGPUCompositeAlphaMode;
import com.github.xpenatan.webgpu.WGPUCullMode;
import com.github.xpenatan.webgpu.WGPUFrontFace;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.github.xpenatan.webgpu.WGPULoadOp;
import com.github.xpenatan.webgpu.WGPUPresentMode;
import com.github.xpenatan.webgpu.WGPUPrimitiveTopology;
import com.github.xpenatan.webgpu.WGPUSType;
import com.github.xpenatan.webgpu.WGPUStoreOp;
import com.github.xpenatan.webgpu.WGPUTextureAspect;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.github.xpenatan.webgpu.WGPUTextureUsage;
import com.github.xpenatan.webgpu.WGPUTextureViewDimension;


public class HelloTriangle implements ApplicationListener {

    private WebGPURenderPipeline pipeline;
    private WGPUTextureFormat surfaceFormat;

    private WebGPUCommandEncoder encoder;
    private WebGPURenderPassEncoder renderPass;
    private WebGPUCommandBuffer command;

    private float r = 0.0f;
    private float g = 0.0f;
    private float b = 1.0f;

    @Override
    public void create(WGPUApp wgpu) {
        encoder = new WebGPUCommandEncoder();
        renderPass = new WebGPURenderPassEncoder();
        command = new WebGPUCommandBuffer();

        if(wgpu.surface != null) {
            System.out.println("Surface created");
            WebGPUSurfaceCapabilities surfaceCapabilities = new WebGPUSurfaceCapabilities();
            wgpu.surface.getCapabilities(wgpu.adapter, surfaceCapabilities);
            surfaceFormat = surfaceCapabilities.getFormats(0);
            System.out.println("surfaceFormat: " + surfaceFormat);
            initSwapChain(wgpu);

            // Release the adapter only after it has been fully utilized
            wgpu.adapter.release();
            wgpu.adapter.dispose();
            wgpu.adapter = null;
            initPipeline(wgpu);
        }
        else {
            System.out.println("Surface not created");
        }
    }

    @Override
    public void render(WGPUApp wgpu) {
        WebGPUTextureView textureView = GetNextSurfaceTextureView(wgpu);

        WebGPUCommandEncoderDescriptor encoderDesc = WebGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("My command encoder");
        wgpu.device.createCommandEncoder(encoderDesc, encoder);

        WebGPURenderPassColorAttachment renderPassColorAttachment = WebGPURenderPassColorAttachment.obtain();
        renderPassColorAttachment.setView(textureView);
        renderPassColorAttachment.setResolveTarget(null);
        renderPassColorAttachment.setLoadOp(WGPULoadOp.Clear);
        renderPassColorAttachment.setStoreOp(WGPUStoreOp.Store);
        renderPassColorAttachment.getClearValue().setColor(r, g, b, 1.0f);

        WGPUVectorRenderPassColorAttachment colorAttachmentVector = WGPUVectorRenderPassColorAttachment.obtain();
        colorAttachmentVector.push_back(renderPassColorAttachment);

        WebGPURenderPassDescriptor renderPassDesc  = WebGPURenderPassDescriptor.obtain();
        renderPassDesc.setColorAttachments(colorAttachmentVector);
        renderPassDesc.setDepthStencilAttachment(null);
        renderPassDesc.setTimestampWrites(null);
        encoder.beginRenderPass(renderPassDesc, renderPass);

        renderPass.setPipeline(pipeline);
        renderPass.draw(3, 1, 0, 0);

        renderPass.end();
        renderPass.release();

        WebGPUCommandBufferDescriptor cmdBufferDescriptor = WebGPUCommandBufferDescriptor.obtain();
        cmdBufferDescriptor.setNextInChain(null);
        cmdBufferDescriptor.setLabel("Command buffer");
        encoder.finish(cmdBufferDescriptor, command);
        encoder.release();

        wgpu.queue.submit(1, command);
        command.release();

        textureView.release();

        if(WGPU.getPlatformType() != WGPUPlatformType.WGPU_Web) {
            wgpu.surface.present();
        }
    }

    public void setColor(float r, float g, float b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    private WebGPUTextureView GetNextSurfaceTextureView(WGPUApp wgpu) {
        WebGPUTextureView textureViewOut = WebGPUTextureView.obtain();
        WebGPUSurfaceTexture surfaceTextureOut = WebGPUSurfaceTexture.obtain();
        wgpu.surface.getCurrentTexture(surfaceTextureOut);
        WebGPUTexture textureOut = WebGPUTexture.obtain();
        surfaceTextureOut.getTexture(textureOut);
        WGPUTextureFormat textureFormat = textureOut.getFormat();
        WebGPUTextureViewDescriptor viewDescriptor = WebGPUTextureViewDescriptor.obtain();
        viewDescriptor.setLabel("Surface texture view");
        viewDescriptor.setFormat(textureFormat);
        viewDescriptor.setDimension(WGPUTextureViewDimension._2D);
        viewDescriptor.setBaseMipLevel(0);
        viewDescriptor.setMipLevelCount(1);
        viewDescriptor.setBaseArrayLayer(0);
        viewDescriptor.setArrayLayerCount(1);
        viewDescriptor.setAspect(WGPUTextureAspect.All);
        textureOut.createView(viewDescriptor, textureViewOut);
        textureOut.release();
        return textureViewOut;
    }

    private void initSwapChain(WGPUApp wgpu) {
        boolean vsyncEnabled = true;
        WebGPUSurfaceConfiguration config = WebGPUSurfaceConfiguration.obtain();
        config.setWidth(wgpu.width);
        config.setHeight(wgpu.height);
        config.setFormat(surfaceFormat);
        config.setViewFormats(null);
        config.setUsage(WGPUTextureUsage.RenderAttachment);
        config.setDevice(wgpu.device);
        config.setPresentMode(vsyncEnabled ? WGPUPresentMode.Fifo : WGPUPresentMode.Immediate);
        config.setAlphaMode(WGPUCompositeAlphaMode.Auto);
        wgpu.surface.configure(config);
    }

    public void initPipeline(WGPUApp wgpu) {
        WebGPUShaderModule shaderModule = makeShaderModule(wgpu);

        WebGPURenderPipelineDescriptor pipelineDesc = WebGPURenderPipelineDescriptor.obtain();
        pipelineDesc.setLabel("my pipeline");

        pipelineDesc.getVertex().setBuffers(null);
        pipelineDesc.getVertex().setModule(shaderModule);
        pipelineDesc.getVertex().setEntryPoint("vs_main");
        pipelineDesc.getVertex().setConstants(null);

        pipelineDesc.getPrimitive().setTopology(WGPUPrimitiveTopology.TriangleList);
        pipelineDesc.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Undefined);
        pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
        pipelineDesc.getPrimitive().setCullMode(WGPUCullMode.None);

        WebGPUFragmentState fragmentState = WebGPUFragmentState.obtain();
        fragmentState.setNextInChain(null);
        fragmentState.setModule(shaderModule);
        fragmentState.setEntryPoint("fs_main");
        fragmentState.setConstants(null);

        // blending
        WebGPUBlendState blendState = WebGPUBlendState.obtain();
        blendState.getColor().setSrcFactor(WGPUBlendFactor.SrcAlpha);
        blendState.getColor().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
        blendState.getColor().setOperation(WGPUBlendOperation.Add);
        blendState.getAlpha().setSrcFactor(WGPUBlendFactor.One);
        blendState.getAlpha().setDstFactor(WGPUBlendFactor.Zero);
        blendState.getAlpha().setOperation(WGPUBlendOperation.Add);

        WebGPUColorTargetState colorTarget = WebGPUColorTargetState.obtain();
        colorTarget.setFormat(surfaceFormat); // match output surface
        colorTarget.setBlend(blendState);
        colorTarget.setWriteMask(WGPUColorWriteMask.All);

        WGPUVectorColorTargetState colorStateTargets = WGPUVectorColorTargetState.obtain();
        colorStateTargets.push_back(colorTarget);
        fragmentState.setTargets(colorStateTargets);

        pipelineDesc.setFragment(fragmentState);
        pipelineDesc.setDepthStencil(null); // no depth or stencil buffer
        pipelineDesc.getMultisample().setCount(1);
        pipelineDesc.getMultisample().setMask(-1);
        pipelineDesc.getMultisample().setAlphaToCoverageEnabled(false);
        pipelineDesc.setLayout(null);

        pipeline = new WebGPURenderPipeline();
        wgpu.device.createRenderPipeline(pipelineDesc, pipeline);

        shaderModule.release();

        System.out.println("RenderPipeline created");
    }

    public WebGPUShaderModule makeShaderModule(WGPUApp wgpu) {
        String shaderSource = readShaderSource();

        WebGPUShaderModuleDescriptor shaderDesc = WebGPUShaderModuleDescriptor.obtain();
        shaderDesc.setLabel("triangle shader");

        WebGPUShaderSourceWGSL shaderCodeDesc = WebGPUShaderSourceWGSL.obtain();
        shaderCodeDesc.getChain().setNext(null);
        shaderCodeDesc.getChain().setSType(WGPUSType.ShaderSourceWGSL);
        shaderCodeDesc.setCode(shaderSource);

        shaderDesc.setNextInChain(shaderCodeDesc.getChain());
        WebGPUShaderModule shaderModule = WebGPUShaderModule.obtain();
        wgpu.device.createShaderModule(shaderDesc, shaderModule);
        return shaderModule;
    }

    private String readShaderSource () {

        return "// triangleShader.wgsl\n" + "\n" + "@vertex\n"
                + "fn vs_main(@builtin(vertex_index) in_vertex_index: u32) -> @builtin(position) vec4f {\n"
                + "    var p = vec2f(0.0, 0.0);\n" + "    if (in_vertex_index == 0u) {\n" + "        p = vec2f(-0.5, -0.5);\n"
                + "    } else if (in_vertex_index == 1u) {\n" + "        p = vec2f(0.5, -0.5);\n" + "    } else {\n"
                + "        p = vec2f(0.0, 0.5);\n" + "    }\n" + "    return vec4f(p, 0.0, 1.0);\n" + "}\n" + "\n" + "@fragment\n"
                + "fn fs_main() -> @location(0) vec4f {\n" + "    return vec4f(0.0, 0.4, 1.0, 1.0);\n" + "}";
    }
}

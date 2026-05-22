package com.monstrous.gdx.benchmarks.webgpu.raw;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.TimeUtils;
import com.github.xpenatan.webgpu.JWebGPUBackend;
import com.github.xpenatan.webgpu.WGPUBindGroup;
import com.github.xpenatan.webgpu.WGPUBindGroupDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUBindGroupLayoutDescriptor;
import com.github.xpenatan.webgpu.WGPUBindGroupLayoutEntry;
import com.github.xpenatan.webgpu.WGPUBlendFactor;
import com.github.xpenatan.webgpu.WGPUBlendOperation;
import com.github.xpenatan.webgpu.WGPUBlendState;
import com.github.xpenatan.webgpu.WGPUBuffer;
import com.github.xpenatan.webgpu.WGPUBufferBindingType;
import com.github.xpenatan.webgpu.WGPUBufferDescriptor;
import com.github.xpenatan.webgpu.WGPUBufferUsage;
import com.github.xpenatan.webgpu.WGPUChainedStruct;
import com.github.xpenatan.webgpu.WGPUColorTargetState;
import com.github.xpenatan.webgpu.WGPUColorWriteMask;
import com.github.xpenatan.webgpu.WGPUCullMode;
import com.github.xpenatan.webgpu.WGPUDepthStencilState;
import com.github.xpenatan.webgpu.WGPUFragmentState;
import com.github.xpenatan.webgpu.WGPUFrontFace;
import com.github.xpenatan.webgpu.WGPUIndexFormat;
import com.github.xpenatan.webgpu.WGPULoadOp;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.github.xpenatan.webgpu.WGPUPipelineLayoutDescriptor;
import com.github.xpenatan.webgpu.WGPUPrimitiveTopology;
import com.github.xpenatan.webgpu.WGPUQuerySet;
import com.github.xpenatan.webgpu.WGPURenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPURenderPassDescriptor;
import com.github.xpenatan.webgpu.WGPURenderPassEncoder;
import com.github.xpenatan.webgpu.WGPURenderPipeline;
import com.github.xpenatan.webgpu.WGPURenderPipelineDescriptor;
import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUSamplerBindingType;
import com.github.xpenatan.webgpu.WGPUShaderStage;
import com.github.xpenatan.webgpu.WGPUStoreOp;
import com.github.xpenatan.webgpu.WGPUTextureSampleType;
import com.github.xpenatan.webgpu.WGPUTextureView;
import com.github.xpenatan.webgpu.WGPUTextureViewDimension;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupEntry;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupLayout;
import com.github.xpenatan.webgpu.WGPUVectorBindGroupLayoutEntry;
import com.github.xpenatan.webgpu.WGPUVectorColorTargetState;
import com.github.xpenatan.webgpu.WGPUVectorConstantEntry;
import com.github.xpenatan.webgpu.WGPUVectorRenderPassColorAttachment;
import com.github.xpenatan.webgpu.WGPUVectorVertexAttribute;
import com.github.xpenatan.webgpu.WGPUVectorVertexBufferLayout;
import com.github.xpenatan.webgpu.WGPUVertexAttribute;
import com.github.xpenatan.webgpu.WGPUVertexBufferLayout;
import com.github.xpenatan.webgpu.WGPUVertexFormat;
import com.github.xpenatan.webgpu.WGPUVertexStepMode;
import com.monstrous.gdx.benchmarks.BenchmarkConfig;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

public class RawWebGPUSpriteBenchmarkLauncher {
    public static void main(String[] args) {
        BenchmarkConfig benchmarkConfig = BenchmarkConfig.fromArgs(args);
        JWebGPUBackend webgpuBackend = parseWebGPUBackend(args, JWebGPUBackend.WGPU);
        WebGPUContext.Backend backend = parseBackend(args, WebGPUContext.Backend.DEFAULT);
        int samples = parseIntArg(args, "samples", 1);
        String binding = parseStringArg(args, "binding", System.getProperty("benchmark.binding", "jni"));
        if (samples != 1) {
            throw new IllegalArgumentException("The raw WebGPU sprite benchmark currently supports --samples=1 only.");
        }

        WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
        config.setTitle("gdx benchmark raw WebGPU sprites");
        config.setWindowedMode(benchmarkConfig.width, benchmarkConfig.height);
        config.backendWebGPU = webgpuBackend;
        config.backend = backend;
        config.enableGPUtiming = false;
        config.samples = 1;
        config.setForegroundFPS(0);
        config.useVsync(false);

        String backendName = "webgpu-raw-" + binding + "-" + webgpuBackend + "-" + backend;
        new WgDesktopApplication(new RawSpriteBenchmarkApplication(backendName, benchmarkConfig), config);
    }

    private static JWebGPUBackend parseWebGPUBackend(String[] args, JWebGPUBackend defaultValue) {
        String value = parseStringArg(args, "webgpu", null);
        return value == null ? defaultValue : JWebGPUBackend.valueOf(value);
    }

    private static WebGPUContext.Backend parseBackend(String[] args, WebGPUContext.Backend defaultValue) {
        String value = parseStringArg(args, "backend", null);
        return value == null ? defaultValue : WebGPUContext.Backend.valueOf(value);
    }

    private static int parseIntArg(String[] args, String key, int defaultValue) {
        String value = parseStringArg(args, key, null);
        return value == null ? defaultValue : Integer.parseInt(value);
    }

    private static String parseStringArg(String[] args, String key, String defaultValue) {
        String prefix = "--" + key + "=";
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private static class RawSpriteBenchmarkApplication extends ApplicationAdapter {
        private static final long SECOND = 1000000000L;

        private final String backendName;
        private final BenchmarkConfig config;
        private RawSpriteRenderer renderer;
        private long startNanos;
        private long secondStartNanos;
        private int secondFrames;
        private int loggedSeconds;
        private int sampleCount;
        private int minFps = Integer.MAX_VALUE;
        private int maxFps;
        private long totalFps;
        private boolean finished;

        RawSpriteBenchmarkApplication(String backendName, BenchmarkConfig config) {
            this.backendName = backendName;
            this.config = config;
        }

        @Override
        public void create() {
            if (!"sprite2d".equalsIgnoreCase(config.testName) && !"spritebatch2d".equalsIgnoreCase(config.testName)) {
                throw new IllegalArgumentException("Unknown raw WebGPU benchmark test: " + config.testName);
            }

            renderer = new RawSpriteRenderer(config);
            renderer.create();
            startNanos = TimeUtils.nanoTime();
            secondStartNanos = startNanos;

            System.out.println("BENCH_START backend=" + backendName
                    + " test=sprite2d-raw"
                    + " sprites=" + config.sprites
                    + " size=" + config.width + "x" + config.height
                    + " warmup=" + config.warmupSeconds
                    + " seconds=" + config.seconds
                    + " rotate=" + config.rotate
                    + " scale=" + config.scale
                    + " vsync=" + BenchmarkConfig.VSYNC_ENABLED);
        }

        @Override
        public void resize(int width, int height) {
            if (renderer != null) {
                renderer.resize(width, height);
            }
        }

        @Override
        public void render() {
            renderer.render();
            secondFrames++;

            long now = TimeUtils.nanoTime();
            if (now - secondStartNanos >= SECOND) {
                boolean warmup = loggedSeconds < config.warmupSeconds;
                int fps = secondFrames;
                System.out.println("BENCH_SECOND backend=" + backendName
                        + " test=sprite2d-raw"
                        + " sprites=" + config.sprites
                        + " warmup=" + warmup
                        + " fps=" + fps);
                if (!warmup) {
                    sampleCount++;
                    totalFps += fps;
                    minFps = Math.min(minFps, fps);
                    maxFps = Math.max(maxFps, fps);
                    if (sampleCount >= config.seconds) {
                        finish();
                        return;
                    }
                }
                loggedSeconds++;
                secondFrames = 0;
                secondStartNanos = now;
            }
        }

        @Override
        public void dispose() {
            if (renderer != null) {
                renderer.dispose();
            }
        }

        private void finish() {
            if (finished) {
                return;
            }
            finished = true;
            int min = sampleCount == 0 ? 0 : minFps;
            int avg = sampleCount == 0 ? 0 : (int)Math.round((double)totalFps / sampleCount);
            System.out.println("BENCH_RESULT backend=" + backendName
                    + " test=sprite2d-raw"
                    + " sprites=" + config.sprites
                    + " avgFps=" + avg
                    + " minFps=" + min
                    + " maxFps=" + maxFps
                    + " samples=" + sampleCount);
            writeResult(avg, min);
            Gdx.app.exit();
        }

        private void writeResult(int avg, int min) {
            if (config.resultFile == null || config.resultFile.length() == 0) {
                return;
            }

            File file = new File(config.resultFile);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            boolean writeHeader = !file.isFile() || file.length() == 0;
            try (PrintWriter writer = new PrintWriter(new FileWriter(file, true))) {
                if (writeHeader) {
                    writer.println("backend\ttest\tsprites\tvsync\tavgFps\tminFps\tmaxFps\tsamples");
                }
                writer.println(backendName
                        + "\tsprite2d-raw"
                        + "\t" + config.sprites
                        + "\t" + BenchmarkConfig.VSYNC_ENABLED
                        + "\t" + avg
                        + "\t" + min
                        + "\t" + maxFps
                        + "\t" + sampleCount);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write benchmark result file: " + config.resultFile, e);
            }
        }
    }

    private static class RawSpriteRenderer {
        private static final int SPRITE_WIDTH = 32;
        private static final int SPRITE_HEIGHT = 32;
        private static final float ROTATION_SPEED = 20f;
        private static final int UNIFORM_BYTES = 16 * Float.BYTES;
        private static final int FLOATS_PER_INSTANCE = 10;
        private static final int INSTANCE_STRIDE_BYTES = FLOATS_PER_INSTANCE * Float.BYTES;

        private final BenchmarkConfig config;
        private final Matrix4 projection = new Matrix4();
        private final Matrix4 combined = new Matrix4();
        private final Matrix4 shiftDepthMatrix = new Matrix4().idt().scl(1f, 1f, -0.5f).trn(0f, 0f, 0.5f);

        private WebGPUContext webgpu;
        private WgTexture texture;
        private WgShaderProgram shader;
        private WGPUBuffer uniformBuffer;
        private WGPUBuffer instanceBuffer;
        private WGPUBindGroupLayout bindGroupLayout;
        private WGPUPipelineLayout pipelineLayout;
        private WGPUBindGroup bindGroup;
        private WGPURenderPipeline pipeline;
        private WGPURenderPassEncoder passEncoder;
        private WGPUBindGroupLayoutEntry[] bindGroupLayoutEntries;
        private WGPUBindGroupEntry[] bindGroupEntries;
        private WGPUVertexAttribute[] vertexAttributes;
        private ByteBuffer uniformBytes;
        private FloatBuffer uniformFloats;
        private ByteBuffer instanceBytes;
        private FloatBuffer instanceFloats;
        private float[] centerX;
        private float[] centerY;
        private float scale = 1f;
        private float scaleSpeed = -1f;
        private float rotationDegrees;
        private float u0;
        private float v0;
        private float u1;
        private float v1;
        private int instanceBytesUsed;

        RawSpriteRenderer(BenchmarkConfig config) {
            this.config = config;
        }

        void create() {
            webgpu = ((WgGraphics)Gdx.graphics).getContext();
            texture = new WgTexture(Gdx.files.internal("data/badlogicsmall.jpg"));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

            u0 = 0f;
            v0 = 0f;
            u1 = SPRITE_WIDTH / (float)texture.getWidth();
            v1 = SPRITE_HEIGHT / (float)texture.getHeight();

            uniformBytes = BufferUtils.newUnsafeByteBuffer(UNIFORM_BYTES);
            uniformFloats = uniformBytes.asFloatBuffer();
            instanceBytesUsed = config.sprites * INSTANCE_STRIDE_BYTES;
            instanceBytes = BufferUtils.newUnsafeByteBuffer(instanceBytesUsed);
            instanceFloats = instanceBytes.asFloatBuffer();

            uniformBuffer = createBuffer("raw sprite uniforms", WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform),
                    UNIFORM_BYTES);
            instanceBuffer = createBuffer("raw sprite instances", WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Vertex),
                    instanceBytesUsed);

            createBindGroupLayout();
            createBindGroup();
            createPipeline();
            passEncoder = new WGPURenderPassEncoder();

            resize(config.width, config.height);
        }

        void resize(int width, int height) {
            webgpu.setViewportRectangle(0, 0, width, height);
            projection.setToOrtho2D(0, 0, width, height, 0, 100);
            combined.set(shiftDepthMatrix).mul(projection);
            uploadUniforms();
            generateSprites(width, height);
        }

        void render() {
            float delta = Gdx.graphics.getDeltaTime();
            if (config.rotate) {
                rotationDegrees += ROTATION_SPEED * delta;
            }
            if (config.scale) {
                scale += scaleSpeed * delta;
                if (scale < 0.5f) {
                    scale = 0.5f;
                    scaleSpeed = 1f;
                } else if (scale > 1f) {
                    scale = 1f;
                    scaleSpeed = -1f;
                }
            }

            fillInstanceData();
            webgpu.queue.writeBuffer(instanceBuffer, 0, instanceBytes, instanceBytesUsed);
            draw();
        }

        void dispose() {
            if (pipeline != null) {
                pipeline.release();
                pipeline.dispose();
                pipeline = null;
            }
            if (passEncoder != null) {
                passEncoder.dispose();
                passEncoder = null;
            }
            if (bindGroup != null) {
                bindGroup.release();
                bindGroup.dispose();
                bindGroup = null;
            }
            if (pipelineLayout != null) {
                pipelineLayout.release();
                pipelineLayout.dispose();
                pipelineLayout = null;
            }
            if (bindGroupLayout != null) {
                bindGroupLayout.release();
                bindGroupLayout.dispose();
                bindGroupLayout = null;
            }
            if (bindGroupEntries != null) {
                for (int i = 0; i < bindGroupEntries.length; i++) {
                    bindGroupEntries[i].dispose();
                }
                bindGroupEntries = null;
            }
            if (bindGroupLayoutEntries != null) {
                for (int i = 0; i < bindGroupLayoutEntries.length; i++) {
                    bindGroupLayoutEntries[i].dispose();
                }
                bindGroupLayoutEntries = null;
            }
            if (vertexAttributes != null) {
                for (int i = 0; i < vertexAttributes.length; i++) {
                    vertexAttributes[i].dispose();
                }
                vertexAttributes = null;
            }
            if (uniformBuffer != null) {
                uniformBuffer.destroy();
                uniformBuffer.dispose();
                uniformBuffer = null;
            }
            if (instanceBuffer != null) {
                instanceBuffer.destroy();
                instanceBuffer.dispose();
                instanceBuffer = null;
            }
            if (shader != null) {
                shader.dispose();
                shader = null;
            }
            if (texture != null) {
                texture.dispose();
                texture = null;
            }
            if (uniformBytes != null) {
                BufferUtils.disposeUnsafeByteBuffer(uniformBytes);
                uniformBytes = null;
            }
            if (instanceBytes != null) {
                BufferUtils.disposeUnsafeByteBuffer(instanceBytes);
                instanceBytes = null;
            }
        }

        private WGPUBuffer createBuffer(String label, WGPUBufferUsage usage, int size) {
            WGPUBufferDescriptor desc = WGPUBufferDescriptor.obtain();
            desc.setNextInChain(WGPUChainedStruct.NULL);
            desc.setLabel(label);
            desc.setUsage(usage);
            desc.setSize(size);
            desc.setMappedAtCreation(false);
            return webgpu.device.createBuffer(desc);
        }

        private void createBindGroupLayout() {
            WGPUBindGroupLayoutEntry uniform = new WGPUBindGroupLayoutEntry();
            uniform.setNextInChain(WGPUChainedStruct.NULL);
            uniform.setBinding(0);
            uniform.setVisibility(WGPUShaderStage.Vertex);
            uniform.getBuffer().setType(WGPUBufferBindingType.Uniform);
            uniform.getBuffer().setHasDynamicOffset(0);
            uniform.getBuffer().setMinBindingSize(UNIFORM_BYTES);

            WGPUBindGroupLayoutEntry textureEntry = new WGPUBindGroupLayoutEntry();
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(1);
            textureEntry.setVisibility(WGPUShaderStage.Fragment);
            textureEntry.getTexture().setSampleType(WGPUTextureSampleType.Float);
            textureEntry.getTexture().setViewDimension(WGPUTextureViewDimension._2D);
            textureEntry.getTexture().setMultisampled(0);

            WGPUBindGroupLayoutEntry samplerEntry = new WGPUBindGroupLayoutEntry();
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(2);
            samplerEntry.setVisibility(WGPUShaderStage.Fragment);
            samplerEntry.getSampler().setType(WGPUSamplerBindingType.Filtering);
            bindGroupLayoutEntries = new WGPUBindGroupLayoutEntry[] {uniform, textureEntry, samplerEntry};

            WGPUVectorBindGroupLayoutEntry entries = WGPUVectorBindGroupLayoutEntry.obtain();
            entries.push_back(uniform);
            entries.push_back(textureEntry);
            entries.push_back(samplerEntry);

            WGPUBindGroupLayoutDescriptor desc = WGPUBindGroupLayoutDescriptor.obtain();
            desc.setNextInChain(WGPUChainedStruct.NULL);
            desc.setLabel("raw sprite bind group layout");
            desc.setEntries(entries);

            bindGroupLayout = new WGPUBindGroupLayout();
            webgpu.device.createBindGroupLayout(desc, bindGroupLayout);
        }

        private void createBindGroup() {
            WGPUBindGroupEntry uniform = new WGPUBindGroupEntry();
            uniform.setNextInChain(WGPUChainedStruct.NULL);
            uniform.setBinding(0);
            uniform.setBuffer(uniformBuffer);
            uniform.setOffset(0);
            uniform.setSize(UNIFORM_BYTES);
            uniform.setTextureView(WGPUTextureView.NULL);
            uniform.setSampler(WGPUSampler.NULL);

            WGPUBindGroupEntry textureEntry = new WGPUBindGroupEntry();
            textureEntry.setNextInChain(WGPUChainedStruct.NULL);
            textureEntry.setBinding(1);
            textureEntry.setTextureView(texture.getTextureView());
            textureEntry.setBuffer(WGPUBuffer.NULL);
            textureEntry.setOffset(0);
            textureEntry.setSize(0);
            textureEntry.setSampler(WGPUSampler.NULL);

            WGPUBindGroupEntry samplerEntry = new WGPUBindGroupEntry();
            samplerEntry.setNextInChain(WGPUChainedStruct.NULL);
            samplerEntry.setBinding(2);
            samplerEntry.setSampler(texture.getSampler());
            samplerEntry.setBuffer(WGPUBuffer.NULL);
            samplerEntry.setOffset(0);
            samplerEntry.setSize(0);
            samplerEntry.setTextureView(WGPUTextureView.NULL);
            bindGroupEntries = new WGPUBindGroupEntry[] {uniform, textureEntry, samplerEntry};

            WGPUVectorBindGroupEntry entries = WGPUVectorBindGroupEntry.obtain();
            entries.push_back(uniform);
            entries.push_back(textureEntry);
            entries.push_back(samplerEntry);

            WGPUBindGroupDescriptor desc = WGPUBindGroupDescriptor.obtain();
            desc.setNextInChain(WGPUChainedStruct.NULL);
            desc.setLabel("raw sprite bind group");
            desc.setLayout(bindGroupLayout);
            desc.setEntries(entries);

            bindGroup = new WGPUBindGroup();
            webgpu.device.createBindGroup(desc, bindGroup);
        }

        private void createPipeline() {
            shader = new WgShaderProgram("raw_sprite_instanced", shaderSource());

            WGPUVertexAttribute centerHalf = new WGPUVertexAttribute();
            centerHalf.setFormat(WGPUVertexFormat.Float32x4);
            centerHalf.setOffset(0);
            centerHalf.setShaderLocation(0);

            WGPUVertexAttribute rotationUv0 = new WGPUVertexAttribute();
            rotationUv0.setFormat(WGPUVertexFormat.Float32x4);
            rotationUv0.setOffset(4 * Float.BYTES);
            rotationUv0.setShaderLocation(1);

            WGPUVertexAttribute uv1 = new WGPUVertexAttribute();
            uv1.setFormat(WGPUVertexFormat.Float32x2);
            uv1.setOffset(8 * Float.BYTES);
            uv1.setShaderLocation(2);
            vertexAttributes = new WGPUVertexAttribute[] {centerHalf, rotationUv0, uv1};

            WGPUVectorVertexAttribute attributes = WGPUVectorVertexAttribute.obtain();
            attributes.push_back(centerHalf);
            attributes.push_back(rotationUv0);
            attributes.push_back(uv1);

            WGPUVertexBufferLayout instanceLayout = WGPUVertexBufferLayout.obtain();
            instanceLayout.setArrayStride(INSTANCE_STRIDE_BYTES);
            instanceLayout.setStepMode(WGPUVertexStepMode.Instance);
            instanceLayout.setAttributes(attributes);

            WGPUVectorVertexBufferLayout vertexLayouts = WGPUVectorVertexBufferLayout.obtain();
            vertexLayouts.push_back(instanceLayout);

            WGPUVectorBindGroupLayout groupLayouts = WGPUVectorBindGroupLayout.obtain();
            groupLayouts.push_back(bindGroupLayout);

            WGPUPipelineLayoutDescriptor layoutDesc = WGPUPipelineLayoutDescriptor.obtain();
            layoutDesc.setNextInChain(WGPUChainedStruct.NULL);
            layoutDesc.setLabel("raw sprite pipeline layout");
            layoutDesc.setBindGroupLayouts(groupLayouts);
            pipelineLayout = new WGPUPipelineLayout();
            webgpu.device.createPipelineLayout(layoutDesc, pipelineLayout);

            WGPUBlendState blend = WGPUBlendState.obtain();
            blend.getColor().setSrcFactor(WGPUBlendFactor.SrcAlpha);
            blend.getColor().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
            blend.getColor().setOperation(WGPUBlendOperation.Add);
            blend.getAlpha().setSrcFactor(WGPUBlendFactor.SrcAlpha);
            blend.getAlpha().setDstFactor(WGPUBlendFactor.OneMinusSrcAlpha);
            blend.getAlpha().setOperation(WGPUBlendOperation.Add);

            WGPUColorTargetState colorTarget = WGPUColorTargetState.obtain();
            colorTarget.setNextInChain(WGPUChainedStruct.NULL);
            colorTarget.setFormat(webgpu.getSurfaceFormat());
            colorTarget.setBlend(blend);
            colorTarget.setWriteMask(WGPUColorWriteMask.All);

            WGPUVectorColorTargetState colorTargets = WGPUVectorColorTargetState.obtain();
            colorTargets.push_back(colorTarget);

            WGPUFragmentState fragmentState = WGPUFragmentState.obtain();
            fragmentState.setNextInChain(WGPUChainedStruct.NULL);
            fragmentState.setModule(shader.getShaderModule());
            fragmentState.setEntryPoint("fragmentMain");
            fragmentState.setConstants(WGPUVectorConstantEntry.NULL);
            fragmentState.setTargets(colorTargets);

            WGPURenderPipelineDescriptor pipelineDesc = WGPURenderPipelineDescriptor.obtain();
            pipelineDesc.setNextInChain(WGPUChainedStruct.NULL);
            pipelineDesc.setLabel("raw sprite pipeline");
            pipelineDesc.getVertex().setModule(shader.getShaderModule());
            pipelineDesc.getVertex().setEntryPoint("vertexMain");
            pipelineDesc.getVertex().setConstants(WGPUVectorConstantEntry.obtain());
            pipelineDesc.getVertex().setBuffers(vertexLayouts);
            pipelineDesc.getPrimitive().setTopology(WGPUPrimitiveTopology.TriangleStrip);
            pipelineDesc.getPrimitive().setStripIndexFormat(WGPUIndexFormat.Uint16);
            pipelineDesc.getPrimitive().setFrontFace(WGPUFrontFace.CCW);
            pipelineDesc.getPrimitive().setCullMode(WGPUCullMode.None);
            pipelineDesc.setFragment(fragmentState);
            pipelineDesc.setDepthStencil(WGPUDepthStencilState.NULL);
            pipelineDesc.getMultisample().setCount(1);
            pipelineDesc.getMultisample().setMask(-1);
            pipelineDesc.getMultisample().setAlphaToCoverageEnabled(false);
            pipelineDesc.setLayout(pipelineLayout);

            pipeline = new WGPURenderPipeline();
            webgpu.device.createRenderPipeline(pipelineDesc, pipeline);
        }

        private void uploadUniforms() {
            uniformFloats.clear();
            uniformFloats.put(combined.val, 0, 16);
            uniformBytes.position(0);
            uniformBytes.limit(UNIFORM_BYTES);
            webgpu.queue.writeBuffer(uniformBuffer, 0, uniformBytes, UNIFORM_BYTES);
        }

        private void generateSprites(int screenWidth, int screenHeight) {
            centerX = new float[config.sprites];
            centerY = new float[config.sprites];
            Random random = new Random(0x51f15e2dL);
            int maxX = Math.max(1, screenWidth);
            int maxY = Math.max(1, screenHeight);
            for (int i = 0; i < config.sprites; i++) {
                centerX[i] = random.nextInt(maxX);
                centerY[i] = random.nextInt(maxY);
            }
        }

        private void fillInstanceData() {
            instanceFloats.clear();
            float halfWidth = SPRITE_WIDTH * 0.5f * scale;
            float halfHeight = SPRITE_HEIGHT * 0.5f * scale;
            float cos = MathUtils.cosDeg(rotationDegrees);
            float sin = MathUtils.sinDeg(rotationDegrees);

            for (int i = 0; i < config.sprites; i++) {
                instanceFloats.put(centerX[i]);
                instanceFloats.put(centerY[i]);
                instanceFloats.put(halfWidth);
                instanceFloats.put(halfHeight);
                instanceFloats.put(cos);
                instanceFloats.put(sin);
                instanceFloats.put(u0);
                instanceFloats.put(v0);
                instanceFloats.put(u1);
                instanceFloats.put(v1);
            }
            instanceBytes.position(0);
            instanceBytes.limit(instanceBytesUsed);
        }

        private void draw() {
            WGPURenderPassDescriptor desc = WGPURenderPassDescriptor.obtain();
            desc.setNextInChain(WGPUChainedStruct.NULL);
            desc.setLabel("raw sprite pass");
            desc.setOcclusionQuerySet(WGPUQuerySet.NULL);

            WGPURenderPassColorAttachment colorAttachment = WGPURenderPassColorAttachment.obtain();
            colorAttachment.setNextInChain(WGPUChainedStruct.NULL);
            colorAttachment.setView(webgpu.getTargetViews()[0]);
            colorAttachment.setResolveTarget(WGPUTextureView.NULL);
            colorAttachment.setLoadOp(WGPULoadOp.Load);
            colorAttachment.setStoreOp(WGPUStoreOp.Store);
            colorAttachment.setDepthSlice(-1);
            WGPUVectorRenderPassColorAttachment colorAttachments = WGPUVectorRenderPassColorAttachment.obtain();
            colorAttachments.push_back(colorAttachment);
            desc.setColorAttachments(colorAttachments);

            webgpu.encoder.beginRenderPass(desc, passEncoder);
            Rectangle viewport = webgpu.getViewportRectangle();
            passEncoder.setViewport(viewport.x, viewport.y, viewport.width, viewport.height, 0f, 1f);
            passEncoder.setPipeline(pipeline);
            passEncoder.setBindGroup(0, bindGroup);
            passEncoder.setVertexBuffer(0, instanceBuffer, 0, instanceBytesUsed);
            passEncoder.draw(4, config.sprites, 0, 0);
            passEncoder.end();
            passEncoder.release();
        }

        private String shaderSource() {
            return ""
                    + "struct Globals {\n"
                    + "    projection : mat4x4f,\n"
                    + "};\n"
                    + "@group(0) @binding(0) var<uniform> globals : Globals;\n"
                    + "@group(0) @binding(1) var spriteTexture : texture_2d<f32>;\n"
                    + "@group(0) @binding(2) var spriteSampler : sampler;\n"
                    + "\n"
                    + "struct VertexOutput {\n"
                    + "    @builtin(position) position : vec4f,\n"
                    + "    @location(0) uv : vec2f,\n"
                    + "};\n"
                    + "\n"
                    + "@vertex\n"
                    + "fn vertexMain(\n"
                    + "    @builtin(vertex_index) vertexIndex : u32,\n"
                    + "    @location(0) centerHalf : vec4f,\n"
                    + "    @location(1) rotationUv0 : vec4f,\n"
                    + "    @location(2) uv1 : vec2f\n"
                    + ") -> VertexOutput {\n"
                    + "    let corners = array<vec2f, 4>(\n"
                    + "        vec2f(-1.0, -1.0),\n"
                    + "        vec2f( 1.0, -1.0),\n"
                    + "        vec2f(-1.0,  1.0),\n"
                    + "        vec2f( 1.0,  1.0));\n"
                    + "    let uvs = array<vec2f, 4>(\n"
                    + "        vec2f(rotationUv0.z, uv1.y),\n"
                    + "        vec2f(uv1.x, uv1.y),\n"
                    + "        vec2f(rotationUv0.z, rotationUv0.w),\n"
                    + "        vec2f(uv1.x, rotationUv0.w));\n"
                    + "    let local = corners[vertexIndex] * centerHalf.zw;\n"
                    + "    let c = rotationUv0.x;\n"
                    + "    let s = rotationUv0.y;\n"
                    + "    let rotated = vec2f(local.x * c - local.y * s, local.x * s + local.y * c);\n"
                    + "    var output : VertexOutput;\n"
                    + "    output.position = globals.projection * vec4f(centerHalf.xy + rotated, 0.0, 1.0);\n"
                    + "    output.uv = uvs[vertexIndex];\n"
                    + "    return output;\n"
                    + "}\n"
                    + "\n"
                    + "@fragment\n"
                    + "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n"
                    + "    return textureSample(spriteTexture, spriteSampler, input.uv);\n"
                    + "}\n";
        }
    }
}

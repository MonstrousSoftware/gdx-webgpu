package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.LongMap;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;

import static com.badlogic.gdx.math.Matrix4.M33;

/**
 * Procedural clear-sky renderer.
 * <p>
 * Renders a full-screen sky gradient with a sun disc — no cubemap texture needed.
 * Uses the same full-screen triangle technique as {@link SkyBox}.
 * <p>
 * Configure with {@link #setSunDirection(Vector3)}, {@link #setZenithColor(Color)},
 * {@link #setHorizonColor(Color)}, {@link #setGroundColor(Color)}, etc.
 */
public class ProceduralSkyBox implements Disposable {

    // Uniform buffer layout (must match procedural_sky.wgsl):
    //   mat4x4f  inverseProjectionViewMatrix  (64 bytes, offset 0)
    //   vec4f    sunDirection                  (16 bytes, offset 64)
    //   vec4f    skyColorZenith               (16 bytes, offset 80)
    //   vec4f    skyColorHorizon              (16 bytes, offset 96)
    //   vec4f    groundColor                  (16 bytes, offset 112)
    //   vec4f    sunParams                    (16 bytes, offset 128)
    //   Total: 144 bytes
    private static final int UB_SIZE = 144;
    private static final int OFF_MATRIX     = 0;
    private static final int OFF_SUN_DIR    = 64;
    private static final int OFF_ZENITH     = 80;
    private static final int OFF_HORIZON    = 96;
    private static final int OFF_GROUND     = 112;
    private static final int OFF_SUN_PARAMS = 128;

    /** Maximum number of render calls per frame (e.g. user cam + debug cam). */
    private static final int MAX_RENDERS_PER_FRAME = 4;

    private final WebGPUUniformBuffer uniformBuffer;
    private final WebGPUBindGroupLayout bindGroupLayout;
    private final WebGPUBindGroup bindGroup;
    private final WebGPUPipelineLayout pipelineLayout;
    private final LongMap<WebGPUPipeline> pipelineCache = new LongMap<>();
    private final String shaderSource;
    private final Matrix4 invertedProjectionView = new Matrix4();

    private float sunDirX, sunDirY, sunDirZ;
    private float zenithR, zenithG, zenithB;
    private float horizonR, horizonG, horizonB;
    private float groundR, groundG, groundB;
    private float sunRadius;
    private float sunIntensity;

    /** Frame counter to detect new frame and reset slices. */
    private long lastFrameId = -1;
    /** Dynamic offset for the current slice. */
    private int dynamicOffset;

    /** Uniform stride (UB_SIZE rounded up to 256-byte alignment for dynamic offsets). */
    private static final int SLICE_STRIDE = 256;

    /** Create a procedural sky with pleasant defaults (clear blue sky, warm sun). */
    public ProceduralSkyBox() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();

        shaderSource = Gdx.files.classpath("shaders/procedural_sky.wgsl").readString();
        uniformBuffer = new WebGPUUniformBuffer(UB_SIZE, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform), MAX_RENDERS_PER_FRAME);

        bindGroupLayout = new WebGPUBindGroupLayout();
        bindGroupLayout.begin();
        bindGroupLayout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment),
                WGPUBufferBindingType.Uniform, UB_SIZE, true); // hasDynamicOffset = true
        bindGroupLayout.end();

        pipelineLayout = new WebGPUPipelineLayout("ProceduralSky Pipeline Layout", bindGroupLayout);

        bindGroup = new WebGPUBindGroup(bindGroupLayout);
        bindGroup.begin();
        // Bind only one slice worth of data; dynamic offset selects which slice the GPU reads.
        bindGroup.setBuffer(0, uniformBuffer, 0, SLICE_STRIDE);
        bindGroup.end();

        // Defaults: clear blue sky
        setSunDirection(new Vector3(-0.5f, -0.7f, -0.5f));
        setZenithColor(new Color(0.15f, 0.3f, 0.65f, 1f));
        setHorizonColor(new Color(0.55f, 0.72f, 0.9f, 1f));
        setGroundColor(new Color(0.25f, 0.22f, 0.2f, 1f));
        setSunRadius(0.53f);
        setSunIntensity(8f);

        getOrCreatePipeline(webgpu.getSurfaceFormat(), webgpu.getSamples());
    }

    // ---- Configuration API ----

    /**
     * Set the sun direction. Pass the light direction (pointing toward the surface).
     * The sun disc appears in the opposite direction (where light comes FROM).
     */
    public void setSunDirection(Vector3 lightDirection) {
        float len = lightDirection.len();
        if (len < 1e-6f) return;
        // Negate to get direction TO the sun (world space).
        // The shader will apply the same Z-flip to this as it does to the view direction.
        sunDirX = -lightDirection.x / len;
        sunDirY = -lightDirection.y / len;
        sunDirZ = -lightDirection.z / len;
    }

    /** Set the zenith (straight up) sky color in sRGB. */
    public void setZenithColor(Color color) {
        zenithR = toLinear(color.r);
        zenithG = toLinear(color.g);
        zenithB = toLinear(color.b);
    }

    /** Set the horizon sky color in sRGB. */
    public void setHorizonColor(Color color) {
        horizonR = toLinear(color.r);
        horizonG = toLinear(color.g);
        horizonB = toLinear(color.b);
    }

    /** Set the ground hemisphere color in sRGB (visible below the horizon). */
    public void setGroundColor(Color color) {
        groundR = toLinear(color.r);
        groundG = toLinear(color.g);
        groundB = toLinear(color.b);
    }

    /** Set the angular diameter of the sun disc in degrees. Default 0.53 (real sun). */
    public void setSunRadius(float degrees) {
        sunRadius = degrees * MathUtils.degreesToRadians * 0.5f;
    }

    /** Set sun brightness multiplier. Default is 8. */
    public void setSunIntensity(float intensity) {
        sunIntensity = intensity;
    }

    // ---- Rendering ----

    /** Execute a render pass to show the procedural sky. */
    public void renderPass(Camera cam) {
        renderPass(cam, false);
    }

    /** Execute a render pass to show the procedural sky. */
    public void renderPass(Camera cam, boolean clearDepth) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        WGPUTextureFormat fmt = webgpu.surfaceFormats[0];
        int samples = webgpu.getSamples();
        WebGPUPipeline pipeline = getOrCreatePipeline(fmt, samples);

        int offset = writeUniforms(cam);
        WebGPURenderPass pass = RenderPassBuilder.createFirstTargetOnly("procedural_sky", clearDepth, samples);
        pass.setPipeline(pipeline.getPipeline());
        pass.setBindGroup(0, bindGroup.getBindGroup(), offset);
        pass.draw(3);
        pass.end();
    }

    /** Render procedural sky within an existing render pass. */
    public void render(Camera camera, WebGPURenderPass pass) {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        WebGPUContext webgpu = gfx.getContext();
        WebGPUPipeline pipeline = getOrCreatePipeline(webgpu.surfaceFormats[0], webgpu.getSamples());
        int offset = writeUniforms(camera);
        pass.setPipeline(pipeline.getPipeline());
        pass.setBindGroup(0, bindGroup.getBindGroup(), offset);
        pass.draw(3);
    }

    // ---- Internal ----

    private WebGPUPipeline getOrCreatePipeline(WGPUTextureFormat colorFormat, int numSamples) {
        long key = ((long) colorFormat.ordinal() << 32) | (numSamples & 0xFFFFFFFFL);
        WebGPUPipeline cached = pipelineCache.get(key);
        if (cached != null) return cached;

        PipelineSpecification spec = new PipelineSpecification();
        spec.name = "procedural sky pipeline";
        spec.vertexAttributes = null;
        spec.environment = null;
        spec.shader = null;
        spec.shaderSource = shaderSource;
        spec.enableDepthTest();
        spec.setCullMode(WGPUCullMode.Back);
        spec.colorFormats = new WGPUTextureFormat[]{colorFormat};
        spec.depthFormat = WGPUTextureFormat.Depth24Plus;
        spec.numSamples = numSamples;
        spec.isSkyBox = true;

        WebGPUPipeline pipeline = new WebGPUPipeline(pipelineLayout.getLayout(), spec);
        pipelineCache.put(key, pipeline);
        return pipeline;
    }

    private int writeUniforms(Camera camera) {
        // Reset slices at the start of each frame
        long frameId = Gdx.graphics.getFrameId();
        if (frameId != lastFrameId) {
            lastFrameId = frameId;
            uniformBuffer.beginSlices();
        }
        dynamicOffset = uniformBuffer.nextSlice();

        invertedProjectionView.set(camera.combined);
        invertedProjectionView.setTranslation(Vector3.Zero);
        invertedProjectionView.val[M33] = 1.0f;
        try {
            invertedProjectionView.inv();
        } catch (RuntimeException e) {
            Gdx.app.error("ProceduralSkyBox", "camera matrix not invertible");
            return dynamicOffset;
        }

        uniformBuffer.set(OFF_MATRIX, invertedProjectionView);
        setVec4(OFF_SUN_DIR, sunDirX, sunDirY, sunDirZ, 0f);
        setVec4(OFF_ZENITH, zenithR, zenithG, zenithB, 0f);
        setVec4(OFF_HORIZON, horizonR, horizonG, horizonB, 0f);
        setVec4(OFF_GROUND, groundR, groundG, groundB, 0f);
        setVec4(OFF_SUN_PARAMS, sunRadius, sunIntensity, 0f, 0f);
        uniformBuffer.flush();
        return dynamicOffset;
    }

    private void setVec4(int offset, float x, float y, float z, float w) {
        uniformBuffer.set(offset, x);
        uniformBuffer.set(offset + 4, y);
        uniformBuffer.set(offset + 8, z);
        uniformBuffer.set(offset + 12, w);
    }

    @Override
    public void dispose() {
        for (WebGPUPipeline p : pipelineCache.values()) p.dispose();
        pipelineCache.clear();
        pipelineLayout.dispose();
        bindGroup.dispose();
        bindGroupLayout.dispose();
        uniformBuffer.dispose();
    }

    private static float toLinear(float srgb) {
        return (float) Math.pow(srgb, 2.2);
    }
}


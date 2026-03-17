package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.math.Vector3;
import com.github.xpenatan.webgpu.WGPUBufferBindingType;
import com.github.xpenatan.webgpu.WGPUSamplerBindingType;
import com.github.xpenatan.webgpu.WGPUShaderStage;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.github.xpenatan.webgpu.WGPUTextureSampleType;
import com.github.xpenatan.webgpu.WGPUTextureViewDimension;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgIDShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgDirectionalShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;
import com.monstrous.gdx.webgpu.wrappers.WebGPUBindGroupLayout;

import java.util.Locale;

/**
 * Edge Detection Outline Test using MRT (Multiple Render Targets).
 * Demonstrates depth-aware outline rendering where:
 * - Each model gets a unique ID encoded in secondary MRT target
 * - A post-process shader detects edges where object IDs differ between pixels
 * - Front models naturally occlude back model outlines due to depth testing
 * - No outline blending artifacts between overlapping models
 */
public class EdgeDetectionOutlineTest extends GdxTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final int OUTLINE_COLOR_OFFSET = 16 * Float.BYTES;
    private static final Color[] OUTLINE_COLORS = {
            new Color(Color.RED),
            new Color(Color.CYAN),
            new Color(Color.LIME),
            new Color(Color.YELLOW),
            new Color(Color.MAGENTA),
            new Color(Color.WHITE)
    };
    private static final String[] OUTLINE_COLOR_NAMES = {
            "Red",
            "Cyan",
            "Lime",
            "Yellow",
            "Magenta",
            "White"
    };

    private static class OutlineSpriteBatch extends WgSpriteBatch {
        private final Color outlineColor = new Color(Color.RED);

        public void setOutlineColor(Color color) {
            outlineColor.set(color);
        }

        @Override
        protected int getUniformBufferSize() {
            return super.getUniformBufferSize() + 4 * Float.BYTES;
        }

        @Override
        protected void defineBindings(Binder binder) {
            super.defineBindings(binder);
            binder.defineUniform("outlineColor", 0, 0, OUTLINE_COLOR_OFFSET);
        }

        @Override
        protected void updateMatrices() {
            super.updateMatrices();
            binder.setUniform("outlineColor", outlineColor);
        }

        @Override
        protected WebGPUBindGroupLayout createBindGroupLayout() {
            WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("SpriteBatch bind group layout");
            layout.begin();
            layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment),
                    WGPUBufferBindingType.Uniform, getUniformBufferSize(), true);
            layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float,
                    WGPUTextureViewDimension._2D, false);
            layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
            layout.end();
            return layout;
        }
    }

    // Minimal depth-visualisation shader provider that re-uses WgDepthShader but requests the
    // fragment entry point `fs_depth_vis` (added to depthshader.wgsl) so it outputs a greyscale
    // depth image into a colour attachment.
    private static class WgDepthVisShaderProvider extends WgDepthShaderProvider {
        public WgDepthVisShaderProvider(final WgModelBatch.Config config) {
            super(config);
        }

        @Override
        protected Shader createShader(final com.badlogic.gdx.graphics.g3d.Renderable renderable) {
            // Construct a WgDepthShader that provides the fragment entry point name so a color target
            // will be created and the shader can output depth as color.
            return new WgDepthShader(renderable, this.config, WgDepthShader.getDefaultShaderSource(), "fs_depth_vis");
        }
    }

    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
    WgModelBatch depthVisBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    OutlineSpriteBatch batch;
    WgBitmapFont font;
    Environment environment;
    Environment emptyEnvironment;
    WgFrameBuffer mrtFbo;
    WgFrameBuffer shadowCamFbo;   // colour FBO rendered from the shadow camera's point of view
    WgShaderProgram edgeDetectionShader;
    float currentOutlineWidth = 1.0f;
    private int currentOutlineColorIndex = 0;
    private final Color outlineColor = new Color(OUTLINE_COLORS[0]);
    private Viewport viewport;
    private Array<Disposable> disposables;
    /** 0 = split-screen with outline, 1 = full-color full-screen, 2 = shadow-camera depth view */
    private int renderMode = 0;
    private static final int MODE_SPLIT    = 0;
    private static final int MODE_FULL     = 1;
    private static final int MODE_SHADOW   = 2;
    private static final String[] MODE_NAMES = { "Split-screen outline", "Full-screen color", "Shadow depth view" };
    WgDirectionalShadowLight shadowLight;
    WgCubemap cubemap;
    SkyBox skybox;
    private int currentSkyboxIndex = 0;
    private static final String[] SKYBOX_NAMES = {"environment_01", "environment_02", "leadenhall"};
    Vector3 lightPos;

    // Shadow bias control
    private float shadowBias = 0.01f;
    private static final float BIAS_STEP = 0.01f;
    private static final float BIAS_MIN = 0.01f;
    private static final float BIAS_MAX = 0.15f;

    Model[] staticModels;
    WgModelInstance[] staticInstances;
    float[] staticRotations;

    Model groundModel;
    WgModelInstance groundInstance;

    Model morphModel;
    WgModelInstance morphInstance;
    AnimationController morphAnimationController;

    Model skinnedModel;
    WgModelInstance skinnedInstance;
    AnimationController skinnedAnimationController;

    private static String formatValue(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    public void create() {
        disposables = new Array<>();

        // Setup MRT FrameBuffer (Color + Object ID)
        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {
            WGPUTextureFormat.BGRA8Unorm,    // Color target (location 0)
            WGPUTextureFormat.RGBA8Unorm     // Object ID target (location 1)
        };
        mrtFbo = new WgFrameBuffer(formats, WIDTH, HEIGHT, true);

        // Shadow camera FBO: plain colour+depth, same resolution as the shadow map
        final int MAP = 2048;
        shadowCamFbo = new WgFrameBuffer(MAP, MAP, true);

        // Setup model batch with edge detection shader provider
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.maxInstances = 100;
        config.numBones = 100;  // Increased to support SillyDancing model (65 bones)
        modelBatch = new WgModelBatch(new WgIDShaderProvider(config, true));
        disposables.add(modelBatch);

        // Improved camera setup
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 2, 8);
        cam.lookAt(0, 0.5f, 0);
        cam.near = 0.1f;
        cam.far = 100f;

        controller = new CameraInputController(cam);

        // Use InputMultiplexer to allow both camera input and test input
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(this);  // Add this test class first
        multiplexer.addProcessor(controller);  // Then add camera controller
        Gdx.input.setInputProcessor(multiplexer);

        batch = new OutlineSpriteBatch();
        setOutlineColor(OUTLINE_COLORS[currentOutlineColorIndex]);
        disposables.add(batch);
        font = new WgBitmapFont();
        disposables.add(font);

        // Load edge detection shader
        updateOutlineShader();

        // Create environment with enhanced lighting
        environment = new Environment();
        emptyEnvironment = new Environment();

        lightPos = new Vector3(-.75f, 1f, -0.75f);

        // Enhanced lighting setup with warm main light and cool fill light
        float ambientLevel = 0.3f;
        ColorAttribute ambient = ColorAttribute.createAmbientLight(ambientLevel, ambientLevel, ambientLevel, 1f);
        environment.set(ambient);

        // Main directional light with warm color (yellowish-white for pleasant appearance)
        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(-lightPos.x, -lightPos.y, -lightPos.z);
        float intensity = 1.5f;
        dirLight1.setColor(intensity, 0.95f * intensity, 0.8f * intensity, 1f); // Warm white color
        environment.add(dirLight1);

        // Add a secondary fill light for better illumination
        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(0.5f, -0.3f, 0.8f); // Opposite direction from main light
        fillLight.setColor(0.3f, 0.35f, 0.4f, 1f); // Cool blue tint for contrast
        environment.add(fillLight);

        // Set up shadow mapping
        final int VIEWPORT = 18;
        final float DEPTH = 10f;
        shadowLight = new WgDirectionalShadowLight(MAP, MAP, VIEWPORT, VIEWPORT, 0.01f, DEPTH);
        shadowLight.setDirection(dirLight1.direction);
        shadowLight.set(dirLight1);
        environment.shadowMap = shadowLight;

        // Add skybox with PBR environment mapping
        currentSkyboxIndex = 0;
        loadSkybox(currentSkyboxIndex);

        // Create multiple models with different colors
        WgModelBuilder builder = new WgModelBuilder();
        long vertexUsage = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;

        staticModels = new Model[2];
        staticInstances = new WgModelInstance[2];
        staticRotations = new float[2];

        // Static Model 0: Red cube (front left)
        Material redMaterial = new Material(ColorAttribute.createDiffuse(Color.RED));
        staticModels[0] = builder.createBox(2f, 2f, 2f, redMaterial, vertexUsage);
        staticInstances[0] = new WgModelInstance(staticModels[0]);
        staticInstances[0].transform.setToTranslation(-4, 1, 0);
        disposables.add(staticModels[0]);

        // Static Model 1: Green sphere (back left)
        Material greenMaterial = new Material(ColorAttribute.createDiffuse(Color.GREEN));
        staticModels[1] = builder.createSphere(1.5f, 1.5f, 1.5f, 32, 32, greenMaterial, vertexUsage);
        staticInstances[1] = new WgModelInstance(staticModels[1]);
        staticInstances[1].transform.setToTranslation(-2, 1, -3);
        disposables.add(staticModels[1]);

        // Create ground plane with improved appearance - dark blue-gray
        Material groundMat = new Material(ColorAttribute.createDiffuse(0.25f, 0.28f, 0.32f, 1f));
        groundModel = builder.createBox(16f, 0.1f, 16f, groundMat, vertexUsage);
        groundInstance = new WgModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0, -0.1f, 0);
        disposables.add(groundModel);

        // Load animated morph model
        morphModel = loadMorphModel();
        if (morphModel != null) {
            morphInstance = new WgModelInstance(morphModel);
            morphInstance.transform.setToTranslation(-1, 0, 0);
            if (morphInstance.animations != null && morphInstance.animations.size > 0) {
                morphAnimationController = new AnimationController(morphInstance);
                morphAnimationController.setAnimation(morphInstance.animations.get(0).id, -1);
            }
            disposables.add(morphModel);
        }

        // Load animated skinned model
        skinnedModel = loadSkinnedModel();
        if (skinnedModel != null) {
            skinnedInstance = new WgModelInstance(skinnedModel);
            skinnedInstance.transform.setToTranslation(1, 0, 2);
            if (skinnedInstance.animations != null && skinnedInstance.animations.size > 0) {
                skinnedAnimationController = new AnimationController(skinnedInstance);
                skinnedAnimationController.setAnimation(skinnedInstance.animations.get(0).id, -1);
            }
            disposables.add(skinnedModel);
        }

        // Initialize shadow batch for rendering shadow maps
        shadowBatch = new WgModelBatch(new WgDepthShaderProvider(config));
        disposables.add(shadowBatch);

        // Initialize depth visualization batch
        depthVisBatch = new WgModelBatch(new WgDepthVisShaderProvider(config));
        disposables.add(depthVisBatch);

        viewport = new ScreenViewport();
    }

    public void setOutlineColor(Color color) {
        outlineColor.set(color);
        if (batch != null) {
            batch.setOutlineColor(outlineColor);
        }
    }

    private void updateOutlineShader() {
        if(edgeDetectionShader != null) {
            edgeDetectionShader.dispose();
        }
        String baseShaderSource = Gdx.files.internal("data/wgsl/edge-detection-outline.wgsl").readString();
        String prefix = "#define OUTLINE_WIDTH " + currentOutlineWidth + "\n";
        edgeDetectionShader = new WgShaderProgram("edge-detection-", baseShaderSource,
            prefix);
    }

    private void loadSkybox(int skyboxIndex) {
        // Dispose old skybox and cubemap if they exist
        if (skybox != null) {
            skybox.dispose();
            skybox = null;
        }
        if (cubemap != null) {
            cubemap.dispose();
            cubemap = null;
        }

        // Load new skybox based on index
        String skyboxName = SKYBOX_NAMES[skyboxIndex];
        String prefix = "data/g3d/environment/";

        try {
            String[] fileNames;
            if (skyboxName.equals("leadenhall")) {
                // Leadenhall uses jpg files in a subdirectory
                String[] sides = {"pos-x.jpg", "neg-x.jpg", "pos-y.jpg", "neg-y.jpg", "pos-z.jpg", "neg-z.jpg"};
                prefix += "leadenhall/";
                fileNames = sides;
            } else {
                // environment_01 and environment_02 use PNG files with different naming
                fileNames = new String[6];
                String namePrefix = skyboxName + "_";
                fileNames[0] = namePrefix + "PX.png";  // pos-x
                fileNames[1] = namePrefix + "NX.png";  // neg-x
                fileNames[2] = namePrefix + "PY.png";  // pos-y
                fileNames[3] = namePrefix + "NY.png";  // neg-y
                fileNames[4] = namePrefix + "PZ.png";  // pos-z
                fileNames[5] = namePrefix + "NZ.png";  // neg-z
            }

            FileHandle[] fileHandles = new FileHandle[6];
            for (int i = 0; i < fileNames.length; i++) {
                fileHandles[i] = Gdx.files.internal(prefix + fileNames[i]);
            }

            cubemap = new WgCubemap(fileHandles[0], fileHandles[1], fileHandles[2], fileHandles[3], fileHandles[4],
                    fileHandles[5], true);

            // Update environment with new cubemap for PBR reflections
            environment.set(new WgCubemapAttribute(WgCubemapAttribute.EnvironmentMap, cubemap));

            // Create skybox from cubemap
            skybox = new SkyBox(cubemap);

            System.out.println("Successfully loaded skybox: " + skyboxName);
        } catch (Exception e) {
            System.err.println("Failed to load skybox '" + skyboxName + "': " + e.getMessage());
            skybox = null;
        }
    }

    private Model loadMorphModel() {
        try {
            String modelFileName = "data/g3d/gltf/MorphStressTest/MorphStressTest.glb";
            WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
            params.textureParameter.genMipMaps = true;
            FileHandle file = Gdx.files.internal(modelFileName);
            if (file.exists()) {
                return new WgGLBModelLoader().loadModel(file, params);
            }
        } catch (Exception e) {
            System.err.println("Failed to load morph model: " + e.getMessage());
        }
        return null;
    }

    private Model loadSkinnedModel() {
        try {
            String modelFileName = "data/g3d/gltf/SillyDancing/SillyDancing.gltf";
            WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
            params.textureParameter.genMipMaps = true;
            FileHandle file = Gdx.files.internal(modelFileName);
            if (file.exists()) {
                return new WgGLTFModelLoader().loadModel(file, params);
            }
        } catch (Exception e) {
            System.err.println("Failed to load skinned model: " + e.getMessage());
        }
        return null;
    }

    /** Handle keyboard input for outline width, shadow bias, skybox switching, and render mode toggle */
    @Override
    public boolean keyDown(int keycode) {
        // Toggle render mode with 'T' key
        if (keycode == Input.Keys.T) {
            renderMode = (renderMode + 1) % 3;
            System.out.println("Render mode: " + MODE_NAMES[renderMode]);
            return true;
        }

        // Cycle outline color with 'C' key
        if (keycode == Input.Keys.C) {
            currentOutlineColorIndex = (currentOutlineColorIndex + 1) % OUTLINE_COLORS.length;
            setOutlineColor(OUTLINE_COLORS[currentOutlineColorIndex]);
            System.out.println("Outline color: " + OUTLINE_COLOR_NAMES[currentOutlineColorIndex]);
            return true;
        }

        // Switch skybox with 'S' key
        if (keycode == Input.Keys.S) {
            currentSkyboxIndex = (currentSkyboxIndex + 1) % SKYBOX_NAMES.length;
            loadSkybox(currentSkyboxIndex);
            System.out.println("Switched to skybox: " + SKYBOX_NAMES[currentSkyboxIndex]);
            return true;
        }

        // In split-screen mode: + / - adjust outline width
        // In full-screen mode: + / - adjust shadow bias
        if (keycode == Input.Keys.PLUS || keycode == Input.Keys.EQUALS) {
            if (renderMode != MODE_SPLIT) {
                // Full-screen / shadow mode: adjust shadow bias
                shadowBias = Math.min(shadowBias + BIAS_STEP, BIAS_MAX);
                System.out.println("Shadow Bias: " + formatValue(shadowBias));
            } else {
                // Split-screen mode: adjust outline width
                currentOutlineWidth = Math.min(currentOutlineWidth + 0.25f, 3.0f);
                System.out.println("Outline width: " + formatValue(currentOutlineWidth) + " pixels");
                updateOutlineShader();
            }
            return true;
        }
        if (keycode == Input.Keys.MINUS) {
            if (renderMode != MODE_SPLIT) {
                // Full-screen / shadow mode: adjust shadow bias
                shadowBias = Math.max(shadowBias - BIAS_STEP, BIAS_MIN);
                System.out.println("Shadow Bias: " + formatValue(shadowBias));
            } else {
                // Split-screen mode: adjust outline width
                currentOutlineWidth = Math.max(currentOutlineWidth - 0.25f, 0.25f);
                System.out.println("Outline width: " + formatValue(currentOutlineWidth) + " pixels");
                updateOutlineShader();
            }
            return true;
        }

        return false;
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        cam.update();

        // Update animations
        if (morphAnimationController != null) {
            morphAnimationController.update(delta);
        }
        if (skinnedAnimationController != null) {
            skinnedAnimationController.update(delta);
        }

        // Update static model rotations
        for (int i = 0; i < staticRotations.length; i++) {
            staticRotations[i] += delta * 20f;
        }

        // Update static model transforms
        staticInstances[0].transform.idt();
        staticInstances[0].transform.translate(1, 1, -3);
        staticInstances[0].transform.rotate(com.badlogic.gdx.math.Vector3.Y, staticRotations[0]);

        staticInstances[1].transform.idt();
        staticInstances[1].transform.translate(-2, 1, -3);
        staticInstances[1].transform.rotate(com.badlogic.gdx.math.Vector3.Y, staticRotations[1]);

        if (renderMode == MODE_FULL) {
            renderFullScreen();
        } else if (renderMode == MODE_SHADOW) {
            renderShadowDepth();
        } else {
            renderSplitScreen();
        }
    }

    private void renderFullScreen() {
        // Render directly to screen without framebuffer
        WgScreenUtils.clear(0.15f, 0.18f, 0.22f, 1f, true);  // Dark blue-gray background

        // Render scene to screen directly
        renderScene(cam, environment);

        // Draw info text overlay
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 20, 60);
        font.draw(batch, "Shadow Bias: " + formatValue(shadowBias) + "  (+/- to adjust)", 20, 80);
        font.draw(batch, "Skybox: " + SKYBOX_NAMES[currentSkyboxIndex] + "  (S = switch)", 20, 100);
        font.draw(batch, "T = Cycle render mode (current: " + MODE_NAMES[renderMode] + ")", 20, 120);
        batch.end();
    }

    private void renderSplitScreen() {
        // Render to MRT FBO with improved background color
        mrtFbo.begin();
        {
            WgScreenUtils.clear(0.15f, 0.18f, 0.22f, 1f, true);  // Dark blue-gray background

            // Render scene to framebuffer
            renderScene(cam, environment);
        }
        mrtFbo.end();

        // Render skybox for PBR environment mapping
        if (skybox != null) {
            skybox.renderPass(cam);
        }

        // Render to screen with split-screen and outline
        WgScreenUtils.clear(0.15f, 0.18f, 0.22f, 1f);  // Dark blue-gray background
        renderSplitScreenUI();
    }

    private void renderShadow() {
        Vector3 focalPoint = Vector3.Zero;
        shadowLight.begin(focalPoint, Vector3.Zero);
        shadowBatch.begin(shadowLight.getCamera(), Color.BLUE, true, RenderPassType.DEPTH_ONLY);
        shadowBatch.render(groundInstance);
        for (WgModelInstance inst : staticInstances) {
            shadowBatch.render(inst);
        }
        if (morphInstance != null) {
            shadowBatch.render(morphInstance);
        }
        if (skinnedInstance != null) {
            shadowBatch.render(skinnedInstance);
        }
        shadowBatch.end();
        shadowLight.end();
    }

    private void renderScene(PerspectiveCamera camera, Environment env) {
        // Render shadow map first with fixed focal point at world center
        renderShadow();

        // Apply current shadow bias to environment
        applyCurrentShadowBias();

        // Render main scene with shadows
        modelBatch.begin(camera);
        modelBatch.render(groundInstance, env);
        for (WgModelInstance inst : staticInstances) {
            modelBatch.render(inst, env);
        }
        if (morphInstance != null) {
            modelBatch.render(morphInstance, env);
        }
        if (skinnedInstance != null) {
            modelBatch.render(skinnedInstance, env);
        }
        modelBatch.end();

        // Render skybox for PBR environment mapping
        if (skybox != null) {
            skybox.renderPass(cam);
        }
    }

    private void applyCurrentShadowBias() {
        // Apply the current shadow bias to the environment for shadow rendering
        if (environment != null) {
            environment.remove(PBRFloatAttribute.ShadowBias);
            environment.set(PBRFloatAttribute.createShadowBias(shadowBias));
        }
    }

    private void renderSplitScreenUI() {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.setOutlineColor(outlineColor);
        batch.begin();
        float w = Gdx.graphics.getWidth() / 3f;
        float h = Gdx.graphics.getHeight();
        float compositeX = w * 2f;

        // Draw Color Output (left side)
        batch.draw(mrtFbo.getColorBufferTexture(0), 0, 0, w, h);
        font.draw(batch, "Color Output", 20, 40);

        // Draw Edge Detection Output (middle panel)
        batch.setShader(edgeDetectionShader);
        batch.draw(mrtFbo.getColorBufferTexture(1), w, 0, w, h);
        batch.setShader((WgShaderProgram) null);
        font.draw(batch, "Edge Detection Outline", w + 20, 40);

        // Draw composite output (right side): color scene + outline overlay
        batch.draw(mrtFbo.getColorBufferTexture(0), compositeX, 0, w, h);
        batch.setShader(edgeDetectionShader);
        batch.draw(mrtFbo.getColorBufferTexture(1), compositeX, 0, w, h);
        batch.setShader((WgShaderProgram) null);
        font.draw(batch, "Color + Outline", compositeX + 20, 40);

        // Draw info text
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 20, 60);
        font.draw(batch, "+ / - keys: Adjust outline width", 20, 80);
        font.draw(batch, "Current width: " + formatValue(currentOutlineWidth) + " pixels", 20, 100);
        font.draw(batch, "C: Cycle outline color (" + OUTLINE_COLOR_NAMES[currentOutlineColorIndex] + ")", 20, 120);
        font.draw(batch, "Outline color: rgba(" + formatValue(outlineColor.r) + ", "
                + formatValue(outlineColor.g) + ", " + formatValue(outlineColor.b) + ", "
                + formatValue(outlineColor.a) + ")", 20, 140);
        font.draw(batch, "Shadow Bias: " + formatValue(shadowBias) + " (full-screen mode)", 20, 160);
        font.draw(batch, "Skybox: " + SKYBOX_NAMES[currentSkyboxIndex] + "  (S = switch)", 20, 180);
        font.draw(batch, "T = Cycle render mode (" + MODE_NAMES[renderMode] + ")", 20, 200);

        batch.end();
    }

    /**
     * Render mode 2: Show what the shadow camera sees (rendered with the regular modelBatch
     * into a plain colour FBO, then blitted full-screen with the standard batch).
     * No custom shader or custom SpriteBatch needed.
     */
    private void renderShadowDepth() {
        // First update the real shadow map so renderScene() stays correct next frame.
        renderShadow();

        // Render the depth-visualisation pass into the shadowCamFbo using depthVisBatch
        shadowCamFbo.begin();
        WgScreenUtils.clear(0f, 0f, 0f, 1f, true);
        depthVisBatch.begin(shadowLight.getCamera());
        depthVisBatch.render(groundInstance);
        for (WgModelInstance inst : staticInstances) {
            depthVisBatch.render(inst);
        }
        if (morphInstance != null) {
            depthVisBatch.render(morphInstance);
        }
        if (skinnedInstance != null) {
            depthVisBatch.render(skinnedInstance);
        }
        depthVisBatch.end();
        shadowCamFbo.end();

        // Blit the shadow camera colour texture full-screen with the plain batch.
        WgScreenUtils.clear(0f, 0f, 0f, 1f);
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        batch.draw(shadowCamFbo.getColorBufferTexture(), 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        font.draw(batch, "Shadow Camera Depth View", 20, 40);
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 20, 60);
        font.draw(batch, "T = Cycle render mode (" + MODE_NAMES[renderMode] + ")", 20, 80);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        viewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        // Dispose skybox and cubemap manually (not in disposables array)
        if (skybox != null) {
            try {
                skybox.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing skybox: " + e.getMessage());
            }
            skybox = null;
        }
        if (cubemap != null) {
            try {
                cubemap.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing cubemap: " + e.getMessage());
            }
            cubemap = null;
        }

        // Dispose edge detection shader
        if (edgeDetectionShader != null) {
            try {
                edgeDetectionShader.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing shader: " + e.getMessage());
            }
        }

        // Dispose MRT framebuffer
        if (mrtFbo != null) {
            try {
                mrtFbo.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing MRT FBO: " + e.getMessage());
            }
        }

        if (shadowCamFbo != null) {
            try {
                shadowCamFbo.dispose();
            } catch (Exception e) {
                System.err.println("Error disposing shadow cam FBO: " + e.getMessage());
            }
        }

        // Dispose all resources from the array
        if (disposables != null) {
            for (Disposable disposable : disposables) {
                try {
                    if (disposable != null) {
                        disposable.dispose();
                    }
                } catch (Exception e) {
                    System.err.println("Error disposing resource: " + e.getMessage());
                }
            }
            disposables.clear();
        }
    }
}

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
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgEdgeDetectionOutlineShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.environment.WgDirectionalShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

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

    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Environment environment;
    Environment emptyEnvironment;
    WgFrameBuffer mrtFbo;
    WgShaderProgram[] edgeDetectionShaders;
    int currentOutlineWidthIndex = 2; // Start at 1.0 (middle)
    float[] outlineWidths = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f};
    boolean[] keyPressed = new boolean[256];
    private Viewport viewport;
    private Array<Disposable> disposables;
    private boolean showFullScreenModel = false;  // Toggle between split-screen and full-screen render
    WgDirectionalShadowLight shadowLight;
    WgCubemap cubemap;
    SkyBox skybox;
    private int currentSkyboxIndex = 0;
    private static final String[] SKYBOX_NAMES = {"environment_01", "environment_02", "leadenhall"};
    Vector3 lightPos;
    WgGraphics gfx;
    WebGPUContext webgpu;

    // Shadow bias control
    private float shadowBias = 0.04f;
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

    public void create() {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        disposables = new Array<>();

        // Setup MRT FrameBuffer (Color + Object ID)
        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {
            WGPUTextureFormat.BGRA8Unorm,    // Color target (location 0)
            WGPUTextureFormat.RGBA8Unorm     // Object ID target (location 1)
        };
        mrtFbo = new WgFrameBuffer(formats, WIDTH, HEIGHT, true);

        // Setup model batch with edge detection shader provider
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.maxInstances = 100;
        config.numBones = 100;  // Increased to support SillyDancing model (65 bones)
        modelBatch = new WgModelBatch(new WgEdgeDetectionOutlineShaderProvider(config));
//        modelBatch = new WgModelBatch(config);
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

        batch = new WgSpriteBatch();
        disposables.add(batch);
        font = new WgBitmapFont();
        disposables.add(font);

        // Load edge detection shaders with different outline widths
        String baseShaderSource = Gdx.files.internal("data/wgsl/edge-detection-outline.wgsl").readString();
        edgeDetectionShaders = new WgShaderProgram[outlineWidths.length];
        for (int i = 0; i < outlineWidths.length; i++) {
            String prefix = "#define OUTLINE_WIDTH " + outlineWidths[i] + "\n";
            edgeDetectionShaders[i] = new WgShaderProgram(
                "edge-detection-" + outlineWidths[i],
                baseShaderSource,
                prefix
            );
        }

        // Create environment with enhanced lighting
        environment = new Environment();
        emptyEnvironment = new Environment();

        lightPos = new Vector3(-.75f, 1f, -1.00f);

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
        final int MAP = 2048;
        final int VIEWPORT = 8;
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

        viewport = new ScreenViewport();
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
            showFullScreenModel = !showFullScreenModel;
            System.out.println("Render mode: " + (showFullScreenModel ? "Full-screen model" : "Split-screen with outline"));
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
            if (showFullScreenModel) {
                // Full-screen mode: adjust shadow bias
                shadowBias = Math.min(shadowBias + BIAS_STEP, BIAS_MAX);
                System.out.println("Shadow Bias: " + String.format("%.2f", shadowBias));
            } else {
                // Split-screen mode: adjust outline width
                if (!keyPressed[Input.Keys.PLUS]) {
                    currentOutlineWidthIndex = Math.min(outlineWidths.length - 1, currentOutlineWidthIndex + 1);
                    keyPressed[Input.Keys.PLUS] = true;
                }
            }
            return true;
        }
        if (keycode == Input.Keys.MINUS) {
            if (showFullScreenModel) {
                // Full-screen mode: adjust shadow bias
                shadowBias = Math.max(shadowBias - BIAS_STEP, BIAS_MIN);
                System.out.println("Shadow Bias: " + String.format("%.2f", shadowBias));
            } else {
                // Split-screen mode: adjust outline width
                if (!keyPressed[Input.Keys.MINUS]) {
                    currentOutlineWidthIndex = Math.max(0, currentOutlineWidthIndex - 1);
                    keyPressed[Input.Keys.MINUS] = true;
                }
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        keyPressed[keycode] = false;
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

        if (showFullScreenModel) {
            renderFullScreen();
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
        font.draw(batch, "Shadow Bias: " + String.format("%.2f", shadowBias) + "  (+/- to adjust)", 20, 80);
        font.draw(batch, "Skybox: " + SKYBOX_NAMES[currentSkyboxIndex] + "  (S = switch)", 20, 100);
        font.draw(batch, "T = Toggle render mode", 20, 120);
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

    private void renderScene(PerspectiveCamera camera, Environment env) {
        // Render shadow map first with fixed focal point at world center
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
        batch.begin();
        float w = Gdx.graphics.getWidth() / 2f;
        float h = Gdx.graphics.getHeight();

        // Draw Color Output (left side)
        batch.draw(mrtFbo.getColorBufferTexture(0), 0, 0, w, h);
        font.draw(batch, "Color Output", 20, 40);

        // Draw Edge Detection Output (right side)
        batch.setShader(edgeDetectionShaders[currentOutlineWidthIndex]);
        batch.draw(mrtFbo.getColorBufferTexture(1), w, 0, w, h);
        batch.setShader((WgShaderProgram) null);
        font.draw(batch, "Edge Detection Outline", w + 20, 40);

        // Draw info text
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 20, 60);
        font.draw(batch, "+ / - keys: Adjust outline width", 20, 80);
        font.draw(batch, "Current width: " + String.format("%.2f", outlineWidths[currentOutlineWidthIndex]) + " pixels", 20, 100);
        font.draw(batch, "Shadow Bias: " + String.format("%.2f", shadowBias) + " (full-screen mode)", 20, 120);
        font.draw(batch, "Skybox: " + SKYBOX_NAMES[currentSkyboxIndex] + "  (S = switch)", 20, 140);
        font.draw(batch, "T = Toggle render mode", 20, 160);

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

        // Dispose edge detection shaders
        for (WgShaderProgram shader : edgeDetectionShaders) {
            if (shader != null) {
                try {
                    shader.dispose();
                } catch (Exception e) {
                    System.err.println("Error disposing shader: " + e.getMessage());
                }
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

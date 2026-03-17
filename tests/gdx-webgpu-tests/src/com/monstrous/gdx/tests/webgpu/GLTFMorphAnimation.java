package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgDirectionalShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.wrappers.SkyBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Test GLTF Morph animation */
public class GLTFMorphAnimation extends GdxTest {

    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model model;
    ModelInstance instance;
    String modelFileName;
    Environment environment;
    int numMeshes;
    int numVerts;
    int numIndices;
    WgGraphics gfx;
    WebGPUContext webgpu;
    private Viewport viewport;
    private AnimationController animationController;
    Model ground;
    Array<ModelInstance> instances;
    Array<Disposable> disposables;
    WgDirectionalShadowLight shadowLight;
    Vector3 lightPos;
    Environment emptyEnvironment;
    WgCubemap cubemap;
    SkyBox skybox;
    private int currentSkyboxIndex = 0;
    private static final String[] SKYBOX_NAMES = {"environment_01", "environment_02", "leadenhall"};

    // animation cycling
    private List<String> animationNames = new ArrayList<>();
    private int currentAnimationIndex = 0;

    // Shadow bias control
    private float shadowBias = 0.04f;
    private static final float BIAS_STEP = 0.01f;
    private static final float BIAS_MIN = 0.01f;
    private static final float BIAS_MAX = 0.15f;

    // application
    public void create() {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        disposables = new Array<>();

        WgModelBatch.Config config = new WgModelBatch.Config();
        modelBatch = new WgModelBatch(config);
        disposables.add(modelBatch);

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(2, 2, 3f);
        cam.lookAt(0, 1, 0);
        cam.near = 0.001f;
        cam.far = 100f;

        controller = new CameraInputController(cam);

        // Use InputMultiplexer to allow both camera input and test input
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(this);  // Add this test class first to intercept SPACE and +/- keys
        multiplexer.addProcessor(controller);  // Then add camera controller for camera input
        Gdx.input.setInputProcessor(multiplexer);

        batch = new WgSpriteBatch();
        disposables.add(batch);
        font = new WgBitmapFont();
        disposables.add(font);

        instances = new Array<>();

        // Create ground model with better visual appearance
        ModelBuilder modelBuilder = new WgModelBuilder();
        // Use a nicer ground color - dark gray with slight blue tint for elegance
        Material groundMat = new Material(ColorAttribute.createDiffuse(0.25f, 0.28f, 0.32f, 1f));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.ColorPacked;
        ground = modelBuilder.createBox(8, 0.1f, 9, groundMat, attribs);
        disposables.add(ground);
        instances.add(new ModelInstance(ground, 0, 0, 0));

        modelFileName = "data/g3d/gltf/MorphStressTest/MorphStressTest.glb";

        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        FileHandle file = Gdx.files.internal(modelFileName);
        if (file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if (file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: " + modelFileName);

        disposables.add(model);
        instance = new WgModelInstance(model, 0, 0.15f, 0);
        instances.add(instance);

        // collect all animation names from the model
        for (Animation anim : model.animations) {
            animationNames.add(anim.id);
            System.out.println("Found animation: " + anim.id);
        }

        animationController = new AnimationController(instance);

        // start with the first animation if any exist
        if (!animationNames.isEmpty()) {
            animationController.setAnimation(animationNames.get(0), -1);
        }

        numMeshes = instance.model.meshes.size;
        for (int i = 0; i < numMeshes; i++) {
            numVerts += instance.model.meshes.get(i).getNumVertices();
            numIndices += instance.model.meshes.get(i).getNumIndices();
        }

        // Set up lighting and shadows
        environment = new Environment();
        emptyEnvironment = new Environment();

        lightPos = new Vector3(-.75f, 1f, -1.00f);
        Vector3 vec = new Vector3(lightPos).nor();

        Model lightArrow = modelBuilder.createArrow(vec, Vector3.Zero, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
        disposables.add(lightArrow);
        instances.add(new ModelInstance(lightArrow, lightPos));

        // Enhanced lighting for better visual appeal
        float ambientLevel = 0.25f; // Increased from 0.1f for better visibility
        ColorAttribute ambient = ColorAttribute.createAmbientLight(ambientLevel, ambientLevel, ambientLevel, 1f);
        environment.set(ambient);

        // Main directional light with warm color (yellowish-white for pleasant appearance)
        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(-lightPos.x, -lightPos.y, -lightPos.z);
        float intensity = 1.5f; // More reasonable intensity
        dirLight1.setColor(intensity, 0.95f * intensity, 0.8f * intensity, 1f); // Warm white color
        environment.add(dirLight1);

        // Add a secondary fill light for better illumination
        DirectionalLight fillLight = new DirectionalLight();
        fillLight.setDirection(0.5f, -0.3f, 0.8f); // Opposite direction from main light
        fillLight.setColor(0.3f, 0.35f, 0.4f, 1f); // Cool blue tint for contrast
        environment.add(fillLight);

        final int MAP = 2048; // resolution of shadow map texture
        final int VIEWPORT = 8; // depth and width of shadow volume in world units
        final float DEPTH = 10f; // length of shadow volume along light direction
        // Use a small near plane offset to help with shadow bias issues on moving geometry
        shadowLight = new WgDirectionalShadowLight(MAP, MAP, VIEWPORT, VIEWPORT, 0.01f, DEPTH);
        shadowLight.setDirection(dirLight1.direction);
        shadowLight.set(dirLight1);
        environment.shadowMap = shadowLight;

        // Add skybox with PBR environment mapping for better lighting
        // Start with environment_01 (sun view)
        currentSkyboxIndex = 0;
        loadSkybox(currentSkyboxIndex);

        // Note: Shadow bias can be adjusted for different models by accessing the shader:
        // The WgDefaultShader has getShadowBias()/setShadowBias() methods
        // Typical values: 0.01f (minimum artifacts) to 0.1f (less shadow dropout)
        // For this morph animation model: 0.04f works well

        shadowBatch = new WgModelBatch(new WgDepthShaderProvider(config));
        disposables.add(shadowBatch);

        viewport = new ScreenViewport();

        // Initialize default shader reference by rendering a dummy frame
        // This will create shaders that we can then configure
        // The shader will be available after the first render
        shadowBias = 0.04f; // Set initial shadow bias value
    }

    /** Press SPACE to advance to the next animation, +/- to adjust shadow bias, S to switch skybox */
    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.SPACE && !animationNames.isEmpty()) {
            currentAnimationIndex = (currentAnimationIndex + 1) % animationNames.size();
            animationController.setAnimation(animationNames.get(currentAnimationIndex), -1);
            return true;
        }

        // Adjust shadow bias with + and - keys (or = and - on US keyboard)
        if (keycode == Input.Keys.PLUS || keycode == Input.Keys.EQUALS) {
            shadowBias = Math.min(shadowBias + BIAS_STEP, BIAS_MAX);
            System.out.println("Shadow Bias: " + String.format(Locale.US, "%.2f", shadowBias));
            return true;
        }
        if (keycode == Input.Keys.MINUS) {
            shadowBias = Math.max(shadowBias - BIAS_STEP, BIAS_MIN);
            System.out.println("Shadow Bias: " + String.format(Locale.US, "%.2f", shadowBias));
            return true;
        }

        // Switch skybox with 'S' key
        if (keycode == Input.Keys.S) {
            currentSkyboxIndex = (currentSkyboxIndex + 1) % SKYBOX_NAMES.length;
            loadSkybox(currentSkyboxIndex);
            System.out.println("Switched to skybox: " + SKYBOX_NAMES[currentSkyboxIndex]);
            return true;
        }

        return false;
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
            // DO NOT add to disposables - we manage skybox/cubemap disposal manually in loadSkybox()

            // Update environment with new cubemap for PBR reflections
            environment.set(new WgCubemapAttribute(WgCubemapAttribute.EnvironmentMap, cubemap));

            // Create skybox from cubemap
            skybox = new SkyBox(cubemap);
            // DO NOT add to disposables - we manage skybox/cubemap disposal manually in loadSkybox()

            System.out.println("Successfully loaded skybox: " + skyboxName);
        } catch (Exception e) {
            System.err.println("Failed to load skybox '" + skyboxName + "': " + e.getMessage());
            skybox = null;
        }
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        animationController.update(delta);

        cam.update();

        // Render shadow map with fixed focal point at world center
        Vector3 focalPoint = Vector3.Zero;
        shadowLight.begin(focalPoint, Vector3.Zero);
        shadowBatch.begin(shadowLight.getCamera(), Color.BLUE, true, RenderPassType.DEPTH_ONLY);
        shadowBatch.render(instances);
        shadowBatch.end();
        shadowLight.end();

        // Render main scene with improved background color
        WgScreenUtils.clear(0.15f, 0.18f, 0.22f, 1f); // Dark blue-gray background instead of DARK_GRAY
        modelBatch.begin(cam, Color.WHITE, true);
        modelBatch.render(instances, environment);
        modelBatch.end();

        // Render skybox for PBR environment mapping
        if (skybox != null) {
            skybox.renderPass(cam);
        }

        // Apply shadow bias to shader after rendering (it gets created during render)
        // Note: This approach updates shaders in cache, affecting future renders
        // The bias will take effect in the next frame for already-created shaders
        applyCurrentShadowBias();

        // draw current animation name and shadow bias on screen
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        String animName = animationNames.isEmpty() ? "No animations" : animationNames.get(currentAnimationIndex);
        font.draw(batch, "Animation: " + animName + "  (SPACE = next)", 10, Gdx.graphics.getHeight() - 10);
        font.draw(batch, "Shadow Bias: " + String.format(Locale.US, "%.2f", shadowBias) + "  (+/- to adjust)", 10, Gdx.graphics.getHeight() - 28);
        font.draw(batch, "Skybox: " + SKYBOX_NAMES[currentSkyboxIndex] + "  (S = switch)", 10, Gdx.graphics.getHeight() - 46);
        font.draw(batch, "Fps: " + Gdx.graphics.getFramesPerSecond(), 10, Gdx.graphics.getHeight() - 64);
        batch.end();
    }

    private void applyCurrentShadowBias() {
        // Apply the current shadow bias to the environment
        if (environment != null) {
            environment.remove(PBRFloatAttribute.ShadowBias);
            environment.set(PBRFloatAttribute.createShadowBias(shadowBias));
        }
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

        // Dispose all other resources from the array
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

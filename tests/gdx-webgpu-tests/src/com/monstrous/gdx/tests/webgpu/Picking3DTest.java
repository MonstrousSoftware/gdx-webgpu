package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
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
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.IdAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenReader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgIDShaderProvider;
import com.github.xpenatan.webgpu.WGPUTextureFormat;

/**
 * 3D Model Picking Test using MRT (Multiple Render Targets).
 * Tests picking with static geometry, morph models, and animated skeletal models.
 * Renders models with picking IDs encoded as material attributes.
 */
public class Picking3DTest extends GdxTest {

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    WgModelBatch modelBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model[] staticModels;
    WgModelInstance[] staticInstances;
    Model morphModel;
    WgModelInstance morphInstance;
    AnimationController morphAnimationController;
    Model animatedModel;
    WgModelInstance animatedInstance;
    AnimationController animationController;
    Environment environment;
    WgFrameBuffer mrtFbo;
    WgScreenReader screenReader;

    private int selectedModelId = -1;
    private String selectedModelName = "None";
    private int nextPickingId = 1;

    public void create() {
        // Setup MRT FrameBuffer (Color + Picking ID)
        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {
            WGPUTextureFormat.BGRA8Unorm,
            WGPUTextureFormat.RGBA8Unorm
        };
        mrtFbo = new WgFrameBuffer(formats, WIDTH, HEIGHT, true);

        // Build MRT shader with picking ID support
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.numBones = 80; // For animated models
        modelBatch = new WgModelBatch(new WgIDShaderProvider(config, true));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(10, 10, 10);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 300f;

        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);

        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        screenReader = new WgScreenReader();

        // Create static models with picking IDs
        WgModelBuilder modelBuilder = new WgModelBuilder();
        staticModels = new Model[4];
        staticInstances = new WgModelInstance[4];

        // Model 0: Red cube
        staticModels[0] = modelBuilder.createBox(2f, 2f, 2f,
                new Material(
                    ColorAttribute.createDiffuse(Color.RED),
                    new IdAttribute(nextPickingId++)
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        staticInstances[0] = new WgModelInstance(staticModels[0]);
        staticInstances[0].transform.setToTranslation(-5, 2, 0);

        // Model 1: Green sphere
        staticModels[1] = modelBuilder.createSphere(2f, 2f, 2f, 16, 16,
                new Material(
                    ColorAttribute.createDiffuse(Color.GREEN),
                    new IdAttribute(nextPickingId++)
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        staticInstances[1] = new WgModelInstance(staticModels[1]);
        staticInstances[1].transform.setToTranslation(5, 2, 0);

        // Model 2: Blue cylinder
        staticModels[2] = modelBuilder.createCylinder(1.5f, 3f, 1.5f, 16,
                new Material(
                    ColorAttribute.createDiffuse(Color.BLUE),
                    new IdAttribute(nextPickingId++)
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        staticInstances[2] = new WgModelInstance(staticModels[2]);
        staticInstances[2].transform.setToTranslation(0, 0, -5);

        // Model 3: Yellow cone
        staticModels[3] = modelBuilder.createCone(1.5f, 3f, 1.5f, 16,
                new Material(
                    ColorAttribute.createDiffuse(Color.YELLOW),
                    new IdAttribute(nextPickingId++)
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        staticInstances[3] = new WgModelInstance(staticModels[3]);
        staticInstances[3].transform.setToTranslation(0, 5, 0);

        // Load morph model with picking ID
        morphModel = loadMorphModel();
        if (morphModel != null) {
            morphInstance = new WgModelInstance(morphModel);
            morphInstance.transform.setToTranslation(0, 1, 5);

            // Apply picking ID to each material in the instance
            for (Material material : morphInstance.materials) {
                if (!material.has(ColorAttribute.Diffuse)) {
                    material.set(ColorAttribute.createDiffuse(Color.WHITE));
                }
                material.set(new IdAttribute(nextPickingId++));
            }

            // Setup morph animation if model has animations
            if (morphInstance.animations != null && morphInstance.animations.size > 0) {
                morphAnimationController = new AnimationController(morphInstance);
                String animationName = morphInstance.animations.get(0).id;
                morphAnimationController.setAnimation(animationName, -1);
                System.out.println("Morph animation: " + animationName);
            }
        }

        // Load animated model with picking ID
        animatedModel = loadAnimatedModel();
        if (animatedModel != null) {
            animatedInstance = new WgModelInstance(animatedModel);
            animatedInstance.transform.setToTranslation(0, 0, 0);

            // Apply picking ID to each material in the instance
            for (Material material : animatedInstance.materials) {
                if (!material.has(ColorAttribute.Diffuse)) {
                    material.set(ColorAttribute.createDiffuse(Color.WHITE));
                }
                material.set(new IdAttribute(nextPickingId++));
            }

            if (animatedInstance.animations != null && animatedInstance.animations.size > 0) {
                animationController = new AnimationController(animatedInstance);
                String animationName = animatedInstance.animations.get(0).id;
                animationController.setAnimation(animationName, -1);
                System.out.println("Animated model animation: " + animationName);
            }
        }

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
    }

    private Model loadMorphModel() {
        try {
            // MorphStressTest has actual morph targets
            String modelFileName = "data/g3d/gltf/MorphStressTest/MorphStressTest.glb";
            WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
            params.textureParameter.genMipMaps = true;

            System.out.println("Loading morph model: " + modelFileName);
            long startLoad = System.currentTimeMillis();
            FileHandle file = Gdx.files.internal(modelFileName);
            Model model = null;
            if (file.extension().contentEquals("gltf"))
                model = new WgGLTFModelLoader().loadModel(file, params);
            else if (file.extension().contentEquals("glb"))
                model = new WgGLBModelLoader().loadModel(file, params);

            long endLoad = System.currentTimeMillis();
            System.out.println("Morph model loading time (ms): " + (endLoad - startLoad));
            return model;
        } catch (Exception e) {
            System.err.println("Failed to load morph model: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Model loadAnimatedModel() {
        try {
            // SillyDancing - same as used in GLTFSkinningShadow
            String modelFileName = "data/g3d/gltf/SillyDancing/SillyDancing.gltf";
            WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
            params.textureParameter.genMipMaps = true;

            System.out.println("Loading animated model: " + modelFileName);
            long startLoad = System.currentTimeMillis();
            FileHandle file = Gdx.files.internal(modelFileName);
            Model model = null;
            if (file.extension().contentEquals("gltf"))
                model = new WgGLTFModelLoader().loadModel(file, params);
            else if (file.extension().contentEquals("glb"))
                model = new WgGLBModelLoader().loadModel(file, params);

            long endLoad = System.currentTimeMillis();
            System.out.println("Animated model loading time (ms): " + (endLoad - startLoad));
            return model;
        } catch (Exception e) {
            System.err.println("Failed to load animated model: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void applyPickingIdToModel(Model model, int pickingId) {
        if (model == null) return;
        for (Material material : model.materials) {
            material.set(new IdAttribute(pickingId));
        }
    }


    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        cam.update();

        // Update animations
        if (animationController != null) {
            animationController.update(delta);
        }

        if (morphAnimationController != null) {
            morphAnimationController.update(delta);
        }

        // Handle mouse click for picking
        if (Gdx.input.justTouched()) {
            pickModel(Gdx.input.getX(), Gdx.input.getY());
        }

        // Render to MRT FBO
        mrtFbo.begin();
        {
            WgScreenUtils.clear(Color.DARK_GRAY, true);

            modelBatch.begin(cam);

            // Render static models
            for (WgModelInstance instance : staticInstances) {
                modelBatch.render(instance, environment);
            }

            // Render morph model
            if (morphInstance != null) {
                modelBatch.render(morphInstance, environment);
            }

            // Render animated model
            if (animatedInstance != null) {
                modelBatch.render(animatedInstance, environment);
            }

            modelBatch.end();
        }
        mrtFbo.end();

        // Render to screen
        WgScreenUtils.clear(Color.BLACK, true);
        batch.begin();
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();

        batch.draw(mrtFbo.getColorBufferTexture(0), 0, 0, w, h);

        font.draw(batch, "Selected Model: " + selectedModelName, 20, 40);
        font.draw(batch, "Click to pick a model", 20, 60);
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond(), 20, 80);

        batch.end();
    }

    private void pickModel(int mouseX, int mouseY) {
        float scaleX = (float) WIDTH / Gdx.graphics.getWidth();
        float scaleY = (float) HEIGHT / Gdx.graphics.getHeight();
        int fbX = (int) (mouseX * scaleX);
        int fbY = (int) (mouseY * scaleY);
        fbX = Math.max(0, Math.min(fbX, WIDTH - 1));
        fbY = Math.max(0, Math.min(fbY, HEIGHT - 1));

        final int finalFbX = fbX;
        final int finalFbY = fbY;

        screenReader.readPixelAsync(mrtFbo, 1, fbX, fbY, new WgScreenReader.PixelReadCallback() {
            @Override
            public void onPixelRead(int r, int g, int b, int a) {
                int pickingId = IdAttribute.decodeIdFromBytes(r, g, b);

                if (pickingId < 1 || pickingId > nextPickingId - 1) {
                    selectedModelId = -1;
                    selectedModelName = "None (Background)";
                } else {
                    selectedModelId = pickingId;
                    selectedModelName = "Model " + pickingId + " (ID: " + pickingId + ")";
                }

                System.out.println("Picked at (" + finalFbX + "," + finalFbY + ") - RGB=(" + r + "," + g + "," + b + ") -> Picking ID: " + pickingId + " -> Selected: " + selectedModelName);
                Gdx.app.log("PickingTest", "Picked at (" + finalFbX + "," + finalFbY + ") -> Picking ID: " + pickingId);
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        batch.dispose();
        font.dispose();
        for (Model model : staticModels) {
            if (model != null) model.dispose();
        }
        if (morphModel != null) morphModel.dispose();
        if (animatedModel != null) animatedModel.dispose();
        mrtFbo.dispose();
        screenReader.dispose();
    }
}

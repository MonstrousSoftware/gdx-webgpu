package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
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
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Environment environment;
    WgFrameBuffer mrtFbo;
    WgShaderProgram[] edgeDetectionShaders;
    int currentOutlineWidthIndex = 2; // Start at 1.0 (middle)
    float[] outlineWidths = {0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 2.5f};
    boolean[] keyPressed = new boolean[256];

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

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 2, 8);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 100f;

        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);

        batch = new WgSpriteBatch();
        font = new WgBitmapFont();

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

        // Create environment with basic lighting
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.3f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

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

        // Static Model 1: Green sphere (back left)
        Material greenMaterial = new Material(ColorAttribute.createDiffuse(Color.GREEN));
        staticModels[1] = builder.createSphere(1.5f, 1.5f, 1.5f, 32, 32, greenMaterial, vertexUsage);
        staticInstances[1] = new WgModelInstance(staticModels[1]);
        staticInstances[1].transform.setToTranslation(-2, 1, -3);

        // Create ground plane
        Material groundMaterial = new Material(ColorAttribute.createDiffuse(Color.OLIVE));
        groundModel = builder.createBox(16f, 0.1f, 16f, groundMaterial, vertexUsage);
        groundInstance = new WgModelInstance(groundModel);
        groundInstance.transform.setToTranslation(0, -0.5f, 0);

        // Load animated morph model
        morphModel = loadMorphModel();
        if (morphModel != null) {
            morphInstance = new WgModelInstance(morphModel);
            morphInstance.transform.setToTranslation(2, 0, 0);
            if (morphInstance.animations != null && morphInstance.animations.size > 0) {
                morphAnimationController = new AnimationController(morphInstance);
                morphAnimationController.setAnimation(morphInstance.animations.get(0).id, -1);
            }
        }

        // Load animated skinned model
        skinnedModel = loadSkinnedModel();
        if (skinnedModel != null) {
            skinnedInstance = new WgModelInstance(skinnedModel);
            skinnedInstance.transform.setToTranslation(4, 0, -2);
            if (skinnedInstance.animations != null && skinnedInstance.animations.size > 0) {
                skinnedAnimationController = new AnimationController(skinnedInstance);
                skinnedAnimationController.setAnimation(skinnedInstance.animations.get(0).id, -1);
            }
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
        staticInstances[0].transform.translate(-4, 1, 0);
        staticInstances[0].transform.rotate(com.badlogic.gdx.math.Vector3.Y, staticRotations[0]);

        staticInstances[1].transform.idt();
        staticInstances[1].transform.translate(-2, 1, -3);
        staticInstances[1].transform.rotate(com.badlogic.gdx.math.Vector3.Y, staticRotations[1]);

        // Render to MRT FBO
        mrtFbo.begin();
        {
            WgScreenUtils.clear(Color.DARK_GRAY, true);

            modelBatch.begin(cam);
            modelBatch.render(groundInstance, environment);
            for (WgModelInstance inst : staticInstances) {
                modelBatch.render(inst, environment);
            }
            if (morphInstance != null) {
                modelBatch.render(morphInstance, environment);
            }
            if (skinnedInstance != null) {
                modelBatch.render(skinnedInstance, environment);
            }
            modelBatch.end();
        }
        mrtFbo.end();

        // Handle outline width adjustment with + and - keys
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.PLUS) || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.EQUALS)) {
            if (!keyPressed[com.badlogic.gdx.Input.Keys.PLUS]) {
                currentOutlineWidthIndex = Math.min(outlineWidths.length - 1, currentOutlineWidthIndex + 1);
                keyPressed[com.badlogic.gdx.Input.Keys.PLUS] = true;
            }
        } else {
            keyPressed[com.badlogic.gdx.Input.Keys.PLUS] = false;
        }

        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.MINUS)) {
            if (!keyPressed[com.badlogic.gdx.Input.Keys.MINUS]) {
                currentOutlineWidthIndex = Math.max(0, currentOutlineWidthIndex - 1);
                keyPressed[com.badlogic.gdx.Input.Keys.MINUS] = true;
            }
        } else {
            keyPressed[com.badlogic.gdx.Input.Keys.MINUS] = false;
        }

        // Render to screen with two panels
        WgScreenUtils.clear(Color.BLACK, true);

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

        batch.end();
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
        if (groundModel != null) groundModel.dispose();
        if (morphModel != null) morphModel.dispose();
        if (skinnedModel != null) skinnedModel.dispose();
        mrtFbo.dispose();
        for (WgShaderProgram shader : edgeDetectionShaders) {
            if (shader != null) shader.dispose();
        }
    }
}

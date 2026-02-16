package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgMaskingShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

/**
 * Test WebGPU ModelBatch with masking. A sphere acts as a masking model that hides parts of the cube behind it. The
 * sphere is positioned in front of the cube, and only the parts of the cube not covered by the sphere are visible.
 */
public class ModelBatchMaskingTest extends GdxTest {

    WgModelBatch sceneBatch; // For scene objects (red cube)
    WgModelBatch maskBatch; // For masking objects (blue cube)
    WgModelBatch depthOnlyBatch; // For depth-only masking (sphere)
    PerspectiveCamera cam;
    PerspectiveCamController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Environment environment;

    ModelInstance blueCubeInstance;
    ModelInstance redCubeInstance;
    ModelInstance sphereInstance;

    public void create() {
        sceneBatch = new WgModelBatch(); // Scene rendering (red cube)
        maskBatch = new WgModelBatch(); // Gizmo rendering (blue cube)
        depthOnlyBatch = new WgModelBatch(new WgMaskingShaderProvider()); // Masking depth (sphere with constant depth)
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, 4);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 30f;
        cam.update();

        controller = new PerspectiveCamController(cam);
        Gdx.input.setInputProcessor(controller);

        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        // Create environment with lighting
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        DirectionalLight light = new DirectionalLight().set(1.0f, 1.0f, 1.0f, -1f, -0.8f, -0.2f);
        environment.add(light);

        // Create the blue cube model (front) with normals for lighting
        WgModelBuilder modelBuilder = new WgModelBuilder();
        long vertexUsage = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material blueMaterial = new Material(ColorAttribute.createDiffuse(Color.BLUE));
        Model blueCubeModel = modelBuilder.createBox(2f, 2f, 2f, blueMaterial, vertexUsage);
        blueCubeInstance = new ModelInstance(blueCubeModel);
        blueCubeInstance.transform.setToTranslation(0, 0, 0);

        // Create the red cube model (behind, positioned further back on Z axis)
        modelBuilder = new WgModelBuilder();
        Material redMaterial = new Material(ColorAttribute.createDiffuse(Color.RED));
        Model redCubeModel = modelBuilder.createBox(2f, 2f, 2f, redMaterial, vertexUsage);
        redCubeInstance = new ModelInstance(redCubeModel);
        redCubeInstance.transform.setToTranslation(0, 0, -3f);

        // Create the sphere model (masking model) - SMALLER than cube to mask only center area
        modelBuilder = new WgModelBuilder();
        Material sphereMaterial = new Material(ColorAttribute.createDiffuse(Color.GREEN));
        Model sphereModel = modelBuilder.createSphere(1.8f, 1.8f, 1.8f, 32, 32, sphereMaterial, vertexUsage);
        sphereInstance = new ModelInstance(sphereModel);
        // Position sphere slightly FORWARD (z=0.05) so its depth blocks the blue cube
        sphereInstance.transform.setToTranslation(0, 0, 0.05f);
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Reset transforms to base positions before rotating
        blueCubeInstance.transform.setToTranslation(0, 0, 0);
        redCubeInstance.transform.setToTranslation(0, 0, -3f);
        sphereInstance.transform.setToTranslation(0, 0, 0.05f); // Sphere slightly forward

        // Rotate the cubes around their own centers
        blueCubeInstance.transform.rotate(Vector3.Y, delta * 15f);
        blueCubeInstance.transform.rotate(Vector3.X, delta * 10f);

        redCubeInstance.transform.rotate(Vector3.Y, delta * 15f);
        redCubeInstance.transform.rotate(Vector3.X, delta * 10f);

        // Rotate the sphere
        sphereInstance.transform.rotate(Vector3.Y, delta * 20f);

        cam.update();

        // SCENE RENDERING: Red cube in its own batch - clear both color and depth initially
        sceneBatch.begin(cam, Color.TEAL, true);
        sceneBatch.render(redCubeInstance, environment);
        sceneBatch.end();

        // CUBE MASKING: Apply sphere depth mask - fragment shader outputs 0.0 depth
        // This creates a depth barrier at closest possible value (0.0), blocking blue cube
        depthOnlyBatch.begin(cam, null, false, RenderPassType.DEPTH_ONLY);
        depthOnlyBatch.render(sphereInstance);
        depthOnlyBatch.end();

        // CUBE RENDERING: Render blue cube ONCE - sphere's depth will mask it
        // Preserve scene color and depth from previous passes
        maskBatch.begin(cam, null, false);
        maskBatch.render(blueCubeInstance, environment);
        maskBatch.end();

        batch.begin();
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, 20);
        font.draw(batch, "Sphere masks blue cube, showing red behind", 0, 40);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        sceneBatch.dispose();
        maskBatch.dispose();
        depthOnlyBatch.dispose();
    }
}

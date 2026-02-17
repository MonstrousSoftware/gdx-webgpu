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
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgOutlineShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.OutlineColorAttribute;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

/**
 * Test WebGPU ModelBatch with outline rendering. Objects are rendered with a colored outline, similar to selection
 * highlighting in game engines.
 */
public class ModelBatchOutlineTest extends GdxTest {

    WgModelBatch modelBatch; // For normal model rendering
    WgModelBatch outlineBatch; // For outline rendering (single batch for all colors)
    WgOutlineShaderProvider outlineProvider;
    PerspectiveCamera cam;
    PerspectiveCamController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Environment environment;

    ModelInstance cubeInstance;
    ModelInstance sphereInstance;

    // Temporary instances for outline rendering with scaled transforms
    ModelInstance cubeOutlineInstance;
    ModelInstance sphereOutlineInstance;

    float outlineThickness = 0.05f; // How much to scale up for outline

    public void create() {
        modelBatch = new WgModelBatch(); // Normal rendering

        // Create ONE outline batch - the shader provider will handle color changes
        outlineProvider = new WgOutlineShaderProvider();
        outlineBatch = new WgModelBatch(outlineProvider);


        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 0, 5);
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
        DirectionalLight light = new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f);
        environment.add(light);

        // Create a cube model with normals for lighting
        WgModelBuilder modelBuilder = new WgModelBuilder();
        long vertexUsage = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        Material cubeMaterial = new Material(ColorAttribute.createDiffuse(Color.BLUE));
        Model cubeModel = modelBuilder.createBox(2f, 2f, 2f, cubeMaterial, vertexUsage);
        cubeInstance = new ModelInstance(cubeModel);
        cubeInstance.transform.setToTranslation(-2, 0, 0);

        // Create outline instance for cube (shares the same model)
        cubeOutlineInstance = new ModelInstance(cubeModel);
        // Add outline color as material attribute
        cubeOutlineInstance.materials.get(0).set(OutlineColorAttribute.createOutlineColor(Color.GREEN));

        // Create a sphere model
        Material sphereMaterial = new Material(ColorAttribute.createDiffuse(Color.RED));
        Model sphereModel = modelBuilder.createSphere(1.5f, 1.5f, 1.5f, 32, 32, sphereMaterial, vertexUsage);
        sphereInstance = new ModelInstance(sphereModel);
        sphereInstance.transform.setToTranslation(2, 0, 0);

        // Create outline instance for sphere (shares the same model)
        sphereOutlineInstance = new ModelInstance(sphereModel);
        // Add outline color as material attribute
        sphereOutlineInstance.materials.get(0).set(OutlineColorAttribute.createOutlineColor(Color.CYAN));
    }

    public void render() {
        float time = System.currentTimeMillis() / 1000f;

        // Update cube transform
        cubeInstance.transform.idt();
        cubeInstance.transform.translate(-2, 0, 0);
        cubeInstance.transform.rotate(Vector3.Y, time * 30f);
        cubeInstance.transform.rotate(Vector3.X, time * 20f);

        // Update sphere transform
        sphereInstance.transform.idt();
        sphereInstance.transform.translate(2, 0, 0);
        sphereInstance.transform.rotate(Vector3.Y, time * 25f);
        sphereInstance.transform.rotate(Vector3.X, time * 15f);

        cam.update();

        // Prepare outline instances with scaled transforms
        cubeOutlineInstance.transform.set(cubeInstance.transform);
        cubeOutlineInstance.transform.scale(1f + outlineThickness, 1f + outlineThickness, 1f + outlineThickness);

        sphereOutlineInstance.transform.set(sphereInstance.transform);
        sphereOutlineInstance.transform.scale(1f + outlineThickness, 1f + outlineThickness, 1f + outlineThickness);

        // STEP 1: Render cube outline with GREEN (separate batch for different color)
        outlineBatch.begin(cam, Color.DARK_GRAY, true); // Clear screen
        outlineBatch.render(cubeOutlineInstance);
        outlineBatch.end();

        // STEP 2: Render sphere outline with CYAN (separate batch for different color)
        outlineBatch.begin(cam, null, false); // Don't clear - keep cube outline
        outlineBatch.render(sphereOutlineInstance);
        outlineBatch.end();

        // STEP 2: Render normal models on top (don't clear, so outlines show through)
        modelBatch.begin(cam, null, false);
        modelBatch.render(cubeInstance, environment);
        modelBatch.render(sphereInstance, environment);
        modelBatch.end();

        batch.begin();
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, 20);
        font.draw(batch, "Models with outline rendering (Cube=Green, Sphere=Cyan)", 0, 40);
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
        modelBatch.dispose();
        outlineBatch.dispose();
    }
}




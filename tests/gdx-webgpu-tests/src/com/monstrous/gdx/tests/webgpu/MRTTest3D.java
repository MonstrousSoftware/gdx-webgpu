package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShaderProvider;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

/**
 * Multiple Render Targets (MRT) test with 3D model. Renders a GLTF model with animation into a FrameBuffer with Color
 * and Normal attachments.
 */
public class MRTTest3D extends GdxTest {

    WgModelBatch modelBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model model;
    WgModelInstance instance;
    Environment environment;
    WgFrameBuffer mrtFbo;
    AnimationController animationController;

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;

    public void create() {
        // Setup MRT FrameBuffer (Color + Normal)
        WGPUTextureFormat[] formats = new WGPUTextureFormat[] {WGPUTextureFormat.BGRA8Unorm, // Color (use BGRA for
                                                                                             // compatibility with many
                                                                                             // swapchains/defaults)
                WGPUTextureFormat.RGBA16Float // Normal (high precision)
        };
        mrtFbo = new WgFrameBuffer(formats, WIDTH, HEIGHT, true);

        // Setup ModelBatch with custom MRT Shader via high-level Config
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.numBones = 100; // Increase bone limit for SillyDancing model (65 bones)
        config.shaderSource = Gdx.files.internal("shaders/modelbatch_mrt.wgsl").readString();

        modelBatch = new WgModelBatch(new WgDefaultShaderProvider(config));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(2, 2, 3f);
        cam.lookAt(0, 1, 0);
        cam.near = 0.1f;
        cam.far = 100f;

        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);

        batch = new WgSpriteBatch();
        font = new WgBitmapFont();

        // Load Model (SillyDancing)
        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;
        model = new WgGLTFModelLoader().loadModel(Gdx.files.internal("data/g3d/gltf/SillyDancing/SillyDancing.gltf"),
                params);
        instance = new WgModelInstance(model);

        animationController = new AnimationController(instance);
        if (instance.animations.size > 0) {
            animationController.setAnimation(instance.animations.get(0).id, -1);
        }

        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        animationController.update(delta);
        cam.update();

        // 1. Render to MRT FBO
        mrtFbo.begin();
        {
            // Clear attachments. Note: built-in clear might only clear color/depth.
            // We can rely on render pass load op = Clear if set up correctly,
            // or simply use WgScreenUtils.clear which clears the currently bound target(s).
            WgScreenUtils.clear(Color.DARK_GRAY, true);

            modelBatch.begin(cam);
            modelBatch.render(instance, environment);
            modelBatch.end();
        }
        mrtFbo.end();

        // 2. Render results to screen
        WgScreenUtils.clear(Color.BLACK, true);

        batch.begin();
        float w = Gdx.graphics.getWidth() / 2f;
        float h = Gdx.graphics.getHeight();

        // Draw Color Attachment
        batch.draw(mrtFbo.getColorBufferTexture(0), 0, 0, w, h);
        font.draw(batch, "Color Output", 20, 40);

        // Draw Normal Attachment
        batch.draw(mrtFbo.getColorBufferTexture(1), w, 0, w, h);
        font.draw(batch, "Normal Output", w + 20, 40);

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
        model.dispose();
        mrtFbo.dispose();
    }

}

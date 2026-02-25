package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.InputMultiplexer;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelInstance;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import java.util.ArrayList;
import java.util.List;

/** Test GLTF Morph animation */
public class GLTFMorphAnimation extends GdxTest {

    WgModelBatch modelBatch;
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

    // animation cycling
    private List<String> animationNames = new ArrayList<>();
    private int currentAnimationIndex = 0;

    // application
    public void create() {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        modelBatch = new WgModelBatch();
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(2, 2, 3f);
        cam.lookAt(0, 1, 0);
        cam.near = 0.001f;
        cam.far = 100f;

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

        instance = new WgModelInstance(model);

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

        controller = new CameraInputController(cam);

        // use a multiplexer so both the camera controller and keyDown() work
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(this);
        multiplexer.addProcessor(controller);
        Gdx.input.setInputProcessor(multiplexer);

        viewport = new ScreenViewport();
        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        environment = new Environment();
        float amb = 0.5f;
        ColorAttribute ambient = ColorAttribute.createAmbientLight(amb, amb, amb, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(1f, -.2f, .2f);
        dirLight1.setColor(Color.WHITE);
        environment.add(dirLight1);
    }

    /** Press SPACE to advance to the next animation */
    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.SPACE && !animationNames.isEmpty()) {
            currentAnimationIndex = (currentAnimationIndex + 1) % animationNames.size();
            animationController.setAnimation(animationNames.get(currentAnimationIndex), -1);
            return true;
        }
        return false;
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        animationController.update(delta);

        WgScreenUtils.clear(Color.DARK_GRAY, true);

        cam.update();
        modelBatch.begin(cam);
        modelBatch.render(instance, environment);
        modelBatch.end();

        // draw current animation name on screen
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        String animName = animationNames.isEmpty() ? "No animations" : animationNames.get(currentAnimationIndex);
        font.draw(batch, "Animation: " + animName + "  (SPACE = next)", 10, Gdx.graphics.getHeight() - 10);
        font.draw(batch, "Fps: " + Gdx.graphics.getFramesPerSecond(), 10, Gdx.graphics.getHeight() - 28);
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
        batch.dispose();
        font.dispose();
        modelBatch.dispose();
        model.dispose();
    }

}

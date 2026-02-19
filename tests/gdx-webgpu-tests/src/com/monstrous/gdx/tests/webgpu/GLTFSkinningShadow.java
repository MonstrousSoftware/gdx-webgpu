/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.environment.WgDirectionalShadowLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

/**
 * Skeletal animation and shadows.
 */
public class GLTFSkinningShadow extends GdxTest {

    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;
    PerspectiveCamera cam;
    CameraInputController controller;
    WgSpriteBatch batch;
    WgBitmapFont font;
    Model box;
    Model ground;
    Model lightArrow;
    ModelInstance lightInstance;
    Array<ModelInstance> instances;
    ModelInstance animatedInstance;
    Array<ModelInstance> jointBoxes;
    Array<Node> jointNodes;
    Array<Disposable> disposables;
    Environment environment;
    Environment emptyEnvironment;
    WgDirectionalShadowLight shadowLight;
    Vector3 lightPos;
    WebGPUContext webgpu;
    AnimationController animationController;

    public void create() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        disposables = new Array<>();

        WgModelBatch.Config config = new WgModelBatch.Config();
        config.numBones = 80; // set number of bones as needed for the model
        modelBatch = new WgModelBatch(config);
        disposables.add(modelBatch);

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(0, 1, 5);
        cam.near = 0.1f;
        cam.lookAt(0, 0, 0);

        controller = new CameraInputController(cam);
        Gdx.input.setInputProcessor(controller);

        batch = new WgSpriteBatch();
        disposables.add(batch);
        font = new WgBitmapFont();
        disposables.add(font);

        jointBoxes = new Array<>();
        jointNodes = new Array<>();

        //
        // Create some renderables
        //
        instances = new Array<>();

        ModelBuilder modelBuilder = new WgModelBuilder();
        Texture texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        disposables.add(texture);
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat = new Material(TextureAttribute.createDiffuse(texture));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates
                | VertexAttributes.Usage.Normal;
        box = modelBuilder.createBox(1, 1, 1, mat, attribs);
        disposables.add(box);
        instances.add(new ModelInstance(box, 2, 1f, 0));
        instances.add(new ModelInstance(box, 0, 1f, -2));
        instances.add(new ModelInstance(box, -2, 0.5f, 0));

        Material mat2 = new Material(ColorAttribute.createDiffuse(Color.OLIVE));
        long attribs2 = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.ColorPacked;
        ground = modelBuilder.createBox(8, 0.1f, 9, mat2, attribs2);
        disposables.add(ground);
        instances.add(new ModelInstance(ground, 0, 0, 0));

        Model animated = loadAnimatedModel();
        disposables.add(animated);
        animatedInstance = new ModelInstance(animated, 0, 0.0f, 0);
        instances.add(animatedInstance);
        makeBones(animatedInstance);

        System.out.println("Animation count: " + animatedInstance.animations.size);

        if (animatedInstance.animations != null && animatedInstance.animations.size > 0) {
            animationController = new AnimationController(animatedInstance);
            String animationName = animatedInstance.animations.get(0).id; // play first animation
            animationController.setAnimation(animationName, -1);
        }

        lightPos = new Vector3(-.75f, 1f, -0.25f);
        Vector3 vec = new Vector3(lightPos).nor();

        lightArrow = modelBuilder.createArrow(vec, Vector3.Zero, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);

        lightInstance = new ModelInstance(lightArrow, lightPos);
        instances.add(lightInstance);

        environment = new Environment();
        emptyEnvironment = new Environment();

        float level = 0.1f;
        ColorAttribute ambient = ColorAttribute.createAmbientLight(level, level, level, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(-lightPos.x, -lightPos.y, -lightPos.z);
        float intensity = 20f;
        dirLight1.setColor(intensity, intensity, intensity, 1f);// color * intensity
        environment.add(dirLight1);

        final int MAP = 2048; // resolution of shadow map texture (may affect frame rate)
        final int VIEWPORT = 8; // depth and width of shadow volume in world units
        final float DEPTH = 10f; // length of shadow volume in world units along light direction
        shadowLight = new WgDirectionalShadowLight(MAP, MAP, VIEWPORT, VIEWPORT, 0f, DEPTH);
        shadowLight.setDirection(dirLight1.direction);
        shadowLight.set(dirLight1);
        environment.shadowMap = shadowLight;

        shadowBatch = new WgModelBatch(new WgDepthShaderProvider(config));
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        if (animationController != null) {
            animationController.update(delta);
            updateBones(animatedInstance);
        }
        cam.update();

        Vector3 focalPoint = Vector3.Zero; // central position for shadow volume

        shadowLight.begin(focalPoint, Vector3.Zero);
        shadowBatch.begin(shadowLight.getCamera(), Color.BLUE, true, RenderPassType.DEPTH_ONLY);
        shadowBatch.render(instances);
        shadowBatch.end();
        shadowLight.end();

        modelBatch.begin(cam, Color.TEAL, true);
        modelBatch.render(instances, environment);
        modelBatch.end();

        modelBatch.begin(cam, null, true);
        modelBatch.render(jointBoxes);
        modelBatch.end();

        batch.begin();
        float y = 200;
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, y -= 20);
        for (int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
            font.draw(batch, "GPU time (pass " + pass + " " + webgpu.getGPUTimer().getPassName(pass) + ") : "
                    + (int) webgpu.getAverageGPUtime(pass) + " microseconds", 0, y -= 20);

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
        for (Disposable disposable : disposables)
            disposable.dispose();
    }

    private Model loadAnimatedModel() {
        Model model = null;
        String modelFileName = "data/g3d/gltf/SillyDancing/SillyDancing.gltf";

        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        System.out.println("Start loading");
        long startLoad = System.currentTimeMillis();
        FileHandle file = Gdx.files.internal(modelFileName);
        if (file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if (file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: " + modelFileName);
        long endLoad = System.currentTimeMillis();
        System.out.println("Model loading time (ms): " + (endLoad - startLoad));
        return model;
    }

    /** create boxes to visualize the skeleton joints */
    private void makeBones(ModelInstance instance) {
        ModelBuilder modelBuilder = new WgModelBuilder();

        for (Node node : instance.nodes) {
            System.out.println("instance.node: " + node.id);
            makeBones(node, modelBuilder);
        }
    }

    private void makeBones(Node node, ModelBuilder modelBuilder) {
        // System.out.println("checking node "+node.id);
        for (NodePart part : node.parts) {
            if (part.invBoneBindTransforms != null) {
                for (Node joint : part.invBoneBindTransforms.keys()) {
                    // System.out.println("joint: "+joint.id);
                    float size = 2.5f;
                    Model model = modelBuilder.createBox(size, size, size,
                            new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    ModelInstance boneInstance = new ModelInstance(model);
                    boneInstance.transform.set(joint.globalTransform);
                    disposables.add(model);
                    jointBoxes.add(boneInstance);
                    jointNodes.add(joint);
                }
            }
        }
        for (Node child : node.getChildren())
            makeBones(child, modelBuilder);
    }

    private void updateBones(ModelInstance instance) {
        for (int i = 0; i < jointBoxes.size; i++) {
            if (jointNodes.get(i).isAnimated)
                jointBoxes.get(i).transform.set(instance.transform).mul(jointNodes.get(i).globalTransform);
        }
    }

}

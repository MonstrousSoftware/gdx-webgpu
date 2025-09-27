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
import com.badlogic.gdx.graphics.g3d.model.Animation;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
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
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;


/** Test GLTF skinning i.e. skeletal animation with multiple rigged instances, not per se synchronized */

public class GLTFSkinningMultiple extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
    CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	Model model;
    Model model2;
    Model floorModel;
    ModelInstance floor;
	Array<ModelInstance> jointBoxes;
    Array<Node> jointNodes;
    ModelInstance instance;
    Array<ModelInstance> instances;
    Array<Disposable> disposables;
    String modelFileName;
    Environment environment;
    WgGraphics gfx;
    WebGPUContext webgpu;
    private Viewport viewport;
    private AnimationController animationController;



	// application
	public void create () {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        WgDefaultShader.Config config = new WgDefaultShader.Config();
        config.numBones = 80;   // set number of bones as needed for the model
		modelBatch = new WgModelBatch(config);

		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(2, 2, 3f);
		cam.lookAt(0,1,0);
		cam.near = 0.001f;
		cam.far = 1000f;

        jointBoxes = new Array<>();
        jointNodes = new Array<>();
        disposables = new Array<>();

        model = loadModel("data/g3d/gltf/SimpleSkin/SimpleSkin.gltf");
        model2 = loadModel("data/g3d/gltf/SimpleSkin/SimpleSkin.gltf");
        //modelFileName = "data/g3d/gltf/RiggedFigure/RiggedFigure.gltf";
        //modelFileName = "data/g3d/gltf/Fox/Fox.gltf";
        //modelFileName = "data/g3d/gltf/RiggedSimple/RiggedSimple.gltf";
        //modelFileName = "data/g3d/gltf/SillyDancing/SillyDancing.gltf";
        //modelFileName = "data/g3d/gltf/Warrior/Warrior.gltf";


        instances = new Array<>();

        instance = new ModelInstance(model, -2, 0, 0);
        instances.add(instance);

        instance = new ModelInstance(model2, 2, 0, 0);
        instances.add(instance);

        makeBones(instance);

        System.out.println("Animation count: "+instance.animations.size);

        if(instance.animations != null && instance.animations.size > 0) {
            animationController = new AnimationController(instance);
            String animationName = instance.animations.get(0).id;   // play first animation
            Animation anim = instance.animations.get(0);
            System.out.println("Animation[0]: " + animationName);
            animationController.setAnimation(animationName, -1);
        }

        ModelBuilder modelBuilder = new WgModelBuilder();
        Texture texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
        disposables.add( texture );
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        Material mat = new Material(TextureAttribute.createDiffuse(texture));
        long attribs = VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates | VertexAttributes.Usage.Normal ;
        Model box = modelBuilder.createBox(1, 1, 1, mat, attribs);
        disposables.add( box );

        //instances.add(new ModelInstance(box, 0, .5f, 0));

        floorModel = makeFloorModel();
        instances.add(new ModelInstance(floorModel, 0, -.5f, 0));

		controller = new CameraInputController(cam);

		Gdx.input.setInputProcessor(controller);
        viewport = new ScreenViewport();
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        environment = new Environment();
        float amb = 0.2f;
        ColorAttribute ambient =  ColorAttribute.createAmbientLight(amb, amb, amb, 1f);
        environment.set(ambient);

        Vector3 lightPos = new Vector3(-.75f, 2f, -0.25f);
        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(-lightPos.x, -lightPos.y, -lightPos.z);
        dirLight1.setColor(24f, 2f, 2f, 1f);// color * intensity

        environment.add(dirLight1);
	}

    private Model loadModel(String modelFileName ){
        Model model = null;
        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        FileHandle file = Gdx.files.internal(modelFileName);
        if(file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if(file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: "+modelFileName);

        return model;
    }

    private Model makeFloorModel(){
        ModelBuilder modelBuilder = new WgModelBuilder();
        float size = 20f;
        Model model = modelBuilder.createBox(size, 1, size, new Material(ColorAttribute.createDiffuse(Color.OLIVE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        return model;
    }

    /** create boxes to visualize the skeleton joints */
    private void makeBones(ModelInstance instance){
        ModelBuilder modelBuilder = new WgModelBuilder();

        for(Node node : instance.nodes) {
            System.out.println("instance.node: "+node.id);
            makeBones(node, instance, modelBuilder);
        }
    }


    // todo : this assumes instance is at origin
    private void makeBones(Node node, ModelInstance instance, ModelBuilder modelBuilder){
        //System.out.println("checking node "+node.id);
        instance.calculateTransforms();
        for(NodePart part : node.parts){
            if(part.invBoneBindTransforms != null){
                for(Node joint : part.invBoneBindTransforms.keys()){
                    //System.out.println("joint: "+joint.id);
                    float size = 0.05f;
                    model = modelBuilder.createBox(size, size, size, new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
                    ModelInstance boneInstance = new ModelInstance(model);
                    boneInstance.transform.set(joint.globalTransform);
                    disposables.add(model);
                    jointBoxes.add(boneInstance);
                    jointNodes.add(joint);
                }
            }
        }
        for(Node child : node.getChildren())
            makeBones(child, instance, modelBuilder);
    }

    private void updateBones(ModelInstance instance){
        for(int i = 0; i < jointBoxes.size; i++){
            if(jointNodes.get(i).isAnimated)
                jointBoxes.get(i).transform.set(instance.transform).mul(jointNodes.get(i).globalTransform);
        }
    }

	public void render () {
		float delta =Gdx.graphics.getDeltaTime();
        if(animationController != null) {
            animationController.update(delta);
            updateBones(instance);
        }
        //instance.calculateTransforms();

		cam.update();

        modelBatch.begin(cam,Color.TEAL, true);
		modelBatch.render(instances, environment);
		modelBatch.end();

        modelBatch.begin(cam, null, true);
        modelBatch.render(jointBoxes);
        modelBatch.end();

//        viewport.apply();
//        batch.setProjectionMatrix(viewport.getCamera().combined);
//		batch.begin();
//        int y = 200;
//		font.draw(batch, "Model loaded: "+modelFileName , 0, y-=20);
//        font.draw(batch, "FPS: "+Gdx.graphics.getFramesPerSecond() ,0, y -= 20);
//        font.draw(batch, "delta time: "+(int)(1000000/(Gdx.graphics.getFramesPerSecond()+0.001f))+" microseconds",0, y -= 20);
//
//        for(int pass = 0; pass < webgpu.getGPUTimer().getNumPasses(); pass++)
//            font.draw(batch, "GPU time (pass "+pass+" "+webgpu.getGPUTimer().getPassName(pass)+") : "+(int)webgpu.getAverageGPUtime(pass)+ " microseconds" ,0, y -= 20);
//        batch.end();

	}

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();
        viewport.update(width, height, true);

	}

	@Override
	public void dispose () {
		batch.dispose();
		font.dispose();
		modelBatch.dispose();
		model.dispose();
        floorModel.dispose();

	}


}

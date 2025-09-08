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
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.graphics.g3d.model.NodePart;
import com.badlogic.gdx.graphics.g3d.utils.AnimationController;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
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
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLBModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;


/** Test GLTF skinning */

public class GLTFSkinning extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
    CameraInputController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	Model model;
	Array<ModelInstance> jointBoxes;
    Array<Node> jointNodes;
    ModelInstance instance;
    Array<Disposable> disposables;
    String modelFileName;
    Environment environment;
    int numMeshes;
    int numVerts;
    int numIndices;
    WgGraphics gfx;
    WebGPUContext webgpu;
    private Viewport viewport;
    private AnimationController animationController;



	// application
	public void create () {
        gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(2, 2, 3f);
		cam.lookAt(0,1,0);
		cam.near = 0.001f;
		cam.far = 1000f;

        jointBoxes = new Array<>();
        jointNodes = new Array<>();
        disposables = new Array<>();

        //modelFileName = "data/g3d/gltf/SimpleSkin/SimpleSkin.gltf";
        //modelFileName = "data/g3d/gltf/RiggedFigure/RiggedFigure.gltf";
        //modelFileName = "data/g3d/gltf/Fox/Fox.gltf";
        //modelFileName = "data/g3d/gltf/RiggedSimple/RiggedSimple.gltf";
        //modelFileName = "data/g3d/gltf/SillyDancing/SillyDancing.gltf";
        modelFileName = "data/g3d/gltf/BendyBox/BendyBox.gltf";

        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        System.out.println("Start loading");
        long startLoad = System.currentTimeMillis();
        FileHandle file = Gdx.files.internal(modelFileName);
        if(file.extension().contentEquals("gltf"))
            model = new WgGLTFModelLoader().loadModel(file, params);
        else if(file.extension().contentEquals("glb"))
            model = new WgGLBModelLoader().loadModel(file, params);
        else
            System.out.println("File extension not supported: "+modelFileName);
        long endLoad = System.currentTimeMillis();
        System.out.println("Model loading time (ms): "+(endLoad - startLoad));

        instance = new ModelInstance(model);

        makeBones(instance);

        System.out.println("Animation count: "+instance.animations.size);

        if(instance.animations != null && instance.animations.size > 0) {
            animationController = new AnimationController(instance);
            String animationName = instance.animations.get(0).id;   // play first animation
            System.out.println("Animation[0]: " + animationName);
            animationController.setAnimation(animationName, -1);
        }

        numMeshes = instance.model.meshes.size;
        for(int i = 0; i < numMeshes; i++){
            numVerts += instance.model.meshes.get(i).getNumVertices();
            numIndices += instance.model.meshes.get(i).getNumIndices();
        }

		controller = new CameraInputController(cam);

		Gdx.input.setInputProcessor(controller);
        viewport = new ScreenViewport();
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        environment = new Environment();
        float amb = 0.5f;
        ColorAttribute ambient =  ColorAttribute.createAmbientLight(amb, amb, amb, 1f);
        environment.set(ambient);

        DirectionalLight dirLight1 = new DirectionalLight();
        dirLight1.setDirection(1f, -.2f, .2f);
        dirLight1.setColor(Color.WHITE);
        environment.add(dirLight1);

	}

    /** create boxes to visualize the skeleton joints */
    private void makeBones(ModelInstance instance){
        ModelBuilder modelBuilder = new WgModelBuilder();

        for(Node node : instance.nodes) {
            System.out.println("instance.node: "+node.id);
            makeBones(node, modelBuilder);
        }
    }

    private void makeBones(Node node, ModelBuilder modelBuilder){
        //System.out.println("checking node "+node.id);
        for(NodePart part : node.parts){
            if(part.invBoneBindTransforms != null){
                for(Node joint : part.invBoneBindTransforms.keys()){
                    //System.out.println("joint: "+joint.id);
                    float size = .1f;
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
            makeBones(child, modelBuilder);
    }

    private void updateBones(){
        for(int i = 0; i < jointBoxes.size; i++){
            // if(jointNodes.get(i).isAnimated
            jointBoxes.get(i).transform.set(jointNodes.get(i).globalTransform);

        }
        //System.out.println("transform: "+jointNodes.get(1).localTransform);
    }

	public void render () {
		float delta =Gdx.graphics.getDeltaTime();
        if(animationController != null) {
            animationController.update(delta);
            updateBones();
        }

		WgScreenUtils.clear(Color.DARK_GRAY, true);

		cam.update();
		modelBatch.begin(cam);
		modelBatch.render(instance, environment);
        //odelBatch.render(jointBoxes);
		modelBatch.end();

        modelBatch.begin(cam, null, true);
        //modelBatch.render(instance, environment);
        modelBatch.render(jointBoxes);
        modelBatch.end();

//        viewport.apply();
//        batch.setProjectionMatrix(viewport.getCamera().combined);
//		batch.begin();
//        int y = 200;
//		font.draw(batch, "Model loaded: "+modelFileName , 0, y-=20);
//        font.draw(batch, "Meshes: "+numMeshes , 0, y-=20);
//        font.draw(batch, "Vertices: "+numVerts , 0, y-=20);
//        font.draw(batch, "Indices: "+numIndices , 0, y-=20);
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
	}


}

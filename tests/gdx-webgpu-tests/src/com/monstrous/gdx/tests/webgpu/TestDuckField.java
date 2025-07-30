package com.monstrous.gdx.tests.webgpu;



import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;


import java.util.ArrayList;

// Test instancing
// For comparison to DuckField demo but is lacking shadows, PBR shading,etc.

public class TestDuckField extends ApplicationAdapter {

    private WgModelBatch modelBatch;
    private Camera camera;
    private CameraInputController camController;
    private Model model;
    private ArrayList<ModelInstance> modelInstances;
    private Environment environment;
    private ArrayList<Matrix4> transforms;
    private WgBitmapFont font;
    private WgSpriteBatch batch;
    private String info;
//    private Stage stage;
    private int fps;
    private long startTime;
    private int frames;
    private final Vector3 up = new Vector3(0,1,0);

    public void create() {
        startTime = System.nanoTime();
        frames = 0;

        model = new WgGLTFModelLoader().loadModel(Gdx.files.internal("data/g3d/gltf/ducky.gltf"));

        modelInstances = new ArrayList<>();

//        transforms = makeTransforms();
//        for(Matrix4 transform: transforms) {
//            ModelInstance modelInstance = new ModelInstance(model, transform);
//            modelInstances.add(modelInstance);
//        }


        camera = new PerspectiveCamera(70, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 1, -3);
        camera.direction.set(0,0f, 1f);
        camera.far = 1000f;
        camera.update();

//        environment = new Environment();
//        DirectionalLight light = new DirectionalLight( Color.WHITE, new Vector3(.4f,-1,.2f));
//        light.setIntensity(5f);
//        environment.add( light );
//        environment.ambientLightLevel = 0.4f;

        modelBatch = new WgModelBatch();

        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        info = "Number of instances: "+transforms.size();

//        stage = new Stage();
//        Table sliderTable = new Table();
//        WrappedFloat side = new WrappedFloat(15);
//        Slider slider = new Slider(side, 0, 360, 5f);
//
//        Label.Style style = new Label.Style();
//        style.font = font;
//        style.fontColor = Color.WHITE;
//        FloatLabel value = new FloatLabel( side, "count:", style );
//        sliderTable.add(slider);
//        sliderTable.row();
//        sliderTable.add(value);
//        stage.add(sliderTable).setAlign(Align.topRight);


        camController = new CameraInputController(camera);
//        InputMultiplexer im= new InputMultiplexer();
//        im.addProcessor(stage);
//        im.addProcessor(camController);
        Gdx.input.setInputProcessor(camController);
    }

//    private ArrayList<Matrix4> makeTransforms(){
//        ArrayList<Matrix4> transforms = new ArrayList<>();
//        float N = 60;
//        float x = -N;
//
//        for(int i = 0; i < N; i++, x += 2f) {
//            float z = -N;
//            for (int j = 0; j < N; j++, z += 2f) {
//                transforms.add(new Matrix4().translate(x, 0, z).scale(new Vector3( 1f,1f+.3f*(float)Math.sin(x-z), 1f)).rotate(up, 30f * x + 20f * z));
//            }
//        }
//        System.out.println("Instances: "+transforms.size());
//        return transforms;
//    }



    private void rotate(ArrayList<Matrix4> transforms, float deltaTime){
        for(Matrix4 transform : transforms)
            transform.rotate(up, 30f*deltaTime);
    }



    public void render( ){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE))
            Gdx.app.exit();
        camController.update();

        rotate(transforms, Gdx.graphics.getDeltaTime());



        modelBatch.begin(camera, Color.GRAY);
        modelBatch.render(modelInstances, environment);
        modelBatch.end();

        batch.begin(null);
        font.draw(batch, info, 10, 70);
        font.draw(batch, "frames per second: "+fps, 10, 50);
        batch.end();

//        stage.draw();

        // At the end of the frame
        if (System.nanoTime() - startTime > 1000000000) {
            System.out.println("SpriteBatch : fps: " + frames +" instances: "+ transforms.size() );
            fps = frames;
            frames = 0;
            startTime = System.nanoTime();
        }
        frames++;
    }

    public void dispose(){
        // cleanup
        model.dispose();
        modelBatch.dispose();
        batch.dispose();
        font.dispose();
//        stage.dispose();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        //stage.resize(width, height);
    }


}


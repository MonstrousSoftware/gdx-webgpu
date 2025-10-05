package com.monstrous.gdx.tests.webgpu;



import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDefaultShader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

// Test instancing
// For comparison to DuckField demo but is lacking shadows, PBR shading,etc.

public class DuckField extends GdxTest {
    private int ducksInARow = 10;

    private WgModelBatch modelBatch;
    private Camera camera;
    private CameraInputController camController;
    private Model model;
    private Array<ModelInstance> modelInstances;
    private Environment environment;
    private Array<Matrix4> transforms;
    private WgStage stage;
    private WgSkin skin;
    private int fps;
    private Label fpsLabel;
    private long startTime;
    private int frames;
    private final Vector3 up = new Vector3(0,1,0);

    public void create() {
        startTime = System.nanoTime();
        frames = 0;

        model = new WgGLTFModelLoader().loadModel(Gdx.files.internal("data/g3d/gltf/Ducky/ducky.gltf"));

        modelInstances = new Array<>();
        transforms = new Array<>();
        makeDucks(ducksInARow);

        camera = new PerspectiveCamera(70, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0, 1, -3);
        camera.direction.set(0,0f, 1f);
        camera.far = 1000f;
        camera.near = 0.1f;
        camera.update();

        environment = new Environment();
        DirectionalLight light = new DirectionalLight();
        light.set(Color.WHITE, new Vector3(.4f,-1,.2f));
        environment.add( light );
        environment.set(ColorAttribute.createAmbientLight(Color.DARK_GRAY));

        WgModelBatch.Config config = new WgModelBatch.Config();
        config.maxInstances = 4000;
        modelBatch = new WgModelBatch(config );

        stage = new WgStage(new ScreenViewport());
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));
        rebuildStage();

        camController = new CameraInputController(camera);
        InputMultiplexer im= new InputMultiplexer();
        im.addProcessor(stage);
        im.addProcessor(camController);
        Gdx.input.setInputProcessor(im);
    }

    private void makeDucks(int ducksPerSide){
        modelInstances.clear();
        transforms = makeTransforms();
        for(Matrix4 transform: transforms) {
            modelInstances.add(new ModelInstance(model, transform));
        }
    }

    private void rebuildStage(){
        stage.clear();

        Table screenTable = new Table();
        screenTable.setFillParent(true);

        Label value = new Label(""+ducksInARow*ducksInARow, skin);


        Table sliderTable = new Table();
        Slider slider = new Slider(1, 60, 1f, false, skin);
        slider.setValue(ducksInARow);
        slider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                System.out.println("Ducks/side: " + slider.getValue());
                ducksInARow = (int)slider.getValue();
                value.setText(ducksInARow*ducksInARow);
                makeDucks(ducksInARow);

            }
        });

        fpsLabel = new Label("",skin);


        sliderTable.add(slider);
        sliderTable.row();
        sliderTable.add(value);
        sliderTable.row();
        sliderTable.add(fpsLabel);

        screenTable.add(sliderTable).align(Align.topRight).expand();
        stage.addActor(screenTable);
    }

    private Array<Matrix4> makeTransforms(){
        transforms.clear();
        float N = ducksInARow;
        float x = -N;

        for(int i = 0; i < N; i++, x += 2f) {
            float z = -N;
            for (int j = 0; j < N; j++, z += 2f) {
                transforms.add(new Matrix4().translate(x, 0, z)
                    .scale( 1f,1f+.3f*(float)Math.sin(x-z), 1f)
                    .rotate(up, 30f * x + 20f * z));
            }
        }
        System.out.println("Instances: "+transforms.size);
        return transforms;
    }



    private void rotate(Array<Matrix4> transforms, float deltaTime){
        for(Matrix4 transform : transforms)
            transform.rotate(up, 30f*deltaTime);
    }



    public void render( ){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE))
            Gdx.app.exit();
        camController.update();

        rotate(transforms, Gdx.graphics.getDeltaTime());


        WgScreenUtils.clear(Color.SKY, true);
        modelBatch.begin(camera);
        modelBatch.render(modelInstances, environment);
        modelBatch.end();

        fpsLabel.setText("FPS: "+fps);

        stage.act();
        stage.draw();

        // At the end of the frame
        if (System.nanoTime() - startTime > 1000000000) {
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
        stage.dispose();
        skin.dispose();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        stage.getViewport().update(width, height, true);
        rebuildStage();
    }


}


package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgGraphics;
import com.badlogic.gdx.webgpu.graphics.WgShaderProgram;
import com.badlogic.gdx.webgpu.graphics.WgTexture;
import com.badlogic.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.badlogic.gdx.webgpu.scene2d.WgSkin;
import com.badlogic.gdx.webgpu.scene2d.WgStage;
import com.badlogic.gdx.webgpu.utils.JavaWebGPU;
import com.badlogic.gdx.webgpu.webgpu.*;
import com.badlogic.gdx.webgpu.wrappers.*;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;


/**
 * Compute shader test.
 * Based on Sebastian Lague's Youtube video : Coding Adventure: Ant and Slime Simulations
 */

public class ComputeMoldSlime extends GdxTest {

    private int agentSize = 4*Float.BYTES; // bytes including padding
    private int uniformSize = 7*Float.BYTES;
    private int width;
    private int height;

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WgGraphics gfx;
    private WebGPUComputePipeline pipeline1, pipeline2, pipeline3;
    private WgTexture texture, texture2;
    WebGPUBindGroup bindGroupMove, bindGroupEvap, bindGroupBlur;
    WebGPUQueue queue;
    WebGPUUniformBuffer uniforms;
    WebGPUBuffer agents;
    Config config;
    Viewport viewport;
    WgStage stage;
    WgSkin skin;


    public static class Config {
        int numAgents;
        float evapSpeed;
        float senseDistance;
        float senseAngleSpacing;
        float turnSpeed;
    }


    public static void main (String[] argv) {
        WgApplicationConfiguration config = new WgApplicationConfiguration();
        config.setWindowedMode(800, 600);
        config.setTitle("Compute Shader Slime Mold");
        new WgApplication(new ComputeMoldSlime(), config);
    }

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        gfx = (WgGraphics) Gdx.graphics;
        // start the simulation on resize()

        config = new Config();
        config.numAgents = 40960;
        config.evapSpeed = 0.88f;
        config.senseDistance = 10f;
        config.senseAngleSpacing = 0.2f;
        config.turnSpeed = 10f;

        viewport = new ScreenViewport();
        stage = new WgStage(viewport);
        skin = new WgSkin();

        // Add some GUI
        //
        viewport = new ScreenViewport();
        stage = new WgStage(viewport);
        Gdx.input.setInputProcessor(stage);
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));
        //rebuildStage();
    }



    private void initSim(int width, int height){


        int textureUsage = WGPUTextureUsage.TextureBinding | WGPUTextureUsage.StorageBinding | WGPUTextureUsage.CopyDst | WGPUTextureUsage.CopySrc;

        //public WgTexture(String label, int width, int height, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numSamples )
        texture = new WgTexture("texture0", width, height, 1, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);
        texture2 = new WgTexture("texture2", width, height, 1, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);

        Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pm.setColor(Color.BLACK);
        pm.fill();
        texture.load(pm.getPixels(), 0);

        // Create input and output textures

        // create a queue
        queue = new WebGPUQueue(gfx.getDevice());



        uniforms = new WebGPUUniformBuffer(uniformSize, WGPUBufferUsage.CopyDst |WGPUBufferUsage.Uniform);
        uniforms.set(0, width);
        uniforms.set(Float.BYTES, height);
        uniforms.set(2*Float.BYTES, config.evapSpeed);  // evapSpeed
        uniforms.set(3*Float.BYTES, 0.01f);  // deltaTime
        uniforms.set(4*Float.BYTES, config.senseDistance);    // senseDistance
        uniforms.set(5*Float.BYTES, config.senseAngleSpacing);    // senseAngleSpacing (fraction of PI)
        uniforms.set(6*Float.BYTES, config.turnSpeed);    // turnSpeed
        uniforms.flush();


        // create a buffer for the agents
        agents = new WebGPUBuffer("agents", WGPUBufferUsage.Storage | WGPUBufferUsage.CopyDst | WGPUBufferUsage.CopySrc, (long) agentSize * config.numAgents);
        // fill agent buffer with initial data
        initAgents(queue, agents, config.numAgents);

        // we use a single shader source file with 3 entry points for the different steps
        WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/compute-slime.wgsl")); // from assets folder


        // for simplicity, we use the same bind group layout for all steps, although the agents array is only used in step 1.
        WebGPUBindGroupLayout bindGroupLayout = makeBindGroupLayout();
        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("slime pipeline layout", bindGroupLayout);
        pipeline1 = new WebGPUComputePipeline(shader, "moveAgents", pipelineLayout);
        pipeline2 = new WebGPUComputePipeline(shader, "evaporate", pipelineLayout);
        pipeline3 = new WebGPUComputePipeline(shader, "blur", pipelineLayout);
        pipelineLayout.dispose();

        // as we switch between 2 textures the output of the last pass will be the input for the first pass in the next iteration
        bindGroupMove = makeBindGroup(bindGroupLayout, uniforms, agents, texture2.getTextureView(), texture.getTextureView());
        bindGroupEvap = makeBindGroup(bindGroupLayout, uniforms, agents, texture.getTextureView(), texture2.getTextureView());
        bindGroupBlur = makeBindGroup(bindGroupLayout, uniforms, agents, texture2.getTextureView(), texture.getTextureView());

        bindGroupLayout.dispose();
        shader.dispose();
    }

    // clean up all the resources
    private void exitSim(){
        texture.dispose();
        texture2.dispose();
        uniforms.dispose();
        agents.dispose();

        queue.dispose();
        pipeline1.dispose();
        pipeline2.dispose();
        pipeline3.dispose();
        bindGroupMove.dispose();
        bindGroupEvap.dispose();
        bindGroupBlur.dispose();
    }


    private void step(float deltaTime){
        uniforms.set(3*Float.BYTES, deltaTime);  // deltaTime
        uniforms.flush();


        WebGPUCommandEncoder encoder = new WebGPUCommandEncoder(gfx.getDevice());

        // Step 1. move agents
        WebGPUComputePass pass = encoder.beginComputePass();
        // set pipeline & bind group 0
        pass.setPipeline(pipeline1);
        pass.setBindGroup(0, bindGroupMove);

        int invocationCountX = config.numAgents;    // nr of input values

        int workgroupSizeX = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        int workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        pass.dispatchWorkGroups( workgroupCountX, 1, 1);
        pass.end();

        // Step 2. evaporate trails

        pass = encoder.beginComputePass();
        // set pipeline & bind group 0
        pass.setPipeline(pipeline2);
        pass.setBindGroup(0, bindGroupEvap);

        invocationCountX = width;
        int invocationCountY = height;

        workgroupSizeX = 16;
        int workgroupSizeY = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        int workgroupCountY = (invocationCountY + workgroupSizeY - 1) / workgroupSizeY;
        pass.dispatchWorkGroups( workgroupCountX, workgroupCountY, 1);

        pass.end();

        // Step 3. blur screen

        pass = encoder.beginComputePass();
        // set pipeline & bind group 0
        pass.setPipeline(pipeline3);
        pass.setBindGroup(0, bindGroupBlur);

        invocationCountX = width;
        invocationCountY = height;

        workgroupSizeX = 16;
        workgroupSizeY = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        workgroupCountY = (invocationCountY + workgroupSizeY - 1) / workgroupSizeY;
        pass.dispatchWorkGroups( workgroupCountX, workgroupCountY, 1);

        pass.end();

        // copy output buffer to map buffer so that we can read it back
        // encoder.copyBufferToBuffer(outputBuffer, 0, mapBuffer, 0, BUFFER_SIZE);

        // finish the encoder to give use command buffer
        WebGPUCommandBuffer commandBuffer = encoder.finish();
        encoder.dispose();
        // feed the command buffer to the queue
        queue.submit(commandBuffer);
        commandBuffer.dispose();
        pass.dispose();
    }

    private void initAgents(  WebGPUQueue queue, WebGPUBuffer agents, int numAgents ){
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer( agentSize * numAgents * Float.BYTES);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        for(int i = 0; i < numAgents; i++){
            float angle = MathUtils.random(0, MathUtils.PI2);
            float distance = MathUtils.random(0, 100);
            float x = width/2.0f - distance * MathUtils.cos(angle);
            float y = height/2.0f - distance * MathUtils.sin(angle);
            float dir = angle;
            // here offset is in floats, 4 floats per agent
            floatBuffer.put(i*4, x);
            floatBuffer.put(i*4+1, y);
            floatBuffer.put(i*4+2, dir);
            floatBuffer.put(i*4+3, 0);   // padding
        }
        queue.writeBuffer(agents, 0, JavaWebGPU.createByteBufferPointer(byteBuffer),agentSize * numAgents);
        // todo dispose nio buffers
    }




    private WebGPUBindGroupLayout makeBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform,  uniformSize, false );
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage, (long) agentSize * config.numAgents, false );
        layout.addTexture(2, WGPUShaderStage.Compute, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addStorageTexture(3, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUUniformBuffer uniforms, WebGPUBuffer agents, WebGPUTextureView inView, WebGPUTextureView textureView){
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, uniforms);
        bg.setBuffer(1, agents);
        bg.setTexture(2, inView);
        bg.setTexture(3, textureView);
        bg.end();
        return bg;
    }

    @Override
    public void render(){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit();
            return;
        }

        if(Gdx.input.isKeyJustPressed(Input.Keys.TAB)){
            if(stage.getActors().size > 0)
                stage.clear();
            else
                rebuildStage();
        }


        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pm.setColor(Color.BLACK);
            pm.fill();
            texture.load(pm.getPixels(), 0);
        }

        step(Gdx.graphics.getDeltaTime());

        batch.begin(Color.BLACK);
        batch.draw(texture2,   0,0);
        batch.end();

        stage.act();
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        this.width = width;
        this.height = height;
        if(texture != null) // sim was running already?
            exitSim();
        initSim(width, height);
        //viewport.update(width, height);
        viewport.setWorldSize(width, height);
        stage.getViewport().update(width, height, true);
        rebuildStage();
    }

    @Override
    public void dispose(){
        // cleanup
        exitSim();
        batch.dispose();
        font.dispose();
    }


    private void rebuildStage(){

        Label numAgents = new Label(String.valueOf(config.numAgents), skin);
        Label evapSpeed = new Label(String.valueOf(config.evapSpeed), skin);
        Label turnSpeed = new Label(String.valueOf(config.turnSpeed), skin);
        Label angle = new Label(String.valueOf(config.senseAngleSpacing), skin);
        Label senseDistance = new Label(String.valueOf(config.senseDistance), skin);

        Slider instancesSlider = new Slider(1, 1000000, 10, false, skin);
        instancesSlider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                config.numAgents = (int) instancesSlider.getValue();
                numAgents.setText(config.numAgents);
                exitSim();
                initSim(width, height);
            }
        });

        Slider evapSlider = new Slider(0, 2.5f, 0.01f, false, skin);
        evapSlider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                config.evapSpeed = evapSlider.getValue();
                evapSpeed.setText(String.valueOf(config.evapSpeed));
                uniforms.set(2*Float.BYTES, config.evapSpeed);  // evapSpeed
                uniforms.flush();
            }
        });

        Slider angleSlider = new Slider(0, 0.5f, 0.01f, false, skin);
        angleSlider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                config.senseAngleSpacing = angleSlider.getValue();
                angle.setText(String.valueOf(config.senseAngleSpacing));
                uniforms.set(5*Float.BYTES, config.senseAngleSpacing);
                uniforms.flush();
            }
        });

        Slider distSlider = new Slider(0, 40f, 1f, false, skin);
        distSlider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                config.senseDistance = distSlider.getValue();
                senseDistance.setText(String.valueOf(config.senseDistance));
                uniforms.set(4*Float.BYTES, config.senseDistance);
                uniforms.flush();
            }
        });

        Slider turnSlider = new Slider(0, 20, 0.01f, false, skin);
        turnSlider.addListener(new ChangeListener() {
            public void changed (ChangeEvent event, Actor actor) {
                config.turnSpeed = turnSlider.getValue();
                turnSpeed.setText(String.valueOf(config.turnSpeed));
                uniforms.set(6*Float.BYTES, config.turnSpeed);
                uniforms.flush();
            }
        });

        stage.clear();
        Table screenTable = new Table();
        screenTable.setFillParent(true);
        Table controls = new Table();
        controls.add(new Label("#Agents:", skin)).align(Align.left);
        controls.add(numAgents).align(Align.left).row();
        controls.add(instancesSlider).align(Align.left).row();

        controls.add(new Label("Evaporation speed:", skin)).align(Align.left);
        controls.add(evapSpeed).align(Align.left).row();
        controls.add(evapSlider).align(Align.left).row();


        controls.add(new Label("Sense angle:", skin)).align(Align.left);
        controls.add(angle).align(Align.left).row();
        controls.add(angleSlider).align(Align.left).row();


        controls.add(new Label("Sense distance:", skin)).align(Align.left);
        controls.add(senseDistance).align(Align.left).row();
        controls.add(distSlider).align(Align.left).row();


        controls.add(new Label("Turn speed:", skin)).align(Align.left);
        controls.add(turnSpeed).align(Align.left).row();
        controls.add(turnSlider).align(Align.left).row();

        controls.add(new Label("(TAB to hide sliders)", skin)).pad(20).align(Align.left);

        screenTable.add(controls).left().top().expand();

        stage.addActor(screenTable);
    }



}

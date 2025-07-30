package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUApplication;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;
import com.monstrous.gdx.webgpu.wrappers.*;


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


/**
 * Compute shader test.
 * Based on Sebastian Lague's Youtube video : Coding Adventure: Ant and Slime Simulations
 */

public class ComputeMoldSlime extends GdxTest {

    private final int agentSize = 4*Float.BYTES; // bytes including padding
    private final int uniformSize = 7*Float.BYTES;
    private int width;
    private int height;

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WebGPUContext webgpu;
    private WebGPUComputePipeline pipeline1, pipeline2, pipeline3;
    private WgTexture texture1, texture2;
    private WebGPUBindGroup bindGroupMove, bindGroupEvap, bindGroupBlur;
    private WGPUQueue queue;
    private WebGPUUniformBuffer uniforms;
    private WebGPUBuffer agents;
    private WGPUComputePassEncoder pass;
    private Config config;
    private Viewport viewport;
    private WgStage stage;
    private WgSkin skin;
    private int savedWidth, savedHeight;
    private boolean needRestart = false;
    private float time;


    public static class Config {
        int numAgents;
        float evapSpeed;
        float senseDistance;
        float senseAngleSpacing;
        float turnSpeed;
    }

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();
        // start the simulation on resize()

        config = new Config();
        config.numAgents = 40960;
        config.evapSpeed = 0.88f;
        config.senseDistance = 10f;
        config.senseAngleSpacing = 0.2f;
        config.turnSpeed = 10f;

        // Add some GUI
        //
        viewport = new ScreenViewport();
        stage = new WgStage(viewport);
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

        Gdx.input.setInputProcessor(stage);

        width = Gdx.graphics.getWidth();
        height = Gdx.graphics.getHeight();

        // let resize() build the stage, and start the sim
    }



    private void initSim(int width, int height){

        pass = new WGPUComputePassEncoder();
        WGPUTextureUsage textureUsage = WGPUTextureUsage.TextureBinding.or( WGPUTextureUsage.StorageBinding).or( WGPUTextureUsage.CopyDst).or( WGPUTextureUsage.CopySrc);

        //public WgTexture(String label, int width, int height, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numSamples )
        texture1 = new WgTexture("texture1", width, height, false, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);
        texture2 = new WgTexture("texture2", width, height,false, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);

        Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        pm.setColor(Color.BLACK);
        pm.fill();
        texture1.load(pm.getPixels() );
        pm.dispose();

        // Create input and output textures

        // create a queue
        queue = webgpu.device.getQueue();

        uniforms = new WebGPUUniformBuffer(uniformSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
        uniforms.set(0, width);
        uniforms.set(Float.BYTES, height);
        uniforms.set(2*Float.BYTES, config.evapSpeed);  // evapSpeed
        uniforms.set(3*Float.BYTES, 0.01f);  // deltaTime
        uniforms.set(4*Float.BYTES, config.senseDistance);    // senseDistance
        uniforms.set(5*Float.BYTES, config.senseAngleSpacing);    // senseAngleSpacing (fraction of PI)
        uniforms.set(6*Float.BYTES, config.turnSpeed);    // turnSpeed
        uniforms.flush();


        // create a buffer for the agents
        agents = new WebGPUBuffer("agents", WGPUBufferUsage.Storage.or(WGPUBufferUsage.CopyDst).or(WGPUBufferUsage.CopySrc),  agentSize * config.numAgents);
        // fill agent buffer with initial data
        initAgents(queue, agents, config.numAgents);

        // we use a single shader source file with 3 entry points for the different steps
        //WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/compute-slime.wgsl")); // from assets folder
        WgShaderProgram shader = new WgShaderProgram("shader", shaderSource, ""); // from assets folder


        // for simplicity, we use the same bind group layout for all steps, although the agents array is only used in step 1.
        WebGPUBindGroupLayout bindGroupLayout = makeBindGroupLayout();
        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("slime pipeline layout", bindGroupLayout);

        pipeline1 = new WebGPUComputePipeline(shader, "moveAgents", pipelineLayout);
        pipeline2 = new WebGPUComputePipeline(shader, "evaporate", pipelineLayout);
        pipeline3 = new WebGPUComputePipeline(shader, "blur", pipelineLayout);
        pipelineLayout.dispose();

        // as we switch between 2 textures the output of the last pass will be the input for the first pass in the next iteration
        bindGroupMove = makeBindGroup(bindGroupLayout, uniforms, agents, texture2.getTextureView(), texture1.getTextureView());
        bindGroupEvap = makeBindGroup(bindGroupLayout, uniforms, agents, texture1.getTextureView(), texture2.getTextureView());
        bindGroupBlur = makeBindGroup(bindGroupLayout, uniforms, agents, texture2.getTextureView(), texture1.getTextureView());

        bindGroupLayout.dispose();
        shader.dispose();
    }

    // clean up all the resources
    private void exitSim(){
//        texture1.dispose();
//        texture2.dispose();
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
        time += deltaTime;
        if(needRestart && (int)time % 2 == 0 ) {
            exitSim();
            initSim(width, height);
            needRestart = false;
        }

        uniforms.set(3*Float.BYTES, deltaTime);  // deltaTime
        uniforms.flush();

        WGPUCommandEncoder encoder = WGPUCommandEncoder.obtain();
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("Command Encoder");
        webgpu.device.createCommandEncoder(encoderDesc, encoder);

        WGPUComputePassDescriptor passDescriptor = WGPUComputePassDescriptor.obtain();
        passDescriptor.setNextInChain(null);
        passDescriptor.setTimestampWrites(null);


        // Step 1. move agents
        encoder.beginComputePass(passDescriptor, pass);
        // set pipeline & bind group 0
        pass.setPipeline(pipeline1.getPipeline());
        pass.setBindGroup(0, bindGroupMove.getBindGroup(), null);

        int invocationCountX = config.numAgents;    // nr of input values

        int workgroupSizeX = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        int workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        pass.setDispatchWorkgroups( workgroupCountX, 1, 1);
        pass.end();

        // Step 2. evaporate trails

        encoder.beginComputePass(passDescriptor, pass);
        // set pipeline & bind group 0
        pass.setPipeline(pipeline2.getPipeline());
        pass.setBindGroup(0, bindGroupEvap.getBindGroup(), null);

        invocationCountX = width;
        int invocationCountY = height;

        workgroupSizeX = 16;
        int workgroupSizeY = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        int workgroupCountY = (invocationCountY + workgroupSizeY - 1) / workgroupSizeY;
        pass.setDispatchWorkgroups(  workgroupCountX, workgroupCountY, 1);

        pass.end();

        // Step 3. blur screen

        encoder.beginComputePass(passDescriptor, pass);
        // set pipeline & bind group 0
        pass.setPipeline(pipeline3.getPipeline());
        pass.setBindGroup(0, bindGroupBlur.getBindGroup(), null);

        invocationCountX = width;
        invocationCountY = height;

        workgroupSizeX = 16;
        workgroupSizeY = 16;
        // This ceils invocationCountX / workgroupSizePerDim
        workgroupCountX = (invocationCountX + workgroupSizeX - 1) / workgroupSizeX;
        workgroupCountY = (invocationCountY + workgroupSizeY - 1) / workgroupSizeY;
        pass.setDispatchWorkgroups(  workgroupCountX, workgroupCountY, 1);

        pass.end();

        // copy output buffer to map buffer so that we can read it back
        // encoder.copyBufferToBuffer(outputBuffer, 0, mapBuffer, 0, BUFFER_SIZE);

        // finish the encoder to give use command buffer
        WGPUCommandBuffer commandBuffer = WGPUCommandBuffer.obtain();
        WGPUCommandBufferDescriptor commandDescr = WGPUCommandBufferDescriptor.obtain();
        encoder.finish(commandDescr, commandBuffer);
        encoder.dispose();

        // feed the command buffer to the queue
        queue.submit(1, commandBuffer);
        commandBuffer.release();
        commandBuffer.dispose();
        encoder.release();
        encoder.dispose();
        pass.release();
    }


    private void initAgents(  WGPUQueue queue, WebGPUBuffer agents, int numAgents ){
        ByteBuffer byteBuffer = BufferUtils.newUnsafeByteBuffer( agentSize * numAgents * Float.BYTES);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        for(int i = 0; i < numAgents; i++){
            float angle = MathUtils.random(0, MathUtils.PI2);
            float distance = MathUtils.random(0, 100);
            float x = width/2.0f - distance * MathUtils.cos(angle);
            float y = height/2.0f - distance * MathUtils.sin(angle);

            // here offset is in floats, 4 floats per agent
            floatBuffer.put(i*4, x);
            floatBuffer.put(i*4+1, y);
            floatBuffer.put(i*4+2, angle);
            floatBuffer.put(i*4+3, 0);   // padding
        }

        queue.writeBuffer(agents.getBuffer(), 0, byteBuffer, agentSize * numAgents);
        BufferUtils.disposeUnsafeByteBuffer(byteBuffer);
    }




    private WebGPUBindGroupLayout makeBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform,  uniformSize, false );
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage,  agentSize * config.numAgents, false );
        layout.addTexture(2, WGPUShaderStage.Compute, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addStorageTexture(3, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUUniformBuffer uniforms, WebGPUBuffer agents, WGPUTextureView inView, WGPUTextureView textureView){
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

        if(Gdx.input.isKeyJustPressed(Input.Keys.F11)){
            boolean fullScreen = Gdx.graphics.isFullscreen();
            WgGraphics.DisplayMode currentMode = Gdx.graphics.getDisplayMode();
            if (fullScreen)
                Gdx.graphics.setWindowedMode(savedWidth, savedHeight);
            else {
                savedWidth = Gdx.graphics.getWidth();
                savedHeight = Gdx.graphics.getHeight();
                Gdx.graphics.setFullscreenMode(currentMode);
            }
            return;
        }


        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pm.setColor(Color.BLACK);
            pm.fill();
            texture1.load(pm.getPixels());
            pm.dispose();
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
        needRestart = true;
//        if(texture1 != null) // sim was running already?
//            exitSim();
        if(texture1 == null)
            initSim(width, height);
        viewport.update(width, height);
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
        pass.dispose();
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
                needRestart = true;

            }
        });

        Slider evapSlider = new Slider(0, 1.0f, 0.01f, false, skin);
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
        controls.add(instancesSlider).colspan(2).align(Align.left).row();

        controls.add(new Label("Evaporation speed:", skin)).align(Align.left);
        controls.add(evapSpeed).align(Align.left).row();
        controls.add(evapSlider).colspan(2).align(Align.left).row();


        controls.add(new Label("Sense angle:", skin)).align(Align.left);
        controls.add(angle).align(Align.left).row();
        controls.add(angleSlider).colspan(2).align(Align.left).row();


        controls.add(new Label("Sense distance:", skin)).align(Align.left);
        controls.add(senseDistance).align(Align.left).row();
        controls.add(distSlider).colspan(2).align(Align.left).row();


        controls.add(new Label("Turn speed:", skin)).align(Align.left);
        controls.add(turnSpeed).align(Align.left).row();
        controls.add(turnSlider).colspan(2).align(Align.left).row();

        controls.add(new Label("(TAB to hide sliders)", skin)).colspan(2).pad(20).align(Align.left).row();
        controls.add(new Label("(F11 to toggle full screen)", skin)).colspan(2).pad(10).align(Align.left);

        screenTable.add(controls).left().top().expand();

        stage.addActor(screenTable);
    }

    String shaderSource = "// Compute Shader for Slime Mold\n" +
        "// Simulate mold spores\n" +
        "// Following Coding Adventure: Ant and Slime Simulations by Sebastian Lague.\n" +
        "//\n" +
        "\n" +
        "struct Uniforms {\n" +
        "    width : f32,\n" +
        "    height : f32,\n" +
        "    evaporationSpeed: f32,\n" +
        "    deltaTime: f32,\n" +
        "    sensorDistance: f32,\n" +
        "    sensorAngleSpacing: f32,\n" +
        "    turnSpeed: f32,\n" +
        "}\n" +
        "\n" +
        "\n" +
        "struct Agent {\n" +
        "  position: vec2f,\n" +
        "  direction: f32,   // in radians\n" +
        "  dummy: f32        // explicit padding\n" +
        "}\n" +
        "\n" +
        "\n" +
        "@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
        "@group(0) @binding(1) var<storage, read_write> agents: array<Agent>;\n" +
        "@group(0) @binding(2) var inputTexture: texture_2d<f32>;\n" +
        "@group(0) @binding(3) var outputTexture: texture_storage_2d<rgba8unorm,write>;\n" +
        "\n" +
        "const pi : f32 = 3.14159;\n" +
        "\n" +
        "\n" +
        "@compute @workgroup_size(16, 1, 1)\n" +
        "fn moveAgents(@builtin(global_invocation_id) id: vec3<u32>) {\n" +
        "    // id is index into agents array\n" +
        "    let agent : Agent = agents[id.x];\n" +
        "\n" +
        "    let direction: vec2f = vec2f( cos(agent.direction), sin(agent.direction));\n" +
        "    let random : u32 = hash( u32(agent.position.x + uniforms.width * agent.position.y + f32(id.x)));\n" +
        "\n" +
        "    let weightForward: f32 = sense(agent, 0);\n" +
        "    let weightLeft: f32 = sense(agent, uniforms.sensorAngleSpacing * pi);\n" +
        "    let weightRight: f32 = sense(agent, -uniforms.sensorAngleSpacing * pi);\n" +
        "    let randomSteerStrength: f32 = unitScale(random);\n" +
        "\n" +
        "    if(weightForward > weightLeft && weightForward > weightRight){\n" +
        "        // do nothing\n" +
        "    } else if(weightForward < weightLeft && weightForward < weightRight) {\n" +
        "        agents[id.x].direction += (randomSteerStrength - 0.5) * uniforms.turnSpeed * uniforms.deltaTime;\n" +
        "   } else if(weightRight > weightLeft){\n" +
        "        agents[id.x].direction -= randomSteerStrength * uniforms.turnSpeed * uniforms.deltaTime;\n" +
        "    } else if(weightLeft > weightRight){\n" +
        "        agents[id.x].direction += randomSteerStrength * uniforms.turnSpeed * uniforms.deltaTime;\n" +
        "    }\n" +
        "\n" +
        "    var newPosition: vec2f = agent.position + direction;\n" +
        "    if(newPosition.x < 0  || newPosition.x >= uniforms.width || newPosition.y < 0 || newPosition.y >= uniforms.height){\n" +
        "        agents[id.x].direction += unitScale(random) * pi * 2.0;\n" +
        "        newPosition = agent.position;\n" +
        "    }\n" +
        "    agents[id.x].position = newPosition;\n" +
        "\n" +
        "    let texCoord : vec2i = vec2i(newPosition);\n" +
        "    let white = vec4f(0.5, 1, 0.8, 1);\n" +
        "\n" +
        "    textureStore(outputTexture, texCoord, white);\n" +
        "}\n" +
        "\n" +
        "fn sense(agent: Agent, angleOffset : f32) ->f32 {\n" +
        "    let sensorAngle : f32 = agent.direction + angleOffset;\n" +
        "    let sensorDir : vec2f = vec2f(cos(sensorAngle), sin(sensorAngle));\n" +
        "    let sensorCentre: vec2i = vec2i(agent.position + sensorDir * uniforms.sensorDistance);\n" +
        "\n" +
        "    var sum: f32 = 0;\n" +
        "    for(var x: i32 = -1; x <= 1; x++){\n" +
        "        for(var y: i32 = -1; y <= 1; y++){\n" +
        "            let pos: vec2i = sensorCentre + vec2i(x,y);\n" +
        "            sum += textureLoad(inputTexture, pos, 0).r;\n" +
        "        }\n" +
        "    }\n" +
        "    return sum;\n" +
        "}\n" +
        "\n" +
        "\n" +
        "fn unitScale( h: u32 ) -> f32 {\n" +
        "    return f32(h) / 4294967295.0;\n" +
        "}\n" +
        "\n" +
        "// hash function   schechter-sca08-turbulence\n" +
        "fn hash( input: u32) -> u32 {\n" +
        "    var state = input;\n" +
        "    state ^= 2747636419u;\n" +
        "    state *= 2654435769u;\n" +
        "    state ^= state >> 16;\n" +
        "    state *= 2654435769u;\n" +
        "    state ^= state >> 16;\n" +
        "    state *= 2654435769u;\n" +
        "    return state;\n" +
        "}\n" +
        "\n" +
        "\n" +
        "\n" +
        "@compute @workgroup_size(16, 16, 1)\n" +
        "fn evaporate(@builtin(global_invocation_id) id: vec3<u32>) {\n" +
        "\n" +
        "    if(id.x < 0  || id.x >= u32(uniforms.width) || id.y < 0 || id.y >= u32(uniforms.height)){\n" +
        "        return;\n" +
        "    }\n" +
        "\n" +
        "    let originalColor = textureLoad(inputTexture, id.xy, 0);\n" +
        "    let evaporatedColor = max(vec4f(0.0), originalColor - uniforms.deltaTime*uniforms.evaporationSpeed);\n" +
        "\n" +
        "    textureStore(outputTexture, id.xy, evaporatedColor);\n" +
        "}\n" +
        "\n" +
        "\n" +
        "@compute @workgroup_size(16, 16, 1)\n" +
        "fn blur(@builtin(global_invocation_id) id: vec3<u32>) {\n" +
        "\n" +
        "    if(id.x < 0  || id.x >= u32(uniforms.width) || id.y < 0 || id.y >= u32(uniforms.height)){\n" +
        "        return;\n" +
        "    }\n" +
        "\n" +
        "    var color : vec4f = vec4f(0);\n" +
        "\n" +
        "    for(var x: i32 = -1; x <= 1; x++){\n" +
        "        for(var y: i32 = -1; y <= 1; y++){\n" +
        "            let sampleX : i32 = i32(id.x) + x;\n" +
        "            let sampleY : i32 = i32(id.y) + y;\n" +
        "            if(sampleX > 0 && sampleX < i32(uniforms.width) && sampleY > 0 && sampleY < i32(uniforms.height)){\n" +
        "                color += textureLoad(inputTexture, vec2(sampleX, sampleY), 0);\n" +
        "            }\n" +
        "        }\n" +
        "    }\n" +
        "    color /= 9.0;\n" +
        "    textureStore(outputTexture, id.xy, color);\n" +
        "}\n";
}

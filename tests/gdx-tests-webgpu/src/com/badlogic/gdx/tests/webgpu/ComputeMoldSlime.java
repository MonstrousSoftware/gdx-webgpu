package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgGraphics;
import com.badlogic.gdx.webgpu.graphics.WgShaderProgram;
import com.badlogic.gdx.webgpu.graphics.WgTexture;
import com.badlogic.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WgSpriteBatch;
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


    private static final int NUM_AGENTS = 81920;
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
        config.numAgents = 4096;
        config.evapSpeed = 0.88f;
        config.senseDistance = 10f;
        config.senseAngleSpacing = 0.2f;
        config.turnSpeed = 10f;
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
        agents = new WebGPUBuffer("agents", WGPUBufferUsage.Storage | WGPUBufferUsage.CopyDst | WGPUBufferUsage.CopySrc, (long) agentSize * NUM_AGENTS);
        // fill agent buffer with initial data
        initAgents(queue, agents);

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

        int invocationCountX = NUM_AGENTS;    // nr of input values

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

    private void initAgents(  WebGPUQueue queue, WebGPUBuffer agents ){
        ByteBuffer byteBuffer = BufferUtils.createByteBuffer( agentSize * NUM_AGENTS * Float.BYTES);
        FloatBuffer floatBuffer = byteBuffer.asFloatBuffer();
        for(int i = 0; i < NUM_AGENTS; i++){
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
        queue.writeBuffer(agents, 0, JavaWebGPU.createByteBufferPointer(byteBuffer),agentSize * NUM_AGENTS);
    }




    private WebGPUBindGroupLayout makeBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform,  uniformSize, false );
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage, (long) agentSize * NUM_AGENTS, false );
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

//    private WebGPUBindGroupLayout makeBindGroupLayout2(){
//        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
//        layout.begin();
//        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform, uniformSize, false );
//        layout.addTexture(1, WGPUShaderStage.Compute, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
//        layout.addStorageTexture(2, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
//        layout.end();
//        return layout;
//    }
//
//    private WebGPUBindGroup makeBindGroup2(WebGPUBindGroupLayout bindGroupLayout, WebGPUUniformBuffer uniforms, WebGPUTextureView textureViewIn,  WebGPUTextureView textureViewOut){
//        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
//        bg.begin();
//        bg.setBuffer(0, uniforms);
//        bg.setTexture(1, textureViewIn);
//        bg.setTexture(2, textureViewOut);
//        bg.end();
//        return bg;
//    }


    @Override
    public void render(){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit();
            return;
        }
        step(Gdx.graphics.getDeltaTime());
        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pm.setColor(Color.BLACK);
            pm.fill();
            texture.load(pm.getPixels(), 0);
        }

        batch.begin(Color.BLACK);
        batch.draw(texture2,   0,0);
        batch.end();
    }

    @Override
    public void dispose(){
        // cleanup
        exitSim();
        batch.dispose();
        font.dispose();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
        this.width = width;
        this.height = height;
        if(texture != null) // sim was running already?
            exitSim();
        initSim(width, height);
    }

}

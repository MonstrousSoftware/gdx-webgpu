package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
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

    private int width = 640;
    private int height = 480;
    private static final int NUM_AGENTS = 512;
    private int agentSize = 4*Float.BYTES; // bytes including padding

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WgGraphics gfx;
    private WebGPUComputePipeline pipelineMove, pipelineEvap, pipelineBlur;
    private WgTexture texture, texture2;
    WebGPUBindGroup bindGroupMove, bindGroupEvap, bindGroupBlur;
    WebGPUQueue queue;





    public static void main (String[] argv) {
        new WgApplication(new ComputeMoldSlime());
    }

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        gfx = (WgGraphics) Gdx.graphics;


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



        WebGPUUniformBuffer uniforms = new WebGPUUniformBuffer(4*Float.BYTES, WGPUBufferUsage.CopyDst |WGPUBufferUsage.Uniform);
        uniforms.set(0, width);
        uniforms.set(Float.BYTES, height);
        uniforms.set(2*Float.BYTES, 0.15f);  // evapSpeed
        uniforms.set(3*Float.BYTES, 0.01f);  // deltaTime
        uniforms.flush();


        // create a buffer for the agents
        WebGPUBuffer agents = new WebGPUBuffer("agents", WGPUBufferUsage.Storage | WGPUBufferUsage.CopyDst | WGPUBufferUsage.CopySrc, (long) agentSize * NUM_AGENTS);
        // fill agent buffer with initial data
        initAgents(queue, agents);

        // make a pipeline
        WebGPUBindGroupLayout bindGroupLayout = makeBindGroupLayout();

        bindGroupMove = makeBindGroup(bindGroupLayout, uniforms, agents, texture.getTextureView());
        bindGroupLayout.dispose();

        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("move agents pipeline layout", bindGroupLayout);
        WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/compute-agents.wgsl")); // from assets folder
        pipelineMove = new WebGPUComputePipeline(shader, "compute", pipelineLayout);
        pipelineLayout.dispose();
        shader.dispose();


        // make a pipeline
        bindGroupLayout = makeBindGroupLayout2();

        bindGroupEvap = makeBindGroup2(bindGroupLayout, uniforms, texture.getTextureView(), texture2.getTextureView());
        bindGroupLayout.dispose();

        pipelineLayout = new WebGPUPipelineLayout("evap pipeline layout", bindGroupLayout);
        shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/compute-evaporate.wgsl")); // from assets folder
        pipelineEvap = new WebGPUComputePipeline(shader, "compute", pipelineLayout);
        pipelineLayout.dispose();
        shader.dispose();

        // make a pipeline
        bindGroupLayout = makeBindGroupLayout2();

        bindGroupBlur = makeBindGroup2(bindGroupLayout, uniforms, texture2.getTextureView(), texture.getTextureView());
        bindGroupLayout.dispose();

        pipelineLayout = new WebGPUPipelineLayout("blur pipeline layout", bindGroupLayout);
        shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/compute-blur.wgsl")); // from assets folder
        pipelineBlur = new WebGPUComputePipeline(shader, "compute", pipelineLayout);
        pipelineLayout.dispose();
        shader.dispose();

        // as we switch between 2 textures
        // the output of the last pass (texture) will be the input for the first pass in the next iteration
    }

    private void iterate(){
        WebGPUCommandEncoder encoder = new WebGPUCommandEncoder(gfx.getDevice());

        // Step 1. move agents
        WebGPUComputePass pass = encoder.beginComputePass();
        // set pipeline & bind group 0
        pass.setPipeline(pipelineMove);
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
        pass.setPipeline(pipelineEvap);
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
        pass.setPipeline(pipelineBlur);
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
            float x = width/2f; //MathUtils.random(0, width);
            float y = height/2f; //MathUtils.random(0, height);
            float dir = MathUtils.random(0, 2f*(float)Math.PI);
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
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform, (long) 4*Float.BYTES, false );
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage, (long) agentSize * NUM_AGENTS, false );
        layout.addStorageTexture(2, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUUniformBuffer uniforms, WebGPUBuffer agents, WebGPUTextureView textureView){
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, uniforms);
        bg.setBuffer(1, agents);
        bg.setTexture(2, textureView);
        bg.end();
        return bg;
    }

    private WebGPUBindGroupLayout makeBindGroupLayout2(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.Uniform, (long) 4*Float.BYTES, false );
        layout.addTexture(1, WGPUShaderStage.Compute, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addStorageTexture(2, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup2(WebGPUBindGroupLayout bindGroupLayout, WebGPUUniformBuffer uniforms, WebGPUTextureView textureViewIn,  WebGPUTextureView textureViewOut){
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, uniforms);
        bg.setTexture(1, textureViewIn);
        bg.setTexture(2, textureViewOut);
        bg.end();
        return bg;
    }


    @Override
    public void render(){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit();
            return;
        }
        iterate();
        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            Pixmap pm = new Pixmap(width, height, Pixmap.Format.RGBA8888);
            pm.setColor(Color.BLACK);
            pm.fill();
            texture.load(pm.getPixels(), 0);
        }

        batch.begin(Color.BLACK);

        batch.draw(texture2,   0,0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight() );

        //batch.draw(texture2,   (Gdx.graphics.getWidth() - width) /2f, (Gdx.graphics.getHeight() - height) /2f);

        batch.end();
    }

    @Override
    public void dispose(){
        // cleanup
        batch.dispose();
        font.dispose();
        queue.dispose();
        pipelineMove.dispose();
        pipelineBlur.dispose();
        bindGroupMove.dispose();
        bindGroupBlur.dispose();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

}

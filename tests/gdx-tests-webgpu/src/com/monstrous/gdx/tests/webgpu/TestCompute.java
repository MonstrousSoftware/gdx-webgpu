package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;
import jnr.ffi.Pointer;



/**
 * Demonstration of using a compute shader: performs a simple function on the array of input floats.
 * Follows example from https://eliemichel.github.io/LearnWebGPU/basic-compute/compute-pipeline.html#
 * Uses some comfort classes to encapsulate WebGPU concepts.
 */

public class TestCompute extends GdxTest {

    private static final int BUFFER_SIZE = 64*Float.BYTES;    // bytes

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WgDesktopGraphics gfx;
    private WebGPUComputePipeline pipeline;
    float[] inputData = new float[BUFFER_SIZE/Float.BYTES];
    float[] outputData = new float[BUFFER_SIZE/Float.BYTES];

    public static void main (String[] argv) {
        new WgDesktopApplication(new TestCompute());
    }

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        gfx = (WgDesktopGraphics)Gdx.graphics;

        // do the compute pass once on start up
        onCompute();
    }

    private void onCompute() {

        // Create input and output buffers
        WebGPUUniformBuffer inputBuffer = new WebGPUUniformBuffer("Input storage buffer",BUFFER_SIZE, WGPUBufferUsage.CopyDst | WGPUBufferUsage.Storage, 1 );
        WebGPUBuffer outputBuffer = new WebGPUBuffer("Output storage buffer", WGPUBufferUsage.CopySrc | WGPUBufferUsage.Storage, BUFFER_SIZE );

        // Create an intermediary buffer to which we copy the output and that can be
        // used for reading into the CPU memory (because Storage is incompatible with MapRead).
        WebGPUBuffer mapBuffer = new WebGPUBuffer("Map buffer",  WGPUBufferUsage.CopyDst | WGPUBufferUsage.MapRead, BUFFER_SIZE );

        WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/computeBasic.wgsl")); // from assets folder


        // make a pipeline
        WebGPUBindGroupLayout bindGroupLayout = makeBindGroupLayout();
        WebGPUBindGroup bindGroup = makeBindGroup(bindGroupLayout, inputBuffer, outputBuffer);

        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("compute pipeline layout", bindGroupLayout);
        pipeline = new WebGPUComputePipeline(shader, "computeStuff", pipelineLayout);

        compute(bindGroup, inputBuffer, outputBuffer, mapBuffer);

        // cleanup
        pipeline.dispose();
        pipelineLayout.dispose();
        bindGroup.dispose();
        bindGroupLayout.dispose();

        shader.dispose();

        inputBuffer.dispose();
        outputBuffer.dispose();
        mapBuffer.dispose();
    }


    private WebGPUBindGroupLayout makeBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.ReadOnlyStorage, BUFFER_SIZE, false);// input buffer
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage, BUFFER_SIZE, false);// output buffer
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUBuffer inputBuffer, WebGPUBuffer outputBuffer){
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, inputBuffer);
        bg.setBuffer(1, outputBuffer);
        bg.end();
        return bg;
    }



    private void compute(WebGPUBindGroup bindGroup, WebGPUUniformBuffer inputBuffer, WebGPUBuffer outputBuffer, WebGPUBuffer mapBuffer) {

        // create a queue
        WebGPUQueue queue = new WebGPUQueue(gfx.context.device);

        // Fill input buffer

        int numFloats = BUFFER_SIZE / Float.BYTES;
        for (int i = 0; i < numFloats; i++) {
            inputData[i] = 0.1f * i;
            inputBuffer.set(i * Float.BYTES,  inputData[i]);
        }
        inputBuffer.flush();

        WebGPUCommandEncoder encoder = new WebGPUCommandEncoder(gfx.context.device);

        WebGPUComputePass pass = encoder.beginComputePass();

        // set pipeline & bind group 0
        pass.setPipeline(pipeline);
        pass.setBindGroup(0, bindGroup);

        int workGroupSize = 32;
        int invocationCount = BUFFER_SIZE / Float.BYTES;    // nr of input values
        // This ceils invocationCount / workgroupSize
        int workgroupCount = (invocationCount + workGroupSize - 1) / workGroupSize;
        pass.dispatchWorkGroups( workgroupCount, 1, 1);

        pass.end();

        // copy output buffer to map buffer so that we can read it back
        encoder.copyBufferToBuffer(outputBuffer, 0, mapBuffer, 0, BUFFER_SIZE);

        // finish the encoder to give use command buffer
        WebGPUCommandBuffer commandBuffer = encoder.finish();
        encoder.dispose();
        // feed the command buffer to the queue
        queue.submit(commandBuffer);

        boolean[] done = { false };
        WGPUBufferMapCallback callback = (WGPUBufferMapAsyncStatus status, Pointer userdata) -> {
            if (status == WGPUBufferMapAsyncStatus.Success) {
                Pointer buf = gfx.getWebGPU().wgpuBufferGetConstMappedRange(mapBuffer.getHandle(), 0, BUFFER_SIZE);
                for(int i = 0; i < numFloats; i++){
                    outputData[i] = buf.getFloat(i*Float.BYTES);
                }
            } else
                System.out.println("Buffer map async error: "+status);
            done[0] = true; // signal that the call back was executed
        };

        // note: there is a newer function for this and using this one will raise a warning,
        // but it requires a struct containing a pointer to a callback function...
        gfx.getWebGPU().wgpuBufferMapAsync(mapBuffer.getHandle(), WGPUMapMode.Read, 0, BUFFER_SIZE, callback, null);

        while(!done[0]) {
            System.out.println("Tick.");
            gfx.context.device.tick();
        }

        System.out.println("output: ");
        for(int i = 0; i < 5; i++)
            System.out.print(" "+outputData[i]);
        System.out.println();

        commandBuffer.dispose();
        encoder.dispose();
        pass.dispose();
        queue.dispose();
    }


    @Override
    public void render(){
        if(Gdx.input.isKeyPressed(Input.Keys.ESCAPE)){
            Gdx.app.exit();
            return;
        }

        batch.begin(Color.TEAL);
        int y = 300;
        font.draw(batch, "Compute Shader", 10, y);
        y-=30;
        font.draw(batch, "Input", 10, y);
        for(int i = 0; i < 9; i++)
            font.draw(batch, " "+inputData[i], 100+30*i, y);
        y-=30;
        font.draw(batch, "Output", 10, y);
        for(int i = 0; i < 9; i++)
            font.draw(batch, " "+outputData[i], 100+30*i, y);
        batch.end();
    }

    @Override
    public void dispose(){
        // cleanup
        batch.dispose();
        font.dispose();
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

}

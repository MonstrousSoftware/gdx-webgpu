package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.BufferUtils;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.wrappers.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Demonstration of using a compute shader: performs a simple function on the array of input floats. Follows example
 * from https://eliemichel.github.io/LearnWebGPU/basic-compute/compute-pipeline.html# Uses some comfort classes to
 * encapsulate WebGPU concepts.
 */

public class TestCompute extends GdxTest {

    private static final int BUFFER_SIZE = 64 * Float.BYTES; // bytes

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WebGPUContext webgpu;
    private WebGPUComputePipeline pipeline;
    private ByteBuffer buf;
    float[] inputData = new float[BUFFER_SIZE / Float.BYTES];
    float[] outputData = new float[BUFFER_SIZE / Float.BYTES];

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        buf = BufferUtils.newUnsafeByteBuffer(BUFFER_SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        // do the compute pass once on start up
        onCompute();
    }

    private void onCompute() {

        // Create input and output buffers
        WebGPUUniformBuffer inputBuffer = new WebGPUUniformBuffer("Input storage buffer", BUFFER_SIZE,
                WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Storage), 1);
        WebGPUBuffer outputBuffer = new WebGPUBuffer("Output storage buffer",
                WGPUBufferUsage.CopySrc.or(WGPUBufferUsage.Storage), BUFFER_SIZE);

        // Create an intermediary buffer to which we copy the output and that can be
        // used for reading into the CPU memory (because Storage is incompatible with MapRead).
        WebGPUBuffer mapBuffer = new WebGPUBuffer("Map buffer", WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.MapRead),
                BUFFER_SIZE);

        WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/computeBasic.wgsl")); // from assets
                                                                                                         // folder

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
        // don't dispose mapBuffer because it is part of an async operation
        // the callback will dispose it.
    }

    private void compute(WebGPUBindGroup bindGroup, WebGPUUniformBuffer inputBuffer, WebGPUBuffer outputBuffer,
            WebGPUBuffer mapBuffer) {

        // create a queue
        WGPUQueue queue = webgpu.device.getQueue();

        // Fill input buffer

        int numFloats = BUFFER_SIZE / Float.BYTES;
        for (int i = 0; i < numFloats; i++) {
            inputData[i] = 0.1f * i;
            inputBuffer.set(i * Float.BYTES, inputData[i]);
        }
        inputBuffer.flush();

        // Get an encoder
        WGPUCommandEncoder encoder = WGPUCommandEncoder.obtain();
        WGPUCommandEncoderDescriptor encoderDesc = WGPUCommandEncoderDescriptor.obtain();
        encoderDesc.setLabel("Command Encoder");
        webgpu.device.createCommandEncoder(encoderDesc, encoder);

        // Create a compute pass
        WGPUComputePassEncoder pass = new WGPUComputePassEncoder();
        WGPUComputePassDescriptor passDescriptor = WGPUComputePassDescriptor.obtain();
        passDescriptor.setNextInChain(WGPUChainedStruct.NULL);
        passDescriptor.setTimestampWrites(WGPUComputePassTimestampWrites.NULL);
        encoder.beginComputePass(passDescriptor, pass);

        // set pipeline & bind group 0
        pass.setPipeline(pipeline.getPipeline());
        pass.setBindGroup(0, bindGroup.getBindGroup(), WGPUVectorInt.NULL);

        int workGroupSize = 32;
        int invocationCount = BUFFER_SIZE / Float.BYTES; // nr of input values
        // This ceils invocationCount / workgroupSize
        int workgroupCount = (invocationCount + workGroupSize - 1) / workGroupSize;
        pass.setDispatchWorkgroups(workgroupCount, 1, 1);

        pass.end();

        // copy output buffer to map buffer so that we can read it back
        encoder.copyBufferToBuffer(outputBuffer.getBuffer(), 0, mapBuffer.getBuffer(), 0, BUFFER_SIZE);

        // finish the encoder to give use command buffer
        WGPUCommandBuffer commandBuffer = WGPUCommandBuffer.obtain();
        WGPUCommandBufferDescriptor commandDescr = WGPUCommandBufferDescriptor.obtain();
        encoder.finish(commandDescr, commandBuffer);

        // feed the command buffer to the queue
        queue.submit(commandBuffer);

        WGPUBuffer map = mapBuffer.getBuffer();
        WGPUFuture webGPUFuture = map.mapAsync(WGPUMapMode.Read, 0, BUFFER_SIZE, WGPUCallbackMode.AllowProcessEvents,
                new WGPUBufferMapCallback() {
                    @Override
                    protected void onCallback(WGPUMapAsyncStatus status, String message) {
                        System.out.println("Callback: " + status + " " + message);
                        if (status == WGPUMapAsyncStatus.Success) {
                            buf.position(0);
                            map.getConstMappedRange(0, BUFFER_SIZE, buf);
                            for (int i = 0; i < BUFFER_SIZE / Float.BYTES; i++) {
                                outputData[i] = buf.getFloat();
                            }
                            map.unmap();
                        } else
                            Gdx.app.error("mapAsync", "Buffer map async error: " + status);
                        mapBuffer.dispose();
                    }
                });
        // don't wait for the async to complete
        // just enter the render loop which will at some point
        // show the new outputData[] values

        commandBuffer.release();
        pass.dispose();
    }

    @Override
    public void render() {
        if (Gdx.input.isKeyPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
            return;
        }

        batch.begin(Color.TEAL);
        int y = 300;
        font.draw(batch, "Compute Shader", 10, y);
        y -= 30;
        font.draw(batch, "Input", 10, y);
        for (int i = 0; i < 9; i++)
            font.draw(batch, " " + inputData[i], 100 + 30 * i, y);
        y -= 30;
        font.draw(batch, "Output", 10, y);
        for (int i = 0; i < 9; i++)
            font.draw(batch, " " + outputData[i], 100 + 30 * i, y);
        batch.end();
    }

    @Override
    public void dispose() {
        // cleanup
        batch.dispose();
        font.dispose();
        BufferUtils.disposeUnsafeByteBuffer(buf);
    }

    @Override
    public void resize(int width, int height) {
        batch.getProjectionMatrix().setToOrtho2D(0, 0, width, height);
    }

    private WebGPUBindGroupLayout makeBindGroupLayout() {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Compute, WGPUBufferBindingType.ReadOnlyStorage, BUFFER_SIZE, false);// input
                                                                                                                // buffer
        layout.addBuffer(1, WGPUShaderStage.Compute, WGPUBufferBindingType.Storage, BUFFER_SIZE, false);// output buffer
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout, WebGPUBuffer inputBuffer,
            WebGPUBuffer outputBuffer) {
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setBuffer(0, inputBuffer);
        bg.setBuffer(1, outputBuffer);
        bg.end();
        return bg;
    }

}

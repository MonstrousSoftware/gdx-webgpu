package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.webgpu.*;
import com.monstrous.gdx.webgpu.wrappers.*;


/**
 * Compute shader test.
 */

public class TestComputeRNG extends GdxTest {

    private WgSpriteBatch batch;
    private WgBitmapFont font;

    private WgGraphics gfx;
    private WebGPUComputePipeline pipeline;
    private WgTexture[] textures;
    private WebGPUTextureView[] textureViews;
    private int pingPong;

    public static void main (String[] argv) {
        new WgApplication(new TestComputeRNG());
    }

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        gfx = (WgGraphics)Gdx.graphics;

        textures = new WgTexture[2];

        int textureUsage = WGPUTextureUsage.TextureBinding | WGPUTextureUsage.StorageBinding|WGPUTextureUsage.CopyDst | WGPUTextureUsage.CopySrc;

        //public WgTexture(String label, int width, int height, int mipLevelCount, int textureUsage, WGPUTextureFormat format, int numSamples )
        textures[0] = new WgTexture("texture0", 256, 256, 1, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);
        textures[1] = new WgTexture("texture1", 256, 256, 1, textureUsage, WGPUTextureFormat.RGBA8Unorm, 1);

        Pixmap pm = new Pixmap(256, 256, Pixmap.Format.RGBA8888);
        pm.setColor(Color.RED);
        pm.fill();
        pm.setColor(Color.WHITE);
        pm.fillCircle(128, 128, 100);
        textures[0].load(pm.getPixels(), 0);

        textureViews = new WebGPUTextureView[2];
        textureViews[0] = textures[0].getTextureView();
        textureViews[1] = textures[1].getTextureView();

        pingPong = 0;

        // do the compute pass once on start up
        onCompute();
    }

    private void onCompute() {

        // Create input and output textures


        WgShaderProgram shader = new WgShaderProgram(Gdx.files.internal("data/wgsl/computeRNG.wgsl")); // from assets folder


        // make a pipeline
        WebGPUBindGroupLayout bindGroupLayout = makeBindGroupLayout();
        WebGPUBindGroup bindGroup = makeBindGroup(bindGroupLayout);

        WebGPUPipelineLayout pipelineLayout = new WebGPUPipelineLayout("compute pipeline layout", bindGroupLayout);
        pipeline = new WebGPUComputePipeline(shader, "compute", pipelineLayout);

        compute(bindGroup);

        // cleanup
        pipeline.dispose();
        pipelineLayout.dispose();
        bindGroup.dispose();
        bindGroupLayout.dispose();

        shader.dispose();

        pingPong = 1 - pingPong;

    }




    private WebGPUBindGroupLayout makeBindGroupLayout(){
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout();
        layout.begin();
        layout.addTexture(0, WGPUShaderStage.Compute, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2D, false);
        layout.addStorageTexture(1, WGPUShaderStage.Compute, WGPUStorageTextureAccess.WriteOnly, WGPUTextureFormat.RGBA8Unorm, WGPUTextureViewDimension._2D);
        layout.end();
        return layout;
    }

    private WebGPUBindGroup makeBindGroup(WebGPUBindGroupLayout bindGroupLayout){
        WebGPUBindGroup bg = new WebGPUBindGroup(bindGroupLayout);
        bg.begin();
        bg.setTexture(0, textureViews[pingPong]);
        bg.setTexture(1, textureViews[1 - pingPong]);
        bg.end();
        return bg;
    }



    private void compute(WebGPUBindGroup bindGroup) {

        // create a queue
        WebGPUQueue queue = new WebGPUQueue(gfx.getDevice());


        WebGPUCommandEncoder encoder = new WebGPUCommandEncoder(gfx.getDevice());

        WebGPUComputePass pass = encoder.beginComputePass();

        // set pipeline & bind group 0
        pass.setPipeline(pipeline);
        pass.setBindGroup(0, bindGroup);

        int invocationCountX = 256;    // nr of input values
        int invocationCountY = 256;

        int workgroupSizePerDim = 8;
        // This ceils invocationCountX / workgroupSizePerDim
        int workgroupCountX = (invocationCountX + workgroupSizePerDim - 1) / workgroupSizePerDim;
        int workgroupCountY = (invocationCountY + workgroupSizePerDim - 1) / workgroupSizePerDim;
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
        if(Gdx.input.isKeyPressed(Input.Keys.SPACE)){
            onCompute();
        }

        batch.begin(Color.TEAL);

        batch.draw(textures[pingPong], 20, 100);
        batch.draw(textures[1-pingPong], 320+20, 100);

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

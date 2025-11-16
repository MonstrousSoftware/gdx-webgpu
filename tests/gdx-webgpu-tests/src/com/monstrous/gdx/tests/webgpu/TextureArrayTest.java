/*
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.Binder;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.WgTextureArray;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.*;

/** @author Tomski **/
/** Adapted for gdx-webgpu */
public class TextureArrayTest extends GdxTest {

    WgTextureArray textureArray;
    WgMesh terrain;

    ShaderProgram shaderProgram;
    WebGPUPipeline pipeline;

    PerspectiveCamera camera;
    CameraInputController cameraController;

    Matrix4 modelView = new Matrix4();
    Binder binder;

    WebGPUUniformBuffer uniformBuffer;
    int uniformBufferSize;

    @Override
    public void create() {

        String[] texPaths = new String[] {"data/g3d/materials/Searing Gorge.jpg", "data/g3d/materials/Lava Cracks.jpg",
                "data/g3d/materials/Deep Fire.jpg"};

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(8, 10f, 20f);
        camera.lookAt(10, 0, 10);
        camera.up.set(0, 1, 0);
        camera.update();
        cameraController = new CameraInputController(camera);
        Gdx.input.setInputProcessor(cameraController);

        FileHandle[] texFiles = new FileHandle[texPaths.length];
        for (int i = 0; i < texPaths.length; i++) {
            texFiles[i] = Gdx.files.internal(texPaths[i]);
        }

        textureArray = new WgTextureArray(true, texFiles);
        textureArray.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        textureArray.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);

        VertexAttributes vattr = new VertexAttributes(VertexAttribute.Position(),
                new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 3, ShaderProgram.TEXCOORD_ATTRIBUTE));

        int vertexStride = 6;
        int vertexCount = 100 * 100;
        terrain = new WgMesh(false, vertexCount * 6, 0, vattr);

        Pixmap data = new Pixmap(Gdx.files.internal("data/g3d/heightmap.png"));
        float[] vertices = new float[vertexCount * vertexStride * 6];
        int idx = 0;
        for (int i = 0; i < 100 - 1; i++) {
            for (int j = 0; j < 100 - 1; j++) {
                idx = addVertex(i, j, vertices, data, idx);
                idx = addVertex(i, j + 1, vertices, data, idx);
                idx = addVertex(i + 1, j, vertices, data, idx);

                idx = addVertex(i, j + 1, vertices, data, idx);
                idx = addVertex(i + 1, j + 1, vertices, data, idx);
                idx = addVertex(i + 1, j, vertices, data, idx);
            }
        }
        terrain.setVertices(vertices);

        data.dispose();

        // Create uniform buffer for global uniforms, e.g. projection matrix, model matrix
        uniformBufferSize = 2 * 16 * Float.BYTES;
        uniformBuffer = new WebGPUUniformBuffer(uniformBufferSize, WGPUBufferUsage.CopyDst.or(WGPUBufferUsage.Uniform));
        initBinder();

        // bind texture array
        binder.setTexture("diffuseTexture", textureArray.getTextureView());
        binder.setSampler("diffuseSampler", textureArray.getSampler());

        // create a render pipeline with the given shader code
        PipelineSpecification pipelineSpec = new PipelineSpecification(vattr, getShaderSource());
        pipelineSpec.name = "pipeline";
        pipeline = new WebGPUPipeline(binder.getPipelineLayout("pipeline layout"), pipelineSpec);

    }

    Color tmpColor = new Color();

    private int addVertex(int i, int j, float[] vertsOut, Pixmap heightmap, int idx) {
        int pixel = heightmap.getPixel((int) (i / 100f * heightmap.getWidth()),
                (int) (j / 100f * heightmap.getHeight()));
        tmpColor.set(pixel);
        // position
        vertsOut[idx++] = i / 5f;
        vertsOut[idx++] = tmpColor.r * 25f / 5f;
        vertsOut[idx++] = j / 5f;

        // texture coord (3d)
        vertsOut[idx++] = i / 20f;
        vertsOut[idx++] = j / 20f;
        float h = (tmpColor.r * 3f) - 0.5f;
        vertsOut[idx++] = h;
        return idx;
    }

    private void initBinder() {

        binder = new Binder();
        // define groups
        binder.defineGroup(0, createBindGroupLayout(uniformBufferSize));

        // define bindings in the groups
        // must match with shader code
        binder.defineBinding("uniforms", 0, 0);
        binder.defineBinding("diffuseTexture", 0, 1);
        binder.defineBinding("diffuseSampler", 0, 2);

        // define uniforms in uniform buffers with their offset
        // frame uniforms
        int offset = 0;
        binder.defineUniform("projectionViewTransform", 0, 0, offset);
        offset += 16 * 4;
        binder.defineUniform("modelTransform", 0, 0, offset);
        offset += 16 * 4;

        // set binding 0 to uniform buffer
        binder.setBuffer("uniforms", uniformBuffer, 0, uniformBufferSize);

    }

    private WebGPUBindGroupLayout createBindGroupLayout(int uniformBufferSize) {
        WebGPUBindGroupLayout layout = new WebGPUBindGroupLayout("bind group layout (frame)");
        layout.begin();
        layout.addBuffer(0, WGPUShaderStage.Vertex.or(WGPUShaderStage.Fragment), WGPUBufferBindingType.Uniform,
                uniformBufferSize, false);
        layout.addTexture(1, WGPUShaderStage.Fragment, WGPUTextureSampleType.Float, WGPUTextureViewDimension._2DArray,
                false);
        layout.addSampler(2, WGPUShaderStage.Fragment, WGPUSamplerBindingType.Filtering);
        layout.end();
        return layout;
    }

    @Override
    public void render() {

        modelView.translate(10f, 0, 10f).rotate(0, 1f, 0, 2f * Gdx.graphics.getDeltaTime()).translate(-10f, 0, -10f);

        cameraController.update();
        binder.setUniform("projectionViewTransform", camera.combined);
        binder.setUniform("modelTransform", modelView);
        uniformBuffer.flush();

        WgScreenUtils.clear(Color.BLACK, true);

        modelView.translate(10f, 0, 10f).rotate(0, 1f, 0, 2f * Gdx.graphics.getDeltaTime()).translate(-10f, 0, -10f);

        cameraController.update();

        // create a render pass
        WebGPURenderPass pass = RenderPassBuilder.create("Test Mesh", Color.BLACK);

        pass.setPipeline(pipeline);
        binder.bindGroup(pass, 0);

        terrain.render(pass, GL20.GL_TRIANGLES, 0, terrain.getNumVertices(), 1, 0);

        // end the render pass
        pass.end();
    }

    @Override
    public void dispose() {
        terrain.dispose();
        pipeline.dispose();
        uniformBuffer.dispose();
    }

    private String getShaderSource() {
        return Gdx.files.internal("data/wgsl/texture-array.wgsl").readString();
    }

}

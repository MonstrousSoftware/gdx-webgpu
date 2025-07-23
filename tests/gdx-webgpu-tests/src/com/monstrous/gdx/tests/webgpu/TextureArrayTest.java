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

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g3d.utils.FirstPersonCameraController;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.profiling.GLProfiler;
import com.badlogic.gdx.math.Matrix4;
import com.github.xpenatan.webgpu.WGPUPipelineLayout;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.WgTextureArray;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.wrappers.PipelineSpecification;
import com.monstrous.gdx.webgpu.wrappers.RenderPassBuilder;
import com.monstrous.gdx.webgpu.wrappers.WebGPUPipeline;
import com.monstrous.gdx.webgpu.wrappers.WebGPURenderPass;


// TO BE CONVERTED

/** @author Tomski **/
public class TextureArrayTest extends GdxTest {

	WgTextureArray textureArray;
	WgMesh terrain;

	ShaderProgram shaderProgram;
    WebGPUPipeline pipeline;

	PerspectiveCamera camera;
	FirstPersonCameraController cameraController;

	Matrix4 modelView = new Matrix4();



	@Override
	public void create () {

		String[] texPaths = new String[] {"data/g3d/materials/Searing Gorge.jpg", "data/g3d/materials/Lava Cracks.jpg",
			"data/g3d/materials/Deep Fire.jpg"};

		camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		camera.position.set(8, 10f, 20f);
		camera.lookAt(10, 0, 10);
		camera.up.set(0, 1, 0);
		camera.update();
		cameraController = new FirstPersonCameraController(camera);
		Gdx.input.setInputProcessor(cameraController);

		FileHandle[] texFiles = new FileHandle[texPaths.length];
		for (int i = 0; i < texPaths.length; i++) {
			texFiles[i] = Gdx.files.internal(texPaths[i]);
		}

		textureArray = new WgTextureArray(true, texFiles);
//		textureArray.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
//		textureArray.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear);
//		shaderProgram = new ShaderProgram(Gdx.files.internal("data/shaders/texturearray.vert"),
//			Gdx.files.internal("data/shaders/texturearray.frag"));
//		System.out.println(shaderProgram.getLog());

		int vertexStride = 6;
		int vertexCount = 100 * 100;
		terrain = new WgMesh(false, vertexCount * 6, 0, new VertexAttributes(VertexAttribute.Position(),
			new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 3, ShaderProgram.TEXCOORD_ATTRIBUTE + 0)));

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

        // create a render pipeline with the given shader code
        VertexAttributes vattr = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.TexCoords(0));
        PipelineSpecification pipelineSpec = new PipelineSpecification(vattr, getShaderSource());
        pipelineSpec.name = "pipeline";
        pipeline = new WebGPUPipeline((WGPUPipelineLayout) null, pipelineSpec);
	}

	Color tmpColor = new Color();

	private int addVertex (int i, int j, float[] vertsOut, Pixmap heightmap, int idx) {
		int pixel = heightmap.getPixel((int)(i / 100f * heightmap.getWidth()), (int)(j / 100f * heightmap.getHeight()));
		tmpColor.set(pixel);
		vertsOut[idx++] = i / 5f;
		vertsOut[idx++] = tmpColor.r * 25f / 5f;
		vertsOut[idx++] = j / 5f;
		vertsOut[idx++] = i / 20f;
		vertsOut[idx++] = j / 20f;
		vertsOut[idx++] = (tmpColor.r * 3f) - 0.5f;
		return idx;
	}

	@Override
	public void render () {
//		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
//		Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
//		Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
//		Gdx.gl.glCullFace(GL20.GL_BACK);

        WgScreenUtils.clear(Color.BLACK, true);

		modelView.translate(10f, 0, 10f).rotate(0, 1f, 0, 2f * Gdx.graphics.getDeltaTime()).translate(-10f, 0, -10f);

		cameraController.update();

        // create a render pass
        WebGPURenderPass pass = RenderPassBuilder.create( "Test Mesh", Color.SKY );

        pass.setPipeline(pipeline);

        // to render the whole mesh (a rectangle)
        //mesh.render(pass, GL20.GL_TRIANGLES, 0, mesh.getNumIndices(), 1, 0);

        // to render a mesh
        //public void render (WebGPURenderPass renderPass, int primitiveType, int offset, int size, int numInstances, int firstInstance)
        int size = terrain.getNumVertices();
        terrain.render(pass, GL20.GL_TRIANGLES, 0, size, 1, 0);

        // end the render pass
        pass.end();

		//textureArray.bind();

//		shaderProgram.bind();
//		shaderProgram.setUniformi("u_textureArray", 0);
//		shaderProgram.setUniformMatrix("u_projViewTrans", camera.combined);
//		shaderProgram.setUniformMatrix("u_modelView", modelView);


//		terrain.render(shaderProgram, GL20.GL_TRIANGLES);
	}

	@Override
	public void dispose () {
		terrain.dispose();
		shaderProgram.dispose();
	}

    private String getShaderSource() {
        return "\n" +
            "\n" +
            "\n" +
            "struct VertexInput {\n" +
            "    @location(0) position: vec3f,\n" +
            "    @location(1) uv: vec2f,\n" +

            "};\n" +
            "\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position: vec4f,\n" +
            "    @location(0) uv : vec2f,\n" +
            "};\n" +
            "\n" +
            "\n" +
            "@vertex\n" +
            "fn vs_main(in: VertexInput) -> VertexOutput {\n" +
            "   var out: VertexOutput;\n" +
            "\n" +
            "   var pos =  vec4f(in.position,  1.0);\n" +
            "   out.position = pos;\n" +
            "   out.uv = vec2f(0,0);\n" +
            "\n" +
            "   return out;\n" +
            "}\n" +
            "\n" +
            "@fragment\n" +
            "fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" +
            "\n" +
            "    let color = vec4f(1,1,1,1);\n" +
            "    return vec4f(color);\n" +
            "}";
    }

}

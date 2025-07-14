/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.graphics.utils.WgMeshBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** Test WebGPUModelBatch with single Renderables.
 * */
public class ModelBatchTest extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
	PerspectiveCamController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	WgMesh mesh;
	Renderable renderable;
	Renderable renderable2;

	public void create () {
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 0, 2);
		cam.near = 0.1f;
        cam.far = 30f;


		controller = new PerspectiveCamController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

		//
		// Create some renderables
		//

		WgTexture texture1 = new WgTexture(Gdx.files.internal("data/planet_earth.png"), true);
		texture1.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
		Material mat1 = new Material(TextureAttribute.createDiffuse(texture1));
		WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
		texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
		Material mat2 = new Material(TextureAttribute.createDiffuse(texture2));


		final WgMeshPart meshPart = createMeshPart();
		renderable = new Renderable();
		renderable.meshPart.set(meshPart);
		renderable.worldTransform.idt().trn(0,-2,-3);
		renderable.material = mat1;

		renderable2 = new Renderable();
		renderable2.meshPart.set(meshPart);
		renderable2.worldTransform.idt().trn(0,0,-1);
		renderable2.material = mat2;

	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		renderable.worldTransform.rotate(Vector3.Y, delta*15f);
		renderable2.worldTransform.rotate(Vector3.Y, -delta*15f);

		WgScreenUtils.clear(Color.TEAL, true);

		cam.update();
		modelBatch.begin(cam);

		modelBatch.render(renderable2);

		modelBatch.render(renderable);

		modelBatch.end();


		batch.begin();
		font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 0, 20);
		batch.end();
	}

	@Override
	public void resize(int width, int height) {
		cam.viewportWidth = width;
		cam.viewportHeight = height;
		cam.update();

	}

	@Override
	public void dispose () {
		batch.dispose();
		font.dispose();
		modelBatch.dispose();
		mesh.dispose();

	}

	public WgMeshPart createMeshPart() {
		WgMeshBuilder mb = new WgMeshBuilder();

		VertexAttributes attr = WgMeshBuilder.createAttributes(VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates| VertexAttributes.Usage.ColorUnpacked);

		mb.begin(attr);

		WgMeshPart part = mb.part("block", GL20.GL_TRIANGLES);
		// rotate unit cube by 90 degrees to get the textures the right way up.
		Matrix4 transform = new Matrix4().rotate(Vector3.Z, 90);
		BoxShapeBuilder.build(mb, transform);	// create unit cube
		mesh = mb.end();	// keep this for disposal

		return part;
	}

	public WgMeshPart createMeshPartOri() {
		VertexAttributes vattr = new VertexAttributes(VertexAttribute.Position(),  VertexAttribute.TexCoords(0), VertexAttribute.ColorUnpacked());

		mesh = new WgMesh(true, 8, 12, vattr);
		mesh.setVertices(new float[]{
				-0.5f, -0.5f, 0.5f, 	0, 1, 	1,0,1,1,
				0.5f, -0.5f, 0.5f, 	1,1,	0,1,1,1,
				0.5f, 0.5f, 0.5f, 		1,0,	1,1,0,1,
				-0.5f, 0.5f, 0.5f, 	0,0,	0,1,0,1,

				-0.5f, -0.5f, -0.5f, 	0, 1, 	1,0,1,1,
				0.5f, -0.5f, -0.5f, 	1,1,	0,1,1,1,
				0.5f, 0.5f, -0.5f, 		1,0,	1,1,0,1,
				-0.5f, 0.5f, -0.5f, 	0,0,	0,1,0,1,
		});

		mesh.setIndices(new short[] {0, 1, 2, 	2, 3, 0, 	4, 5, 6,  6, 7, 4});

		int offset = 0;	// offset in the indices array, since the mesh is indexed
		int size = 12;	// nr of indices, since the mesh is indexed
		int type = GL20.GL_TRIANGLES;	// primitive type using GL constant
		return new WgMeshPart("part", mesh, offset, size, type);
	}
}

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
import com.badlogic.gdx.graphics.g3d.RenderableProvider;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BoxShapeBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.tests.webgpu.utils.PerspectiveCamController;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.Pool;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.graphics.utils.WgMeshBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** Test use of different shaders due to differing vertex attributes */


public class ModelBatchShadersTest extends GdxTest {

	WgModelBatch modelBatch;
	PerspectiveCamera cam;
	PerspectiveCamController controller;
	WgSpriteBatch batch;
	WgBitmapFont font;
	MyRenderableProvider renderableProvider;


	// application
	public void create () {
		modelBatch = new WgModelBatch();
		cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.position.set(0, 0, 2);
		cam.near = 0.1f;


		controller = new PerspectiveCamController(cam);
		Gdx.input.setInputProcessor(controller);
		batch = new WgSpriteBatch();
		font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

		renderableProvider = new MyRenderableProvider();

	}

	public void render () {
		float delta = Gdx.graphics.getDeltaTime();
		renderableProvider.update(delta);


		WgScreenUtils.clear(Color.TEAL, true);

		cam.update();
		modelBatch.begin(cam);

		modelBatch.render(renderableProvider);

		//modelBatch.render(renderable);

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
		renderableProvider.dispose();
	}



	/** artificial implementation of a renderable provider just for testing */
	public static class MyRenderableProvider implements RenderableProvider, Disposable {
		final WgMeshPart meshPart1, meshPart2, meshPart3;
		final Material mat1, mat2, mat3;
		float angle;


		public MyRenderableProvider() {
			//
			// Create some renderables
			//

			WgTexture texture2 = new WgTexture(Gdx.files.internal("data/badlogic.jpg"), true);
			texture2.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
			mat1 = new Material(TextureAttribute.createDiffuse(texture2));

			WgTexture texture1 = new WgTexture(Gdx.files.internal("data/planet_earth.png"), true);
			texture1.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
			mat2 = new Material(TextureAttribute.createDiffuse(texture1));
			mat2.set(ColorAttribute.createDiffuse(Color.GREEN));

			mat3 = new Material(ColorAttribute.createDiffuse(Color.ORANGE),TextureAttribute.createDiffuse(texture1));

			VertexAttributes attr1 = WgMeshBuilder.createAttributes(VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
			meshPart1 = createMeshPart(attr1);

			VertexAttributes attr2 = WgMeshBuilder.createAttributes(VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
			meshPart2 = createMeshPart(attr2);

			VertexAttributes attr3 = WgMeshBuilder.createAttributes(VertexAttributes.Usage.Position | VertexAttributes.Usage.TextureCoordinates);
			meshPart3 = createMeshPart(attr3);
		}

		public void update(float deltaTime){
			angle += 15f*deltaTime;
		}

		@Override
		public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool) {
			Renderable renderable = pool.obtain();
			renderable.meshPart.set(meshPart1);
			renderable.worldTransform.idt().trn(0,-2,-3).rotate(Vector3.Y, angle);
			renderable.material = mat1;
			renderables.add(renderable);

			renderable = pool.obtain();
			renderable.meshPart.set(meshPart2);
			renderable.worldTransform.idt().trn(0,0,-1).rotate(Vector3.Y, -angle);
			renderable.material = mat2;
			renderables.add(renderable);

			renderable = pool.obtain();
			renderable.meshPart.set(meshPart3);
			renderable.worldTransform.idt().scl(0.5f).trn(0,1.5f,-1).rotate(Vector3.Y, angle);
			renderable.material = mat3;
			renderables.add(renderable);
		}

		private WgMeshPart createMeshPart(VertexAttributes attr) {
			WgMeshBuilder mb = new WgMeshBuilder();

			mb.begin(attr);

			WgMeshPart part = mb.part("block", GL20.GL_TRIANGLES);
			// rotate unit cube by 90 degrees to get the textures the right way up.
			Matrix4 transform = new Matrix4().rotate(Vector3.Z, 90);
			BoxShapeBuilder.build(mb, transform);	// create unit cube
			mb.end();	// keep this for disposal

			return part;
		}

		@Override
		public void dispose() {

			meshPart1.mesh.dispose();
			meshPart2.mesh.dispose();
		}
	}
}

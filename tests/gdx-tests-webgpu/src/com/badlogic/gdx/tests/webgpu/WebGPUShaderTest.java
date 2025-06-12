
package com.badlogic.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplicationConfiguration;
import com.badlogic.gdx.webgpu.graphics.WebGPUShaderProgram;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUBitmapFont;
import com.badlogic.gdx.webgpu.graphics.g2d.WebGPUSpriteBatch;
import com.badlogic.gdx.webgpu.wrappers.WebGPUTexture;

/** Demonstrates the use of ShaderProgram in combination with SpriteBatch, using a shader coded in WGSL shading language.
*/
public class WebGPUShaderTest {

	// launcher
	public static void main (String[] argv) {

		WebGPUApplicationConfiguration config = new WebGPUApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WebGPUApplication(new TestApp(), config);
	}

	// application
	static class TestApp extends ApplicationAdapter {
		private WebGPUSpriteBatch batch;
		private WebGPUTexture texture;
		private WebGPUTexture texture2;
		private WebGPUBitmapFont font;
		private WebGPUShaderProgram shaderProgram;


		public void create () {

			// alternative shader program to be used instead of the default one.
			shaderProgram = new WebGPUShaderProgram("my shader", getShaderSource(), "");

			batch = new WebGPUSpriteBatch();


			texture = new WebGPUTexture(Gdx.files.internal("data/badlogic.jpg"));
			texture2 = new WebGPUTexture(Gdx.files.internal("data/tile.png"));

			font = new WebGPUBitmapFont();
		}

		@Override
		public void render () {



			batch.begin(Color.FOREST);
			// use special shader
			batch.setShader(shaderProgram);
			batch.draw(texture, 50, 100);
			batch.draw(texture2, 50, 400);

			// revert to default shader
			batch.setShader((WebGPUShaderProgram) null);
			batch.draw(texture, 370, 100);
			batch.draw(texture2, 370, 400);

			batch.end();
		}


		@Override
		public void resize (int width, int height) {
			Gdx.app.log("resize", "");
			batch.getProjectionMatrix().setToOrtho2D(0,0,Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		}

		@Override
		public void dispose () {
			batch.dispose();
			texture.dispose();
			font.dispose();
		}

		/* shader source must be compatible with the default one with regard to bindings and vertex attributes

		 */
		private String getShaderSource() {
			return "\n" +
					"struct Uniforms {\n" +
					"    projectionMatrix: mat4x4f,\n" +
					"};\n" +
					"\n" +
					"@group(0) @binding(0) var<uniform> uniforms: Uniforms;\n" +
					"@group(0) @binding(1) var texture: texture_2d<f32>;\n" +
					"@group(0) @binding(2) var textureSampler: sampler;\n" +
					"\n" +
					"\n" +
					"struct VertexInput {\n" +
					"    @location(0) position: vec2f,\n" +
					"    @location(1) uv: vec2f,\n" +
					"    @location(5) color: vec4f,\n" +
					"};\n" +
					"\n" +
					"struct VertexOutput {\n" +
					"    @builtin(position) position: vec4f,\n" +
					"    @location(0) uv : vec2f,\n" +
					"    @location(1) color: vec4f,\n" +
					"};\n" +
					"\n" +
					"\n" +
					"@vertex\n" +
					"fn vs_main(in: VertexInput) -> VertexOutput {\n" +
					"   var out: VertexOutput;\n" +
					"\n" +
					"   var pos =  uniforms.projectionMatrix * vec4f(in.position, 0.0, 1.0);\n" +
					"   out.position = pos;\n" +
					"   out.uv = in.uv;\n" +
					"\n" +
					"   let color:vec4f = in.color;\n" +
					"   out.color = color;\n" +
					"\n" +
					"   return out;\n" +
					"}\n" +
					"\n" +
					"@fragment\n" +
					"fn fs_main(in : VertexOutput) -> @location(0) vec4f {\n" +
					"\n" +
					"    var color = in.color * textureSample(texture, textureSampler, in.uv);\n" +
					"    color.r = 0.5;"+
					"    color.b = 1.0 - color.b;"+
					"    return vec4f(color);\n" +
					"}";
		}
	}
}


package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/** Demonstrates the use of ShaderProgram in combination with SpriteBatch, using a shader coded in WGSL shading language.
*/
public class WebGPUShaderTest {

	// launcher
	public static void main (String[] argv) {

		WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
		config.setWindowedMode(640, 480);
		config.setTitle("WebGPUTest");

		new WgDesktopApplication(new TestApp(), config);
	}

	// application
	static class TestApp extends ApplicationAdapter {
		private WgSpriteBatch batch;
		private WgTexture texture;
		private WgTexture texture2;
		private WgBitmapFont font;
		private WgShaderProgram shaderProgram;


		public void create () {

			// alternative shader program to be used instead of the default one.
			shaderProgram = new WgShaderProgram("my shader", getShaderSource(), "");

			batch = new WgSpriteBatch();


			texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));
			texture2 = new WgTexture(Gdx.files.internal("data/tile.png"));

			font = new WgBitmapFont();
		}

		@Override
		public void render () {



			batch.begin(Color.FOREST);
			// use special shader
			batch.setShader(shaderProgram);
			batch.draw(texture, 50, 100);
			batch.draw(texture2, 50, 400);

			// revert to default shader
			batch.setShader((WgShaderProgram) null);
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

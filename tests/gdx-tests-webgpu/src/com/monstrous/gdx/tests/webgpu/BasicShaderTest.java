package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.shaders.DefaultShader;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.RenderContext;
import com.badlogic.gdx.utils.ScreenUtils;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgApplicationConfiguration;
import com.monstrous.gdx.webgpu.graphics.WgMesh;
import com.monstrous.gdx.webgpu.graphics.g3d.model.WgMeshPart;
import com.monstrous.gdx.webgpu.graphics.utils.WgMeshBuilder;

/* Shader test based on Xoppa tutorial
    todo requires to load a Model
 */


public class BasicShaderTest  implements ApplicationListener {
    public PerspectiveCamera cam;
    public CameraInputController camController;
    public Shader shader;
    public RenderContext renderContext;
    public Model model;
    public Environment environment;
    public Renderable renderable;
    public ModelInstance instance;

    public static void main (String[] argv) {

        WgApplicationConfiguration config = new WgApplicationConfiguration();
        config.setWindowedMode(640, 480);
        config.setTitle("WebGPUTest");
        //config.backend = WGPUBackendType.D3D12;
        config.enableGPUtiming = false;

        new WgApplication(new BasicShaderTest(), config);
    }


    @Override
    public void create () {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(2f, 2f, 2f);
        cam.lookAt(0,0,0);
        cam.near = 1f;
        cam.far = 300f;
        cam.update();

        camController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(camController);

        VertexAttributes vertAttribs = new VertexAttributes(VertexAttribute.Position(), VertexAttribute.ColorPacked(), VertexAttribute.TexCoords(0), VertexAttribute.Normal());
        WgMeshBuilder builder = new WgMeshBuilder();
        builder.begin(vertAttribs);
        WgMeshPart part = builder.part("box", GL20.GL_TRIANGLES);
        builder.box(1, 1,1);
        WgMesh mesh = builder.end();

        Material mat = new Material(ColorAttribute.createDiffuse(Color.GREEN));

        renderable = new Renderable();

        renderable.material = mat;
        renderable.meshPart.set(part);
        renderable.bones = null;
        renderable.environment = environment;
        renderable.worldTransform.idt();

        renderContext = null; //new RenderContext(new DefaultTextureBinder(DefaultTextureBinder.ROUNDROBIN, 1));
        shader = new DefaultShader(renderable);
        shader.init();
    }

    @Override
    public void render () {
        camController.update();

        ScreenUtils.clear(Color.TEAL);

        renderContext.begin();
        shader.begin(cam, renderContext);
        shader.render(renderable);
        shader.end();
        renderContext.end();
    }

    @Override
    public void dispose () {
        shader.dispose();
        model.dispose();
    }

    @Override public void resume () {}
    @Override public void resize (int width, int height) {}
    @Override public void pause () {}
}

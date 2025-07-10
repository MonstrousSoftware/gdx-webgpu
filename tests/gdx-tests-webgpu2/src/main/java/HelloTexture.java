package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.wrappers.*;

// Render a shape using a vertex buffer
//


// todo CRASHES on indexBuffer.flip()

public class HelloTexture extends ApplicationAdapter {

    private Texture texture;
    private WgSpriteBatch batch;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloTexture());
    }

    @Override
    public void create() {
        texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));
        batch = new WgSpriteBatch();

    }

    @Override
    public void render() {
        batch.begin();
        batch.draw(texture, 0, 0);
        batch.draw(texture, 300, 100);
        batch.end();

    }

    @Override
    public void resize(int width, int height) {
        Gdx.app.log("resize", "");
    }

    @Override
    public void dispose(){
        batch.dispose();
        texture.dispose();
    }

}

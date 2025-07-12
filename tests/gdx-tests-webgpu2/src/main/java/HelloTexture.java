package main.java;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;


// Render a shape using a vertex buffer
//


// todo CRASHES on indexBuffer.flip()

public class HelloTexture extends ApplicationAdapter {

    private Texture texture;
    private Texture texture2;
    private WgSpriteBatch batch;

    public static void main(String[] argv) {
        new WgDesktopApplication(new HelloTexture());
    }

    @Override
    public void create() {
        texture = new WgTexture(Gdx.files.internal("data/badlogic.jpg"));
        texture2 = new WgTexture(Gdx.files.internal("data/stones.jpg"));
        batch = new WgSpriteBatch();

    }

    @Override
    public void render() {
        batch.begin();
        batch.draw(texture, 0, 0);
        batch.draw(texture2, 300, 100);
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
        texture2.dispose();
    }

}

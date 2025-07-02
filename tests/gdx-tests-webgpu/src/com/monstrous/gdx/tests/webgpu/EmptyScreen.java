
package com.monstrous.gdx.tests.webgpu;



import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.backends.lwjgl3.WgDesktopApplication;



public class EmptyScreen extends GdxTest {

    public static void main (String[] argv) {
        new WgDesktopApplication(new EmptyScreen());
    }

	@Override
	public void render () {
        // do nothing

	}



}

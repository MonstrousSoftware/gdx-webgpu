
package main.java;


import com.badlogic.gdx.ApplicationAdapter;
import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;

// demonstrates the use of WebGPUScreenUtils
//
public class ClearScreen extends ApplicationAdapter {

    public static void main (String[] argv) {

        new WgDesktopApplication(new ClearScreen());
    }

	@Override
	public void render () {

        //WgScreenUtils.clear(Color.CORAL);	// use ScreenUtils variant to clear the screen
	}

}

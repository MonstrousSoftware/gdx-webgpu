
package com.badlogic.gdx.tests.webgpu;


import com.badlogic.gdx.graphics.Color;

import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WgApplication;
import com.badlogic.gdx.webgpu.graphics.utils.WgScreenUtils;

// demonstrates the use of WebGPUScreenUtils
//
public class ClearScreen extends GdxTest {

    public static void main (String[] argv) {
        new WgApplication(new ClearScreen());
    }

	@Override
	public void render () {
		WgScreenUtils.clear(Color.CORAL);	// use ScreenUtils variant to clear the screen
	}



}


package com.badlogic.gdx.tests.webgpu;


import com.badlogic.gdx.graphics.Color;

import com.badlogic.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.webgpu.backends.lwjgl3.WebGPUApplication;
import com.badlogic.gdx.webgpu.graphics.utils.WebGPUScreenUtils;

// demonstrates the use of WebGPUScreenUtils
//
public class ClearScreen extends GdxTest {

    public static void main (String[] argv) {
        new WebGPUApplication(new ClearScreen());
    }

	@Override
	public void render () {
		WebGPUScreenUtils.clear(Color.CORAL);	// use ScreenUtils variant to clear the screen
	}



}

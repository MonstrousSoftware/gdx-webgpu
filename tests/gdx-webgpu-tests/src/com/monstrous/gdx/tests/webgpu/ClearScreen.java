
package com.monstrous.gdx.tests.webgpu;


import com.badlogic.gdx.graphics.Color;

import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

// demonstrates the use of WebGPUScreenUtils
//
public class ClearScreen extends GdxTest {


	@Override
	public void render () {

        WgScreenUtils.clear(Color.CORAL);	// use ScreenUtils variant to clear the screen
	}

}

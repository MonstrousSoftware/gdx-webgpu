/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.webgpu.graphics.viewport;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Version of Viewport for WebGPU. Avoids the GL call in apply(). */
public abstract class WebGPUViewport extends Viewport {

	/** Applies the viewport to the camera and sets the glViewport.
	 * @param centerCamera If true, the camera position is set to the center of the world. */
	@Override
	public void apply (boolean centerCamera) {
		// overrides Viewport#apply to avoid the call of HdpiUtils.glViewport()
		Camera camera = getCamera();
		camera.viewportWidth = getWorldWidth();
		camera.viewportHeight = getWorldHeight();
		if (centerCamera) camera.position.set(getWorldWidth() / 2, getWorldHeight() / 2, 0);
		camera.update();
	}
}

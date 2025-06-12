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
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Scaling;

/** A viewport that scales the world using {@link Scaling}.
 * <p>
 * {@link Scaling#fit} keeps the aspect ratio by scaling the world up to fit the screen, adding black bars (letterboxing) for the
 * remaining space.
 * <p>
 * {@link Scaling#fill} keeps the aspect ratio by scaling the world up to take the whole screen (some of the world may be off
 * screen).
 * <p>
 * {@link Scaling#stretch} does not keep the aspect ratio, the world is scaled to take the whole screen.
 * <p>
 * {@link Scaling#none} keeps the aspect ratio by using a fixed size world (the world may not fill the screen or some of the world
 * may be off screen).
 * @author Daniel Holderbaum
 * @author Nathan Sweet */
public class WebGPUScalingViewport extends WebGPUViewport {
	private Scaling scaling;

	/** Creates a new viewport using a new {@link OrthographicCamera}. */
	public WebGPUScalingViewport(Scaling scaling, float worldWidth, float worldHeight) {
		this(scaling, worldWidth, worldHeight, new OrthographicCamera());
		// adapt camera near/far for WebGPU clip space
		getCamera().far = -1;
		getCamera().near = 1;
	}

	public WebGPUScalingViewport(Scaling scaling, float worldWidth, float worldHeight, Camera camera) {
		this.scaling = scaling;
		setWorldSize(worldWidth, worldHeight);
		setCamera(camera);
	}

	public void update (int screenWidth, int screenHeight, boolean centerCamera) {
		Vector2 scaled = scaling.apply(getWorldWidth(), getWorldHeight(), screenWidth, screenHeight);
		int viewportWidth = Math.round(scaled.x);
		int viewportHeight = Math.round(scaled.y);

		// Center.
		setScreenBounds((screenWidth - viewportWidth) / 2, (screenHeight - viewportHeight) / 2, viewportWidth, viewportHeight);



		apply(centerCamera);
	}

	public Scaling getScaling () {
		return scaling;
	}

	public void setScaling (Scaling scaling) {
		this.scaling = scaling;
	}
}

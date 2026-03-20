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

package com.monstrous.gdx.webgpu.graphics.g3d.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Vector3;

/**
 * A first-person fly-mode camera controller.
 * <p>
 * Movement: W/S = forward/back, A/D = strafe left/right, E/Q = up/down.
 * Look: hold right mouse button and drag to rotate the camera.
 * Speed: scroll wheel adjusts movement speed; hold Shift to move faster.
 * </p>
 */
public class FirstPersonCameraController extends InputAdapter {

    private final Camera camera;
    private final Vector3 tmp = new Vector3();

    /** Base movement speed in units per second. */
    public float moveSpeed = 5f;
    /** Multiplier applied while Shift is held. */
    public float shiftMultiplier = 3f;
    /** Mouse look sensitivity (degrees per pixel). */
    public float lookSensitivity = 0.15f;
    /** Minimum scroll speed. */
    public float minSpeed = 0.5f;
    /** Maximum scroll speed. */
    public float maxSpeed = 100f;

    private boolean enabled = true;
    private boolean rightButtonDown = false;
    private int lastScreenX, lastScreenY;

    // Accumulated yaw / pitch so we can clamp pitch
    private float yaw;
    private float pitch;
    private boolean anglesInitialized = false;

    public FirstPersonCameraController(Camera camera) {
        this.camera = camera;
    }

    /**
     * Enable or disable this controller. When disabled, {@link #update()} is a no-op
     * and all input events are ignored. The drag state is also reset on disable so no
     * stale mouse position causes a snap when the controller is re-enabled.
     */
    public void setEnabled(boolean enabled) {
        if (!enabled && this.enabled) {
            rightButtonDown = false;
        }
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @deprecated Use {@link #setEnabled(boolean)} instead.
     */
    @Deprecated
    public void resetDragState() {
        rightButtonDown = false;
    }

    /**
     * Call every frame (typically at the start of render()) to process
     * continuous keyboard movement.
     */
    public void update() {
        update(Gdx.graphics.getDeltaTime());
    }

    /**
     * Process continuous keyboard movement for the given delta time.
     */
    public void update(float deltaTime) {
        if (!enabled) return;
        float speed = moveSpeed * deltaTime;
        if (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) {
            speed *= shiftMultiplier;
        }

        // Forward / backward along camera direction
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            tmp.set(camera.direction).nor().scl(speed);
            camera.position.add(tmp);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            tmp.set(camera.direction).nor().scl(-speed);
            camera.position.add(tmp);
        }

        // Strafe left / right
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            tmp.set(camera.direction).crs(camera.up).nor().scl(-speed);
            camera.position.add(tmp);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            tmp.set(camera.direction).crs(camera.up).nor().scl(speed);
            camera.position.add(tmp);
        }

        // Up / down (world Y axis)
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            camera.position.y += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            camera.position.y -= speed;
        }

        camera.update();
    }

    // ---- Mouse look (right-button drag) ----

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!enabled) return false;
        if (button == Input.Buttons.RIGHT) {
            rightButtonDown = true;
            lastScreenX = screenX;
            lastScreenY = screenY;
            initAnglesIfNeeded();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (!enabled) return false;
        if (button == Input.Buttons.RIGHT) {
            rightButtonDown = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (!enabled || !rightButtonDown) return false;

        float deltaX = (screenX - lastScreenX) * lookSensitivity;
        float deltaY = (screenY - lastScreenY) * lookSensitivity;
        lastScreenX = screenX;
        lastScreenY = screenY;

        yaw -= deltaX;
        pitch -= deltaY;
        // Clamp pitch to avoid flipping
        pitch = Math.max(-89f, Math.min(89f, pitch));

        applyRotation();
        return true;
    }

    // ---- Scroll to adjust speed ----

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (!enabled) return false;
        moveSpeed *= (1f - amountY * 0.1f);
        moveSpeed = Math.max(minSpeed, Math.min(maxSpeed, moveSpeed));
        return true;
    }

    // ---- Internals ----

    /**
     * Lazily initialise yaw/pitch from the current camera direction the first
     * time the user grabs the mouse.  This avoids a jarring snap on first drag.
     */
    private void initAnglesIfNeeded() {
        if (anglesInitialized) return;
        anglesInitialized = true;

        Vector3 dir = camera.direction;
        // pitch = asin(y)
        pitch = (float) Math.toDegrees(Math.asin(dir.y));
        // yaw = atan2(x, z)  (z is forward in libGDX default)
        yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
    }

    private void applyRotation() {
        float yawRad = (float) Math.toRadians(yaw);
        float pitchRad = (float) Math.toRadians(pitch);
        float cosPitch = (float) Math.cos(pitchRad);

        camera.direction.set(
                cosPitch * (float) Math.sin(yawRad),
                (float) Math.sin(pitchRad),
                cosPitch * (float) Math.cos(yawRad)
        ).nor();

        // Keep the up vector purely vertical
        camera.up.set(0, 1, 0);
        camera.update();
    }
}


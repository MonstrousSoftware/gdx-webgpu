/*******************************************************************************
 * Copyright 2025 Monstrous Software.
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

package com.monstrous.gdx.webgpu.wrappers;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUSampler;
import com.github.xpenatan.webgpu.WGPUTextureView;

/**
 * Cache/Pool for bind groups. Provides reusable WebGPUBindGroup instances to avoid creating new Java objects and native
 * WGPUBindGroupEntry instances every frame.
 * <p>
 * This implements a simple pooling strategy: bind groups are created on demand and kept in a pool. They can be
 * reconfigured and reused for different bindings within the same layout.
 * <p>
 * Usage:
 * 
 * <pre>
 * BindGroupCache cache = new BindGroupCache();
 * // Get a bind group (either new or from pool)
 * WebGPUBindGroup bg = cache.getBindGroup(layout, buffer, offset, size, textureView, sampler);
 * // Use bg...
 * // Bind groups are automatically returned to the pool when getting a new one
 * </pre>
 */
public class BindGroupCache implements Disposable {
    private final Array<WebGPUBindGroup> pool;
    private WebGPUBindGroup currentBindGroup;

    public BindGroupCache() {
        pool = new Array<>();
        currentBindGroup = null;
    }

    /**
     * Get a bind group and configure it with the given bindings. Reuses a pooled bind group if available, creating a
     * new one if needed.
     *
     * @param layout the bind group layout
     * @param buffer the buffer to bind (or null)
     * @param bufferOffset offset into the buffer
     * @param bufferSize size of the buffer binding
     * @param textureView texture to bind (or null)
     * @param sampler sampler to bind (or null)
     * @return a configured WebGPUBindGroup, ready to use
     */
    public WebGPUBindGroup getBindGroup(WebGPUBindGroupLayout layout, WebGPUBuffer buffer, int bufferOffset,
            int bufferSize, WGPUTextureView textureView, WGPUSampler sampler) {
        // Get a bind group from the pool or create a new one
        WebGPUBindGroup bg = obtainBindGroup(layout);

        // Configure it with the new bindings
        bg.begin();

        if (buffer != null) {
            bg.setBuffer(0, buffer, bufferOffset, bufferSize);
        }
        if (textureView != null) {
            bg.setTexture(1, textureView);
        }
        if (sampler != null) {
            bg.setSampler(2, sampler);
        }

        bg.end();

        currentBindGroup = bg;
        return bg;
    }

    /**
     * Get or create a bind group from the pool.
     */
    private WebGPUBindGroup obtainBindGroup(WebGPUBindGroupLayout layout) {
        // Try to find an available bind group in the pool
        if (!pool.isEmpty()) {
            WebGPUBindGroup bg = pool.pop();
            return bg;
        }

        // Create a new one
        return new WebGPUBindGroup(layout);
    }

    /**
     * Return a bind group to the pool for reuse.
     */
    private void returnBindGroup(WebGPUBindGroup bg) {
        bg.reset();
        pool.add(bg);
    }

    /**
     * Clear the pool and reset for next frame. Should be called at end of frame/render pass.
     */
    public void reset() {
        // Return current bind group to pool if any
        if (currentBindGroup != null) {
            returnBindGroup(currentBindGroup);
            currentBindGroup = null;
        }
        // Note: We don't clear the pool - those objects are kept for reuse
    }

    public void dispose() {
        // Dispose all bind groups in the pool
        for (WebGPUBindGroup bg : pool) {
            bg.dispose();
        }
        pool.clear();
        if (currentBindGroup != null) {
            currentBindGroup.dispose();
            currentBindGroup = null;
        }
    }
}

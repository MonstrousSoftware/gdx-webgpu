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
package com.monstrous.gdx.webgpu.graphics.g3d.environment;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.*;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgTexture;

/**
 * Cascaded Shadow Map (CSM) light source.
 * <p>
 * Splits the view frustum into {@code cascadeCount} sub-frustums, renders each cascade directly
 * into a per-layer depth view of a shared depth texture array, and exposes that array for
 * sampling in {@code modelbatch.wgsl}.
 * <p>
 * This class is fully self-contained — it uses the standard {@link WebGPUContext#pushTargetView}
 * mechanism for rendering and needs no modifications to core classes.
 * <p>
 * Typical usage:
 * <pre>
 *   WgCascadedShadowLight csmLight = new WgCascadedShadowLight(4, 1024, 1024);
 *   csmLight.setDirection(lightDir);
 *   environment.set(CascadedShadowAttribute.createShadow(csmLight));
 *
 *   // Per-frame shadow pass:
 *   csmLight.begin(cam);
 *   for (int i = 0; i &lt; csmLight.getCascadeCount(); i++) {
 *       csmLight.beginCascade(i);
 *       shadowBatch.begin(csmLight.getCascadeCamera(i), null, true, RenderPassType.DEPTH_ONLY);
 *       shadowBatch.render(instances);
 *       shadowBatch.end();
 *       csmLight.endCascade();
 *   }
 *   csmLight.end();
 *
 *   // Main scene render (unchanged):
 *   modelBatch.begin(cam, Color.TEAL, true);
 *   modelBatch.render(instances, environment);
 *   modelBatch.end();
 * </pre>
 */
public class WgCascadedShadowLight extends DirectionalLight implements Disposable {

    /** Maximum supported cascades (matches the vec4f cascadeSplits in the shader). */
    public static final int MAX_CASCADES = 4;

    /** Default split scheme blend factor (0 = linear, 1 = logarithmic). 0.75 is the
     *  Practical Split Scheme default from GPU Gems 3 — good for outdoor scenes. */
    public static final float DEFAULT_LAMBDA = 0.75f;

    /** Default shadow bias in world-space units. The clip-space bias per cascade is
     *  {@code max(baseBias, minTexelBias * texelSize) / cascadeDepthRange}, ensuring
     *  the bias is at least {@link #DEFAULT_MIN_TEXEL_BIAS} shadow-map texels wide.
     *  This prevents shadow acne when a cascade covers a large frustum (e.g. 1 cascade). */
    public static final float DEFAULT_BASE_BIAS = 0.3f;

    /** Minimum bias in shadow-map texels. When a cascade covers a large area, the
     *  texel size in world units can exceed {@link #DEFAULT_BASE_BIAS}, causing acne.
     *  The actual bias per cascade is {@code max(baseBias, minTexelBias * texelSizeWorld) / depthRange}. */
    public static final float DEFAULT_MIN_TEXEL_BIAS = 2.5f;

    // Remap OpenGL depth range [-1,1] to WebGPU [0,1]. Must match WgModelBatch.begin().
    private static final Matrix4 shiftDepthMatrix = new Matrix4().idt().scl(1, 1, 0.5f).trn(0, 0, 0.5f);

    private final int cascadeCount;
    private final int shadowMapSize;
    private float lambda;
    private float baseBias = DEFAULT_BASE_BIAS;
    private float minTexelBias = DEFAULT_MIN_TEXEL_BIAS;
    private boolean stabilize = true;
    private float maxShadowDistance;  // 0 = use camera far
    private final OrthographicCamera[] cameras;

    // Per-cascade bias overrides (world-space units). When set, overrides the auto-computed bias.
    private final float[] cascadeBiasOverrides;
    private final boolean[] hasCascadeBiasOverride;

    // GPU resources for shader sampling
    private final WgTexture depthArrayTexture;     // depth texture array — owns the underlying GPU texture
    private final WGPUTextureView arrayView;       // explicit 2DArray view for shader sampling
                                                   // (can't use depthArrayTexture.getTextureView() because
                                                   //  WgTexture creates a _2D default view when numLayers==1)

    // Per-layer 2D views for rendering directly into the array texture (needed by pushTargetView)
    private final WgTexture[] layerDepthTextures;
    // Shared dummy color texture for push/pop (depth-only pass ignores it)
    private final WgTexture dummyColorTexture;
    // Pre-allocated arrays for pushTargetView
    private final WGPUTextureView[] dummyTargetViews = new WGPUTextureView[1];
    private final WGPUTextureFormat[] dummyTargetFormats = new WGPUTextureFormat[1];
    // Per-cascade push/pop state
    private final WebGPUContext.RenderOutputState prevState = new WebGPUContext.RenderOutputState();

    // Per-frame data (pre-allocated, written in begin())
    private final Matrix4[] lightSpaceProjViews;  // pre-shifted PVT per cascade, ready for uniform upload
    private final float[] cascadeSplitNDC;        // NDC-z threshold per cascade split (length MAX_CASCADES)
    private final float[] cascadeBiases;           // per-cascade shadow bias (length MAX_CASCADES)
    private final float[] splitDistances;          // view-space distances: [near, split1, ..., far]
    /** Shifted projection-view of the CSM driver camera. Used by the shader for cascade selection. */
    private final Matrix4 csmCameraProjectionView = new Matrix4();

    private int currentCascade;

    // Scratch objects (pre-allocated to avoid per-frame GC)
    private final Matrix4 tmpM = new Matrix4();
    private final Matrix4 lightView = new Matrix4();
    private final Vector3 tmpV = new Vector3();
    private final Vector3 tmpCenter = new Vector3();
    private final Vector3 tmpCorner = new Vector3();
    private final Vector3[] frustumCorners = new Vector3[8];

    // Pre-allocated arrays for computeFrustumCornersWS() — avoids per-frame GC
    private static final float[] NDC_XS = {-1, 1, 1, -1};
    private static final float[] NDC_YS = {-1, -1, 1, 1};
    private final Vector3[] farPts = new Vector3[4];
    private final Vector3[] nearPts = new Vector3[4];

    private final WebGPUContext webgpu;

    /**
     * @param cascadeCount  number of shadow cascades (1–4)
     * @param shadowMapWidth  shadow map texture width in pixels
     * @param shadowMapHeight shadow map texture height in pixels (must equal width — shadow maps are square)
     */
    public WgCascadedShadowLight(int cascadeCount, int shadowMapWidth, int shadowMapHeight) {
        if (cascadeCount < 1 || cascadeCount > MAX_CASCADES)
            throw new IllegalArgumentException("cascadeCount must be 1.." + MAX_CASCADES + ", got " + cascadeCount);
        if (shadowMapWidth != shadowMapHeight)
            throw new IllegalArgumentException("CSM requires square shadow maps, got " + shadowMapWidth + "x" + shadowMapHeight);

        this.cascadeCount = cascadeCount;
        this.shadowMapSize = shadowMapWidth;
        this.lambda = DEFAULT_LAMBDA;

        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        // --- Pre-allocated scratch ---
        for (int i = 0; i < 8; i++) frustumCorners[i] = new Vector3();
        for (int i = 0; i < 4; i++) {
            farPts[i] = new Vector3();
            nearPts[i] = new Vector3();
        }

        cameras = new OrthographicCamera[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) {
            cameras[i] = new OrthographicCamera();
            cameras[i].up.set(1, 0, 0); // safe default in case light comes straight down
        }

        lightSpaceProjViews = new Matrix4[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) lightSpaceProjViews[i] = new Matrix4();

        cascadeSplitNDC = new float[MAX_CASCADES]; // vec4 slot — always 4 floats
        cascadeBiases = new float[MAX_CASCADES];   // vec4 slot — always 4 floats
        cascadeBiasOverrides = new float[MAX_CASCADES];
        hasCascadeBiasOverride = new boolean[MAX_CASCADES];
        splitDistances = new float[cascadeCount + 1];

        // --- Depth texture array for both rendering and shader sampling ---
        // Uses RenderAttachment (render directly into layers) + TextureBinding (sample in main shader).
        // NOTE: depth24plus does NOT support CopyDst per WebGPU spec, so we render directly
        // into the array layers instead of copying from intermediate FBOs.
        WGPUTextureUsage arrayUsage = WGPUTextureUsage.TextureBinding
                .or(WGPUTextureUsage.RenderAttachment);

        // Create the depth array texture — its default view is a 2DArray spanning all layers.
        // buildTextureView() provides per-layer 2D views for rendering.
        depthArrayTexture = new WgTexture("CSM depth array", shadowMapSize, shadowMapSize,
                cascadeCount, false, arrayUsage, WGPUTextureFormat.Depth24Plus);

        // Build an explicit 2DArray view for shader sampling.
        // WgTexture's default view is _2D when numLayers==1, but the shader always needs _2DArray.
        arrayView = depthArrayTexture.buildTextureView(WGPUTextureViewDimension._2DArray,
                WGPUTextureFormat.Depth24Plus, 0, 1, 0, cascadeCount);

        // --- Per-layer 2D views for rendering ---
        layerDepthTextures = new WgTexture[cascadeCount];
        for (int i = 0; i < cascadeCount; i++) {
            WGPUTextureView layerView = depthArrayTexture.buildTextureView(
                    WGPUTextureViewDimension._2D, WGPUTextureFormat.Depth24Plus, 0, 1, i, 1);
            layerDepthTextures[i] = new WgTexture(layerView, WGPUTextureFormat.Depth24Plus,
                    shadowMapSize, shadowMapSize);
        }

        // --- Shared dummy color texture (depth-only passes ignore color, but pushTargetView needs one) ---
        {
            WGPUTextureUsage colorUsage = WGPUTextureUsage.RenderAttachment
                    .or(WGPUTextureUsage.TextureBinding);
            dummyColorTexture = new WgTexture("CSM dummy color", shadowMapSize, shadowMapSize,
                    false, colorUsage, WGPUTextureFormat.BGRA8Unorm, 1);
            dummyTargetViews[0] = dummyColorTexture.getTextureView();
            dummyTargetFormats[0] = dummyColorTexture.getFormat();
        }
    }

    // -------------------------------------------------------------------------
    //  Frame lifecycle
    // -------------------------------------------------------------------------

    /**
     * Call once per frame before the shadow passes.
     * Fits each cascade's orthographic camera to the view frustum sub-range,
     * pre-shifts the projection matrices, and computes the NDC-z split thresholds.
     *
     * @param mainCamera the scene camera whose frustum is split
     */
    public void begin(Camera mainCamera) {
        float near = mainCamera.near;
        float far  = mainCamera.far;
        // If a max shadow distance is set, clamp the effective far so cascades
        // concentrate their resolution within the shadow range rather than
        // stretching across the full camera far plane.
        if (maxShadowDistance > 0 && maxShadowDistance < far) {
            far = maxShadowDistance;
        }

        // Practical Split Scheme: blend of logarithmic and linear splits
        splitDistances[0] = near;
        splitDistances[cascadeCount] = far;
        for (int i = 1; i < cascadeCount; i++) {
            float t = (float) i / cascadeCount;
            float logSplit    = near * (float) Math.pow((double) far / near, t);
            float linearSplit = near + (far - near) * t;
            splitDistances[i] = lambda * logSplit + (1f - lambda) * linearSplit;
        }

        // Build a light-view matrix once (translationless, just orientation)
        tmpV.set(direction).nor();
        Vector3 up = (Math.abs(tmpV.y) < 0.999f) ? Vector3.Y : Vector3.X;
        lightView.setToLookAt(Vector3.Zero, tmpV, up);

        for (int c = 0; c < cascadeCount; c++) {
            float cNear = splitDistances[c];
            float cFar  = splitDistances[c + 1];

            // Compute the 8 world-space corners of the view sub-frustum [cNear, cFar]
            computeFrustumCornersWS(mainCamera, cNear, cFar);

            // Transform corners to light space and build AABB
            float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            // Also accumulate the geometric centroid of the 8 corners in light space.
            // All inter-point distances (and thus the bounding sphere radius) are preserved
            // under the rigid camera-to-light-space transform, making them rotation-invariant.
            float centX = 0f, centY = 0f, centZ = 0f;
            for (int k = 0; k < 8; k++) {
                tmpCorner.set(frustumCorners[k]).mul(lightView);
                centX += tmpCorner.x;
                centY += tmpCorner.y;
                centZ += tmpCorner.z;
                if (tmpCorner.x < minX) minX = tmpCorner.x;
                if (tmpCorner.x > maxX) maxX = tmpCorner.x;
                if (tmpCorner.y < minY) minY = tmpCorner.y;
                if (tmpCorner.y > maxY) maxY = tmpCorner.y;
                if (tmpCorner.z < minZ) minZ = tmpCorner.z;
                if (tmpCorner.z > maxZ) maxZ = tmpCorner.z;
            }
            centX *= (1f / 8f);
            centY *= (1f / 8f);
            centZ *= (1f / 8f);

            // Extend Z range so objects just outside the sub-frustum can still cast/receive shadows.
            // minZ (farthest from light) is padded for receivers; maxZ (closest to light) is
            // padded more aggressively for casters behind the view frustum (e.g. tall buildings).
            float zRange = maxZ - minZ;
            float zPad = zRange * 0.1f;
            minZ -= zPad;
            maxZ += zPad;

            float orthoW, orthoH;
            if (stabilize) {
                // Sphere-based stable sizing using FULL 3D distances (not just XY).
                //
                // Why 3D: the frustum-to-light-space transform is a rigid body transform
                // (rotation + translation), which preserves all inter-point distances.
                // The 3D bounding sphere radius therefore depends only on the frustum
                // geometry (FOV, aspect, split distances) — NOT on camera orientation.
                // An orthographic projection of a 3D sphere always produces a circle of
                // the same radius, so using the 3D radius as the ortho viewport half-extent
                // is both correct and rotation-invariant.
                //
                // The previous XY-only approach gave a radius that changed as the camera
                // rotated (because the frustum's 2D XY footprint in light space changed),
                // which changed the viewport size, texel grid, and caused shadow edge jumps.
                //
                // Trade-off: slightly larger viewport than the 2D-only version (Z extent
                // is included), but shadow edges are completely stable during rotation.
                float radius = 0f;
                for (int k = 0; k < 8; k++) {
                    tmpCorner.set(frustumCorners[k]).mul(lightView);
                    float dx = tmpCorner.x - centX;
                    float dy = tmpCorner.y - centY;
                    float dz = tmpCorner.z - centZ;
                    float dist = dx * dx + dy * dy + dz * dz;
                    if (dist > radius) radius = dist;
                }
                radius = (float) Math.sqrt(radius);
                // Use diameter as the fixed viewport size (square)
                orthoW = radius * 2f;
                orthoH = radius * 2f;
            } else {
                orthoW = maxX - minX;
                orthoH = maxY - minY;
            }

            // Set up the orthographic camera for this cascade.
            // The camera must use the same rotational basis as lightView so that
            // the AABB's XY extent maps correctly to the camera's clip-space XY.
            OrthographicCamera cam = cameras[c];
            cam.viewportWidth  = orthoW;
            cam.viewportHeight = orthoH;
            cam.near = 0f;
            cam.far  = maxZ - minZ;
            cam.direction.set(tmpV);
            cam.up.set(up);          // must match the 'up' used by lightView
            cam.normalizeUp();

            // Position = centroid XY at the back (maxZ) of the light-space bounding box.
            // Using the centroid (instead of the AABB center) ensures the position doesn't
            // shift as the frustum AABB changes shape during camera rotation.
            tmpCenter.set(centX, centY, maxZ);

            // Texel snapping: round the XY camera position to shadow-map texel boundaries
            // in light space. This prevents the shadow map from shifting by sub-texel amounts
            // when the camera moves, eliminating edge swimming/shimmering artifacts.
            if (stabilize) {
                float texelSizeX = cam.viewportWidth / shadowMapSize;
                float texelSizeY = cam.viewportHeight / shadowMapSize;
                tmpCenter.x = Math.round(tmpCenter.x / texelSizeX) * texelSizeX;
                tmpCenter.y = Math.round(tmpCenter.y / texelSizeY) * texelSizeY;
            }
            // Convert center from light space back to world space
            Matrix4 invLightView = tmpM.set(lightView).inv(); // re-use tmpM briefly
            tmpCenter.mul(invLightView);
            cam.position.set(tmpCenter);
            cam.update();

            // Pre-shift the PVT to match shadow-map depth convention
            // (WgModelBatch.begin() shifts cam.combined for the shadow pass; we mirror that here
            //  so the lookup matrix matches the stored depth values.)
            lightSpaceProjViews[c].set(shiftDepthMatrix).mul(cam.combined);
        }

        // Compute per-cascade shadow biases.
        // If a per-cascade override is set, use that (world-space). Otherwise, compute automatically.
        // The auto bias is max(baseBias, minTexelBias * texelSize) in world units.
        // The texel-size term prevents acne when a cascade covers a large frustum (few texels per unit).
        // Dividing by the depth range converts from world-space to clip-space [0,1].
        for (int c = 0; c < cascadeCount; c++) {
            float depthRange = cameras[c].far;
            float worldBias;
            if (hasCascadeBiasOverride[c]) {
                worldBias = cascadeBiasOverrides[c];
            } else {
                float texelSizeWorld = cameras[c].viewportWidth / shadowMapSize;
                worldBias = Math.max(baseBias, minTexelBias * texelSizeWorld);
            }
            cascadeBiases[c] = worldBias / Math.max(depthRange, 1e-6f);
        }
        // Unused slots stay 0 (always length MAX_CASCADES = 4)

        // Compute NDC-z cascade split thresholds (used by the shader to select cascade)
        // These are the FAR-plane NDC depths of each cascade except the last.
        // Pre-shifted combined matrix matching what WgModelBatch.begin() applies:
        Matrix4 shiftedCombined = tmpM.set(shiftDepthMatrix).mul(mainCamera.combined);
        // Store for the shader — used as csmCameraProjectionView for cascade selection
        csmCameraProjectionView.set(shiftedCombined);
        float[] m = shiftedCombined.val;
        for (int c = 0; c < cascadeCount - 1; c++) {
            // Project a point at splitDistances[c+1] along the view direction
            Vector3 splitPt = tmpCorner.set(mainCamera.direction).nor().scl(splitDistances[c + 1])
                    .add(mainCamera.position);
            float x = splitPt.x, y = splitPt.y, z = splitPt.z;
            // Manual 4×4 multiply for the point (w=1), extracting clip-space Z and W
            float clipZ = m[Matrix4.M20]*x + m[Matrix4.M21]*y + m[Matrix4.M22]*z + m[Matrix4.M23];
            float clipW = m[Matrix4.M30]*x + m[Matrix4.M31]*y + m[Matrix4.M32]*z + m[Matrix4.M33];
            cascadeSplitNDC[c] = (clipW != 0f) ? (clipZ / clipW) : 0f;
        }
        // Store the last cascade's far boundary in the last slot.
        // The shader uses this for fade-out blending at the shadow distance edge.
        {
            Vector3 farPt = tmpCorner.set(mainCamera.direction).nor().scl(far)
                    .add(mainCamera.position);
            float x = farPt.x, y = farPt.y, z = farPt.z;
            float clipZ = m[Matrix4.M20]*x + m[Matrix4.M21]*y + m[Matrix4.M22]*z + m[Matrix4.M23];
            float clipW = m[Matrix4.M30]*x + m[Matrix4.M31]*y + m[Matrix4.M32]*z + m[Matrix4.M33];
            cascadeSplitNDC[cascadeCount - 1] = (clipW != 0f) ? (clipZ / clipW) : 1f;
        }
        // Unused slots beyond cascadeCount stay 0
    }

    /**
     * Returns the 8 world-space frustum corners that were last computed for cascade {@code i}.
     * Call only after {@link #begin(Camera)}. The returned array is shared scratch — copy if needed.
     * <p>
     * Corners 0-3 are the near face, corners 4-7 are the far face (in NDC corner order: BL, BR, TR, TL).
     */
    public Vector3[] getLastFrustumCorners(Camera mainCamera, int cascadeIndex) {
        float cNear = splitDistances[cascadeIndex];
        float cFar  = splitDistances[cascadeIndex + 1];
        computeFrustumCornersWS(mainCamera, cNear, cFar);
        return frustumCorners;
    }

    /**
     * Begin rendering cascade {@code i}.
     * Pushes the per-cascade framebuffer as the render target using the existing
     * WgFrameBuffer#begin() / {@link WebGPUContext#pushTargetView} mechanism.
     */
    public void beginCascade(int i) {
        currentCascade = i;
        // Push the per-layer depth view as the render target.
        // The dummy color target is needed by pushTargetView but ignored by the DEPTH_ONLY render pass.
        webgpu.pushTargetView(prevState, dummyTargetViews, dummyTargetFormats,
                shadowMapSize, shadowMapSize, layerDepthTextures[i]);
    }

    /** End rendering the current cascade and restore the previous render target. */
    public void endCascade() {
        webgpu.popTargetView(prevState);
    }

    /**
     * Finalize all shadow passes by copying each cascade's depth texture into the
     * corresponding layer of the shared depth array texture for shader sampling.
     * Must be called after all cascades have been rendered.
     */
    public void end() {
        // Nothing to do here — all rendering is direct to the depth array layers.
    }

    // -------------------------------------------------------------------------
    //  Getters
    // -------------------------------------------------------------------------

    public int getCascadeCount() { return cascadeCount; }

    public OrthographicCamera getCascadeCamera(int i) { return cameras[i]; }

    /** The full depth-array view to bind in the main shader (texture_depth_2d_array). */
    public WGPUTextureView getArrayView() { return arrayView; }

    /** Comparison sampler (compare=Less) for sampling the depth array. */
    public WGPUSampler getDepthArraySampler() { return depthArrayTexture.getDepthSampler(); }

    /**
     * Pre-shifted light-space projection-view matrices, one per cascade.
     * Ready to upload directly to the {@code shadowProjViewTransforms} uniform array.
     */
    public Matrix4[] getLightSpaceProjViews() { return lightSpaceProjViews; }

    /**
     * NDC-z split thresholds (always length {@link #MAX_CASCADES} = 4).
     * Only the first {@code cascadeCount - 1} values are valid; the rest are 0.
     * Uploaded as a {@code vec4f cascadeSplits} uniform.
     */
    public float[] getCascadeSplitNDC() { return cascadeSplitNDC; }

    /**
     * Per-cascade shadow biases (always length {@link #MAX_CASCADES} = 4).
     * Scaled by each cascade's depth range so a constant world-space bias is maintained.
     * Uploaded as a {@code vec4f cascadeBiases} uniform. Valid only after {@link #begin(Camera)}.
     */
    public float[] getCascadeBiases() { return cascadeBiases; }

    /**
     * Sets the base shadow bias in world-space units.
     * Default is {@link #DEFAULT_BASE_BIAS}. The actual per-cascade clip-space bias is
     * {@code max(baseBias, minTexelBias * texelSize) / cascadeDepthRange}.
     * Per-cascade overrides (see {@link #setCascadeBias}) take priority over this value.
     */
    public void setBaseBias(float bias) { this.baseBias = bias; }

    /** Returns the current base shadow bias (world-space units). */
    public float getBaseBias() { return baseBias; }

    /**
     * Sets the minimum shadow bias expressed in shadow-map texels.
     * When a cascade covers a large area, its texel size (world units) can exceed {@code baseBias},
     * causing shadow acne. This floor ensures the bias is always at least {@code minTexelBias} texels.
     * Default is {@link #DEFAULT_MIN_TEXEL_BIAS}.
     */
    public void setMinTexelBias(float texels) { this.minTexelBias = texels; }

    /** Returns the current minimum texel bias. */
    public float getMinTexelBias() { return minTexelBias; }

    /**
     * Overrides the automatically computed bias for a specific cascade.
     * The value is in world-space units and is converted to clip-space in {@link #begin(Camera)}.
     * Call {@link #clearCascadeBias(int)} to revert to auto-computation.
     *
     * @param cascade cascade index (0-based)
     * @param worldSpaceBias bias in world-space units
     */
    public void setCascadeBias(int cascade, float worldSpaceBias) {
        cascadeBiasOverrides[cascade] = worldSpaceBias;
        hasCascadeBiasOverride[cascade] = true;
    }

    /**
     * Returns the per-cascade bias override in world-space units, or -1 if no override is set.
     */
    public float getCascadeBiasOverride(int cascade) {
        return hasCascadeBiasOverride[cascade] ? cascadeBiasOverrides[cascade] : -1f;
    }

    /** Returns whether cascade {@code i} has a manually set bias override. */
    public boolean hasCascadeBiasOverride(int cascade) {
        return hasCascadeBiasOverride[cascade];
    }

    /** Clears the per-cascade bias override, reverting to the auto-computed value. */
    public void clearCascadeBias(int cascade) {
        hasCascadeBiasOverride[cascade] = false;
    }

    /** Clears all per-cascade bias overrides. */
    public void clearAllCascadeBiasOverrides() {
        for (int i = 0; i < MAX_CASCADES; i++) hasCascadeBiasOverride[i] = false;
    }

    public int getShadowMapSize() { return shadowMapSize; }

    /**
     * The CSM driver camera's projection-view matrix, pre-shifted to WebGPU [0,1] depth range.
     * Used by the shader for cascade selection (independent of the rendering camera).
     * Valid only after {@link #begin(Camera)}.
     */
    public Matrix4 getCsmCameraProjectionView() { return csmCameraProjectionView; }

    /**
     * View-space split distances (length = {@code cascadeCount + 1}).
     * Entry 0 is the camera near plane, entry {@code cascadeCount} is the camera far plane,
     * intermediate entries are the cascade boundaries. Valid only after {@link #begin(Camera)}.
     */
    public float[] getSplitDistances() { return splitDistances; }

    /**
     * Sets the split scheme blend factor. 0 = fully linear, 1 = fully logarithmic.
     * Default is {@link #DEFAULT_LAMBDA} (0.75). Higher values concentrate resolution
     * near the camera (good for outdoor scenes); lower values distribute it more evenly.
     */
    public void setLambda(float lambda) { this.lambda = lambda; }

    /** Returns the current split scheme blend factor. */
    public float getLambda() { return lambda; }

    /**
     * Enables or disables texel-snapping stabilization. When enabled (default), the shadow
     * camera position is snapped to texel boundaries in light space, preventing shadow edge
     * swimming/shimmering as the camera moves. Disable only for debugging.
     */
    public void setStabilize(boolean stabilize) { this.stabilize = stabilize; }

    /** Returns whether texel-snapping stabilization is enabled. */
    public boolean isStabilized() { return stabilize; }

    /**
     * Sets the maximum shadow distance. Cascades are fitted to cover
     * {@code [camera.near, min(camera.far, maxShadowDistance)]} instead of the full camera frustum.
     * This dramatically improves shadow quality with low cascade counts by concentrating
     * the shadow map resolution near the camera. Beyond this distance, no shadow is rendered.
     * <p>
     * Set to 0 (default) to use the full camera far plane.
     *
     * @param distance maximum shadow distance in world units, or 0 to use camera far
     */
    public void setMaxShadowDistance(float distance) { this.maxShadowDistance = Math.max(0, distance); }

    /** Returns the max shadow distance (0 = use camera far). */
    public float getMaxShadowDistance() { return maxShadowDistance; }

    // -------------------------------------------------------------------------
    //  Dispose
    // -------------------------------------------------------------------------

    @Override
    public void dispose() {
        // Dispose per-layer WgTexture wrappers (releases the view but NOT the underlying texture)
        for (WgTexture lt : layerDepthTextures) {
            lt.dispose();
        }
        dummyColorTexture.dispose();
        // Release the explicit 2DArray view built for shader sampling (separate from depthArrayTexture's default view)
        arrayView.release();
        arrayView.dispose();
        depthArrayTexture.dispose(); // disposes the array texture, its default 2DArray view, and any cached samplers
    }

    // -------------------------------------------------------------------------
    //  Private helpers
    // -------------------------------------------------------------------------

    /**
     * Fills {@link #frustumCorners} with the 8 world-space corners of the sub-frustum
     * bounded by [cNear, cFar] along the main camera's view rays.
     */
    private void computeFrustumCornersWS(Camera cam, float cNear, float cFar) {
        // Compute the 8 world-space corners of the view sub-frustum [cNear, cFar].
        // Un-project 8 NDC corners through the inverse combined matrix, then lerp to the split range.

        // Compute from the camera combined matrix inverse (works for both perspective & ortho)
        Matrix4 inv = tmpM.set(cam.combined).inv();

        // Un-project 4 NDC far corners (ndcZ = 1 for GL convention = far plane)
        // and near corners (ndcZ = -1 for GL convention = near plane).
        // Uses pre-allocated NDC_XS, NDC_YS, farPts, nearPts arrays.
        for (int k = 0; k < 4; k++) {
            farPts[k].set(NDC_XS[k], NDC_YS[k], 1f).prj(inv);
            nearPts[k].set(NDC_XS[k], NDC_YS[k], -1f).prj(inv);
        }

        // Interpolate between near and far world-space corners to get sub-frustum corners
        float totalDist = cam.far - cam.near;
        if (totalDist < 1e-6f) totalDist = 1f;
        float tNear = (cNear - cam.near) / totalDist;
        float tFar  = (cFar  - cam.near) / totalDist;

        for (int k = 0; k < 4; k++) {
            frustumCorners[k].set(nearPts[k]).lerp(farPts[k], tNear);
            frustumCorners[k + 4].set(nearPts[k]).lerp(farPts[k], tFar);
        }
    }
}


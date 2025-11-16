package com.monstrous.gdx.webgpu.graphics.g3d.attributes.environment;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.ShadowMap;
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.github.xpenatan.webgpu.WGPUTextureFormat;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;

public class WgDirectionalShadowLight extends DirectionalLight implements ShadowMap, Disposable {
    protected WgFrameBuffer fbo;
    protected Camera cam;
    protected float halfDepth;
    protected float halfHeight;
    protected final Vector3 tmpV = new Vector3();
    private Matrix4 shiftDepthMatrix;
    private final Matrix4 tmpMat4 = new Matrix4();
    private final TextureDescriptor<Texture> textureDesc;

    public WgDirectionalShadowLight(int shadowMapWidth, int shadowMapHeight, float shadowViewportWidth,
            float shadowViewportHeight, float shadowNear, float shadowFar) {
        fbo = new WgFrameBuffer(WGPUTextureFormat.BGRA8Unorm, shadowMapWidth, shadowMapHeight, true);
        cam = new OrthographicCamera(shadowViewportWidth, shadowViewportHeight);
        cam.near = shadowNear;
        cam.far = shadowFar;
        cam.up.set(1, 0, 0); // in case light comes straight down
        halfHeight = shadowViewportHeight * 0.5f;
        halfDepth = shadowNear + 0.5f * (shadowFar - shadowNear);
        textureDesc = new TextureDescriptor<Texture>();
        textureDesc.minFilter = textureDesc.magFilter = Texture.TextureFilter.Nearest;
        textureDesc.uWrap = textureDesc.vWrap = Texture.TextureWrap.ClampToEdge;

        shiftDepthMatrix = new Matrix4().idt().scl(1, 1, 0.5f).trn(0, 0, 0.5f);
    }

    public void update(final Camera camera) {
        update(tmpV.set(camera.direction).scl(halfHeight), camera.direction);
    }

    public void update(final Vector3 center, final Vector3 forward) {
        // note: forward not used
        cam.position.set(direction).scl(-halfDepth).add(center);
        cam.direction.set(direction).nor();
        cam.normalizeUp();
        cam.update();

        tmpMat4.set(shiftDepthMatrix).mul(cam.combined);
        cam.combined.set(tmpMat4);
    }

    public void begin(final Camera camera) {
        update(camera);
        begin();
    }

    public void begin(final Vector3 center, final Vector3 forward) {
        update(center, forward);
        begin();
    }

    public void begin() {
        fbo.begin();
    }

    public void end() {
        fbo.end();
    }

    public WgFrameBuffer getFrameBuffer() {
        return fbo;
    }

    public Camera getCamera() {
        return cam;
    }

    public Matrix4 getProjViewTrans() {
        return cam.combined;
    }

    public TextureDescriptor<Texture> getDepthMap() {
        textureDesc.texture = fbo.getDepthTexture();
        return textureDesc;
    }

    public Texture getFrameBufferColor() {
        return fbo.getColorBufferTexture();
    }

    @Override
    public void dispose() {
        if (fbo != null)
            fbo.dispose();
        fbo = null;
    }
}

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.Vector4;
import com.badlogic.gdx.utils.Array;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.MaterialUniformLayout;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.MaterialsCache;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.modular.ShaderModuleContext;
import com.monstrous.gdx.webgpu.graphics.shader.modular.WgShaderModule;
import com.monstrous.gdx.webgpu.graphics.shader.modular.layout.ShaderLayoutBuilder;
import com.monstrous.gdx.webgpu.graphics.shader.modular.layout.TextureBinding;
import com.monstrous.gdx.webgpu.graphics.shader.modular.layout.UniformType;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderBuildResult;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.ShaderDefines;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.WgShaderTemplate;
import com.monstrous.gdx.webgpu.graphics.shader.modular.template.WgslSnippet;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * 3D Fog-of-War demo using a test-local shader module.
 *
 * The FogOfWarShaderModule customizes modelbatch.template.wgsl to:
 *   - add a 'fogWorld' vec4f to the MaterialUniforms struct  (minX, minZ, maxX, maxZ)
 *   - add a fog texture + sampler to the material bind group
 *   - multiply the final fragment colour by the fog texture brightness
 *
 * No changes are made to the original shader file.
 *
 * Controls:
 *   WASD / Arrow keys - move player
 *   F1                - toggle fog on/off
 *   F2                - reset explored map
 */
public class FogOfWar3DTest extends GdxTest {

    private static final float WORLD_SIZE     = 80f;
    private static final int   FOG_W          = 256;
    private static final int   FOG_H          = 256;
    private static final float PLAYER_SPEED    = 16f;
    private static final float CAM_SENSITIVITY = 0.25f;  // degrees per pixel
    private static final float CAM_DISTANCE    = 12f;    // orbit radius
    private static final float CAM_PITCH_MIN   = -10f;   // degrees (slightly below horizontal)
    private static final float CAM_PITCH_MAX   = 70f;    // degrees (looking down steeply)
    private static final float VISION_RADIUS   = 12f;
    private static final float VISION_SOFTNESS = 5f;
    private static final float EXPLORED_BRIGHTNESS = 0.75f;

    // ------------------------------------------------------------------ scene
    private WgModelBatch           modelBatch;
    private PerspectiveCamera      cam;
    private Environment            environment;
    private final Array<ModelInstance> instances = new Array<>();
    private final Array<Model>         models    = new Array<>();

    // ------------------------------------------------------------------ fog
    /** Shared fog brightness texture (R channel: 0=hidden, 1=fully visible). */
    private WgTexture  fogTexture;
    private WgTexture  fallbackWhiteTexture;
    private WgTexture  fallbackNormalTexture;
    private WgTexture  fallbackBlackTexture;
    /** Per-texel explored brightness, range [0, 1]. */
    private float[]    explored;
    /** Scratch buffer for uploading fog pixels every frame. */
    private ByteBuffer fogPixels;

    private final Vector2 player   = new Vector2(WORLD_SIZE / 2f, WORLD_SIZE / 2f);
    private final Vector2 moveDir  = new Vector2();
    private float cameraYaw   =  180f;  // degrees; 0=North (-Z), increases clockwise
    private float cameraPitch =   30f;  // degrees above horizon (positive = looking down at player)
    private int   lastMouseX, lastMouseY;
    private boolean rightMouseWasDown = false;
    private boolean showFog = true;

    // ------------------------------------------------------------------ HUD
    private WgSpriteBatch hudBatch;
    private WgBitmapFont  hudFont;

    // ================================================================== shader module

    /** Test-local module that applies a top-down fog-of-war texture in the fragment shader. */
    static class FogOfWarShaderModule implements WgShaderModule {
        private final WgTexture fogTexture;
        private final Vector4 worldBounds;
        private TextureBinding fogBinding;

        FogOfWarShaderModule(WgTexture fogTexture, Vector4 worldBounds) {
            this.fogTexture = fogTexture;
            this.worldBounds = new Vector4(worldBounds);
        }

        @Override
        public String getSignature(ShaderModuleContext context) {
            return getClass().getName();
        }

        @Override
        public void configureDefines(ShaderDefines defines, ShaderModuleContext context) {
            defines.define("FOG_OF_WAR");
        }

        @Override
        public void configureLayout(ShaderLayoutBuilder layout, ShaderModuleContext context) {
            layout.addUniform(ShaderLayoutBuilder.SCOPE_MATERIAL, "fogWorld", UniformType.VEC4,
                (mat, binder, name) -> binder.setUniform(name, worldBounds));
            fogBinding = layout.addTexture(ShaderLayoutBuilder.SCOPE_MATERIAL, "fogTexture", "fogSampler",
                mat -> fogTexture);
        }

        @Override
        public void contribute(WgShaderTemplate template, ShaderModuleContext context) {
            template.insert("material.uniformFields", WgslSnippet.text("    fogWorld: vec4f,\n"));
            template.insert("material.bindings", WgslSnippet.text(
                "@group(1) @binding(" + fogBinding.textureBindingId + ") var fogTexture: texture_2d<f32>;\n"
                    + "@group(1) @binding(" + fogBinding.samplerBindingId + ") var fogSampler: sampler;\n"));
            template.insert("color.final", WgslSnippet.text(
                "{\n"
                    + "    let fw = material.fogWorld;\n"
                    + "    let fogUv = vec2f(\n"
                    + "        clamp((in.worldPos.x - fw.x) / max(fw.z - fw.x, 0.0001), 0.0, 1.0),\n"
                    + "        clamp((in.worldPos.z - fw.y) / max(fw.w - fw.y, 0.0001), 0.0, 1.0)\n"
                    + "    );\n"
                    + "    let brightness = textureSample(fogTexture, fogSampler,\n"
                    + "                                   vec2f(fogUv.x, 1.0 - fogUv.y)).r;\n"
                    + "    color = vec4f(color.rgb * brightness, color.a);\n"
                    + "}\n"));
        }
    }
    // ================================================================== lifecycle

    @Override
    public void create() {
        // Fog brightness map: black (0) = unrevealed, white (1) = fully visible
        Pixmap fogPixmap = new Pixmap(FOG_W, FOG_H, Pixmap.Format.RGBA8888);
        fogPixmap.setColor(0f, 0f, 0f, 1f);
        fogPixmap.fill();
        fogTexture = new WgTexture(fogPixmap, "fog_map_3d", false);
        fogTexture.setFilter(Texture.TextureFilter.Linear,      Texture.TextureFilter.Linear);
        fogTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        fogPixmap.dispose();

        explored  = new float[FOG_W * FOG_H];
        fogPixels = ByteBuffer.allocateDirect(FOG_W * FOG_H * 4);

        WgModelBatch.Config config = new WgModelBatch.Config();
        config.shaderSource = buildFogShaderSource(config);
        modelBatch = new WgModelBatch(config);

        cam = new PerspectiveCamera(60, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.near = 0.5f;
        cam.far  = 300f;

        environment = new Environment();
        environment.set(ColorAttribute.createAmbientLight(0.3f, 0.3f, 0.3f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.7f, -1f, -2f, -0.5f));

        WgModelBuilder mb = new WgModelBuilder();

        // Ground
        Model ground = mb.createBox(WORLD_SIZE, 0.5f, WORLD_SIZE,
            new Material(ColorAttribute.createDiffuse(new Color(0.35f, 0.45f, 0.30f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        models.add(ground);
        instances.add(new ModelInstance(ground, WORLD_SIZE / 2f, -0.25f, WORLD_SIZE / 2f));

        // Walls / obstacles
        addWall(mb, 12f,  1f, 24f,  3f, 2f, 12f);
        addWall(mb, 35f,  1f, 20f, 30f, 2f,  3f);
        addWall(mb, 55f,  1f, 14f,  3f, 2f, 20f);
        addWall(mb, 60f,  1f, 50f,  3f, 2f, 16f);
        addWall(mb, 22f,  1f, 60f, 20f, 2f,  3f);
        addWall(mb, 42f,  1f, 42f,  3f, 2f, 14f);

        // Player marker (yellow pillar)
        Model playerModel = mb.createBox(1.5f, 2f, 1.5f,
            new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        models.add(playerModel);
        instances.add(new ModelInstance(playerModel, player.x, 1f, player.y));

        hudBatch = new WgSpriteBatch();
        hudFont  = new WgBitmapFont();
    }

    private String buildFogShaderSource(WgModelBatch.Config config) {
        FogOfWarShaderModule module = new FogOfWarShaderModule(fogTexture,
            new Vector4(0f, 0f, WORLD_SIZE, WORLD_SIZE));

        MaterialUniformLayout materialLayout = createDefaultMaterialLayout();
        ShaderLayoutBuilder layout = new ShaderLayoutBuilder();
        layout.setMaterialLayout(materialLayout);

        ShaderModuleContext context = new ShaderModuleContext(null, null, null);
        module.configureLayout(layout, context);
        layout.apply();

        WgShaderTemplate template = new WgShaderTemplate(Gdx.files.classpath("shaders/modelbatch.template.wgsl"));
        ShaderDefines defines = new ShaderDefines();
        module.configureDefines(defines, context);
        module.contribute(template, context);

        Array<WgShaderModule> modules = new Array<>();
        modules.add(module);
        ShaderBuildResult result = template.build(defines, layout, modules, context);

        config.materials = new MaterialsCache(config.maxMaterials, materialLayout);
        return result.shaderSourceForPipeline;
    }

    private MaterialUniformLayout createDefaultMaterialLayout() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);  pixmap.fill();
        fallbackWhiteTexture  = new WgTexture(pixmap, "fog test default (white)");
        pixmap.setColor(Color.GREEN);  pixmap.fill();
        fallbackNormalTexture = new WgTexture(pixmap, "fog test default normal texture");
        pixmap.setColor(Color.BLACK);  pixmap.fill();
        fallbackBlackTexture  = new WgTexture(pixmap, "fog test default (black)");
        pixmap.dispose();
        return MaterialsCache.buildDefaultLayout(fallbackWhiteTexture, fallbackNormalTexture, fallbackBlackTexture);
    }

    /** Index of the player ModelInstance (always the last one added). */
    private ModelInstance playerInstance() {
        return instances.get(instances.size - 1);
    }

    private void addWall(WgModelBuilder mb,
                         float px, float py, float pz,
                         float sx, float sy, float sz) {
        Model wall = mb.createBox(sx, sy, sz,
            new Material(ColorAttribute.createDiffuse(new Color(0.55f, 0.50f, 0.40f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        models.add(wall);
        instances.add(new ModelInstance(wall, px, py, pz));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render() {
        float delta = Math.min(0.1f, Gdx.graphics.getDeltaTime());

        handleInput(delta);
        updateCamera();
        updateFogTexture();

        WgScreenUtils.clear(0.10f, 0.12f, 0.15f, 1f, true);

        cam.update();
        modelBatch.begin(cam);
        modelBatch.render(instances, environment);
        modelBatch.end();

        hudBatch.begin();
        hudFont.draw(hudBatch, "FogOfWar3DTest", 14, Gdx.graphics.getHeight() - 12);
        hudFont.draw(hudBatch,
            "Move: WASD / Arrows  |  Rotate camera: hold RMB + drag  |  F1: fog  |  F2: reset",
            14, Gdx.graphics.getHeight() - 32);
        hudFont.draw(hudBatch, "Fog: " + (showFog ? "ON" : "OFF"),
            14, Gdx.graphics.getHeight() - 52);
        hudBatch.end();
    }

    // ------------------------------------------------------------------ input / camera

    private void handleInput(float delta) {
        // --- Camera rotation: right mouse button drag ---
        boolean rmb = Gdx.input.isButtonPressed(Input.Buttons.RIGHT);
        if (rmb) {
            if (rightMouseWasDown) {
                int dx = Gdx.input.getX() - lastMouseX;
                int dy = Gdx.input.getY() - lastMouseY;
                cameraYaw   += dx * CAM_SENSITIVITY;
                cameraPitch += dy * CAM_SENSITIVITY;   // mouse Y+ = drag down = look more down
                cameraPitch  = MathUtils.clamp(cameraPitch, CAM_PITCH_MIN, CAM_PITCH_MAX);
            }
            rightMouseWasDown = true;
        } else {
            rightMouseWasDown = false;
        }
        lastMouseX = Gdx.input.getX();
        lastMouseY = Gdx.input.getY();

        // --- Player movement relative to camera yaw (ignore pitch for movement) ---
        float yawRad = cameraYaw * MathUtils.degreesToRadians;
        // forward = direction camera is facing projected onto the XZ plane
        float fwdX =  MathUtils.sin(yawRad);
        float fwdZ = -MathUtils.cos(yawRad);
        // right = 90 degrees clockwise from forward on XZ
        float rgtX =  MathUtils.cos(yawRad);
        float rgtZ =  MathUtils.sin(yawRad);

        moveDir.setZero();
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))    { moveDir.x += fwdX; moveDir.y += fwdZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))  { moveDir.x -= fwdX; moveDir.y -= fwdZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))  { moveDir.x -= rgtX; moveDir.y -= rgtZ; }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) { moveDir.x += rgtX; moveDir.y += rgtZ; }

        if (!moveDir.isZero()) {
            moveDir.nor().scl(PLAYER_SPEED * delta);
            player.add(moveDir);
        }
        player.x = MathUtils.clamp(player.x, 0f, WORLD_SIZE);
        player.y = MathUtils.clamp(player.y, 0f, WORLD_SIZE);

        playerInstance().transform.setTranslation(player.x, 1f, player.y);

        if (Gdx.input.isKeyJustPressed(Input.Keys.F1)) showFog = !showFog;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2)) resetExplored();
    }

    private void updateCamera() {
        // Spherical coordinates: yaw around Y axis, pitch above horizon
        float yawRad   = cameraYaw   * MathUtils.degreesToRadians;
        float pitchRad = cameraPitch * MathUtils.degreesToRadians;

        float cosP = MathUtils.cos(pitchRad);
        float sinP = MathUtils.sin(pitchRad);

        // Offset from player: behind (opposite of forward) + elevated by pitch
        float offX = -MathUtils.sin(yawRad) * cosP * CAM_DISTANCE;
        float offY =  sinP                          * CAM_DISTANCE;
        float offZ =  MathUtils.cos(yawRad) * cosP * CAM_DISTANCE;

        cam.position.set(player.x + offX, 1f + offY, player.y + offZ);
        cam.lookAt(player.x, 1f, player.y);
        cam.up.set(Vector3.Y);
    }

    // ------------------------------------------------------------------ fog texture

    private void updateFogTexture() {
        final int stride = FOG_W * 4;

        for (int z = 0; z < FOG_H; z++) {
            float worldZ = ((z + 0.5f) / FOG_H) * WORLD_SIZE;
            int   dstZ   = FOG_H - 1 - z;   // flip V so (0,0) maps to world (minX, minZ)

            for (int x = 0; x < FOG_W; x++) {
                float worldX = ((x + 0.5f) / FOG_W) * WORLD_SIZE;
                int   index  = z * FOG_W + x;

                float brightness;
                if (!showFog) {
                    brightness = 1f;  // fully reveal everything when fog is toggled off
                } else {
                    float dist         = Vector2.dst(worldX, worldZ, player.x, player.y);
                    float currentVision = MathUtils.clamp(
                        1f - smoothStep(VISION_RADIUS - VISION_SOFTNESS, VISION_RADIUS, dist),
                        0f, 1f);

                    // Explored memory: retain a clearly visible brightness for previously seen cells.
                    float exploredVis = currentVision * EXPLORED_BRIGHTNESS;
                    if (exploredVis > explored[index]) explored[index] = exploredVis;

                    brightness = Math.max(currentVision, explored[index]);
                }

                byte b    = (byte) MathUtils.round(brightness * 255f);
                int  base = dstZ * stride + x * 4;
                fogPixels.put(base,     b);
                fogPixels.put(base + 1, b);
                fogPixels.put(base + 2, b);
                fogPixels.put(base + 3, (byte) 255);
            }
        }

        fogPixels.position(0);
        fogPixels.limit(fogPixels.capacity());
        // Update all mip levels (not just mip 0), otherwise distant samples can read stale black mips.
        fogTexture.load(fogPixels, 4, FOG_W, FOG_H, 0);
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void resetExplored() {
        Arrays.fill(explored, 0f);
    }

    // ------------------------------------------------------------------ resize / dispose

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth  = width;
        cam.viewportHeight = height;
        cam.update();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hudBatch.dispose();
        hudFont.dispose();
        fogTexture.dispose();
        fallbackWhiteTexture.dispose();
        fallbackNormalTexture.dispose();
        fallbackBlackTexture.dispose();
        for (Model m : models) m.dispose();
    }
}

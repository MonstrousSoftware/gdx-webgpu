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

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.Frustum;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.application.WebGPUContext;
import com.monstrous.gdx.webgpu.application.WgGraphics;
import com.monstrous.gdx.webgpu.graphics.WgCubemap;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.CSMShadowAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.CascadedShadowAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.PBRFloatAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.WgCascadedShadowLight;
import com.monstrous.gdx.webgpu.graphics.g3d.environment.ibl.IBLGenerator;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgGLTFModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.loaders.WgModelLoader;
import com.monstrous.gdx.webgpu.graphics.g3d.shaders.WgDepthShaderProvider;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.FirstPersonCameraController;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgShapeRenderer;
import com.monstrous.gdx.webgpu.scene2d.WgScrollPane;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;
import com.monstrous.gdx.webgpu.wrappers.ProceduralSkyBox;
import com.monstrous.gdx.webgpu.wrappers.RenderPassType;

import static com.monstrous.gdx.webgpu.graphics.g3d.attributes.WgCubemapAttribute.*;

/**
 * Cascaded Shadow Map (CSM) debug test — split-screen view with IBL + PBR visuals.
 * <p>
 * Left half:  User camera (drives CSM shadow computation) with skybox.
 * Right half: Debug observer camera (shows cascade frustums and user camera frustum).
 * <p>
 * Press TAB to toggle which camera receives WASD/mouse input.
 * Press +/- to change cascade count 1–4.
 * Press 1–4 to isolate a single cascade's debug frustum, 0 to show all.
 * Press Page Up/Down to increase/decrease shadow map resolution.
 */
public class CSMShadowTest extends GdxTest {

    private static final float SHADOW_BIAS = 0.005f;
    private static final int[] SHADOW_MAP_SIZES = { 256, 512, 1024, 2048, 4096 };
    private static final int DEFAULT_SIZE_INDEX = 2; // 1024

    WgModelBatch modelBatch;
    WgModelBatch shadowBatch;

    /** The "user" camera that drives CSM shadow computation (left half). */
    PerspectiveCamera cam;
    /** An observer camera for debugging (right half). */
    PerspectiveCamera debugCam;
    /** true = debug camera receives input; false = user camera receives input. */
    boolean debugCamActive = false;

    FirstPersonCameraController controller;
    FirstPersonCameraController debugController;
    InputMultiplexer multiplexer;

    // IBL resources
    WgCubemap envMap;
    WgCubemap irradianceMap;
    WgCubemap radianceMap;
    ProceduralSkyBox skyBox;

    // Scene models
    Model ground;
    Model lightArrow;
    Model duckModel;
    Model dragonModel;
    ModelInstance lightArrowInstance;
    Array<ModelInstance> instances;
    Array<Disposable> disposables; // for cleanup
    Environment environment;
    WgCascadedShadowLight csmLight;
    Vector3 lightDir;
    WebGPUContext webgpu;
    WgShapeRenderer shapeRenderer;

    // Scene2D UI
    WgStage stage;
    WgSkin skin;
    Label fpsLabel;
    Label cascadeInfoLabel;
    Label biasInfoLabel;
    SelectBox<String> cascadeCountSelect;
    SelectBox<String> shadowSizeSelect;
    SelectBox<String> frustumFilterSelect;
    CheckBox inputToggle;
    // Bias controls
    Slider baseBiasSlider;
    Label baseBiasValueLabel;
    Slider minTexelBiasSlider;
    Label minTexelBiasValueLabel;
    Slider[] cascadeBiasSliders = new Slider[WgCascadedShadowLight.MAX_CASCADES];
    Label[] cascadeBiasValueLabels = new Label[WgCascadedShadowLight.MAX_CASCADES];
    CheckBox[] cascadeBiasOverrideChecks = new CheckBox[WgCascadedShadowLight.MAX_CASCADES];
    Table biasTable; // sub-table for per-cascade bias rows, rebuilt when cascade count changes
    CSMShadowAttribute shadowAttr; // compound CSM shadow settings attribute on the environment

    // Graphics settings controls
    Slider lightAzimuthSlider, lightElevationSlider;
    Label lightAzimuthLabel, lightElevationLabel;
    Slider lightIntensitySlider;
    Label lightIntensityLabel;
    Slider lightColorRSlider, lightColorGSlider, lightColorBSlider;
    Label lightColorLabel;
    Slider ambientRSlider, ambientGSlider, ambientBSlider;
    Label ambientLabel;
    CheckBox iblCheck;
    Slider lambdaSlider;
    Label lambdaLabel;
    CheckBox stabilizeCheck;
    CheckBox fogCheck;
    Slider fogRSlider, fogGSlider, fogBSlider;
    Label fogColorLabel;
    Slider camFovSlider, camFarSlider, shadowDistSlider;
    Label camFovLabel, camFarLabel;
    CheckBox skyBoxCheck;

    // Live references to environment attributes for real-time modification
    DirectionalLight dirLight;
    ColorAttribute ambientAttr;
    boolean iblEnabled = true;
    float lightAzimuth = 210f;   // degrees, 0 = +X, 90 = +Z
    float lightElevation = -60f; // degrees, negative = pointing down
    float lightIntensity = 1f;

    /** Stored full-screen dimensions for viewport management. */
    int screenWidth, screenHeight;

    /** Current shadow map resolution (index into SHADOW_MAP_SIZES). */
    int shadowSizeIndex = DEFAULT_SIZE_INDEX;

    /** Which cascade frustum to show in the debug view: -1 = all, 0–3 = single cascade. */
    int debugCascadeFilter = -1;

    // Cascade debug colors
    private static final Color[] CASCADE_COLORS = {
        Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW
    };
    int numCascades = 4;

    @Override
    public void create() {
        WgGraphics gfx = (WgGraphics) Gdx.graphics;
        webgpu = gfx.getContext();

        disposables = new Array<>();

        modelBatch = new WgModelBatch();

        screenWidth = Gdx.graphics.getWidth();
        screenHeight = Gdx.graphics.getHeight();
        int halfW = screenWidth / 2;

        cam = new PerspectiveCamera(67, halfW, screenHeight);
        cam.position.set(0, 8, 20);
        cam.near = 1f;
        cam.far = 550f;
        cam.lookAt(0, 0, -20);
        cam.update();

        debugCam = new PerspectiveCamera(67, halfW, screenHeight);
        debugCam.position.set(60, 80, 60);
        debugCam.near = 1f;
        debugCam.far = 500f;
        debugCam.lookAt(0, 0, -40);
        debugCam.update();

        controller = new FirstPersonCameraController(cam);
        debugController = new FirstPersonCameraController(debugCam);
        debugController.setEnabled(false); // user camera active by default

        shapeRenderer = new WgShapeRenderer();

        // -------- IBL Setup (procedural sky cubemap → diffuse/specular cubemaps for PBR reflections) --------
        // Generate a sky cubemap that matches the procedural sky colors.
        // Size 64 is sufficient for a smooth gradient; higher values (e.g., 512) cause extreme
        // slowness on web due to per-pixel Pixmap interop overhead in TeaVM.
        envMap = buildSkyCubemap(64,
                new Color(0.15f, 0.3f, 0.65f, 1f),   // zenith (deep blue)
                new Color(0.55f, 0.72f, 0.9f, 1f),   // horizon (light blue)
                new Color(0.25f, 0.22f, 0.2f, 1f));   // ground (dark brownish)
        disposables.add(envMap);

        irradianceMap = IBLGenerator.buildIrradianceMap(envMap, 64);
        disposables.add(irradianceMap);

        radianceMap = IBLGenerator.buildRadianceMap(envMap, 128);
        disposables.add(radianceMap);

        // -------- Procedural clear-sky --------
        skyBox = new ProceduralSkyBox();
        disposables.add(skyBox);

        // -------- Scene2D UI --------
        skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));
        stage = new WgStage(new ScreenViewport());
        buildUI();

        // Input: stage first (so UI eats clicks), then click-to-switch, then BOTH controllers
        // (only the enabled one processes events).
        // Wrap the stage so that scroll events are only forwarded when the mouse is actually
        // over a UI actor. Without this, libGDX Stage.scrolled() falls back to keyboardFocus,
        // causing the last-clicked slider/selectbox to steal scroll events even when the mouse
        // is outside the UI windows.
        multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(new InputProcessor() {
            private final Vector2 tmp = new Vector2();

            private boolean isOverUI() {
                tmp.set(Gdx.input.getX(), Gdx.input.getY());
                stage.screenToStageCoordinates(tmp);
                return stage.hit(tmp.x, tmp.y, true) != null;
            }

            @Override public boolean scrolled(float amountX, float amountY) {
                if (!isOverUI()) return false;
                return stage.scrolled(amountX, amountY);
            }

            @Override public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // Clear scroll focus when clicking outside the UI so the ScrollPane
                // doesn't keep stealing scroll events after the mouse leaves.
                if (!isOverUI()) stage.setScrollFocus(null);
                return stage.touchDown(screenX, screenY, pointer, button);
            }

            // All other events delegate directly to the stage
            @Override public boolean keyDown(int keycode) { return stage.keyDown(keycode); }
            @Override public boolean keyUp(int keycode) { return stage.keyUp(keycode); }
            @Override public boolean keyTyped(char character) { return stage.keyTyped(character); }
            @Override public boolean touchUp(int screenX, int screenY, int pointer, int button) { return stage.touchUp(screenX, screenY, pointer, button); }
            @Override public boolean touchCancelled(int screenX, int screenY, int pointer, int button) { return stage.touchCancelled(screenX, screenY, pointer, button); }
            @Override public boolean touchDragged(int screenX, int screenY, int pointer) { return stage.touchDragged(screenX, screenY, pointer); }
            @Override public boolean mouseMoved(int screenX, int screenY) { return stage.mouseMoved(screenX, screenY); }
        });
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                boolean wantDebug = screenX >= screenWidth / 2;
                if (wantDebug != debugCamActive) {
                    setActiveInput(wantDebug);
                    inputToggle.setProgrammaticChangeEvents(false);
                    inputToggle.setChecked(wantDebug);
                    inputToggle.setProgrammaticChangeEvents(true);
                }
                return false; // don't consume — let the now-enabled controller receive this event
            }
        });
        multiplexer.addProcessor(controller);
        multiplexer.addProcessor(debugController);
        Gdx.input.setInputProcessor(multiplexer);

        // -------- Build scene geometry --------
        instances = new Array<>();
        WgModelBuilder modelBuilder = new WgModelBuilder();
        long posNorm = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal;
        long posNormTex = VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
                | VertexAttributes.Usage.TextureCoordinates;

        // --- Ground plane with a clean, flat color for clear shadow visibility ---
        Material groundMat = new Material(
                ColorAttribute.createDiffuse(0.52f, 0.50f, 0.46f, 1f),  // medium warm gray
                PBRFloatAttribute.createMetallic(0f),
                PBRFloatAttribute.createRoughness(0.95f));

        float halfSize = 100f;
        modelBuilder.begin();
        MeshPartBuilder mpb = modelBuilder.part("ground", GL20.GL_TRIANGLES,
                new VertexAttributes(
                    new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
                    new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")),
                groundMat);
        MeshPartBuilder.VertexInfo c00 = new MeshPartBuilder.VertexInfo().setPos(-halfSize, 0, -halfSize).setNor(0,1,0);
        MeshPartBuilder.VertexInfo c10 = new MeshPartBuilder.VertexInfo().setPos( halfSize, 0, -halfSize).setNor(0,1,0);
        MeshPartBuilder.VertexInfo c11 = new MeshPartBuilder.VertexInfo().setPos( halfSize, 0,  halfSize).setNor(0,1,0);
        MeshPartBuilder.VertexInfo c01 = new MeshPartBuilder.VertexInfo().setPos(-halfSize, 0,  halfSize).setNor(0,1,0);
        mpb.rect(c01, c11, c10, c00);
        ground = modelBuilder.end();
        disposables.add(ground);
        instances.add(new ModelInstance(ground, 0, -0.05f, -40));

        // --- Load GLTF models ---
        WgModelLoader.ModelParameters params = new WgModelLoader.ModelParameters();
        params.textureParameter.genMipMaps = true;

        // Ducky model
        duckModel = new WgGLTFModelLoader().loadModel(
                Gdx.files.internal("data/g3d/gltf/Ducky/ducky.gltf"), params);
        disposables.add(duckModel);

        // Stanford Dragon model
        dragonModel = new WgGLTFModelLoader().loadModel(
                Gdx.files.internal("data/g3d/gltf/StanfordDragon/stanfordDragon.gltf"), params);
        disposables.add(dragonModel);

        // --- Place dragons at strategic positions ---
        float dragonScale = 3f;
        float[][] dragonPositions = {
            {0, 0, -5},
            {-10, 0, -30},
            {10, 0, -50},
            {0, 0, -70},
            {-8, 0, -90},
            {8, 0, -110},
        };

        // --- Place ducks in rows receding into the distance, skipping positions near dragons ---
        float minDragonDist = 10f; // skip ducks closer than this to any dragon
        for (int z = 0; z >= -120; z -= 12) {
            for (int x = -16; x <= 16; x += 8) {
                boolean tooClose = false;
                for (float[] dp : dragonPositions) {
                    float dx = x - dp[0], dz = z - dp[2];
                    if (dx * dx + dz * dz < minDragonDist * minDragonDist) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) continue;
                ModelInstance duck = new ModelInstance(duckModel);
                duck.transform.setToTranslation(x, 0f, z);
                duck.transform.rotate(Vector3.Y, MathUtils.random(0f, 360f));
                duck.transform.scale(1.5f, 1.5f, 1.5f);
                instances.add(duck);
            }
        }

        // --- Place dragons ---
        for (float[] pos : dragonPositions) {
            ModelInstance dragon = new ModelInstance(dragonModel);
            dragon.transform.setToTranslation(pos[0], pos[1], pos[2]);
            dragon.transform.scale(dragonScale, dragonScale, dragonScale);
            dragon.transform.rotate(Vector3.Y, MathUtils.random(0f, 360f));
            instances.add(dragon);
        }

        // --- PBR spheres with varying metallic/roughness along a line ---
        Color[] sphereColors = {
            new Color(0.9f, 0.1f, 0.1f, 1f),  // red
            new Color(0.1f, 0.7f, 0.1f, 1f),  // green
            new Color(0.1f, 0.3f, 0.9f, 1f),  // blue
            new Color(0.9f, 0.8f, 0.1f, 1f),  // gold
            new Color(0.8f, 0.8f, 0.8f, 1f),  // silver
        };
        for (int i = 0; i < sphereColors.length; i++) {
            float metallic = i / (float)(sphereColors.length - 1);
            float roughness = 0.15f + 0.15f * i;
            Material sphereMat = new Material(
                    ColorAttribute.createDiffuse(sphereColors[i]),
                    PBRFloatAttribute.createMetallic(metallic),
                    PBRFloatAttribute.createRoughness(roughness));
            Model sphere = modelBuilder.createSphere(2f, 2f, 2f, 20, 20, sphereMat, posNorm);
            disposables.add(sphere);
            instances.add(new ModelInstance(sphere, -8f + 4f * i, 1.0f, 8f));
        }

        // --- Tall columns/pillars ---
        Material pillarMat = new Material(
                ColorAttribute.createDiffuse(new Color(0.6f, 0.55f, 0.5f, 1f)),
                PBRFloatAttribute.createMetallic(0f),
                PBRFloatAttribute.createRoughness(0.7f));
        Model pillar = modelBuilder.createCylinder(1.5f, 10f, 1.5f, 16, pillarMat, posNorm);
        disposables.add(pillar);
        float[][] pillarPositions = {
            {-20, 5, -15}, {20, 5, -15},
            {-20, 5, -45}, {20, 5, -45},
            {-20, 5, -75}, {20, 5, -75},
            {-20, 5, -105}, {20, 5, -105},
        };
        for (float[] pos : pillarPositions) {
            instances.add(new ModelInstance(pillar, pos[0], pos[1], pos[2]));
        }

        // --- Light direction from spherical coordinates ---
        lightDir = new Vector3();
        updateLightDirection();

        lightArrow = modelBuilder.createArrow(
                new Vector3(lightDir).scl(-3f), Vector3.Zero,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.ColorPacked);
        disposables.add(lightArrow);
        lightArrowInstance = new ModelInstance(lightArrow);
        lightArrowInstance.transform.setTranslation(0, 5, 0);

        // -------- Environment (IBL + directional light) --------
        environment = new Environment();
        ambientAttr = ColorAttribute.createAmbientLight(0.15f, 0.15f, 0.15f, 1f);
        environment.set(ambientAttr);
        environment.set(new WgCubemapAttribute(DiffuseCubeMap, irradianceMap));
        environment.set(new WgCubemapAttribute(SpecularCubeMap, radianceMap));

        dirLight = new DirectionalLight();
        dirLight.set(new Color(1f, 0.95f, 0.85f, 1f), lightDir); // warm sunlight
        environment.add(dirLight);

        // -------- CSM (initial) --------
        // shadowAttr is created inside buildCsmResources() after csmLight exists
        buildCsmResources(numCascades);
    }

    /** Build the Scene2D control window. */
    private void buildUI() {
        // --- Cascade count ---
        cascadeCountSelect = new SelectBox<>(skin);
        cascadeCountSelect.setItems("1", "2", "3", "4");
        cascadeCountSelect.setSelected(String.valueOf(numCascades));
        cascadeCountSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int count = Integer.parseInt(cascadeCountSelect.getSelected());
                setCascadeCount(count);
                rebuildFrustumFilterItems();
                rebuildBiasTable();
            }
        });

        // --- Shadow map resolution ---
        shadowSizeSelect = new SelectBox<>(skin);
        String[] sizeLabels = new String[SHADOW_MAP_SIZES.length];
        for (int i = 0; i < SHADOW_MAP_SIZES.length; i++) sizeLabels[i] = SHADOW_MAP_SIZES[i] + "px";
        shadowSizeSelect.setItems(sizeLabels);
        shadowSizeSelect.setSelectedIndex(shadowSizeIndex);
        shadowSizeSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setShadowSize(shadowSizeSelect.getSelectedIndex());
            }
        });

        // --- Frustum filter ---
        frustumFilterSelect = new SelectBox<>(skin);
        rebuildFrustumFilterItems();
        frustumFilterSelect.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int idx = frustumFilterSelect.getSelectedIndex();
                debugCascadeFilter = idx - 1; // 0 → -1 (all), 1 → 0, 2 → 1, etc.
            }
        });

        // --- Camera input toggle ---
        inputToggle = new CheckBox(" Debug camera input", skin);
        inputToggle.setChecked(false);
        inputToggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setActiveInput(inputToggle.isChecked());
            }
        });

        // --- Bias controls ---
        baseBiasValueLabel = new Label(formatBias(WgCascadedShadowLight.DEFAULT_BASE_BIAS), skin);
        baseBiasSlider = new Slider(0f, 5f, 0.05f, false, skin);
        baseBiasSlider.setValue(WgCascadedShadowLight.DEFAULT_BASE_BIAS);
        baseBiasSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = baseBiasSlider.getValue();
                baseBiasValueLabel.setText(formatBias(val));
                if (csmLight != null) csmLight.setBaseBias(val);
            }
        });

        minTexelBiasValueLabel = new Label(formatBias(WgCascadedShadowLight.DEFAULT_MIN_TEXEL_BIAS), skin);
        minTexelBiasSlider = new Slider(0f, 10f, 0.1f, false, skin);
        minTexelBiasSlider.setValue(WgCascadedShadowLight.DEFAULT_MIN_TEXEL_BIAS);
        minTexelBiasSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                float val = minTexelBiasSlider.getValue();
                minTexelBiasValueLabel.setText(formatBias(val));
                if (csmLight != null) csmLight.setMinTexelBias(val);
            }
        });

        // Per-cascade bias overrides (sliders + checkboxes)
        for (int i = 0; i < WgCascadedShadowLight.MAX_CASCADES; i++) {
            final int ci = i;
            cascadeBiasValueLabels[i] = new Label("auto", skin);
            cascadeBiasSliders[i] = new Slider(0f, 10f, 0.05f, false, skin);
            cascadeBiasSliders[i].setValue(1.0f);
            cascadeBiasSliders[i].setDisabled(true); // disabled until override is checked
            cascadeBiasSliders[i].addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (csmLight != null && cascadeBiasOverrideChecks[ci].isChecked()) {
                        float val = cascadeBiasSliders[ci].getValue();
                        csmLight.setCascadeBias(ci, val);
                        cascadeBiasValueLabels[ci].setText(formatBias(val));
                    }
                }
            });
            cascadeBiasOverrideChecks[i] = new CheckBox("", skin);
            cascadeBiasOverrideChecks[i].setChecked(false);
            cascadeBiasOverrideChecks[i].addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    boolean on = cascadeBiasOverrideChecks[ci].isChecked();
                    cascadeBiasSliders[ci].setDisabled(!on);
                    if (csmLight != null) {
                        if (on) {
                            csmLight.setCascadeBias(ci, cascadeBiasSliders[ci].getValue());
                        } else {
                            csmLight.clearCascadeBias(ci);
                        }
                    }
                }
            });
        }

        // --- Info labels ---
        fpsLabel = new Label("fps: ", skin);
        // Pre-fill with max lines so pack() allocates enough height
        cascadeInfoLabel = new Label("Cascade 0:\nCascade 1:\nCascade 2:\nCascade 3:", skin);
        biasInfoLabel = new Label("Bias: auto\nBias: auto\nBias: auto\nBias: auto", skin);

        // --- Build window content table (will be wrapped in a ScrollPane) ---
        Table content = new Table();
        content.defaults().pad(4, 8, 4, 8).left();

        content.row();
        content.add(new Label("Cascades:", skin)).right();
        content.add(cascadeCountSelect).minWidth(80).fillX();
        content.row();
        content.add(new Label("Shadow map:", skin)).right();
        content.add(shadowSizeSelect).minWidth(100).fillX();
        content.row();
        content.add(new Label("Show frustum:", skin)).right();
        content.add(frustumFilterSelect).minWidth(120).fillX();
        content.row();
        content.add(inputToggle).colspan(2).padTop(6);

        // --- Bias section ---
        content.row();
        content.add(new Label("--- Shadow Bias ---", skin)).colspan(2).padTop(8);

        content.row();
        content.add(new Label("Base bias:", skin)).right();
        Table baseBiasRow = new Table();
        baseBiasRow.add(baseBiasSlider).minWidth(120).fillX();
        baseBiasRow.add(baseBiasValueLabel).minWidth(40).padLeft(4);
        content.add(baseBiasRow).fillX();

        content.row();
        content.add(new Label("Min texel bias:", skin)).right();
        Table minTexelRow = new Table();
        minTexelRow.add(minTexelBiasSlider).minWidth(120).fillX();
        minTexelRow.add(minTexelBiasValueLabel).minWidth(40).padLeft(4);
        content.add(minTexelRow).fillX();

        // Per-cascade bias overrides in a sub-table (rebuilt when cascade count changes)
        content.row();
        biasTable = new Table();
        content.add(biasTable).colspan(2).padTop(4).fillX();
        rebuildBiasTable();

        content.row();
        content.add(fpsLabel).colspan(2).padTop(8);
        content.row();
        content.add(cascadeInfoLabel).colspan(2).padTop(4).growX();
        content.row();
        content.add(biasInfoLabel).colspan(2).padTop(2).growX();

        // --- Build window with scrollable content ---
        Window window = new Window("CSM Debug", skin);
        window.setKeepWithinStage(true);
        window.setMovable(true);
        window.setResizable(true);

        ScrollPane scrollPane = new WgScrollPane(content, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false); // horizontal scroll off, vertical on

        window.add(scrollPane).grow();
        window.pack();
        // Ensure minimum width so dropdown lists don't overflow the window
        if (window.getWidth() < 340) window.setWidth(340);
        // Cap height to screen height so the window doesn't overflow
        float maxH = Gdx.graphics.getHeight() - 20;
        if (window.getHeight() > maxH) window.setHeight(maxH);
        window.setPosition(10, Gdx.graphics.getHeight() - window.getHeight() - 10);

        stage.addActor(window);

        // --- Second window: Graphics & Lighting settings ---
        buildGraphicsUI();
    }

    /** Rebuild the per-cascade bias override rows in the bias sub-table. */
    private void rebuildBiasTable() {
        biasTable.clear();
        for (int i = 0; i < numCascades; i++) {
            biasTable.row().padTop(2);
            biasTable.add(new Label("C" + i + " override:", skin)).right().padRight(4);
            biasTable.add(cascadeBiasOverrideChecks[i]);
            biasTable.add(cascadeBiasSliders[i]).minWidth(100).fillX();
            biasTable.add(cascadeBiasValueLabels[i]).minWidth(40).padLeft(4);
        }
    }

    private String formatBias(float value) {
        return String.format("%.2f", value);
    }

    /** Build the second Scene2D window for graphics / lighting settings. */
    private void buildGraphicsUI() {
        // --- Build content table (will be wrapped in a ScrollPane) ---
        Table gfxContent = new Table();
        gfxContent.defaults().pad(2, 6, 2, 6).left();

        // ---------- Light Direction ----------
        gfxContent.row();
        gfxContent.add(new Label("--- Light Direction ---", skin)).colspan(2).padTop(4);

        lightAzimuthLabel = new Label(String.format("%.0f", lightAzimuth), skin);
        lightAzimuthSlider = new Slider(0f, 360f, 1f, false, skin);
        lightAzimuthSlider.setValue(lightAzimuth);
        lightAzimuthSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                lightAzimuth = lightAzimuthSlider.getValue();
                lightAzimuthLabel.setText(String.format("%.0f", lightAzimuth));
                updateLightDirection();
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Azimuth:", skin)).right();
        Table azRow = new Table();
        azRow.add(lightAzimuthSlider).minWidth(110).fillX();
        azRow.add(lightAzimuthLabel).minWidth(30).padLeft(4);
        gfxContent.add(azRow).fillX();

        lightElevationLabel = new Label(String.format("%.0f", lightElevation), skin);
        lightElevationSlider = new Slider(-89f, -5f, 1f, false, skin);
        lightElevationSlider.setValue(lightElevation);
        lightElevationSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                lightElevation = lightElevationSlider.getValue();
                lightElevationLabel.setText(String.format("%.0f", lightElevation));
                updateLightDirection();
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Elevation:", skin)).right();
        Table elRow = new Table();
        elRow.add(lightElevationSlider).minWidth(110).fillX();
        elRow.add(lightElevationLabel).minWidth(30).padLeft(4);
        gfxContent.add(elRow).fillX();

        // ---------- Light Color & Intensity ----------
        gfxContent.row();
        gfxContent.add(new Label("--- Light Color ---", skin)).colspan(2).padTop(6);

        lightIntensityLabel = new Label(String.format("%.1f", lightIntensity), skin);
        lightIntensitySlider = new Slider(0f, 20f, 0.1f, false, skin);
        lightIntensitySlider.setValue(lightIntensity);
        lightIntensitySlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                lightIntensity = lightIntensitySlider.getValue();
                lightIntensityLabel.setText(String.format("%.1f", lightIntensity));
                updateLightColor();
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Intensity:", skin)).right();
        Table intRow = new Table();
        intRow.add(lightIntensitySlider).minWidth(110).fillX();
        intRow.add(lightIntensityLabel).minWidth(30).padLeft(4);
        gfxContent.add(intRow).fillX();

        lightColorLabel = new Label("1.00 0.95 0.85", skin);
        lightColorRSlider = new Slider(0f, 1f, 0.01f, false, skin);
        lightColorRSlider.setValue(1f);
        lightColorGSlider = new Slider(0f, 1f, 0.01f, false, skin);
        lightColorGSlider.setValue(0.95f);
        lightColorBSlider = new Slider(0f, 1f, 0.01f, false, skin);
        lightColorBSlider.setValue(0.85f);
        ChangeListener lightColorListener = new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                lightColorLabel.setText(String.format("%.2f %.2f %.2f",
                        lightColorRSlider.getValue(), lightColorGSlider.getValue(), lightColorBSlider.getValue()));
                updateLightColor();
            }
        };
        lightColorRSlider.addListener(lightColorListener);
        lightColorGSlider.addListener(lightColorListener);
        lightColorBSlider.addListener(lightColorListener);

        gfxContent.row();
        gfxContent.add(new Label("R:", skin)).right();
        gfxContent.add(lightColorRSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("G:", skin)).right();
        gfxContent.add(lightColorGSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("B:", skin)).right();
        gfxContent.add(lightColorBSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(lightColorLabel).colspan(2).padTop(2);

        // ---------- Ambient & IBL ----------
        gfxContent.row();
        gfxContent.add(new Label("--- Ambient & IBL ---", skin)).colspan(2).padTop(6);

        // IBL toggle — when enabled, IBL irradiance replaces ambient light in the shader.
        // When disabled, the ambient RGB sliders below take effect.
        iblCheck = new CheckBox(" IBL (overrides ambient)", skin);
        iblCheck.setChecked(iblEnabled);
        iblCheck.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                iblEnabled = iblCheck.isChecked();
                updateIBL();
            }
        });
        gfxContent.row();
        gfxContent.add(iblCheck).colspan(2);

        ambientLabel = new Label("0.15 0.15 0.15", skin);
        ambientRSlider = new Slider(0f, 1f, 0.01f, false, skin);
        ambientRSlider.setValue(0.15f);
        ambientGSlider = new Slider(0f, 1f, 0.01f, false, skin);
        ambientGSlider.setValue(0.15f);
        ambientBSlider = new Slider(0f, 1f, 0.01f, false, skin);
        ambientBSlider.setValue(0.15f);
        ChangeListener ambientListener = new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float r = ambientRSlider.getValue(), g = ambientGSlider.getValue(), b = ambientBSlider.getValue();
                ambientLabel.setText(String.format("%.2f %.2f %.2f", r, g, b));
                if (ambientAttr != null) ambientAttr.color.set(r, g, b, 1f);
            }
        };
        ambientRSlider.addListener(ambientListener);
        ambientGSlider.addListener(ambientListener);
        ambientBSlider.addListener(ambientListener);

        gfxContent.row();
        gfxContent.add(new Label("R:", skin)).right();
        gfxContent.add(ambientRSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("G:", skin)).right();
        gfxContent.add(ambientGSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("B:", skin)).right();
        gfxContent.add(ambientBSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(ambientLabel).colspan(2).padTop(2);

        // ---------- Fog ----------
        gfxContent.row();
        gfxContent.add(new Label("--- Fog ---", skin)).colspan(2).padTop(6);

        fogColorLabel = new Label("0.50 0.50 0.50", skin);
        fogCheck = new CheckBox(" Enable fog", skin);
        fogCheck.setChecked(false);
        fogRSlider = new Slider(0f, 1f, 0.01f, false, skin);
        fogRSlider.setValue(0.5f);
        fogGSlider = new Slider(0f, 1f, 0.01f, false, skin);
        fogGSlider.setValue(0.5f);
        fogBSlider = new Slider(0f, 1f, 0.01f, false, skin);
        fogBSlider.setValue(0.5f);

        ChangeListener fogListener = new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float r = fogRSlider.getValue(), g = fogGSlider.getValue(), b = fogBSlider.getValue();
                fogColorLabel.setText(String.format("%.2f %.2f %.2f", r, g, b));
                if (fogCheck.isChecked()) {
                    environment.set(new ColorAttribute(ColorAttribute.Fog, r, g, b, 1f));
                }
            }
        };
        fogRSlider.addListener(fogListener);
        fogGSlider.addListener(fogListener);
        fogBSlider.addListener(fogListener);
        fogCheck.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (fogCheck.isChecked()) {
                    float r = fogRSlider.getValue(), g = fogGSlider.getValue(), b = fogBSlider.getValue();
                    environment.set(new ColorAttribute(ColorAttribute.Fog, r, g, b, 1f));
                } else {
                    environment.remove(ColorAttribute.Fog);
                }
            }
        });

        gfxContent.row();
        gfxContent.add(fogCheck).colspan(2);
        gfxContent.row();
        gfxContent.add(new Label("R:", skin)).right();
        gfxContent.add(fogRSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("G:", skin)).right();
        gfxContent.add(fogGSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(new Label("B:", skin)).right();
        gfxContent.add(fogBSlider).minWidth(130).fillX();
        gfxContent.row();
        gfxContent.add(fogColorLabel).colspan(2).padTop(2);

        // ---------- CSM Tuning ----------
        gfxContent.row();
        gfxContent.add(new Label("--- CSM Tuning ---", skin)).colspan(2).padTop(6);

        lambdaLabel = new Label(String.format("%.2f", WgCascadedShadowLight.DEFAULT_LAMBDA), skin);
        lambdaSlider = new Slider(0f, 1f, 0.01f, false, skin);
        lambdaSlider.setValue(WgCascadedShadowLight.DEFAULT_LAMBDA);
        lambdaSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float val = lambdaSlider.getValue();
                lambdaLabel.setText(String.format("%.2f", val));
                if (csmLight != null) csmLight.setLambda(val);
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Lambda:", skin)).right();
        Table lamRow = new Table();
        lamRow.add(lambdaSlider).minWidth(110).fillX();
        lamRow.add(lambdaLabel).minWidth(30).padLeft(4);
        gfxContent.add(lamRow).fillX();

        stabilizeCheck = new CheckBox(" Texel stabilize", skin);
        stabilizeCheck.setChecked(true);
        stabilizeCheck.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                if (csmLight != null) csmLight.setStabilize(stabilizeCheck.isChecked());
            }
        });
        gfxContent.row();
        gfxContent.add(stabilizeCheck).colspan(2);

        // Shadow softness (PCF blur radius)
        final Label softnessLabel = new Label("1.0", skin);
        final Slider softnessSlider = new Slider(0f, 5f, 0.1f, false, skin);
        softnessSlider.setValue(1f);
        softnessSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float v = softnessSlider.getValue();
                softnessLabel.setText(String.format("%.1f", v));
                shadowAttr.softness = v;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Shadow soft:", skin)).right();
        Table softRow = new Table();
        softRow.add(softnessSlider).minWidth(110).fillX();
        softRow.add(softnessLabel).minWidth(30).padLeft(4);
        gfxContent.add(softRow).fillX();

        // PCF kernel size
        final Label pcfKernelLabel = new Label("3×3 (9)", skin);
        final SelectBox<String> pcfKernelSelect = new SelectBox<>(skin);
        pcfKernelSelect.setItems("1×1 (1)", "3×3 (9)", "5×5 (25)", "7×7 (49)");
        pcfKernelSelect.setSelectedIndex(1); // default 3×3
        pcfKernelSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                int radius = pcfKernelSelect.getSelectedIndex(); // 0=1×1, 1=3×3, 2=5×5, 3=7×7
                shadowAttr.pcfRadius = radius;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("PCF kernel:", skin)).right();
        gfxContent.add(pcfKernelSelect).fillX();

        // Shadow filter mode (items ordered to match CSMShadowAttribute.FILTER_GRID_PCF / FILTER_POISSON_PCF)
        final SelectBox<String> filterModeSelect = new SelectBox<>(skin);
        filterModeSelect.setItems("Grid PCF", "Poisson Disk PCF");  // index must match FILTER_* constants
        filterModeSelect.setSelectedIndex(CSMShadowAttribute.DEFAULT_FILTER_MODE);
        filterModeSelect.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                int idx = filterModeSelect.getSelectedIndex();
                // Map: 0 → FILTER_GRID_PCF, 1 → FILTER_POISSON_PCF
                shadowAttr.shadowFilterMode = idx == 1 ? CSMShadowAttribute.FILTER_POISSON_PCF
                                                       : CSMShadowAttribute.FILTER_GRID_PCF;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Filter mode:", skin)).right();
        gfxContent.add(filterModeSelect).fillX();

        // Cascade blend fraction
        final Label blendLabel = new Label(String.format("%.0f%%", CSMShadowAttribute.DEFAULT_CASCADE_BLEND * 100f), skin);
        final Slider blendSlider = new Slider(0f, 0.5f, 0.01f, false, skin);
        blendSlider.setValue(CSMShadowAttribute.DEFAULT_CASCADE_BLEND);
        blendSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float v = blendSlider.getValue();
                blendLabel.setText(v == 0 ? "Off" : String.format("%.0f%%", v * 100f));
                shadowAttr.cascadeBlend = v;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Cascade blend:", skin)).right();
        Table blendRow = new Table();
        blendRow.add(blendSlider).minWidth(110).fillX();
        blendRow.add(blendLabel).minWidth(30).padLeft(4);
        gfxContent.add(blendRow).fillX();

        // ---------- Camera ----------
        gfxContent.row();
        gfxContent.add(new Label("--- Camera ---", skin)).colspan(2).padTop(6);

        camFovLabel = new Label("67", skin);
        camFovSlider = new Slider(30f, 120f, 1f, false, skin);
        camFovSlider.setValue(67f);
        camFovSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float fov = camFovSlider.getValue();
                camFovLabel.setText(String.format("%.0f", fov));
                cam.fieldOfView = fov;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("FOV:", skin)).right();
        Table fovRow = new Table();
        fovRow.add(camFovSlider).minWidth(110).fillX();
        fovRow.add(camFovLabel).minWidth(30).padLeft(4);
        gfxContent.add(fovRow).fillX();

        camFarLabel = new Label("550", skin);
        camFarSlider = new Slider(50f, 1500f, 10f, false, skin);
        camFarSlider.setValue(550f);
        camFarSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float far = camFarSlider.getValue();
                camFarLabel.setText(String.format("%.0f", far));
                cam.far = far;
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Far:", skin)).right();
        Table farRow = new Table();
        farRow.add(camFarSlider).minWidth(110).fillX();
        farRow.add(camFarLabel).minWidth(30).padLeft(4);
        gfxContent.add(farRow).fillX();

        // Shadow distance (0 = use camera far)
        final Label shadowDistLabel = new Label("Auto", skin);
        shadowDistSlider = new Slider(0f, 1500f, 10f, false, skin);
        shadowDistSlider.setValue(0f);
        shadowDistSlider.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                float v = shadowDistSlider.getValue();
                shadowDistLabel.setText(v == 0 ? "Auto" : String.format("%.0f", v));
                if (csmLight != null) csmLight.setMaxShadowDistance(v);
            }
        });
        gfxContent.row();
        gfxContent.add(new Label("Shadow dist:", skin)).right();
        Table sdRow = new Table();
        sdRow.add(shadowDistSlider).minWidth(110).fillX();
        sdRow.add(shadowDistLabel).minWidth(30).padLeft(4);
        gfxContent.add(sdRow).fillX();

        // ---------- SkyBox ----------
        skyBoxCheck = new CheckBox(" Show SkyBox", skin);
        skyBoxCheck.setChecked(true);
        gfxContent.row();
        gfxContent.add(skyBoxCheck).colspan(2).padTop(4);

        // ---------- Build window with scrollable content ----------
        Window gfxWin = new Window("Graphics", skin);
        gfxWin.setKeepWithinStage(true);
        gfxWin.setMovable(true);
        gfxWin.setResizable(true);

        ScrollPane gfxScrollPane = new WgScrollPane(gfxContent, skin);
        gfxScrollPane.setFadeScrollBars(false);
        gfxScrollPane.setScrollingDisabled(true, false); // horizontal scroll off, vertical on

        gfxWin.add(gfxScrollPane).grow();
        gfxWin.pack();
        if (gfxWin.getWidth() < 300) gfxWin.setWidth(300);
        // Cap height to screen height so the window doesn't overflow
        float gfxMaxH = Gdx.graphics.getHeight() - 20;
        if (gfxWin.getHeight() > gfxMaxH) gfxWin.setHeight(gfxMaxH);
        // Position to the right of the CSM window — top-right corner
        gfxWin.setPosition(Gdx.graphics.getWidth() - gfxWin.getWidth() - 10,
                Gdx.graphics.getHeight() - gfxWin.getHeight() - 10);

        stage.addActor(gfxWin);
    }

    /** Recompute lightDir from azimuth + elevation (spherical → Cartesian), update light + CSM + arrow. */
    private void updateLightDirection() {
        float azRad = lightAzimuth * MathUtils.degreesToRadians;
        float elRad = lightElevation * MathUtils.degreesToRadians;
        float cosEl = MathUtils.cos(elRad);
        lightDir.set(MathUtils.cos(azRad) * cosEl, MathUtils.sin(elRad), MathUtils.sin(azRad) * cosEl).nor();
        if (dirLight != null) dirLight.setDirection(lightDir);
        if (csmLight != null) csmLight.setDirection(lightDir.x, lightDir.y, lightDir.z);
        if (skyBox != null) skyBox.setSunDirection(lightDir);
        if (lightArrowInstance != null) {
            // Rebuild arrow orientation: point from tip toward origin along -lightDir
            lightArrowInstance.transform.idt();
            lightArrowInstance.transform.setTranslation(0, 5, 0);
        }
    }

    /** Apply current light color RGB × intensity to the directional light.
     *  Note: we assign fields directly instead of using {@code Color.set(r,g,b,a)}
     *  because libGDX's {@code set()} calls {@code clamp()}, capping values to [0,1].
     *  HDR light intensities above 1.0 would be silently lost. */
    private void updateLightColor() {
        if (dirLight != null && lightColorRSlider != null) {
            dirLight.color.r = lightColorRSlider.getValue() * lightIntensity;
            dirLight.color.g = lightColorGSlider.getValue() * lightIntensity;
            dirLight.color.b = lightColorBSlider.getValue() * lightIntensity;
            dirLight.color.a = 1f;
        }
    }

    /** Toggle IBL cubemaps on/off. When IBL is enabled the shader uses irradiance/radiance
     *  maps for ambient lighting (ambient RGB sliders are ignored). When disabled, the shader
     *  falls back to the flat ambient light color from the sliders.
     *  Requires model batch rebuild because USE_IBL is a compile-time shader define. */
    private void updateIBL() {
        if (iblEnabled) {
            environment.set(new WgCubemapAttribute(DiffuseCubeMap, irradianceMap));
            environment.set(new WgCubemapAttribute(SpecularCubeMap, radianceMap));
        } else {
            environment.remove(WgCubemapAttribute.DiffuseCubeMap);
            environment.remove(WgCubemapAttribute.SpecularCubeMap);
        }
        // Shader must be recompiled (USE_IBL is a #define derived from environment mask)
        buildCsmResources(numCascades);
    }

    /** Rebuild the frustum filter SelectBox items to match the current cascade count. */
    private void rebuildFrustumFilterItems() {
        String[] items = new String[numCascades + 1];
        items[0] = "All";
        for (int i = 0; i < numCascades; i++) items[i + 1] = "Cascade " + i;
        frustumFilterSelect.setItems(items);
        frustumFilterSelect.setSelectedIndex(0);
        debugCascadeFilter = -1;
    }

    /** (Re)create CSM light, environment attribute, modelBatch and shadowBatch for the given cascade count. */
    private void buildCsmResources(int count) {
        // Dispose previous resources if they exist
        if (csmLight != null) csmLight.dispose();
        if (modelBatch != null) modelBatch.dispose();
        if (shadowBatch != null) shadowBatch.dispose();

        numCascades = count;
        int mapSize = SHADOW_MAP_SIZES[shadowSizeIndex];

        csmLight = new WgCascadedShadowLight(numCascades, mapSize, mapSize);
        csmLight.setDirection(lightDir.x, lightDir.y, lightDir.z);
        // Apply current slider settings to the new light
        if (baseBiasSlider != null) {
            csmLight.setBaseBias(baseBiasSlider.getValue());
            csmLight.setMinTexelBias(minTexelBiasSlider.getValue());
            // Re-apply any active per-cascade overrides
            for (int i = 0; i < WgCascadedShadowLight.MAX_CASCADES; i++) {
                if (i < numCascades && cascadeBiasOverrideChecks[i].isChecked()) {
                    csmLight.setCascadeBias(i, cascadeBiasSliders[i].getValue());
                }
            }
        }
        // Apply lambda and stabilize from graphics UI
        if (lambdaSlider != null) {
            csmLight.setLambda(lambdaSlider.getValue());
            csmLight.setStabilize(stabilizeCheck.isChecked());
            if (shadowDistSlider != null) {
                csmLight.setMaxShadowDistance(shadowDistSlider.getValue());
            }
        }
        environment.set(CascadedShadowAttribute.createShadow(csmLight));

        // Create or update the CSM shadow settings attribute (bias, softness, pcfRadius)
        float prevSoftness = shadowAttr != null ? shadowAttr.softness : CSMShadowAttribute.DEFAULT_SOFTNESS;
        float prevPcfRadius = shadowAttr != null ? shadowAttr.pcfRadius : CSMShadowAttribute.DEFAULT_PCF_RADIUS;
        float prevBias = shadowAttr != null ? shadowAttr.bias : SHADOW_BIAS;
        int prevFilterMode = shadowAttr != null ? shadowAttr.shadowFilterMode : CSMShadowAttribute.DEFAULT_FILTER_MODE;
        float prevCascadeBlend = shadowAttr != null ? shadowAttr.cascadeBlend : CSMShadowAttribute.DEFAULT_CASCADE_BLEND;
        shadowAttr = new CSMShadowAttribute(csmLight, prevBias, prevSoftness, prevPcfRadius, prevFilterMode, prevCascadeBlend);
        environment.set(shadowAttr);

        // ModelBatch must be recreated because the shader is compiled with #define MAX_CASCADES N
        WgModelBatch.Config config = new WgModelBatch.Config();
        config.maxDirectionalLights = 1;
        config.maxPointLights = 0;
        modelBatch = new WgModelBatch(config);
        shadowBatch = new WgModelBatch(new WgDepthShaderProvider());

        System.out.println("CSM: " + numCascades + " cascade(s), shadow map " + mapSize + "px");
    }

    private void setCascadeCount(int count) {
        if (count == numCascades) return;
        buildCsmResources(count);
    }

    private void setShadowSize(int index) {
        if (index == shadowSizeIndex) return;
        shadowSizeIndex = index;
        buildCsmResources(numCascades);
    }

    /** Set which camera receives WASD/mouse input. Both cameras remain visible. */
    private void setActiveInput(boolean useDebugCam) {
        debugCamActive = useDebugCam;
        controller.setEnabled(!useDebugCam);
        debugController.setEnabled(useDebugCam);
    }

    private boolean debugLog = false;
    private int debugFrame = 0;

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // Update controllers — only the enabled one processes WASD
        controller.update();
        debugController.update();
        cam.update();
        debugCam.update();

        // Rotate some ducks gently
        int rotateCount = Math.min(15, instances.size - 1);
        for (int i = 1; i < rotateCount; i++) { // skip ground at index 0
            instances.get(i).transform.rotate(Vector3.Y, 12f * delta);
        }

        int halfW = screenWidth / 2;

        // -------- CSM shadow passes (always driven by the user camera, full viewport) --------
        csmLight.begin(cam);

        if(debugLog) {
            if (debugFrame % 120 == 0) {
                float[] splits = csmLight.getCascadeSplitNDC();
                System.out.println("=== CSM Debug (frame " + debugFrame + ") ===");
                System.out.println("  cascadeSplitNDC: [" + splits[0] + ", " + splits[1] + ", " + splits[2] + ", " + splits[3] + "]");
                for (int c = 0; c < numCascades; c++) {
                    OrthographicCamera lc = csmLight.getCascadeCamera(c);
                    System.out.println("  Cascade " + c + ": pos=" + lc.position + " dir=" + lc.direction
                        + " vw=" + lc.viewportWidth + " vh=" + lc.viewportHeight
                        + " near=" + lc.near + " far=" + lc.far);
                }
            }
            debugFrame++;
        }
        for (int i = 0; i < csmLight.getCascadeCount(); i++) {
            csmLight.beginCascade(i);
            shadowBatch.begin(csmLight.getCascadeCamera(i), null, true, RenderPassType.DEPTH_ONLY);
            shadowBatch.render(instances);
            shadowBatch.end();
            csmLight.endCascade();
        }
        csmLight.end();

        // ======== LEFT HALF — User camera with skybox ========
        webgpu.setViewportRectangle(0, 0, halfW, screenHeight);

        modelBatch.begin(cam, Color.BLACK, true);
        modelBatch.render(instances, environment);
        modelBatch.render(lightArrowInstance);
        modelBatch.end();

        if (skyBoxCheck.isChecked()) skyBox.renderPass(cam, false);

        // ======== RIGHT HALF — Debug camera ========
        webgpu.setViewportRectangle(halfW, 0, screenWidth - halfW, screenHeight);

        modelBatch.begin(debugCam, null, false);
        modelBatch.render(instances, environment);
        modelBatch.render(lightArrowInstance);
        modelBatch.end();

        if (skyBoxCheck.isChecked()) skyBox.renderPass(debugCam, false);

        // Debug lines in right half (cascade frustums + user camera frustum)
        shapeRenderer.setProjectionMatrix(debugCam.combined);
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        drawCascadeDebugLines();
        // Draw the user camera's full frustum in the debug view
        drawFrustum(cam, Color.CYAN, Color.MAGENTA);
        shapeRenderer.end();

        // ======== Restore full viewport for HUD ========
        webgpu.setViewportRectangle(0, 0, screenWidth, screenHeight);

        // Divider line between the two halves
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.line(halfW, 0, halfW, screenHeight);
        shapeRenderer.end();

        // -------- Scene2D UI (full screen) --------
        fpsLabel.setText("fps: " + Gdx.graphics.getFramesPerSecond());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < csmLight.getCascadeCount(); i++) {
            OrthographicCamera lc = csmLight.getCascadeCamera(i);
            float[] sd = csmLight.getSplitDistances();
            sb.append("Cascade ").append(i)
                    .append(": ").append(String.format("%.0f", sd[i]))
                    .append("-").append(String.format("%.0f", sd[i + 1]))
                    .append("  ").append(String.format("%.0f", lc.viewportWidth))
                    .append("x").append(String.format("%.0f", lc.viewportHeight));
            if (i < csmLight.getCascadeCount() - 1) sb.append("\n");
        }
        cascadeInfoLabel.setText(sb.toString());

        // Show computed clip-space biases per cascade
        StringBuilder biasSb = new StringBuilder();
        float[] biases = csmLight.getCascadeBiases();
        for (int i = 0; i < csmLight.getCascadeCount(); i++) {
            OrthographicCamera lc = csmLight.getCascadeCamera(i);
            float worldBias = biases[i] * lc.far; // convert clip-space back to world for display
            biasSb.append("C").append(i).append(" bias: ")
                    .append(String.format("%.5f", biases[i])).append(" clip, ")
                    .append(String.format("%.2f", worldBias)).append(" world");
            if (csmLight.hasCascadeBiasOverride(i)) biasSb.append(" [override]");
            if (i < csmLight.getCascadeCount() - 1) biasSb.append("\n");
            // Update per-cascade value labels (auto values when not overridden)
            if (!cascadeBiasOverrideChecks[i].isChecked()) {
                cascadeBiasValueLabels[i].setText(String.format("%.2f", worldBias) + "w");
            }
        }
        biasInfoLabel.setText(biasSb.toString());

        stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
        stage.draw();
    }

    /** Draw cascade debug lines (light camera frustums + view sub-frustums). */
    private void drawCascadeDebugLines() {
        // Each cascade's orthographic light camera frustum
        for (int i = 0; i < csmLight.getCascadeCount(); i++) {
            if (debugCascadeFilter >= 0 && debugCascadeFilter != i) continue;
            OrthographicCamera lc = csmLight.getCascadeCamera(i);
            Color cc = CASCADE_COLORS[i % CASCADE_COLORS.length];
            drawOrthoFrustum(lc, cc);
        }
        // Each cascade's view sub-frustum (slice of the user camera's frustum)
        for (int i = 0; i < csmLight.getCascadeCount(); i++) {
            if (debugCascadeFilter >= 0 && debugCascadeFilter != i) continue;
            Color cc = CASCADE_COLORS[i % CASCADE_COLORS.length];
            Vector3[] corners = csmLight.getLastFrustumCorners(cam, i);
            drawFrustumCorners(corners, cc);
        }
    }

    /**
     * Draw the 12 edges of a camera frustum using the ShapeRenderer (must be inside a begin/end pair).
     * Near plane edges use {@code nearColor}, far plane and connecting edges use {@code farColor}.
     *
     * <p>libGDX {@link Frustum#planePoints} layout:
     * <pre>
     *   0–3 = near plane (bottom-left, top-left, top-right, bottom-right)
     *   4–7 = far plane  (bottom-left, top-left, top-right, bottom-right)
     * </pre>
     */
    private void drawFrustum(PerspectiveCamera camera, Color nearColor, Color farColor) {
        Vector3[] pts = camera.frustum.planePoints;

        // Near quad
        shapeRenderer.setColor(nearColor);
        shapeRenderer.line(pts[0].x, pts[0].y, pts[0].z, pts[1].x, pts[1].y, pts[1].z);
        shapeRenderer.line(pts[1].x, pts[1].y, pts[1].z, pts[2].x, pts[2].y, pts[2].z);
        shapeRenderer.line(pts[2].x, pts[2].y, pts[2].z, pts[3].x, pts[3].y, pts[3].z);
        shapeRenderer.line(pts[3].x, pts[3].y, pts[3].z, pts[0].x, pts[0].y, pts[0].z);

        // Far quad
        shapeRenderer.setColor(farColor);
        shapeRenderer.line(pts[4].x, pts[4].y, pts[4].z, pts[5].x, pts[5].y, pts[5].z);
        shapeRenderer.line(pts[5].x, pts[5].y, pts[5].z, pts[6].x, pts[6].y, pts[6].z);
        shapeRenderer.line(pts[6].x, pts[6].y, pts[6].z, pts[7].x, pts[7].y, pts[7].z);
        shapeRenderer.line(pts[7].x, pts[7].y, pts[7].z, pts[4].x, pts[4].y, pts[4].z);

        // Connecting edges (near → far)
        shapeRenderer.setColor(farColor);
        shapeRenderer.line(pts[0].x, pts[0].y, pts[0].z, pts[4].x, pts[4].y, pts[4].z);
        shapeRenderer.line(pts[1].x, pts[1].y, pts[1].z, pts[5].x, pts[5].y, pts[5].z);
        shapeRenderer.line(pts[2].x, pts[2].y, pts[2].z, pts[6].x, pts[6].y, pts[6].z);
        shapeRenderer.line(pts[3].x, pts[3].y, pts[3].z, pts[7].x, pts[7].y, pts[7].z);
    }

    /**
     * Draw the frustum wireframe of an orthographic camera (e.g. a cascade's light camera).
     * The frustum is an axis-aligned box in the camera's local space, computed from its
     * position, direction, up, viewportWidth, viewportHeight, near, and far.
     */
    private void drawOrthoFrustum(OrthographicCamera cam, Color color) {
        shapeRenderer.setColor(color);

        float hw = cam.viewportWidth * 0.5f;
        float hh = cam.viewportHeight * 0.5f;

        // Build the camera's right and up vectors
        Vector3 dir = cam.direction;
        Vector3 up = cam.up;
        // right = dir × up
        float rx = dir.y * up.z - dir.z * up.y;
        float ry = dir.z * up.x - dir.x * up.z;
        float rz = dir.x * up.y - dir.y * up.x;

        // 8 corners: near face (4) and far face (4)
        // Center of near face = position + direction * near
        // Center of far face  = position + direction * far
        float nCx = cam.position.x + dir.x * cam.near;
        float nCy = cam.position.y + dir.y * cam.near;
        float nCz = cam.position.z + dir.z * cam.near;
        float fCx = cam.position.x + dir.x * cam.far;
        float fCy = cam.position.y + dir.y * cam.far;
        float fCz = cam.position.z + dir.z * cam.far;

        // Near face corners: center ± right*hw ± up*hh
        float n0x = nCx - rx*hw - up.x*hh, n0y = nCy - ry*hw - up.y*hh, n0z = nCz - rz*hw - up.z*hh;
        float n1x = nCx + rx*hw - up.x*hh, n1y = nCy + ry*hw - up.y*hh, n1z = nCz + rz*hw - up.z*hh;
        float n2x = nCx + rx*hw + up.x*hh, n2y = nCy + ry*hw + up.y*hh, n2z = nCz + rz*hw + up.z*hh;
        float n3x = nCx - rx*hw + up.x*hh, n3y = nCy - ry*hw + up.y*hh, n3z = nCz - rz*hw + up.z*hh;

        // Far face corners
        float f0x = fCx - rx*hw - up.x*hh, f0y = fCy - ry*hw - up.y*hh, f0z = fCz - rz*hw - up.z*hh;
        float f1x = fCx + rx*hw - up.x*hh, f1y = fCy + ry*hw - up.y*hh, f1z = fCz + rz*hw - up.z*hh;
        float f2x = fCx + rx*hw + up.x*hh, f2y = fCy + ry*hw + up.y*hh, f2z = fCz + rz*hw + up.z*hh;
        float f3x = fCx - rx*hw + up.x*hh, f3y = fCy - ry*hw + up.y*hh, f3z = fCz - rz*hw + up.z*hh;

        // Near quad
        shapeRenderer.line(n0x, n0y, n0z, n1x, n1y, n1z);
        shapeRenderer.line(n1x, n1y, n1z, n2x, n2y, n2z);
        shapeRenderer.line(n2x, n2y, n2z, n3x, n3y, n3z);
        shapeRenderer.line(n3x, n3y, n3z, n0x, n0y, n0z);

        // Far quad
        shapeRenderer.line(f0x, f0y, f0z, f1x, f1y, f1z);
        shapeRenderer.line(f1x, f1y, f1z, f2x, f2y, f2z);
        shapeRenderer.line(f2x, f2y, f2z, f3x, f3y, f3z);
        shapeRenderer.line(f3x, f3y, f3z, f0x, f0y, f0z);

        // Connecting edges
        shapeRenderer.line(n0x, n0y, n0z, f0x, f0y, f0z);
        shapeRenderer.line(n1x, n1y, n1z, f1x, f1y, f1z);
        shapeRenderer.line(n2x, n2y, n2z, f2x, f2y, f2z);
        shapeRenderer.line(n3x, n3y, n3z, f3x, f3y, f3z);
    }

    /**
     * Draw a frustum wireframe from 8 corner points.
     * Corners 0–3 are the near face, 4–7 are the far face.
     */
    private void drawFrustumCorners(Vector3[] pts, Color color) {
        shapeRenderer.setColor(color);

        // Near quad
        shapeRenderer.line(pts[0].x, pts[0].y, pts[0].z, pts[1].x, pts[1].y, pts[1].z);
        shapeRenderer.line(pts[1].x, pts[1].y, pts[1].z, pts[2].x, pts[2].y, pts[2].z);
        shapeRenderer.line(pts[2].x, pts[2].y, pts[2].z, pts[3].x, pts[3].y, pts[3].z);
        shapeRenderer.line(pts[3].x, pts[3].y, pts[3].z, pts[0].x, pts[0].y, pts[0].z);

        // Far quad
        shapeRenderer.line(pts[4].x, pts[4].y, pts[4].z, pts[5].x, pts[5].y, pts[5].z);
        shapeRenderer.line(pts[5].x, pts[5].y, pts[5].z, pts[6].x, pts[6].y, pts[6].z);
        shapeRenderer.line(pts[6].x, pts[6].y, pts[6].z, pts[7].x, pts[7].y, pts[7].z);
        shapeRenderer.line(pts[7].x, pts[7].y, pts[7].z, pts[4].x, pts[4].y, pts[4].z);

        // Connecting edges (near → far)
        shapeRenderer.line(pts[0].x, pts[0].y, pts[0].z, pts[4].x, pts[4].y, pts[4].z);
        shapeRenderer.line(pts[1].x, pts[1].y, pts[1].z, pts[5].x, pts[5].y, pts[5].z);
        shapeRenderer.line(pts[2].x, pts[2].y, pts[2].z, pts[6].x, pts[6].y, pts[6].z);
        shapeRenderer.line(pts[3].x, pts[3].y, pts[3].z, pts[7].x, pts[7].y, pts[7].z);
    }



    @Override
    public void resize(int width, int height) {
        screenWidth = width;
        screenHeight = height;
        int halfW = width / 2;
        cam.viewportWidth  = halfW;
        cam.viewportHeight = height;
        cam.update();
        debugCam.viewportWidth  = halfW;
        debugCam.viewportHeight = height;
        debugCam.update();
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (shadowBatch != null) shadowBatch.dispose();
        stage.dispose();
        skin.dispose();
        shapeRenderer.dispose();
        for (Disposable d : disposables) d.dispose();
        if (csmLight != null) csmLight.dispose();
    }

    /**
     * Generate a clear-sky cubemap that matches the ProceduralSkyBox gradient.
     * Each face pixel is computed from the world-space view direction.
     */
    private static WgCubemap buildSkyCubemap(int size, Color zenith, Color horizon, Color ground) {
        // For each cubemap face, compute the world-space direction per pixel, then derive color from elevation.
        Pixmap[] faces = new Pixmap[6];
        for (int face = 0; face < 6; face++) {
            faces[face] = new Pixmap(size, size, Pixmap.Format.RGBA8888);
            java.nio.ByteBuffer buf = faces[face].getPixels();
            for (int y = 0; y < size; y++) {
                float v = (y + 0.5f) / size * 2f - 1f;
                for (int x = 0; x < size; x++) {
                    float u = (x + 0.5f) / size * 2f - 1f;

                    // Compute world-space direction for this face
                    float dx, dy, dz;
                    switch (face) {
                        case 0: dx =  1; dy = -v; dz = -u; break; // +X
                        case 1: dx = -1; dy = -v; dz =  u; break; // -X
                        case 2: dx =  u; dy =  1; dz =  v; break; // +Y
                        case 3: dx =  u; dy = -1; dz = -v; break; // -Y
                        case 4: dx =  u; dy = -v; dz =  1; break; // +Z
                        default:dx = -u; dy = -v; dz = -1; break; // -Z
                    }
                    float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                    float elevation = dy / len;

                    // Same gradient logic as procedural_sky.wgsl
                    float skyFactor = Math.max(elevation, 0f);
                    float gradientT = (float) Math.pow(skyFactor, 0.6);
                    float r = horizon.r + (zenith.r - horizon.r) * gradientT;
                    float g = horizon.g + (zenith.g - horizon.g) * gradientT;
                    float b = horizon.b + (zenith.b - horizon.b) * gradientT;

                    // Below horizon: blend to ground color
                    float groundFactor = Math.max(-elevation, 0f);
                    float groundT = (float) Math.pow(groundFactor, 0.4);
                    r = r + (ground.r - r) * groundT;
                    g = g + (ground.g - g) * groundT;
                    b = b + (ground.b - b) * groundT;

                    // Write RGBA8888 directly to buffer (avoids setColor + drawPixel interop)
                    buf.put((byte) (int) (r * 255));
                    buf.put((byte) (int) (g * 255));
                    buf.put((byte) (int) (b * 255));
                    buf.put((byte) 255);
                }
            }
            buf.flip();
        }
        WgCubemap cubemap = new WgCubemap(faces[0], faces[1], faces[2], faces[3], faces[4], faces[5]);
        for (Pixmap p : faces) p.dispose();
        return cubemap;
    }
}

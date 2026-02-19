package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.math.collision.BoundingBox;
import java.nio.ByteBuffer;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.CameraInputController;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g3d.WgModelBatch;
import com.monstrous.gdx.webgpu.graphics.g3d.utils.WgModelBuilder;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenReader;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.graphics.utils.WgShapeRenderer;
import com.monstrous.gdx.webgpu.graphics.utils.WgFrameBuffer;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

/**
 * Test for WgScreenReader with pixel picking and 3D model rendering.
 *
 * Controls: - Mouse to look around (via CameraInputController) - WASD to move camera - Hover mouse over the 3D model to
 * read pixel colors - Press S to take a screenshot - Press P to toggle pixel preview - F11 to toggle fullscreen
 */
public class ScreenReaderTest extends GdxTest {

    private static final int PREVIEW_SQUARE_SIZE = 128;
    private static final int PREVIEW_SQUARE_MARGIN = 10;

    private PerspectiveCamera cam;
    private CameraInputController inputController;
    private WgModelBatch modelBatch;
    private WgShapeRenderer shapeRenderer;
    private WgSpriteBatch colorBatch;
    private WgSpriteBatch batch;
    private WgBitmapFont font;
    private OrthographicCamera orthoCam;
    private Model model;
    private ModelInstance[] modelInstances;
    private Environment environment;
    private WgScreenReader screenReader;
    private WgFrameBuffer framebuffer;

    // Pixel preview state
    private int readPixelR = 0;
    private int readPixelG = 0;
    private int readPixelB = 0;
    private int readPixelA = 255;

    private boolean showPixelPreview = true;

    String rgbaFormat = null;

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont(Gdx.files.internal("data/lsans-15.fnt"), false);

        modelBatch = new WgModelBatch();
        shapeRenderer = new WgShapeRenderer();
        colorBatch = new WgSpriteBatch();
        screenReader = new WgScreenReader();

        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();
        framebuffer = new WgFrameBuffer(width, height, true);

        // Setup 2D camera for UI rendering
        orthoCam = new OrthographicCamera(width, height);
        orthoCam.update();

        // Setup environment
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, .4f, .4f, .4f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        // Setup camera
        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(6f, 6f, 6f);
        cam.lookAt(0, 0, 0);
        cam.near = 0.1f;
        cam.far = 150f;
        cam.update();

        // Create 3D models with different colors for testing
        ModelBuilder modelBuilder = new WgModelBuilder();

        // Green box
        Model greenBoxModel = modelBuilder.createBox(3f, 3f, 3f,
                new Material(ColorAttribute.createDiffuse(Color.GREEN)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // Red sphere
        Model redSphereModel = modelBuilder.createSphere(2f, 2f, 2f, 32, 32,
                new Material(ColorAttribute.createDiffuse(Color.RED)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // Blue cylinder
        Model blueCylinderModel = modelBuilder.createCylinder(1.5f, 3f, 1.5f, 32,
                new Material(ColorAttribute.createDiffuse(Color.BLUE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // Yellow cube (smaller)
        Model yellowCubeModel = modelBuilder.createBox(1f, 1f, 1f,
                new Material(ColorAttribute.createDiffuse(Color.YELLOW)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);

        // Create model instances
        modelInstances = new ModelInstance[4];
        modelInstances[0] = new ModelInstance(greenBoxModel);
        modelInstances[0].transform.setToTranslation(-4, 0, 0);

        modelInstances[1] = new ModelInstance(redSphereModel);
        modelInstances[1].transform.setToTranslation(4, 0, 0);

        modelInstances[2] = new ModelInstance(blueCylinderModel);
        modelInstances[2].transform.setToTranslation(0, 0, -4);

        modelInstances[3] = new ModelInstance(yellowCubeModel);
        modelInstances[3].transform.setToTranslation(0, 4, 0);

        // Keep reference to one model for disposal
        model = greenBoxModel;

        // Setup input
        inputController = new CameraInputController(cam);
        Gdx.input.setInputProcessor(new InputMultiplexer(this, inputController));
        rgbaFormat = String.format("RGBA: (%d, %d, %d, %d) | Hex: #%02X%02X%02X%02X", readPixelR, readPixelG,
                readPixelB, readPixelA, readPixelR, readPixelG, readPixelB, readPixelA);

    }

    @Override
    public void render() {
        WgScreenUtils.clear(0.2f, 0.2f, 0.2f, 1f, true);

        // Handle screenshot
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            takeScreenshot();
        }

        // Handle pixel preview toggle
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            showPixelPreview = !showPixelPreview;
            Gdx.app.log("ScreenReaderPixelPickingTest", "Pixel preview: " + (showPixelPreview ? "ON" : "OFF"));
        }

        inputController.update();

        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        // Update ortho camera to screen dimensions
        orthoCam.setToOrtho(false, width, height); // y-up
        orthoCam.position.set(width / 2f, height / 2f, 0);
        orthoCam.lookAt(width / 2f, height / 2f, -1);
        orthoCam.up.set(0, 1, 0);
        orthoCam.update();

        // Handle mouse click for pixel sampling
        if (Gdx.input.justTouched()) {
            readPixelAtMouse(Gdx.input.getX(), Gdx.input.getY());
        }

        cam.update();
        // Render to framebuffer
        framebuffer.begin();
        WgScreenUtils.clear(0.2f, 0.2f, 0.2f, 1f, true);
        modelBatch.begin(cam);
        for (ModelInstance instance : modelInstances) {
            modelBatch.render(instance, environment);
        }
        modelBatch.end();
        framebuffer.end();

        // Render framebuffer texture to screen
        colorBatch.setProjectionMatrix(orthoCam.combined);
        colorBatch.begin();
        colorBatch.draw(framebuffer.getColorBufferTexture(), 0, 0, width, height);
        colorBatch.end();

        // Render pixel preview square and info overlay
        renderPixelPreview();

        batch.begin();
        font.draw(batch, "fps: " + Gdx.graphics.getFramesPerSecond(), 10, 20);
        font.draw(batch, rgbaFormat, 10, 40);
        font.draw(batch, "Click mouse to pick pixel color", 10, 60);
        font.draw(batch, "Press SPACE to take a screenshot", 10, 80);
        batch.end();
    }

    private void readPixelAtMouse(int mouseX, int mouseY) {
        screenReader.readPixelAsync(framebuffer, mouseX, mouseY, new WgScreenReader.PixelReadCallback() {
            @Override
            public void onPixelRead(int r, int g, int b, int a) {
                readPixelR = r;
                readPixelG = g;
                readPixelB = b;
                readPixelA = a;
                rgbaFormat = String.format("RGBA: (%d, %d, %d, %d) | Hex: #%02X%02X%02X%02X", readPixelR, readPixelG,
                        readPixelB, readPixelA, readPixelR, readPixelG, readPixelB, readPixelA);
            }
        });
    }

    private void renderPixelPreview() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        // Update ortho camera to screen dimensions
        orthoCam.setToOrtho(false, width, height);
        orthoCam.position.set(width / 2f, height / 2f, 0);
        orthoCam.lookAt(width / 2f, height / 2f, -1);
        orthoCam.up.set(0, 1, 0);
        orthoCam.update();

        shapeRenderer.setProjectionMatrix(orthoCam.combined);

        // Draw the preview square in the top-right corner
        int squareX = width - PREVIEW_SQUARE_SIZE - PREVIEW_SQUARE_MARGIN;
        int squareY = height - PREVIEW_SQUARE_SIZE - PREVIEW_SQUARE_MARGIN;

        // Draw background
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(readPixelR / 255f, readPixelG / 255f, readPixelB / 255f, readPixelA / 255f);
        shapeRenderer.rect(squareX + 4, squareY + 4, PREVIEW_SQUARE_SIZE - 8, PREVIEW_SQUARE_SIZE - 8);
        shapeRenderer.end();

        // Draw border
        shapeRenderer.begin(WgShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(1f, 1f, 1f, 0.8f);
        shapeRenderer.rect(squareX, squareY, PREVIEW_SQUARE_SIZE, PREVIEW_SQUARE_SIZE);
        shapeRenderer.end();
    }

    private void takeScreenshot() {
        screenReader.readPixelsAsync(framebuffer, new WgScreenReader.PixelsReadCallback() {
            @Override
            public void onPixelsRead(ByteBuffer data, int width, int height) {
                if (data != null) {
                    try {
                        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
                        pixmap.setPixels(data);
                        String filename = "screenshot_" + System.currentTimeMillis() + ".png";
                        FileHandle file = Gdx.files.local(filename);
                        PixmapIO.writePNG(file, pixmap);
                        Gdx.app.log("ScreenReaderPixelPickingTest", "Screenshot saved: " + filename);
                        pixmap.dispose();
                    } catch (Exception e) {
                        Gdx.app.error("ScreenReaderPixelPickingTest", "Failed to save screenshot: " + e.getMessage(),
                                e);
                    }
                } else {
                    Gdx.app.error("ScreenReaderPixelPickingTest", "Failed to read pixels");
                }
            }
        });
    }

    @Override
    public void resize(int width, int height) {
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();

        orthoCam.viewportWidth = width;
        orthoCam.viewportHeight = height;
        orthoCam.update();

        // Recreate framebuffer with new size
        framebuffer.dispose();
        framebuffer = new WgFrameBuffer(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        modelBatch.dispose();
        shapeRenderer.dispose();
        colorBatch.dispose();
        model.dispose();
        screenReader.dispose();
        framebuffer.dispose();
    }
}

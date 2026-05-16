package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgShaderProgram;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;

import java.nio.ByteBuffer;

/**
 * Simple top-down 2D fog-of-war demo with soft radial vision and persistent explored memory.
 */
public class FogOfWar2DTest extends GdxTest {

    private static final float WORLD_WIDTH = 120f;
    private static final float WORLD_HEIGHT = 80f;
    private static final float CAMERA_WIDTH = 40f;
    private static final float CAMERA_HEIGHT = 22.5f;

    private static final int FOG_MAP_WIDTH = 256;
    private static final int FOG_MAP_HEIGHT = 160;

    private static final float PLAYER_SPEED = 24f;
    private static final float DEFAULT_VISION_RADIUS = 10f;
    private static final float MIN_VISION_RADIUS = 3f;
    private static final float MAX_VISION_RADIUS = 28f;
    private static final float VISION_SOFTNESS = 6f;
    private static final float MAX_FOG_ALPHA = 0.9f;

    private WgSpriteBatch batch;
    private WgBitmapFont font;
    private WgShaderProgram fogShader;

    private OrthographicCamera worldCamera;
    private FitViewport worldViewport;
    private ScreenViewport hudViewport;

    private WgTexture whitePixel;
    private WgTexture fogTexture;

    private final Vector2 player = new Vector2(10f, 10f);
    private final Vector2 move = new Vector2();
    private float visionRadius = DEFAULT_VISION_RADIUS;
    private boolean showFog = true;

    private float[] explored;
    private ByteBuffer fogPixels;

    @Override
    public void create() {
        batch = new WgSpriteBatch();
        font = new WgBitmapFont();
        fogShader = new WgShaderProgram(Gdx.files.internal("data/wgsl/fog-of-war-smooth.wgsl"));

        worldCamera = new OrthographicCamera();
        worldViewport = new FitViewport(CAMERA_WIDTH, CAMERA_HEIGHT, worldCamera);
        hudViewport = new ScreenViewport();
        worldViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
        hudViewport.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);

        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixel.setColor(Color.WHITE);
        pixel.fill();
        whitePixel = new WgTexture(pixel, "white_pixel");
        pixel.dispose();

        Pixmap fog = new Pixmap(FOG_MAP_WIDTH, FOG_MAP_HEIGHT, Pixmap.Format.RGBA8888);
        fog.setColor(1f, 1f, 1f, 1f);
        fog.fill();
        fogTexture = new WgTexture(fog, "fog_map", false);
        fogTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fogTexture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        fog.dispose();

        explored = new float[FOG_MAP_WIDTH * FOG_MAP_HEIGHT];
        fogPixels = ByteBuffer.allocateDirect(FOG_MAP_WIDTH * FOG_MAP_HEIGHT * 4);
    }

    @Override
    public void render() {
        float delta = Math.min(0.1f, Gdx.graphics.getDeltaTime());
        handleInput(delta);
        updateCamera();

        WgScreenUtils.clear(0.08f, 0.1f, 0.12f, 1f);

        worldViewport.apply();
        batch.setProjectionMatrix(worldCamera.combined);
        batch.begin();
        drawWorld();
        drawPlayer();
        batch.end();

        updateFogTexture();

        if (showFog) {
            batch.setProjectionMatrix(worldCamera.combined);
            batch.begin();
            batch.setShader(fogShader);
            batch.draw(fogTexture, 0, 0, WORLD_WIDTH, WORLD_HEIGHT);
            batch.setShader((WgShaderProgram) null);
            batch.end();
        }

        hudViewport.apply();
        batch.setProjectionMatrix(hudViewport.getCamera().combined);
        batch.begin();
        font.draw(batch, "FogOfWar2DTest", 14, hudViewport.getWorldHeight() - 12);
        font.draw(batch, "Move: WASD/Arrows  |  Radius: [ and ]  |  Reset explored: F2", 14,
                hudViewport.getWorldHeight() - 34);
        font.draw(batch, "Toggle fog: F1  |  Vision radius: " + (int) visionRadius, 14,
                hudViewport.getWorldHeight() - 56);
        batch.end();
    }

    private void handleInput(float delta) {
        move.setZero();

        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))
            move.x -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT))
            move.x += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))
            move.y += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))
            move.y -= 1f;

        if (!move.isZero()) {
            move.nor().scl(PLAYER_SPEED * delta);
            player.add(move);
        }

        player.x = MathUtils.clamp(player.x, 0f, WORLD_WIDTH);
        player.y = MathUtils.clamp(player.y, 0f, WORLD_HEIGHT);

        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET))
            visionRadius = Math.max(MIN_VISION_RADIUS, visionRadius - 1f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET))
            visionRadius = Math.min(MAX_VISION_RADIUS, visionRadius + 1f);
        if (Gdx.input.isKeyJustPressed(Input.Keys.F2))
            resetExplored();
        if (Gdx.input.isKeyJustPressed(Input.Keys.F1))
            showFog = !showFog;
    }

    private void updateCamera() {
        float halfW = worldViewport.getWorldWidth() * 0.5f;
        float halfH = worldViewport.getWorldHeight() * 0.5f;
        worldCamera.position.set(
                MathUtils.clamp(player.x, halfW, WORLD_WIDTH - halfW),
                MathUtils.clamp(player.y, halfH, WORLD_HEIGHT - halfH),
                0f);
        worldCamera.update();
    }

    private void drawWorld() {
        float tile = 4f;
        float viewLeft = worldCamera.position.x - worldViewport.getWorldWidth() * 0.5f;
        float viewBottom = worldCamera.position.y - worldViewport.getWorldHeight() * 0.5f;
        float viewRight = worldCamera.position.x + worldViewport.getWorldWidth() * 0.5f;
        float viewTop = worldCamera.position.y + worldViewport.getWorldHeight() * 0.5f;

        int startX = Math.max(0, (int) Math.floor(viewLeft / tile));
        int endX = Math.min((int) (WORLD_WIDTH / tile) - 1, (int) Math.ceil(viewRight / tile));
        int startY = Math.max(0, (int) Math.floor(viewBottom / tile));
        int endY = Math.min((int) (WORLD_HEIGHT / tile) - 1, (int) Math.ceil(viewTop / tile));

        for (int yi = startY; yi <= endY; yi++) {
            float y = yi * tile;
            for (int xi = startX; xi <= endX; xi++) {
                float x = xi * tile;
                int parity = ((int) (x / tile) + (int) (y / tile)) & 1;
                if (parity == 0)
                    batch.setColor(0.34f, 0.42f, 0.5f, 1f);
                else
                    batch.setColor(0.28f, 0.36f, 0.43f, 1f);
                batch.draw(whitePixel, x, y, tile, tile);
            }
        }

        // Decorative walls to give explored memory some spatial context.
        batch.setColor(0.12f, 0.12f, 0.14f, 1f);
        drawRectIfVisible(18, 12, 3, 44, viewLeft, viewBottom, viewRight, viewTop);
        drawRectIfVisible(35, 22, 36, 3, viewLeft, viewBottom, viewRight, viewTop);
        drawRectIfVisible(60, 40, 3, 30, viewLeft, viewBottom, viewRight, viewTop);
        drawRectIfVisible(84, 10, 3, 50, viewLeft, viewBottom, viewRight, viewTop);
        drawRectIfVisible(94, 58, 20, 3, viewLeft, viewBottom, viewRight, viewTop);

        batch.setColor(Color.WHITE);
    }

    private void drawRectIfVisible(float x, float y, float w, float h, float viewLeft, float viewBottom, float viewRight,
            float viewTop) {
        if (x + w < viewLeft || x > viewRight || y + h < viewBottom || y > viewTop)
            return;
        batch.draw(whitePixel, x, y, w, h);
    }

    private void drawPlayer() {
        batch.setColor(1f, 0.82f, 0.2f, 1f);
        batch.draw(whitePixel, player.x - 0.8f, player.y - 0.8f, 1.6f, 1.6f);
        batch.setColor(Color.WHITE);
    }

    private void updateFogTexture() {
        int stride = FOG_MAP_WIDTH * 4;

        for (int y = 0; y < FOG_MAP_HEIGHT; y++) {
            float worldY = ((y + 0.5f) / FOG_MAP_HEIGHT) * WORLD_HEIGHT;
            int dstY = FOG_MAP_HEIGHT - 1 - y; // SpriteBatch draw flips V for textures.
            for (int x = 0; x < FOG_MAP_WIDTH; x++) {
                int index = y * FOG_MAP_WIDTH + x;
                float worldX = ((x + 0.5f) / FOG_MAP_WIDTH) * WORLD_WIDTH;

                float dist = Vector2.dst(worldX, worldY, player.x, player.y);
                float currentVision = 1f - smoothStep(visionRadius - VISION_SOFTNESS, visionRadius, dist);
                currentVision = MathUtils.clamp(currentVision, 0f, 1f);

                if (currentVision > explored[index])
                    explored[index] = currentVision;

                float exploredVisibility = explored[index] * 0.55f;
                float visible = Math.max(currentVision, exploredVisibility);
                float fogAlpha = MathUtils.clamp(1f - visible, 0f, MAX_FOG_ALPHA);
                byte fogValue = (byte) MathUtils.round(fogAlpha * 255f);

                int base = dstY * stride + x * 4;
                fogPixels.put(base, fogValue);
                fogPixels.put(base + 1, fogValue);
                fogPixels.put(base + 2, fogValue);
                fogPixels.put(base + 3, (byte) 255);
            }
        }

        fogPixels.position(0);
        fogPixels.limit(fogPixels.capacity());
        fogTexture.load(fogPixels, 4, FOG_MAP_WIDTH, FOG_MAP_HEIGHT);
    }

    private static float smoothStep(float edge0, float edge1, float x) {
        float t = MathUtils.clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private void resetExplored() {
        for (int i = 0; i < explored.length; i++)
            explored[i] = 0f;
    }

    @Override
    public void resize(int width, int height) {
        worldViewport.update(width, height, true);
        hudViewport.update(width, height, true);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        fogShader.dispose();
        whitePixel.dispose();
        fogTexture.dispose();
    }
}

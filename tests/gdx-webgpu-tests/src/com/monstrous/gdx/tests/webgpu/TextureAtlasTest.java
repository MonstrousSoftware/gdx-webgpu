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

package com.monstrous.gdx.tests.webgpu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.g2d.WgBitmapFont;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;
import com.monstrous.gdx.webgpu.graphics.g2d.WgTextureAtlas;

public class TextureAtlasTest extends GdxTest {
    WgSpriteBatch batch;
    Sprite badlogic, badlogicSmall, star;
    TextureAtlas atlas;
    TextureAtlas jumpAtlas;
    Animation<TextureRegion> jumpAnimation;
    WgBitmapFont font;
    float time = 0;
    WgSpriteBatch renderer;

    public void create() {
        batch = new WgSpriteBatch();
        renderer = new WgSpriteBatch();

        atlas = new WgTextureAtlas(Gdx.files.internal("data/pack.atlas"));
        jumpAtlas = new WgTextureAtlas(Gdx.files.internal("data/jump.txt"));

        jumpAnimation = new Animation<TextureRegion>(0.25f, jumpAtlas.findRegions("ALIEN_JUMP_"));

        badlogic = atlas.createSprite("badlogicslice");
        badlogic.setPosition(50, 50);

        // badlogicSmall = atlas.createSprite("badlogicsmall");
        badlogicSmall = atlas.createSprite("badlogicsmall-rotated");
        badlogicSmall.setPosition(10, 10);

        AtlasRegion region = atlas.findRegion("badlogicsmall");
        System.out.println("badlogicSmall original size: " + region.originalWidth + ", " + region.originalHeight);
        System.out.println("badlogicSmall packed size: " + region.packedWidth + ", " + region.packedHeight);

        star = atlas.createSprite("particle-star");
        star.setPosition(10, 70);

        font = new WgBitmapFont(); // Gdx.files.internal("data/font.fnt"), atlas.findRegion("font"), false);

        Gdx.input.setInputProcessor(new InputAdapter() {
            public boolean keyUp(int keycode) {
                if (keycode == Keys.UP) {
                    badlogicSmall.flip(false, true);
                } else if (keycode == Keys.RIGHT) {
                    badlogicSmall.flip(true, false);
                } else if (keycode == Keys.LEFT) {
                    badlogicSmall.setSize(512, 512);
                } else if (keycode == Keys.DOWN) {
                    badlogicSmall.rotate90(true);
                }
                return super.keyUp(keycode);
            }
        });
    }

    public void render() {
        time += Gdx.graphics.getDeltaTime();

        // renderer.begin(ShapeType.Line);
        // //renderer.rect(10, 10, 256, 256);
        // renderer.end();

        batch.begin();
        // badlogic.draw(batch);
        // star.draw(batch);
        // font.draw(batch, "This font was packed!", 26, 65);
        badlogicSmall.draw(batch);
        batch.draw(jumpAnimation.getKeyFrame(time, true), 100, 100);
        batch.end();
    }

    public void dispose() {
        atlas.dispose();
        jumpAtlas.dispose();
        batch.dispose();
        font.dispose();
    }
}

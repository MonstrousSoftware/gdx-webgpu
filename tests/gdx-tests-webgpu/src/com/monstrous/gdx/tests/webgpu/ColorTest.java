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
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

public class ColorTest extends GdxTest {
	WgStage stage;
    WgSkin skin;

	@Override
	public void create () {
		stage = new WgStage(new ScreenViewport());
		Gdx.input.setInputProcessor(stage);

		skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

		//skin.add("default-font", new BitmapFont(Gdx.files.internal("data/lsans-32.fnt"), false));

		Table root = new Table();
		stage.addActor(root);
		root.setFillParent(true);

		Table column1 = new Table(skin);
		column1.add("WHITE", "default-font", Color.WHITE).row();
		column1.add("LIGHT_GRAY", "default-font", Color.LIGHT_GRAY).row();
		column1.add("GRAY", "default-font", Color.GRAY).row();
		column1.add("DARK_GRAY", "default-font", Color.DARK_GRAY).row();

		column1.add("BLUE", "default-font", Color.BLUE).row();
		column1.add("NAVY", "default-font", Color.NAVY).row();
		column1.add("ROYAL", "default-font", Color.ROYAL).row();
		column1.add("SLATE", "default-font", Color.SLATE).row();
		column1.add("SKY", "default-font", Color.SKY).row();
		column1.add("CYAN", "default-font", Color.CYAN).row();
		column1.add("TEAL", "default-font", Color.TEAL).row();

		Table column2 = new Table(skin);
		column2.add("GREEN", "default-font", Color.GREEN).row();
		column2.add("CHARTREUSE", "default-font", Color.CHARTREUSE).row();
		column2.add("LIME", "default-font", Color.LIME).row();
		column2.add("FOREST", "default-font", Color.FOREST).row();
		column2.add("OLIVE", "default-font", Color.OLIVE).row();

		column2.add("YELLOW", "default-font", Color.YELLOW).row();
		column2.add("GOLD", "default-font", Color.GOLD).row();
		column2.add("GOLDENROD", "default-font", Color.GOLDENROD).row();
		column2.add("ORANGE", "default-font", Color.ORANGE).row();

		column2.add("BROWN", "default-font", Color.BROWN).row();
		column2.add("TAN", "default-font", Color.TAN).row();
		column2.add("FIREBRICK", "default-font", Color.FIREBRICK).row();

		Table column3 = new Table(skin);
		column3.add("RED", "default-font", Color.RED).row();
		column3.add("SCARLET", "default-font", Color.SCARLET).row();
		column3.add("CORAL", "default-font", Color.CORAL).row();
		column3.add("SALMON", "default-font", Color.SALMON).row();
		column3.add("PINK", "default-font", Color.PINK).row();
		column3.add("MAGENTA", "default-font", Color.MAGENTA).row();

		column3.add("PURPLE", "default-font", Color.PURPLE).row();
		column3.add("VIOLET", "default-font", Color.VIOLET).row();
		column3.add("MAROON", "default-font", Color.MAROON).row();

		root.add(column1);
		root.add(column2);
		root.add(column3);
	}

	@Override
	public void render () {
		WgScreenUtils.clear(0.2f, 0.2f, 0.2f, 1);

		stage.act(Math.min(Gdx.graphics.getDeltaTime(), 1 / 30f));
		stage.draw();
	}

	@Override
	public void resize (int width, int height) {
		stage.getViewport().update(width, height, true);
	}

    @Override
    public void dispose() {
       stage.dispose();
       skin.dispose();
    }
}

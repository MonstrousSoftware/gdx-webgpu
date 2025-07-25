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
import com.badlogic.gdx.graphics.Cursor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.utils.WgScreenUtils;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

public class SystemCursorTest extends GdxTest {
	private WgStage stage;
	private WgSkin skin;


	@Override
	public void create () {
		super.create();
		stage = new WgStage(new ScreenViewport());
		skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

		Gdx.input.setInputProcessor(stage);

		Table table = new Table();
		table.setFillParent(true);
		stage.addActor(table);

		for (final Cursor.SystemCursor cursor : Cursor.SystemCursor.values()) {
			TextButton button = new TextButton(cursor.name(), skin);
			button.addListener(new ChangeListener() {
				@Override
				public void changed (ChangeEvent event, Actor actor) {
					Gdx.graphics.setSystemCursor(cursor);
				}
			});
			table.add(button).row();
		}
	}

	@Override
	public void render () {
		super.render();
		WgScreenUtils.clear(Color.BLACK);
		stage.act();
		stage.draw();
	}

	@Override
	public void resize (int width, int height) {
		super.resize(width, height);
		stage.getViewport().update(width, height, true);
	}

	@Override
	public void dispose () {
		super.dispose();
		stage.dispose();
		skin.dispose();
	}
}

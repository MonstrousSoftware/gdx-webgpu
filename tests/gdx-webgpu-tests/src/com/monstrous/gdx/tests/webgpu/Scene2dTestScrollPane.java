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
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable;
import com.monstrous.gdx.tests.webgpu.utils.GdxTest;
import com.monstrous.gdx.webgpu.graphics.WgTexture;
import com.monstrous.gdx.webgpu.scene2d.WgSkin;
import com.monstrous.gdx.webgpu.scene2d.WgStage;

import static com.badlogic.gdx.scenes.scene2d.actions.Actions.*;

public class Scene2dTestScrollPane extends GdxTest {
	WgStage stage;
	private final FloatAction meow = new FloatAction(10, 5);
	private TiledDrawable patch;
	private TextureRegion region;


	public void create () {
		stage = new WgStage();
		//stage.setDebugAll(true);
		Gdx.input.setInputProcessor(stage);

		Skin skin = new WgSkin(Gdx.files.internal("data/uiskin.json"));

		Table screenTable = new Table();
        screenTable.setFillParent(true);

        Label introLabel = new Label(introText(), skin);
        introLabel.setWrap(true);
        introLabel.setSize(735, 120);
        ScrollPane scrollPane = new ScrollPane(introLabel, skin, "default");
        Table scrollTable = new Table();
        scrollTable.add(scrollPane).width(735).height(120);
        screenTable.add(scrollTable).width(793);

        screenTable.pack();

        stage.addActor(screenTable);

	}

    private String introText() {
        return "dfdf afsdf asd f fasdfa sdfa sdf adfkasdfkaskldfkla sdfla sdflasdfl asdfk asdklfaskl dfkl asdfkl asdkl fakl sdfakl df" +
            "jjkfsdf  fsdf sdf sdf f sd fs df sdfa sdfa sdf asdfkasd fasdfk asdk fask dfk asdfask dfak sd"+
            "ueuruwensdfj  we r wf w efwefwioefwfkdfjksjkfiowe ef w,kflwelfwl fwl fwlfl wfl wfl wf"+
            "dfdf afsdf asd f fasdfa sdfa sdf adfkasdfkaskldfkla sdfla sdflasdfl asdfk asdklfaskl dfkl asdfkl asdkl fakl sdfakl df" +
            "jjkfsdf  fsdf sdf sdf f sd fs df sdfa sdfa sdf asdfkasd fasdfk asdk fask dfk asdfask dfak sd"+
            "ueuruwensdfj  we r wf w efwefwioefwfkdfjksjkfiowe ef w,kflwelfwl fwl fwlfl wfl wfl wf"+
            "dfdf afsdf asd f fasdfa sdfa sdf adfkasdfkaskldfkla sdfla sdflasdfl asdfk asdklfaskl dfkl asdfkl asdkl fakl sdfakl df" +
            "jjkfsdf  fsdf sdf sdf f sd fs df sdfa sdfa sdf asdfkasd fasdfk asdk fask dfk asdfask dfak sd"+
            "ueuruwensdfj  we r wf w efwefwioefwfkdfjksjkfiowe ef w,kflwelfwl fwl fwlfl wfl wfl wf"+
            "dfdf afsdf asd f fasdfa sdfa sdf adfkasdfkaskldfkla sdfla sdflasdfl asdfk asdklfaskl dfkl asdfkl asdkl fakl sdfakl df" +
            "jjkfsdf  fsdf sdf sdf f sd fs df sdfa sdfa sdf asdfkasd fasdfk asdk fask dfk asdfask dfak sd"+
            "ueuruwensdfj  we r wf w efwefwioefwfkdfjksjkfiowe ef w,kflwelfwl fwl fwlfl wfl wfl wf";
    }


	public void render () {
		// System.out.println(meow.getValue());
		//Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		stage.act(Gdx.graphics.getDeltaTime());
		stage.draw();

		stage.getBatch().begin();
		//stage.getBatch().draw(region, 400, 100, 126, 126);

		// bug: drawing patch erase screen contents on its left
		//patch.draw(stage.getBatch(), 400, 100, 126, 126);
		stage.getBatch().end();
	}

	public void resize (int width, int height) {
		stage.getViewport().update(width, height, true);
	}

	public void dispose () {
		stage.dispose();
	}
}

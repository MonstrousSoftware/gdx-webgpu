package com.monstrous.gdx.webgpu.scene2d;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.SnapshotArray;
import com.monstrous.gdx.webgpu.graphics.g2d.WgSpriteBatch;

public class WgScrollPane extends ScrollPane {
    public WgScrollPane(Actor actor) {
        super(actor);
    }

    public WgScrollPane(Actor actor, Skin skin) {
        super(actor, skin);
    }

    public WgScrollPane(Actor actor, Skin skin, String styleName) {
        super(actor, skin, styleName);
    }

    public WgScrollPane(Actor actor, ScrollPaneStyle style) {
        super(actor, style);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {

        // make a copy of the children
        SnapshotArray<Actor> theChildren = new SnapshotArray<>();
        for (Actor child : getChildren())
            theChildren.add(child);
        // then delete the scrollpane's children
        clearChildren();
        // draw scroll pane without children
        super.draw(batch, parentAlpha);
        // add children back
        for (Actor child : theChildren)
            setActor(child);
        // draw children
        Rectangle localRect = Rectangle.tmp;
        localRect.set(1, 1, getScrollWidth(), getScrollHeight()); // actorArea

        // Setup transform for this group.
        validate();
        applyTransform(batch, computeTransform());
        // determine screen rectangle from local rectangle
        Rectangle screenRect = Rectangle.tmp2;
        getStage().calculateScissors(localRect, screenRect);

        // set scissor rectangle via WgSpriteBatch method (reverse Y direction)
        ((WgSpriteBatch) batch).setScissorRect((int) screenRect.x,
                (int) ((Gdx.graphics.getHeight() - screenRect.height) - screenRect.y), (int) screenRect.width,
                (int) screenRect.height);

        // now draw the children
        drawChildren(batch, parentAlpha);

        resetTransform(batch);

        // reset scissor to show full screen
        ((WgSpriteBatch) batch).setScissorRect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

}

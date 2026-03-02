package com.monstrous.gdx.webgpu.graphics.g3d;

import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.model.Node;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;

public class WgModelInstance extends ModelInstance {

    public WgModelInstance(Model model) {
        super(model);
    }

    public WgModelInstance(Model model, String... nodes) {
        super(model, nodes);
    }

    public WgModelInstance(Model model, Matrix4 transform, String... nodes) {
        super(model, transform, nodes);
    }

    @Override
    public void getRenderables(Array<Renderable> renderables, Pool<Renderable> pool) {
        for (Node node : nodes) {
            getRenderables(node, renderables, pool);
        }
    }

    protected void getRenderables(Node node, Array<Renderable> renderables, Pool<Renderable> pool) {
        if (node.parts.size > 0) {
            for (com.badlogic.gdx.graphics.g3d.model.NodePart nodePart : node.parts) {
                if (nodePart.enabled) {
                    Renderable renderable = pool.obtain();
                    renderable.userData = node; // LINK HERE
                    nodePart.setRenderable(renderable);
                    if (nodePart.bones == null) {
                        renderable.worldTransform.set(transform).mul(node.globalTransform);
                    } else {
                        renderable.worldTransform.set(transform);
                        if (renderable.bones == null || renderable.bones.length < nodePart.bones.length)
                            renderable.bones = new Matrix4[nodePart.bones.length];
                        for (int i = 0; i < renderable.bones.length; i++) {
                            if (renderable.bones[i] == null)
                                renderable.bones[i] = new Matrix4();
                            renderable.bones[i].set(nodePart.bones[i]);
                        }
                    }
                    renderables.add(renderable);
                }
            }
        }

        for (Node child : node.getChildren()) {
            getRenderables(child, renderables, pool);
        }
    }
}

12/06/2025:
Migrated from a libgdx fork to a repo organized as a gdx extension as this will make this project easier to maintain independently.

Need to fix the few modifications that were made to the libgdx core:  the use of ScissorStack by scene2d ui elements (ScrollPane and Table) and Mesh.vertices being private.

Fixed the ScissorStack issue by using a fake GL20 implementation, following the technique used by Stephen Lebed for his Vulkan implementation. It doesn't implement scissoring yet, but at least
scrollpane no longer crashes (it would have been difficult to create a subclass of scrollpane).

The 2d test samples are mostly working now.  The ModelBatch tests (3d tests) are broken because of the Mesh issue.

14/06/2025:
Mesh issue is fixed now. The 3d samples also work again.

Fixed issue with viewports. We can now use libgdx viewports, because we intercept 
the glViewport call using a fake GL20 object.  
Viewport rectangle is held in GraphicsContext so it can be set per window.
(But maybe should be set per render pass?)
Beware: viewport will create a standard Ortho cam, 
but near/far need to be set to 1/-1 for webgpu.

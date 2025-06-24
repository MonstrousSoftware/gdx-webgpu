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
Fixed in SpriteBatch by multiplying with a matrix that scales down the Z rangde from [-1 .. 1] to [0 .. 1].

todo: should probably be done for ModelBatch/perspective matrix too.

16/06/2025:
Added scissoring by intercepting GL calls and using these when creating a render pass.
Added ScissorDemo.

Noticed a bug in Scene2d demo that window cannot be dragged around. Probably the same issue as the drop down panel, always being at (0,0).
Scene2d calls setTransformMatrix with a temp matrix and then pops back to the old transform.
However SpriteBatch doesn't really flush and uses one matrix for all sprites. Only the last value of the transform is effectively used.
We now use a dynamics offset into the uniform buffer, that allows a separate projectionView matrix per flush.
This solved some scene2d issues, where actors were stuck at the bottom left position (scene2d uses push/pop view transforms).


Also: is it required during the course of a frame to set/enable/disable multiple scissor rectangles? Idem for viewports.
Maybe we should call set viewport directly on the renderpass, rather than only during the render pass building.
Does that work? It seems that renderpass.set commands set the render state to be used for the next draw.
Render state includes: viewport, scissor, blend, stencil

todo urgent: smarter queue.write after set uniform.
We could do a Queue.writeBuffer after every setUniform.
Or keep track of a dirty range to be flushed.

17/06:
SpriteBatch now uses dynamics offsets for the uniform buffer to allow different viewProjection transforms per flush.
Now one buffer is allocated of maxFlushes * flushStride.  Should create buffer using WebGPUniformBuffer(contentSize, usage, maxInstances)
so that the backing float buffer is just for one slice, and write just writes one slice. Stride calculation should be left to uniform buffer.
Content size is just the size of the matrix4.
But need WebGPUniformBuffer methods to select the active slice.  This should then be used in buffer.write for the slice offset which will then
write content size (not stride). Any buffer.setUniform method will set a dirty flag causing buffer.flush to write to GPU.  The write
will not be per uniform, but per contentsize ("slice") and could be done just before the draw call along with binding the group.
=> done.

Added (2d) particle effects.
3d particle effects will be harder to add, there are many classes and specific shaders.

todo When launching via the test chooser reaction to input becomes laggy, e.g. scene2d sliders or window dragging.

23/06:
- Added time stamp queries to match GPU time per render pass.
- Todo: caches query objects per pass in GPUTimer class; extends to compute pass.

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

24/06:
- Added wgFrameBuffer. A Frame Buffer is implemented by replacing the output texture view by a texture 
with the usage flag RenderAttachment.  The output texture view is managed by WebGPUGraphicsContext, and it has a push and pop method to change output texture. The previous value is returned by push to be stored in the FrameBuffer object, so that frame buffers can be nested.
The same applies to the depth texture.

 
- Added demo of PostProcessing with a frame buffer and a screen shader.

25/06:
Working on shadows. Have a DirectionalShadowLight which uses a FrameBuffer to capture an orthographic render from the light source.
Some trouble getting depth to work properly.  Again an issue with the matrix definition for ortho projection having a different Z range than for OpenGL.
WgDirectionalShadowLight now post-multiplies the combined matrix to compensate.
For now, it renders to a color buffer, need to make a depth shader.
 
30/06:
Added GLTF loader, still buggy.
Simplified mesh loading by creating one Mesh per GLTF primitive. (A GLTF Mesh consists of multiple primitives, which can have different vertex attributes).
Maybe it is more efficient to use a shared Mesh for multiple MeshParts, but maybe the difference is negligible.
Testing GLB loader. 

01/07:
Regarding the GLTF support, there are a few steps to import a GLTF model.
First the file is parsed to a GLTF object with many support objects such as GLTFAccesor, GLTFAnimation, etc.
This is just a pure translatation of the JSON file to equivalent Java classes.
Then the GLTF object is used to construct a ModelData object.  This ModelData object is specific to LibGDX 
and is also used to import OBJ and G3DB formats. 
The ModelData object is then passed to a Model constructor to create a Model object.

These steps are executed by the model loader, use it as follows:

        FileHandle file = Gdx.files.internal("data/g3d/gltf/Sponza/Sponza.gltf");
        model = new WgGLTFModelLoader(new JsonReader()).loadModel(file);
		instance = new ModelInstance(model);

To use asynchronous loading via the asset manager:

		AssetManager assets = new WgAssetManager();
		assets.load("data/g3d/gltf/Sponza/Sponza.gltf", Model.class);
		while(assets.update()){
			// do other stuff
		}
		model = assets.get("data/g3d/gltf/Sponza/Sponza.gltf", Model.class);
		instance = new ModelInstance(model);
		

LibGDX only support shorts as index values (i.e. 16 bit) for up to 32K index values. Some models will exceed that value, e.g. the Stanford Dragon has 131K index values.
Gdx-webgpu supports models with 32 bit indexing (int type) supporting 2 billion index values.	


03/07:
Note: some classes use BufferUtils or MemoryStack from LWJGL3. Does that make them platform dependent?

- Added module api-new-test to render a triangle via jWebGPU. Uses com.github.xpenatan.jWebGPU repo which is still under development but should
provide multi-platform support as opposed to the Java-to-WebGPU which is desktop only.

- Created alternative modules to migrate to jWebGPU backend:
  - gdx-desktop-webgpu
  - gdx-webgpu2
  - gdx-tests-webgpu2

Once the migration is complete the original modules will be deleted and the latter two modules will be renamed to lose the -2 postfix.

05/07: Made some subclasses for Tiled loading/rendering.  The logic is untouched, just need to make sure they use WgSpriteBatch and
WgTexture.  SuperKoalio demo works now.

07/07: Fog working.

08/07: Shadows working.

12/07: Doing the migration to use the jWebGPU backend which should allow multi-platform support, uses a newer WebGPU version and has a much nicer interface.
Most of the 2d classes have been ported (in the sense that it compiles), however with some serious bugs. Some may be due how WGPU exhibits different behaviour from Dawn.

- Fixed: crash on minimize.

- Fixed: crash on full-screen.  It gave problems when doing a resize from within the render loop for example because the surface 
 out texture was still in use, e.g. when pressing F11. So now, calling
resize will just set a flag and the actual resize is performed just before the render loop.

- Fixed: crash on switching textures in sprite batch (reuse of bind group). Was due to entries being disposed after creating bind group (they were being reused).

- Fixed: crash on launch another application window (e.g. TestStarter): Caused by:
  Surface is not configured for presentation. Was due to reuse of surface resource in jwebgpu. Now a new surface object is generated.

- fixed: bitmapfont looks bad. Alpha blending was broken by the gamma correction code in teh sprite batch shader.

- fixed: mipmapping code was commented out while playing around with ByteBuffer. 
 

15/07:
- Fixed loading WgModel from ModelData. Issue was index buffer filling via ShortBuffer.  Model loading works now including GTLF models. 

to do:
- DepthClearTest behaves strangely like depth sorting doesn't work.
- ShapeRenderer test gives only black screen
- SuperKaolio has incorrect collision detection? Also sky is darker in libgdx version.
- When switching between models, e.g. in LoadModelTest, it seems the wrong pipeline is used. Textures look strange.



	

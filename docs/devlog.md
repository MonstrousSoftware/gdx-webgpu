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
- Fixed: DepthClearTest behaves strangely like depth sorting doesn't work.
- Fixed: ShapeRenderer test gives only black screen. (Used ScreenUtils instead of WgScerenUtils)
- SuperKaolio :  sky is darker in libgdx version.
- Fixed: When switching between models, e.g. in LoadModelTest, it seems the wrong pipeline is used. Textures look strange.

to do/test: 
- Done: blended materials.
- cube map
- texture array

Note on max sprites. If maxSprites is > 8192 (the libgdx maximum) the short index overflow is not an issue because the GPU will read the negative short
as an unsigned int32. If there are more sprites than 16384 the index would roll over to 0 and sprites will not show correctly. (Note this only if 
there are > 16K sprites per flush). 16K should be enough for anyone and is now set as a hard limit (rather than switching to 32 bit index values for this
improbable use case).


Gradle : publishing to maven local
https://www.youtube.com/watch?v=-hYlA2AWPR4

plugins {
    id 'java-library'
    id 'maven-publish'
}
...

afterEvaluate {
    publishing {
        publications {
            mavenJava(MavenPublication) {
                from components.java
            
                    groupId = "com.monstrous.gdx-webgpu"
                    artifactId = "gdx-desktop-webgpu"
                    version = "0.1"
            
                    artifact sourcesJar
                  }
                }
    }
}

tasks.register('sourcesJar', Jar) {
    archiveClassifier.set("sources")
    from sourceSets.main.allJava
}

=====
How to prepare your app.

Use gdx-liftoff as normal.

In the core module add the following to build.gradle:

    dependencies {
        implementation 'com.monstrous.gdx-webgpu:gdx-webgpu:0.1'
    }

In the lwjgl3 module add the following to build.gradle:

    dependencies {
        implementation 'com.monstrous.gdx-webgpu:gdx-desktop-webgpu:0.1'
    }

In the lwjgl3 module add the following file:

Launcher.java:
```java
    package com.monstrous.test.lwjgl3;
    
    import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplication;
    import com.monstrous.gdx.webgpu.backends.desktop.WgDesktopApplicationConfiguration;
    import com.monstrous.test.Main;
    
    public class Launcher {
    public static void main (String[] argv) {
    
            WgDesktopApplicationConfiguration config = new WgDesktopApplicationConfiguration();
            config.setWindowedMode(640, 480);
            config.setTitle("WebGPU");
            config.enableGPUtiming = false;
            config.useVsync(true);
            new WgDesktopApplication(new Main(), config);
        }
    }
```

In the lwjgl3 module change the following line in build.gradle:

    //mainClassName = 'com.monstrous.test.lwjgl3.Lwjgl3Launcher'
    mainClassName = 'com.monstrous.test.lwjgl3.Launcher'


## 23/07: Gamma correction
The gamma correction in the model batch shader is probably wrong, but the same one in spritebatch is probably right.
For spritebatch we generally load textures from file, which will be gamma corrected already as most editing software does.
So these texture are in SRBG space.  When we project to a SRBG output surface, it means it expects linear input and will then correct it to SRGB.
Since the content is already SRGB, we need to convert it to linear for output to a SRGB surface which will then convert it back.
This code does that inverse gamma correction.

    #ifdef GAMMA_CORRECTION
        let linearColor: vec3f = pow(color.rgb, vec3f(2.2));
        color = vec4f(linearColor, color.a);
    #endif

Alternative is to convert to linear when we are reading the textures, but this is also valid assuming spritebatch works in SRGB space.
However, note if you hard code a color of a sprite, it should be SRGB values.

In ModelBatch on the other hand you are doing complex lighting calculations, for which you need to work in linear space.
So here the textures should be converted to linear on loading, calculations done in linear, and a gamma correction is then not needed for a SRGB surface.
For a RGB surface we should however do a gamma correction since the surface is not doing it: raise to the power 1/2.2

## 24/7: Texture filtering in wrapping

How are texture filtering and wrapping managed in libgdx?

Texture is derived from GLTexture which has minFilter, magFilter, uWrap and vWrap attributes.
Furthermore there is a class called TextureDescriptor which holds a Texture and filter and wrap attributes (can be null).
A TextureAttribute holds a TextureDescriptor and offsetU,V and scaleU,V (which is similar in effect to TextureRegion).
A Renderable has a Material which is derived from Attributes.

When the BaseShader sets a texture uniform to the TextureDescriptor of a texture attribute (e.g. DiffuseTexture) 
it calls the DefaultTextureBinder, which will call texture.unsafeSetWrap(TextureDescriptor.uWrap/vWrap) to synch the texture
attributes to the texture descriptor (if not null) and to issue the related GL commands.

In effect, the texture descriptor can overrule the default filter and wrapping of the texture itself.

When a Model is created from ModelData, i.e. at model loading time, the texture descriptor is filled with the wrap and filter values from the texture.
The texture is loaded from file by the TextureProvider.

TextureProvider.load(filename) will set a texture's wrap and filter to standard values.  By default: Linear, Repeat but they can be set at FileTextureProvider level. 

So the wrapping and filtering are set to default values in the texture when read from file. These are then copied to the texture descriptor for the Material and used when binding the texture to a uniform. So where, if anywhere, is the texture descriptor changed?
It seems the OBJ loader and G3D loader have no options to set wrap/filter per material.  
For GLTF, however, there is a sampler that can be set per material.

When loading via the AssetManager, texture are loaded via the TextureLoader.  This will set wrap/filter by default to Nearest/Clamp (but can be parameterized).
Note that this is different from the TextureProvider defaults.
Then again ModelLoader sets ModelParameters by default to Linear/Repeat.
When you load a model via AssetManager does it use TextureLoader for its dependent textures? Or does it let the Model use TextureProvider.

## Idea on skinning
From a writeup Doom Eternal rendering: Perform skinning in a compute shader instead of the vertex shader.
This will declutter the vertex shader greatly, and also means multiple passes (e.g. shadows) don't need to perform the same skinning.

Another interesting point: shadows from a static light source and static geometry doesn't need to be recalculated every frame. The depth buffer could just persist.


## crash
InstancingTest from the Menu:

[WebGPUStage] create shape renderer

thread '<unnamed>' panicked at src\lib.rs:4171:36:
invalid texture
note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace

thread '<unnamed>' panicked at library\core\src\panicking.rs:218:5:
panic in a function that cannot unwind
stack backtrace:
0:     0x7ffdbc65eb32 - <unknown>
1:     0x7ffdbc62c36b - <unknown>
2:     0x7ffdbc65c067 - <unknown>

[WebGPUStage] create shape renderer

thread '<unnamed>' panicked at src\lib.rs:4171:36:
invalid texture
stack backtrace:
note: Some details are omitted, run with `RUST_BACKTRACE=full` for a verbose backtrace.

thread '<unnamed>' panicked at library\core\src\panicking.rs:218:5:
panic in a function that cannot unwind
stack backtrace:
0:     0x7ffda614eb32 - <unknown>
1:     0x7ffda611c36b - <unknown>
2:     0x7ffda614c067 - <unknown>
3:     0x7ffda614e975 - <unknown>


28/07:
Weird bug fixed in CubeMapTest, related to not disposing a pixmap after creating a WgTexture.
  If you call WebGPUTestStarter and then CubeMapTest it will crash with
   the mention of "invalid texture" in the log. If you first call some other tests from
   WebGPUTestStarter it doesn't happen.  It also depends on which textures are
   loaded. E.g. data/g3d/environment/debug_* is okay, but data/g3d/environment/environment_01_* is
   not okay.

It means you can still cause this crash by doing Pixmap pm = new Pixmap( filename );
It is as if the malloc that this does need to be freed before continuing further.

Regarding texture format, now we are forcing all textures to RBGA8. But, see for example TextureAtlasTest which uses a RGBA4444,
shouldn't we allow different formats as long as they have a view matching the surface?
